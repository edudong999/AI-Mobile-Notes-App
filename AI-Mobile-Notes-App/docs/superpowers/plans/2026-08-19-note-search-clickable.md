# Note Search Results Clickable Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `NoteSearchFragment` 中每条搜索结果可点击，点击后跳转至对应笔记的 `NoteDetailFragment`。

**Architecture:** 镜像 `NotesAdapter` + `NotesFragment` 的回调接口模式。Adapter 暴露 `OnHitClick` 接口，Fragment 传入 navigation lambda。Item 布局已带 ripple 背景，无需改 layout。

**Tech Stack:** Android (Java), AndroidX RecyclerView, Android Navigation Component

**Spec:** `docs/superpowers/specs/2026-08-19-note-search-clickable-design.md`

---

## File Structure

| 文件 | 操作 | 职责 |
|---|---|---|
| `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteSearchFragment.java` | Modify | `SearchAdapter` 加回调；Fragment 构造 adapter 时传 navigation lambda |

无新增文件，无删除文件。

---

## Task 1: 给 SearchAdapter 加 OnHitClick 回调接口

**Files:**
- Modify: `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteSearchFragment.java:96-122`

镜像 `NotesAdapter.java:22` 的 `OnClick` 接口模式。

- [ ] **Step 1: 在 `SearchAdapter` 类内添加 `OnHitClick` 接口**

修改 `SearchAdapter`（约 96 行起），在 `static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {` 紧下方插入：

```java
static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {
    interface OnHitClick { void onHit(SearchHit hit); }

    private final OnHitClick onClick;
    private final List<SearchHit> items = new ArrayList<>();

    SearchAdapter(OnHitClick onClick) {
        this.onClick = onClick;
    }
    ...
```

注意：
- 删除原 `private final List<SearchHit> items = new ArrayList<>();` 这一行（已合并到上面片段）
- 在 `submit()` 方法签名保持不变
- 删除原无参构造器（若有）

完整 `SearchAdapter` 修改后如下（替换原 `static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> { ... }` 整段）：

```java
static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.VH> {
    interface OnHitClick { void onHit(SearchHit hit); }

    private final List<SearchHit> items = new ArrayList<>();
    private final OnHitClick onClick;

    SearchAdapter(OnHitClick onClick) {
        this.onClick = onClick;
    }

    void submit(List<SearchHit> data) {
        items.clear(); if (data != null) items.addAll(data); notifyDataSetChanged();
    }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_note_search_hit, parent, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchHit hit = items.get(pos);
        h.title.setText(hit.title != null ? hit.title : "(无标题)");
        h.snippet.setText(hit.snippet != null ? hit.snippet : "");
        h.score.setText(String.format(java.util.Locale.getDefault(), "%.2f", hit.score));
        h.itemView.setOnClickListener(v -> onClick.onHit(hit));
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView title, snippet, score;
        VH(View v) {
            super(v);
            title = v.findViewById(R.id.search_hit_title);
            snippet = v.findViewById(R.id.search_hit_snippet);
            score = v.findViewById(R.id.search_hit_score);
        }
    }
}
```

- [ ] **Step 2: 确认本任务不引入编译错误**

此步仅替换 SearchAdapter 内部代码，编译验证留到 Task 3。

---

## Task 2: 在 NoteSearchFragment 构造 adapter 时传 navigation lambda

**Files:**
- Modify: `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteSearchFragment.java:41`

镜像 `NotesFragment.java:70-75`：

```java
adapter = new NotesAdapter(item -> {
    Bundle args = new Bundle();
    args.putLong("noteId", item.noteId);
    NavHostFragment.findNavController(this)
        .navigate(R.id.action_to_note_detail, args);
});
```

- [ ] **Step 1: 修改 `onViewCreated` 中 adapter 的构造**

将 `adapter = new SearchAdapter();`（约 41 行）替换为：

```java
adapter = new SearchAdapter(hit -> {
    Bundle args = new Bundle();
    args.putLong("noteId", hit.noteId);
    NavHostFragment.findNavController(NoteSearchFragment.this)
        .navigate(R.id.action_to_note_detail, args);
});
```

- [ ] **Step 2: 确认 import 已就绪**

文件顶部 imports 中已有 `android.os.Bundle`（line 2）和 `androidx.navigation.fragment.NavHostFragment`（未确认）。若缺失需补充：

```java
import android.os.Bundle;
import androidx.navigation.fragment.NavHostFragment;
```

读取文件第 1-18 行验证；若 `NavHostFragment` 缺失则补上。

---

## Task 3: 编译验证

- [ ] **Step 1: 重新构建 APK（绕过增量缓存）**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album/frontend"
./gradlew :app:assembleDebug --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`，无 Java 编译错误。

- [ ] **Step 2: 若编译失败，按错误信息修正**

常见错误：
- `cannot find symbol: NavHostFragment` → 缺 import，回到 Task 2 Step 2 补上
- `SearchAdapter(java.lang.Object) not found` → 漏了构造器，回到 Task 1

---

## Task 4: 手动验证

- [ ] **Step 1: 安装 APK 到真机/模拟器**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album/frontend"
./gradlew :app:installDebug
```

- [ ] **Step 2: 手测跳转**

1. 启动 app，登录
2. 进入「AI 笔记 → 搜索」（或在主导航找到搜索入口）
3. 输入关键词，等待命中结果出现
4. **期望行为**：点中任一 hit → 跳转至 `NoteDetailFragment`，显示该笔记详情
5. **视觉**：点击瞬间 item 应有 ripple 高亮反馈（由 layout 的 `?attr/selectableItemBackground` 提供）

- [ ] **Step 3: 若跳转后页面空白/404**

- 检查后端日志，确认该 noteId 存在且 `deleted_at IS NULL`
- 搜索时已过滤 `deleted_at`，故不应出现；若出现说明用户中途删了笔记，属预期行为

---

## Task 5: 提交

- [ ] **Step 1: Stage 修改**

```bash
cd "D:/AI_Projects/AI_APP/Ai_APP/AI-Smart-Photo-Album"
git add frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteSearchFragment.java
```

- [ ] **Step 2: 提交**

```bash
git commit -m "feat(android): make note search hits clickable to open note detail"
```