# OCR / Summary Loading States Design

**Date**: 2026-08-27
**Scope**: Android frontend only — `frontend/ai_photo`
**Status**: Draft (pending user review)

## Problem

笔记详情页的润色 / 翻译 / 出题按钮已有全屏遮罩 + `isAiRunning` 锁,在请求进行中挡住所有操作(见 `NoteDetailFragment.java:139-158`、:536-559、:570-593、`2026-08-19-ai-loading-states-design.md`)。但 OCR(`btn_ocr`)和摘要(`btn_summary`)两个按钮仍是裸 toast:

```java
// 当前实现 (NoteDetailFragment.java:159-172)
view.findViewById(R.id.btn_ocr).setOnClickListener(v ->
    ai.runOcr(noteId, new com.ai_photo.util.ResultCallback<EnqueueResponse>() {
        @Override public void onSuccess(EnqueueResponse data) {
            Toast.makeText(getContext(), R.string.note_ai_ocr_queued, Toast.LENGTH_SHORT).show();
        }
        @Override public void onError(String err) { Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show(); }
    }));
```

点击后立刻弹「OCR 已入队」就完事了,既不显示遮罩,也不置 `isAiRunning`。后果:

1. 用户在 OCR / 摘要还在后端跑的同时,可以再点润色 / 翻译 / 另一份 OCR / 摘要,产生并发任务;
2. 可以去点保存 / 删除 / 导出,与异步 AI 任务叠加,本地缓存与远端状态可能错位;
3. "已入队" 的措辞误导,看起来像完成。

## Goal

把 OCR 和摘要两个按钮的行为对齐到润色 / 翻译 / 出题同一套规则:

- 点击前判 `isAiRunning`,在跑则 toast 拒收并 return;
- 进入任务 → `showAiOverlay(...)` 全屏遮罩 + 中央 spinner + 提示语,设 `isAiRunning = true`;
- 等后端异步任务真正完成(`aiStatus` 走到 `done` 或 `failed`)才收遮罩、放锁;
- 不再弹「OCR 已入队」「摘要已入队」toast(遮罩本身已表达进行中)。

## Non-Goals

- 不修改后端契约(OCR / 摘要 API 仍是入队即返回的异步模式)。
- 不实现取消功能。
- 不改 `NoteAiActions.java` 的回调接口。
- 不引入新的轮询机制,复用现有 4s `refreshStatus()`。
- 不改 OCR / 摘要之外的其它按钮(保存 / 删除 / 导出 / 编辑)原本的非 AI 行为。

## Decisions (User Confirmed)

| 问题 | 决定 |
|---|---|
| 「其他操作」锁定范围 | **全屏遮罩,跟润色 / 翻译一致**(已选) |
| 完成判定方式 | **复用现有 `refreshStatus()` 轮询 + `aiStatus` 终态判定**(已选,方案 A) |
| 现有「已入队」toast | 移除(遮罩已替代) |

## Design

### Affected Files

| 文件 | 改动 |
|---|---|
| `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java` | OCR / 摘要按钮加 `isAiRunning` 守卫与遮罩调用;`refreshStatus()` 末尾加终态收遮罩 |
| `frontend/ai_photo/src/main/res/values/strings.xml` | 新增 `ai_loading_ocr`、`ai_loading_summary` |

### Code Changes — `NoteDetailFragment.java`

1. **OCR 按钮**(替换 :159-165 的 listener):

```java
view.findViewById(R.id.btn_ocr).setOnClickListener(v -> {
    if (isAiRunning) {
        Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
        return;
    }
    showAiOverlay(R.string.ai_loading_ocr);
    ai.runOcr(noteId, new com.ai_photo.util.ResultCallback<EnqueueResponse>() {
        @Override public void onSuccess(EnqueueResponse data) {
            // 遮罩继续显示,直到 refreshStatus() 检测到 aiStatus 终态
        }
        @Override public void onError(String err) {
            hideAiOverlay();
            if (getView() == null) return;
            Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        }
    });
});
```

2. **摘要按钮**(替换 :166-172 的 listener)同样改造,label 用 `R.string.ai_loading_summary`,`onError` 同样 `hideAiOverlay` + toast。

3. **`refreshStatus()` 末尾**(在 :511-518 的 `a.runOnUiThread(() -> {...})` 内,`renderFromLocal(ne, files);` 之后加一段):

```java
String terminal = ne.aiStatus; // 终态判定:done / failed
if (isAiRunning && ("done".equals(terminal) || "failed".equals(terminal))) {
    hideAiOverlay();
}
```

不要复用 `d.aiStatus`(那是接口返回值),用已经写回本地 Entity 的 `ne.aiStatus`,与现有 `refreshStatus` 里 `aiStatus` 兜底逻辑保持一致(`d.aiStatus != null ? d.aiStatus : ne.aiStatus`)。

### String Resources — `strings.xml`

在 548-551 行的 `ai_loading_*` 区段内追加:

```xml
<string name="ai_loading_ocr">OCR 识别中…</string>
<string name="ai_loading_summary">摘要生成中…</string>
```

### Data Flow

```
[点 OCR / 摘要按钮]
        │
        ▼
 if isAiRunning ─── yes ──▶ toast "处理中…" + return
        │ no
        ▼
 showAiOverlay(ai_loading_ocr / ai_loading_summary)
   → isAiRunning = true
   → aiOverlay VISIBLE (全屏,clickable,elevation=16,挡住所有点击)
        │
        ▼
 ai.runOcr / runSummary(noteId, cb)
   cb.onSuccess: 不再弹「已入队」toast,保持 isAiRunning=true
   cb.onError:   hideAiOverlay() + toast 错误
        │
        ▼
 (后台 refreshStatus() 每 4s 拉一次 /api/v1/notes/{id})
   → 写入 ne.aiStatus 到本地
   → 主线程末尾检查:
        if (isAiRunning && aiStatus ∈ {"done", "failed"})
              hideAiOverlay() → isAiRunning = false
```

### Completion Detection — Timing

- 最有 4s 延迟(`refreshStatus` 周期),与现有「OCR / 摘要完成后让用户能看到真实 AI 输出」逻辑完全同步,不引入新延迟。
- 极快完成(罕见:后端入队前 aiStatus 已 `done`):第一次轮询就拿到终态,遮罩立即收掉,这是预期行为。

## Edge Cases

| 场景 | 行为 |
|---|---|
| 点击时已在跑(任意 AI) | toast「处理中…」return,与润色/翻译一致 |
| OCR 进行中点润色 | 点不动(被遮罩拦),与润色互斥 |
| 后端 AI 任务最终失败(`aiStatus=failed`) | 遮罩照样收掉;`d.textContent / d.summary` 未变,既有「渲染摘要/正文」流程会让 UI 保持原样 |
| 用户中途按返回离开页面 | `onDestroyView` 已把 `isAiRunning=false`(commit 76c0995),与润色/翻译一致;后台任务继续,回来后 `loadDetail()` 拉最新 |
| 极快完成 | 首次轮询拿到终态,遮罩立即收 |
| `aiStatus` 字段为 null / 缺失 | 与 `refreshStatus` 现有兜底逻辑一致:只在明确拿到 `"done"` / `"failed"` 字符串时才收遮罩 |
| OCR / 摘要按钮在错误回调路径上 | `hideAiOverlay()` + toast,与润色/翻译同款 |

## Testing

纯前端改动,后端契约不变。手工验证(无需新增单元测试覆盖,改动局限且模式与已通过的润色/翻译同源):

1. 打开任一笔记详情页;
2. 点 **OCR** → 应立刻看到全屏遮罩 + "OCR 识别中…",期间所有按钮(润色/翻译/出题/OCR/摘要/保存/删除/导出)都点不动,正文点不进编辑态;
3. 等后端跑完 → 遮罩自动消失,正文/摘要刷新成 AI 输出;
4. **摘要** 按钮重复 2-3 步;
5. 在 OCR 进行中点摘要 → 应弹 toast「处理中…」;
6. 在 OCR 进行中点保存 → 应点不动(被遮罩拦);
7. OCR 进行中按返回离开页面 → 遮罩消失;重新进入笔记 → `loadDetail()` 拉到最新状态。

## References

- `NoteDetailFragment.java:139-158`(出题按钮 — 模板)
- `NoteDetailFragment.java:159-172`(OCR / 摘要按钮 — 改造目标)
- `NoteDetailFragment.java:258-268`(`showAiOverlay` / `hideAiOverlay` 实现)
- `NoteDetailFragment.java:286-295`(`onDestroyView` 中 `isAiRunning=false` 的兜底)
- `NoteDetailFragment.java:488-521`(`refreshStatus` 轮询)
- `backend/app/models/note.py:13-19`(`AIStatus` 枚举:pending/processing/done/failed)
- `docs/superpowers/specs/2026-08-19-ai-loading-states-design.md`(上一版遮罩设计)