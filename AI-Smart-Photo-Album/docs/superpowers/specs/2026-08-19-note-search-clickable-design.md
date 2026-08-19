# Note Search Results Clickable Design

**Date**: 2026-08-19
**Scope**: Android frontend only — `frontend/ai_photo`
**Status**: Approved (user said "继续" after design presentation)

## Problem

笔记搜索 (`NoteSearchFragment`) 已经能返回命中列表，但每条 hit 不可点击。用户在搜索框输入关键词后看到结果卡，却无法从搜索跳转到对应笔记的详情页。

## Goal

让每条搜索结果可点击：点击后跳转至对应笔记的 `NoteDetailFragment`。

## Non-Goals

- 不修改后端 schema / service / router
- 不修改 `NoteDetailFragment`
- 不修改 item 布局（ripple 反馈已就绪）
- 不在搜索层做"笔记已删除"的兜底（由 `NoteDetailFragment` 现有逻辑负责）

## Environment (Confirmed)

| 项 | 状态 |
|---|---|
| 导航系统 | Android Navigation Component（`NavHostFragment` + `NavController`） |
| nav action | `R.id.action_to_note_detail` 在 `res/navigation/nav_graph.xml:38-39` 定义，所有 fragment 可用 |
| noteId 参数键 | 字面量 `"noteId"`，通过 `Bundle.putLong` 传递 |
| `SearchHit.noteId` | 已有 `public long noteId` 字段 |
| item ripple | `item_note_search_hit.xml:7` 已设 `?attr/selectableItemBackground` |

## Design

### Approach

镜像 `NotesAdapter` + `NotesFragment:70-75` 的回调接口模式：

- Adapter 只暴露"hit 被点击"事件，不感知 Navigation
- Fragment 把事件转成 navigation

### Changes

改动文件 **1 个**：

#### `frontend/ai_photo/src/main/java/com/ai_photo/ui/notes/NoteSearchFragment.java`

**1) `SearchAdapter` 加回调接口**

```java
interface OnHitClick { void onHit(SearchHit hit); }
```

**2) 构造器接收回调**

```java
SearchAdapter(OnHitClick onClick) { ... }
```

**3) `onBindViewHolder` 给 `itemView` 设点击监听**

```java
h.itemView.setOnClickListener(v -> onClick.onHit(hit));
```

**4) Fragment 在 `onViewCreated` 构造 adapter 时传 navigation lambda**

```java
adapter = new SearchAdapter(hit -> {
    Bundle args = new Bundle();
    args.putLong("noteId", hit.noteId);
    NavHostFragment.findNavController(NoteSearchFragment.this)
        .navigate(R.id.action_to_note_detail, args);
});
```

### Unchanged

- `res/layout/item_note_search_hit.xml` — ripple 已就绪
- `data/model/note_search/SearchHit.java` — `noteId` 字段已存在
- 后端（schema / service / router）
- `NoteDetailFragment.java`

## Error Handling

| 情况 | 处理 |
|---|---|
| 后端命中但笔记已被删除 | 不在搜索层处理；`NoteDetailFragment` 加载失败走其既有空态/错误态 |
| `hit.noteId` 为 0 | 后端 schema 强制 `noteId: int`，理论不会出现；不加防御代码 |

## Verification

1. **编译**: `cd frontend && ./gradlew :app:assembleDebug --rerun-tasks` 成功
2. **手测**:
   - 启动 app → 进入搜索 → 输入关键词 → 点中任一 hit
   - 期望：跳转到 `NoteDetailFragment`，显示该笔记详情
   - 视觉：点按时有 ripple 高亮反馈

## Notes

- 模式与 `NotesAdapter` / `NotesFragment` 完全一致，无新概念引入
- 不需要新增任何依赖、不需要改 build.gradle