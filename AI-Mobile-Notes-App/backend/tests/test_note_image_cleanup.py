"""Tests for the /api/v1/note-image-cleanup endpoint + DELETE note-file rollback."""
import base64
import pytest

# A 1x1 PNG (transparent) used both as the upload source and as the
# "cleaned" output that the mock provider returns.
TINY_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
)


async def _make_note(client, headers) -> int:
    r = await client.post("/api/v1/notes", headers=headers, json={"title": "t"})
    assert r.status_code == 200, r.text
    return r.json()["data"]["noteId"]


async def _upload_file(client, headers, note_id: int) -> int:
    files = {"files": ("test.png", TINY_PNG, "image/png")}
    data = {"noteId": str(note_id)}
    r = await client.post("/api/v1/note-files/upload",
                          files=files, data=data, headers=headers)
    assert r.status_code == 200, r.text
    return r.json()["data"]["files"][0]["fileId"]


@pytest.mark.asyncio
async def test_commit_insert_sets_kind_and_parent(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)
    file_id = await _upload_file(client, headers, note_id)

    r = await client.post("/api/v1/note-image-cleanup", headers=headers, json={
        "noteId": note_id, "fileId": file_id, "mode": "commit_insert",
    })
    assert r.status_code == 200, r.text
    data = r.json()["data"]
    assert data["kind"] == "cleaned"
    assert data["parentFileId"] == file_id
    assert data["cleanedUrl"].startswith("/static/note_files/")


@pytest.mark.asyncio
async def test_commit_new_sets_kind_original_no_parent(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)
    file_id = await _upload_file(client, headers, note_id)

    r = await client.post("/api/v1/note-image-cleanup", headers=headers, json={
        "noteId": note_id, "fileId": file_id, "mode": "commit_new",
    })
    assert r.status_code == 200, r.text
    data = r.json()["data"]
    assert data["kind"] == "original"
    assert data["parentFileId"] is None


@pytest.mark.asyncio
async def test_unknown_file_id_404(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)

    r = await client.post("/api/v1/note-image-cleanup", headers=headers, json={
        "noteId": note_id, "fileId": 99999, "mode": "commit_insert",
    })
    assert r.status_code == 404


@pytest.mark.asyncio
async def test_timeout_returns_504(client, registered_user, monkeypatch):
    import asyncio
    from app.services import image_cleanup_service

    async def _slow(*a, **kw):
        await asyncio.sleep(0.5)
        return b""

    monkeypatch.setattr(image_cleanup_service, "CLEANUP_TIMEOUT_SEC", 0.05)
    p = image_cleanup_service.get_provider()
    monkeypatch.setattr(p, "cleanup_image", _slow)

    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)
    file_id = await _upload_file(client, headers, note_id)

    r = await client.post("/api/v1/note-image-cleanup", headers=headers, json={
        "noteId": note_id, "fileId": file_id, "mode": "commit_insert",
    })
    assert r.status_code == 504, r.text
    assert "超时" in r.json()["message"]


@pytest.mark.asyncio
async def test_delete_note_file_rolls_back_cleaned(client, registered_user):
    """Cancel path: POST cleanup, then DELETE the cleaned file via /note-files/{id}."""
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)
    file_id = await _upload_file(client, headers, note_id)

    r = await client.post("/api/v1/note-image-cleanup", headers=headers, json={
        "noteId": note_id, "fileId": file_id, "mode": "commit_insert",
    })
    cleaned_id = r.json()["data"]["cleanedFileId"]

    r = await client.delete(f"/api/v1/note-files/{cleaned_id}", headers=headers)
    assert r.status_code == 200

    # Confirm cleaned file is gone from the note's file list.
    r = await client.get(f"/api/v1/notes/{note_id}", headers=headers)
    file_ids = {f["fileId"] for f in r.json()["data"]["files"]}
    assert cleaned_id not in file_ids