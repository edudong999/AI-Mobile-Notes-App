# AI Operations Loading States Design

**Date**: 2026-08-19
**Scope**: Android frontend only — `frontend/ai_photo`
**Status**: Approved (user said "继续" after design presentation)

## Problem

笔记详情页 5 个 AI 操作（润色/翻译/出题/OCR/摘要）触发后缺少加载态反馈。用户点完按钮不知道系统在处理，体验差。

## Goal

为 3 个耗时 AI 操作（润色/翻译/出题）添加全屏半透明遮罩 + 中央 spinner + 提示语。OCR/摘要保持原样（后端入队即返回、已有 toast）。

## Non-Goals

- 不修改后端
- 不修改 OCR/摘要 的现有行为
- 不实现取消功能
- 不改 `NoteAiActions.java`（callbacks 接口不变）

## Decisions (User Confirmed)

| 问题 | 决定 |
|---|---|
| 加载形式 | 全屏半透明遮罩 + 中央 spinner + 提示语 |
| OCR/摘要是否包 | **不包**（保持原 toast 行为） |
| 能否中途取消 | **不可取消**（v1 最小化） |

## Design

### Loading UI

```
┌──────────────────────────────┐
│                              │
│  ░░░░░░░░░░░░░░░░░░░░░░░░░  │ ← 半透明白底蒙层 alpha≈0.7
│  ░░░░░░░░░░░░░░░░░░░░░░░░░  │
│  ░░░░░░   ◐ ◑ ◒ ◓ ░░░░░  │  ← 中央：旋转 ProgressBar
│  ░░░░░░  正在润色中…  ░░░░░  │  ← 一行提示语
│  ░░░░░░░░░░░░░░░░░░░░░░░░░  │
│  ░░░░░░░░░░░░░░░░░░░░░░░░░  │
│                              │
└──────────────────────────────┘
```

- 半透明白底 (`#CCFFFFFF`)，覆盖整个 fragment（含 action_bar），拦截点击不响应
- 中央：圆形 indeterminate `ProgressBar` + 一行 14sp 文字
- 文字在 spinner 下方 16dp

### Files Changed

| 文件 | 操作 | 职责 |
|---|---|---|
| `frontend/ai_photo/src/main/res/layout/overlay_ai_loading.xml` | Create | 蒙层根 FrameLayout + 内嵌 ProgressBar + TextView；`android:elevation="16dp"` 保证压在 action_bar 之上 |
| `frontend/ai_photo/src/main/res/layout/fragment_note_detail.xml` | Modify | 在 `</androidx.constraintlayout.widget.ConstraintLayout>` 前作为最后一个子节点 `<include>` overlay（z-order 顶端） |
| `frontend/ai_photo/src/main/res/values/strings.xml` | Modify | 加 3 条文案 |
| `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java` | Modify | view 引用、`isAiRunning` 旗标、`showAiOverlay/hideAiOverlay` helper、3 个 callback 接点 |

### Strings

```xml
<string name="ai_loading_polish">正在润色中…</string>
<string name="ai_loading_translate">正在翻译中…</string>
<string name="ai_loading_questions">正在生成题目中…</string>
<string name="ai_loading_in_progress">处理中…</string>
```

### Code Sketch

```java
// 新增字段
private View aiOverlay;
private TextView aiOverlayLabel;
private volatile boolean isAiRunning = false;

// helper
private void showAiOverlay(int labelRes) {
    if (aiOverlay == null) return;
    isAiRunning = true;
    aiOverlayLabel.setText(labelRes);
    aiOverlay.setVisibility(View.VISIBLE);
}
private void hideAiOverlay() {
    if (aiOverlay == null) return;
    isAiRunning = false;
    aiOverlay.setVisibility(View.GONE);
}

// 接入点
// 1) runPolish 入口
private void runPolish(String action) {
    if (isAiRunning) {
        Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
        return;
    }
    String text = readCurrentText();
    if (TextUtils.isEmpty(text) || text.equals(getString(R.string.note_detail_body_empty))) {
        Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
        return;
    }
    showAiOverlay(R.string.ai_loading_polish);
    ai.polish(noteId, action, text, new NoteAiActions.OnTextResult() {
        @Override public void onResult(String s) {
            hideAiOverlay();
            if (getView() == null) return;
            showDiffDialog(action, text, s);
        }
        @Override public void onError(String err) {
            hideAiOverlay();
            if (getView() == null) return;
            Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        }
    });
}
// 2) runTranslate 同理（用 ai_loading_translate）
// 3) btn_questions click 同理（用 ai_loading_questions；成功 navigate 出去前 hideAiOverlay）
```

### Error Handling

| 情况 | 处理 |
|---|---|
| API 报错 | onError 先 `hideAiOverlay()` 再 toast |
| Fragment 在请求中销毁 | onDestroyView 不主动 hide（因为 view 已销毁）；callback 检查 `getView() == null` 后 return（沿用现有模式） |
| 用户点 overlay 区域 | 不响应（`clickable="false"` + `focusable="false"`） |
| 重复点同一按钮 | `isAiRunning` 时 return + toast「处理中…」 |

## Verification

1. **编译**：`./gradlew :ai_photo:assembleDebug --rerun-tasks` 成功
2. **手测**：
   - 进入笔记详情
   - 点「润色」选 polish → 蒙层出现 → 结果对话框出来后遮罩消失
   - 点「翻译」选 English → 同上
   - 点「出题」→ 蒙层出现 → 跳转后遮罩消失
   - 点「OCR / 摘要」→ **无蒙层**，保持原 toast 行为
   - 加载中点同一按钮 → toast「处理中…」