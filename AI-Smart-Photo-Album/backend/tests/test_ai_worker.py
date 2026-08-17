"""AI worker 单元测试。"""

from datetime import timedelta

import pytest
import pytest_asyncio
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import async_sessionmaker

# 与 conftest 共用 engine / DB；不再单独建 ai_worker_test.db，
# 否则 conftest 先 import app.database 会把 engine 锁在 album_test.db，
# 而 worker._claim 走 AsyncSessionLocal 会查错库。
from app.models import (  # noqa: E402,F401
    AITask,
    AITaskStatus,
    AnalysisStatus,
    Photo,
    User,
)
from app.utils.time import utcnow_naive  # noqa: E402


@pytest_asyncio.fixture
async def db(engine):
    """每个测试一个 session，且确保有一个 user_id=1 的用户。"""
    Session = async_sessionmaker(engine, expire_on_commit=False)
    async with Session() as s:
        existing = await s.get(User, 1)
        if not existing:
            s.add(User(user_id=1, username="tester", password_hash="x", email="t@x.com", status=1))
            await s.commit()
        yield s


async def _make_photo(db, user_id=1) -> int:
    """插入一张 photo 并返回 photo_id。"""
    max_id = (await db.execute(select(func.max(Photo.photo_id)))).scalar() or 0
    p = Photo(
        photo_id=max_id + 1,
        user_id=user_id, file_name=f"t{max_id+1}.jpg", file_hash=f"h{max_id+1}",
        file_size=10, original_path=f"/tmp/t{max_id+1}.jpg",
        analysis_status=AnalysisStatus.pending,
    )
    db.add(p)
    await db.commit()
    await db.refresh(p)
    return p.photo_id


async def _make_task(db, photo_id: int, **kwargs) -> int:
    """插入一个 AITask 行并返回 task_id。"""
    defaults = dict(
        status=AITaskStatus.queued,
        retry_count=0,
        max_retries=3,
    )
    defaults.update(kwargs)
    max_id = (await db.execute(select(func.max(AITask.task_id)))).scalar() or 0
    t = AITask(task_id=max_id + 1, photo_id=photo_id, **defaults)
    db.add(t)
    await db.commit()
    await db.refresh(t)
    return t.task_id


@pytest.mark.asyncio
async def test_recover_orphans_resets_stale_processing(db):
    from app.models.ai_task import AIWorker

    pid = await _make_photo(db)
    long_ago = utcnow_naive() - timedelta(seconds=200)
    await _make_task(db, pid, status=AITaskStatus.processing,
                     claimed_at=long_ago, claimed_by="dead-worker", heartbeat_at=long_ago)

    w = AIWorker(worker_id="test-w1")
    await w._recover_orphans()

    task = (await db.execute(
        select(AITask).where(AITask.photo_id == pid)
    )).scalars().one()
    assert task.status == AITaskStatus.queued
    assert task.claimed_at is None
    assert task.claimed_by is None


@pytest.mark.asyncio
async def test_recover_orphans_keeps_fresh_processing(db):
    """claimed_at 在 120s 内的 processing 不应被回收"""
    from app.models.ai_task import AIWorker

    pid = await _make_photo(db)
    recent = utcnow_naive() - timedelta(seconds=10)
    await _make_task(db, pid, status=AITaskStatus.processing,
                     claimed_at=recent, claimed_by="alive-worker", heartbeat_at=recent)

    w = AIWorker(worker_id="test-w1")
    await w._recover_orphans()

    task = (await db.execute(select(AITask).where(AITask.photo_id == pid))).scalars().one()
    assert task.status == AITaskStatus.processing
    assert task.claimed_by == "alive-worker"


@pytest.mark.asyncio
async def test_claim_returns_queued_tasks(db):
    from app.models.ai_task import AIWorker

    for _ in range(3):
        pid = await _make_photo(db)
        await _make_task(db, pid, status=AITaskStatus.queued)

    w = AIWorker(worker_id="test-claim-1")
    claimed = await w._claim(n=2)
    assert len(claimed) == 2

    rows = (await db.execute(
        select(AITask.photo_id, AITask.status, AITask.claimed_by)
        .where(AITask.photo_id.in_(claimed))
    )).all()
    for photo_id, status, claimed_by in rows:
        assert status == AITaskStatus.processing
        assert claimed_by == "test-claim-1"


@pytest.mark.asyncio
async def test_claim_skips_tasks_with_future_next_retry_at(db):
    """在已有任务的环境中插入：能拿到的只有 next_retry_at 已到的"""
    from app.models.ai_task import AIWorker

    await db.execute(delete(AITask))
    await db.commit()

    pid_ready = await _make_photo(db)
    await _make_task(db, pid_ready, status=AITaskStatus.queued, next_retry_at=None)
    pid_wait = await _make_photo(db)
    await _make_task(db, pid_wait, status=AITaskStatus.queued,
                     next_retry_at=utcnow_naive() + timedelta(seconds=600))

    w = AIWorker(worker_id="test-claim-2")
    claimed = await w._claim(n=5)
    assert claimed == [pid_ready]


@pytest.mark.asyncio
async def test_claim_atomic_does_not_double_claim(db):
    """两个 worker 同时抢同一批任务：最终只有一个抢到"""
    from app.models.ai_task import AIWorker

    pid = await _make_photo(db)
    await _make_task(db, pid, status=AITaskStatus.queued)

    w1 = AIWorker(worker_id="w1")
    w2 = AIWorker(worker_id="w2")

    r1 = await w1._claim(n=1)
    r2 = await w2._claim(n=1)
    assert len(r1) == 1
    assert r2 == []


def test_backoff_grows_exponentially():
    """验证失败重试的退避：N=1->10s, N=2->20s, N=3->40s, N=4->80s, N=5->160s, N=6->300s(cap)"""
    from app.models.ai_task import RETRY_BACKOFF_BASE_SECONDS, RETRY_BACKOFF_CAP_SECONDS

    def backoff(n: int) -> int:
        return min(RETRY_BACKOFF_CAP_SECONDS, RETRY_BACKOFF_BASE_SECONDS * (2 ** (n - 1)))

    assert backoff(1) == 10
    assert backoff(2) == 20
    assert backoff(3) == 40
    assert backoff(4) == 80
    assert backoff(5) == 160
    assert backoff(6) == 300  # 已 cap


@pytest.mark.asyncio
async def test_notify_wakes_main_loop():
    from app.models.ai_task import AIWorker
    w = AIWorker(worker_id="test-wake")
    w._wakeup.clear()
    w.notify_new_task()
    assert w._wakeup.is_set()


@pytest.mark.asyncio
async def test_on_failure_will_retry_under_max(db):
    from app.models.ai_task import _on_failure

    pid = await _make_photo(db)
    await _make_task(db, pid, status=AITaskStatus.processing, retry_count=0, max_retries=3)

    await _on_failure(pid, "test error")

    task = (await db.execute(select(AITask).where(AITask.photo_id == pid))).scalars().one()
    photo = await db.get(Photo, pid)
    assert task.status == AITaskStatus.queued
    assert task.retry_count == 1
    assert task.next_retry_at is not None
    assert photo.analysis_status == AnalysisStatus.pending


@pytest.mark.asyncio
async def test_on_failure_final_after_max_retries(db):
    from app.models.ai_task import _on_failure

    pid = await _make_photo(db)
    await _make_task(db, pid, status=AITaskStatus.processing, retry_count=3, max_retries=3)

    await _on_failure(pid, "still failing")

    task = (await db.execute(select(AITask).where(AITask.photo_id == pid))).scalars().one()
    photo = await db.get(Photo, pid)
    assert task.status == AITaskStatus.failed
    assert task.error_message == "still failing"
    assert photo.analysis_status == AnalysisStatus.failed