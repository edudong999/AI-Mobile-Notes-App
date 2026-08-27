# AI Operations Loading States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 3 个耗时 AI 操作（润色/翻译/出题）添加全屏半透明遮罩加载态，提升用户对处理进度的感知。

**Architecture:** Fragment 内嵌 overlay View（不引入 dialog），通过 show/hide Visibility 控制；每操作进入回调前显示遮罩 + 提示语，结束（成功/失败）后关闭。沿用现有 isLoading/isSaving/isDeleting 旗标模式，新增 isAiRunning 防重复点击。

**Tech Stack:** Android (Java), AndroidX ConstraintLayout, FrameLayout, ProgressBar

**Spec:** `docs/superpowers/specs/2026-08-19-ai-loading-states-design.md`

---

## File Structure

| 文件 | 操作 | 职责 |
|---|---|---|
| `frontend/ai_photo/src/main/res/layout/overlay_ai_loading.xml` | Create | 蒙层 FrameLayout + 中央 ProgressBar + TextView |
| `frontend/ai_photo/src/main/res/values/strings.xml` | Modify | 加 4 条文案 |
| `frontend/ai_photo/src/main/res/layout/fragment_note_detail.xml` | Modify | 在 ConstraintLayout 末尾 include overlay |
| `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java` | Modify | view 引用、`isAiRunning` 旗标、helper、3 个 callback 接点 |

---

## Task 1: 新建 overlay_ai_loading.xml

**Files:**
- Create: `frontend/ai_photo/src/main/res/layout/overlay_ai_loading.xml`

- [ ] **Step 1: 写入以下完整内容**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ai_loading_overlay"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:background="#CCFFFFFF"
    android:clickable="true"
    android:focusable="true"
    android:elevation="16dp"
    android:visibility="gone">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center">

        <ProgressBar
            android:id="@+id/ai_loading_spinner"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center" />

        <TextView
            android:id="@+id/ai_loading_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textSize="14sp"
            android:textColor="#222222" />
    </LinearLayout>
</FrameLayout>
```

注：根布局宽高用 `0dp` 是因为在 ConstraintLayout 父容器里会被约束填满。若 include 到其它容器，需在 include 处调整。

---

## Task 2: 加 4 条 strings

**Files:**
- Modify: `frontend/ai_photo/src/main/res/values/strings.xml`

- [ ] **Step 1: 在文件末尾追加（找个合适位置如已有 `note_ai_*` 附近）**

```xml
<string name="ai_loading_polish">正在润色中…</string>
<string name="ai_loading_translate">正在翻译中…</string>
<string name="ai_loading_questions">正在生成题目中…</string>
<string name="ai_loading_in_progress">处理中…</string>
```

---

## Task 3: include overlay 到 fragment_note_detail.xml

**Files:**
- Modify: `frontend/ai_photo/src/main/res/layout/fragment_note_detail.xml`

- [ ] **Step 1: 在 `</androidx.constraintlayout.widget.ConstraintLayout>` 前紧邻位置插入**

```xml
    <include
        android:id="@+id/ai_loading_overlay"
        layout="@layout/overlay_ai_loading"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />
```

注：作为 ConstraintLayout 最后一个子节点，绘制顺序保证它在最上面；`elevation="16dp"` 在 include 的根 FrameLayout 内已设置。

---

## Task 4: 修改 NoteDetailFragment.java（声明字段 + helper）

**Files:**
- Modify: `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java`

- [ ] **Step 1: 加 view 字段（约 38 行 `actionProgress` 字段下方）**

在现有字段块（`private ProgressBar actionProgress;` 后）添加：

```java
    private View aiOverlay;
    private TextView aiOverlayLabel;
    private volatile boolean isAiRunning = false;
```

- [ ] **Step 2: 在 `onViewCreated` 找 view（约 85 行 `actionProgress = ...` 后）**

紧邻 `actionProgress = view.findViewById(R.id.action_progress);` 后加：

```java
        aiOverlay = view.findViewById(R.id.ai_loading_overlay);
        aiOverlayLabel = view.findViewById(R.id.ai_loading_label);
```

- [ ] **Step 3: 加 helper 方法（紧邻 `setProgress(...)` 方法之后，约 176 行后）**

```java
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
```

---

## Task 5: 把 helper 接入 3 个 AI 操作

**Files:**
- Modify: `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java`

### 5a) 润色（runPolish）

- [ ] **Step 1: 修改 `runPolish` 方法（约 439 行）**

替换为：

```java
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
```

### 5b) 翻译（runTranslate）

- [ ] **Step 2: 修改 `runTranslate` 方法（约 464 行）**

替换为：

```java
    private void runTranslate(String lang) {
        if (isAiRunning) {
            Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = readCurrentText();
        if (TextUtils.isEmpty(text) || text.equals(getString(R.string.note_detail_body_empty))) {
            Toast.makeText(getContext(), R.string.note_detail_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        showAiOverlay(R.string.ai_loading_translate);
        ai.translate(noteId, lang, text, new NoteAiActions.OnTextResult() {
            @Override public void onResult(String s) {
                hideAiOverlay();
                if (getView() == null) return;
                showDiffDialog("translate", text, s);
            }
            @Override public void onError(String err) {
                hideAiOverlay();
                if (getView() == null) return;
                Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
            }
        });
    }
```

### 5c) 出题（btn_questions click listener）

- [ ] **Step 3: 修改 `btn_questions` click 监听器（约 122-132 行）**

将原：

```java
        view.findViewById(R.id.btn_questions).setOnClickListener(v ->
            ai.generateQuestions(noteId, new NoteAiActions.OnQuestionsResult() {
                @Override public void onResult(List<com.ai_photo.data.model.note.QuestionItem> items) {
                    if (getView() == null) return;
                    androidx.navigation.fragment.NavHostFragment.findNavController(NoteDetailFragment.this)
                        .navigate(R.id.action_to_question_bank, makeNavArgs(noteId));
                }
                @Override public void onError(String err) {
                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                }
            }));
```

替换为：

```java
        view.findViewById(R.id.btn_questions).setOnClickListener(v -> {
            if (isAiRunning) {
                Toast.makeText(getContext(), R.string.ai_loading_in_progress, Toast.LENGTH_SHORT).show();
                return;
            }
            showAiOverlay(R.string.ai_loading_questions);
            ai.generateQuestions(noteId, new NoteAiActions.OnQuestionsResult() {
                @Override public void onResult(List<com.ai_photo.data.model.note.QuestionItem> items) {
                    hideAiOverlay();
                    if (getView() == null) return;
                    androidx.navigation.fragment.NavHostFragment.findNavController(NoteDetailFragment.this)
                        .navigate(R.id.action_to_question_bank, makeNavArgs(noteId));
                }
                @Override public void onError(String err) {
                    hideAiOverlay();
                    if (getView() == null) return;
                    Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
                }
            });
        });
```

注：保留 OCR (`btn_ocr`) 和 摘要 (`btn_summary`) 现有行为不动。

---

## Task 6: 编译验证

- [ ] **Step 1: 重新构建 APK**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album/frontend"
./gradlew :ai_photo:assembleDebug --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 若编译失败，按错误信息修正**

常见错误：
- `cannot find symbol: ai_loading_overlay` → Task 3 没 include，回到 Task 3 补上
- `cannot find symbol: ai_loading_label` → Task 4 Step 2 没 findViewById，补上

---

## Task 7: 手动验证（用户执行）

- [ ] **Step 1: 安装并启动**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album/frontend"
./gradlew :ai_photo:installDebug
```

- [ ] **Step 2: 验证 4 个场景
1. 进入笔记详情
2. 点「润色」→ 选 polish → 蒙层出现「正在润色中…」+ spinner → 结果对话框出来后遮罩消失
3. 点「翻译」→ 选 English → 蒙层「正在翻译中…」→ 结果 dialog 出来后遮罩消失
4. 点「出题」→ 蒙层「正在生成题目中…」→ 跳转后遮罩消失
5. 点「OCR」/「摘要」→ **不出现蒙层**，保持 toast 行为

---

## Task 8: 提交

- [ ] **Step 1: Stage 修改**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album"
git add \
  frontend/ai_photo/src/main/res/layout/overlay_ai_loading.xml \
  frontend/ai_photo/src/main/res/layout/fragment_note_detail.xml \
  frontend/ai_photo/src/main/res/values/strings.xml \
  frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteDetailFragment.java
```

- [ ] **Step 2: 提交**

```bash
git commit -m "feat(android): show loading overlay during AI polish/translate/questions"
```