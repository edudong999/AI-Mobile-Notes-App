# Android Demo Frontend — Design Spec

**Date:** 2026-08-10
**Status:** Awaiting user review
**Author:** Brainstorming session
**Backend:** FastAPI on `http://10.0.2.2:8000/` (AVD → host loopback)
**Target:** Reuse existing `frontend/ai_photo/` module; cover all 27 backend endpoints end-to-end.
**Scope:** 12 screens (2 Activity + 10 Fragment), single user-facing flow.

---

## 1. Goals & Non-Goals

### Goals
- 一个可"实际使用"的 Android 演示 app：用户能登录、上传、浏览、搜索、筛选、收藏、查看 AI 分析结果、管理分类
- 覆盖后端全部 27 个 API 接口（通过 12 个屏幕）
- 真实路径，不是 UI mockup
- 用 Java + XML 传统路线（与现有 `ai_photo/` 4 个 Java 文件保持一致）
- 在 Android Studio 模拟器 (AVD) 上跑通，后端在宿主 8000 端口

### Non-Goals
- 不做 release 打包、签名、上架
- 不做 Compose、不引入 Kotlin
- 不补 `frontend/app/`（无关的 hello world 模板）
- 不做完整 instrumented test（仅 1 个 happy path）
- 不做离线缓存 / Room
- 不做深色模式 / 多语言 / 无障碍（基础最低限度除外）

---

## 2. Tech Stack

| Layer | Choice | Rationale |
|---|---|---|
| Language | Java | 现有代码一致 |
| Min SDK | 24 (Android 7.0) | 覆盖 >97% 设备，匹配现有 build.gradle.kts 推测 |
| Target SDK | 34 (Android 14) | 当前 Play 政策 |
| UI | XML + AppCompat + Material 1.12 | 传统路线 |
| Architecture | Single Activity + Navigation Component | 现代 Android 默认推荐；backstack 自动 |
| Network | Retrofit 2.9 + OkHttp 4.12 + Gson | 事实标准 |
| Image | Glide 4.16 | Java/Android 路线事实标准 |
| Async | ExecutorService + LiveData (简单) | 不引入 RxJava / Coroutines（保持 Java） |
| Storage | EncryptedSharedPreferences | JWT 加密存储 |
| Navigation | Navigation 2.7.7 + BottomNavigationView | 路径 C |
| Build | AGP 8.2 + Kotlin DSL 1.9 | 与现有 `.kts` 文件一致 |

---

## 3. Module Structure

```
frontend/ai_photo/                         [Module]
├── build.gradle.kts                       [改] 加 18 个依赖
├── src/main/AndroidManifest.xml           [改] 加权限 + 2 Activity
├── src/main/res/                          [新增] 13 layouts + nav_graph + bottom_nav
├── src/main/java/com/ai_photo/
│   ├── ai/                                [保留] TaskItem / TaskAdapter / HistoryItem / HistoryAdapter
│   ├── data/
│   │   ├── api/ApiService.java            [新增] 27 个 Retrofit 方法
│   │   ├── api/RetrofitClient.java        [新增] 单例 + 拦截器
│   │   ├── model/                         [新增] ~20 个 POJO
│   │   ├── repo/                          [新增] 5 个 Repository
│   │   └── local/SessionStore.java        [新增] EncryptedSharedPrefs 封装
│   ├── ui/
│   │   ├── login/LoginActivity.java       [新增] 内含登录 / 注册两个视图
│   │   ├── main/MainActivity.java         [新增]
│   │   ├── photos/                        [新增] 2 Fragment: PhotoList, PhotoDetail
│   │   ├── categories/                    [新增] 2 Fragment: Categories, CategoryPhotos
│   │   ├── search/                        [新增] 1 Fragment
│   │   ├── profile/                       [新增] 3 Fragment: Profile, Favorites, Statistics
│   │   ├── admin/                         [新增] 1 Fragment
│   │   └── ai/AiQueueFragment.java        [新增] 复用现有 Adapter
│   └── util/                              [新增] GlideUtil / Result<T> / ErrorMapping
├── src/test/ApiContractTest.java          [新增] 调后端校验字段名
└── src/androidTest/LoginFlowTest.java     [新增] Espresso happy path
```

**屏幕总数**：2 Activity + 10 Fragment = 12 个屏幕

**不动**：
- `frontend/app/`（无关 hello world）
- `frontend/ai_photo/src/main/java/com/ai_photo/ai/` 4 个现有文件
- 后端任何文件

---

## 4. Page Inventory (10 Fragment + 2 Activity = 12 screens)

| # | Component | API endpoints called |
|---|---|---|
| 0 | `LoginActivity` | POST /auth/login, POST /auth/register |
| 1 | `MainActivity` (NavHost + BottomNav 5 tabs) | — |
| 2 | `PhotoListFragment` | GET /photos, POST /photos/upload, POST/DELETE /photos/{id}/favorite |
| 3 | `PhotoDetailFragment` | GET /photos/{id}, PATCH /photos/{id}, POST /ai/reanalyze |
| 4 | `SearchFragment` (2 sub-tabs) | POST /photos/search, POST /photos/filter |
| 5 | `CategoriesFragment` (3 tabs: scene/emotion/tag) | GET /categories/preview, GET /categories |
| 6 | `CategoryPhotosFragment` | GET /categories/{id}/photos |
| 7 | `ProfileFragment` | GET /users/me |
| 8 | `FavoritesFragment` | GET /users/me/favorites |
| 9 | `StatisticsFragment` | GET /users/me/statistics |
| 10 | `AiQueueFragment` | GET /ai/status, POST /ai/reanalyze |
| 11 | `AdminCategoryFragment` | GET/POST/PATCH/DELETE /admin/categories, POST /admin/categories/reset |

**5 BottomNav Tabs:** Photos / Search / Categories / AI / Profile
**Stack-only pages (非 Tab):** Login / Register / PhotoDetail / CategoryPhotos / Favorites / Statistics / AdminCategory

---

## 5. Data Flow (Example: Photo Upload)

```
User taps FAB on PhotoListFragment
    ↓
Intent.ACTION_GET_CONTENT ("image/*") 启动系统选择器
    ↓ onActivityResult 拿到 content:// Uri
PhotoRepository.uploadPhoto(context, uri)
    ↓ ContentResolver.openInputStream(uri) → byte[]
    ↓ RequestBody.create(MediaType, bytes)
    ↓ MultipartBody.Part.createFormData("files", filename, body)
ApiService.uploadPhotos(@Part List<MultipartBody.Part>)
    ↓ OkHttp 拦截器注入 "Authorization: Bearer {jwt}"
    ↓ Base URL = "http://10.0.2.2:8000/"
Retrofit → POST /api/v1/photos/upload
    ↓ Response<ApiResponse<PhotoUploadResponseDto>>
Interceptor 解析 {code, message, data} envelope
    ↓ code==200 → 返回 data
    ↓ code!=200 → throw BizException(message)
PhotoListFragment 收到回调
    ↓ RecyclerView.notifyItemInserted
    ↓ 通知后端 worker 入队 (后端自动)
```

---

## 6. Error Handling

### Centralized envelope parser

```java
// OkHttp Interceptor (after response)
if (response.code() != 200) throw new HttpException(response.message());
JsonObject body = parse(response.body().string());
if (body.get("code").getAsInt() != 200) throw new BizException(body.get("message").getAsString());
return body.get("data"); // 类型擦除用 TypeToken 解决
```

### Sealed result type

```java
public abstract class Result<T> {
    public static class Success<T> extends Result<T> { public T data; }
    public static class Error extends Result<Object> { public int code; public String message; }
    public static class Network extends Result<Object> { public Throwable cause; }
}
```

### UI display rule

| Result 类型 | 显示方式 |
|---|---|
| Success | 默认无 toast；列表场景更新 UI；表单场景 Snackbar "保存成功" |
| Error (BizException) | Snackbar 显示 `message`（已含可读文案） |
| Network | Snackbar "网络异常，请检查后端是否启动 (10.0.2.2:8000)" |

### Token 失效 (401)

`SessionStore.clear()` + 启动 LoginActivity + `finishAffinity()` 清空栈。

---

## 7. Configuration

### BuildConfig

```java
public class Config {
    public static final String BASE_URL = "http://10.0.2.2:8000/";
    public static final int CONNECT_TIMEOUT_SEC = 10;
    public static final int READ_TIMEOUT_SEC = 30;
}
```

> 首启允许用户在 LoginActivity 上改 BaseUrl（点 logo 5 次进入设置），写到 SharedPreferences，覆盖默认值。覆盖范围限 `192.168.*.*` 或 `10.0.2.2`。

### `network_security_config.xml`

只对下列主机允许明文 HTTP：
- `10.0.2.2` (AVD)
- `192.168.0.0/16` (真机同 WiFi)
- `127.0.0.1` (本机)

其它走 HTTPS（虽然后端目前是 HTTP，但配置做防御性）。

---

## 8. Permissions

| Permission | 用途 | 申请时机 |
|---|---|---|
| INTERNET | 网络 | 安装时 |
| READ_MEDIA_IMAGES (API 33+) | 选图 | 首次点 FAB |
| READ_EXTERNAL_STORAGE (≤ API 32) | 选图 | 同上 |
| CAMERA | 拍照上传 | 进入拍照入口时 |

不申请：ACCESS_FINE_LOCATION / READ_CONTACTS / POST_NOTIFICATIONS（与 photo album 无关）。

---

## 9. Testing

### Manual test script (documented in `frontend/README.md`)

```
1. cd backend && uvicorn app.main:app --reload
2. Android Studio → 启动 AVD (Pixel 5, API 33)
3. Run 'ai_photo' configuration
4. App 启动 → LoginActivity
5. 输入测试账号 alice-{uuid} / secret123（先用 backend test 跑一遍注册）
   或先用 in-app "立即注册" 创建账号
6. Photos Tab → FAB → 选 2 张图 → 看缩略图出现 + Snackbar "上传成功"
7. 等 2-3 秒 → 下拉刷新 → 看到 analysisStatus 从 pending → done
8. 点照片进 Detail → 看 description / scene / emotion / tags
9. 收藏 → 切到 Profile Tab → 收藏数 = 1
10. Search Tab → 输入 "海滩" → 看到刚打的 scene 标签被命中
11. Search Tab → 切到筛选子 Tab → 选 scene=海滩 → 列表只有那张
12. Categories Tab → 选任一分类 → 看到下属照片
13. AI Tab → 看任务队列 → 点重跑 → 状态变化
14. Profile Tab → 登出 → 回到 LoginActivity
```

### Automated tests

**`src/test/ApiContractTest.java`** (JVM-only, 不需要 AVD)
- 用 OkHttp 直接调后端 27 个接口
- 断言响应 JSON 包含 interface.md 规定的字段名
- 后端改 schema 时，编译期失败 → 提醒前端同步
- 跑通命令：`./gradlew :ai_photo:testDebugUnitTest`

**`src/androidTest/LoginFlowTest.java`** (Espresso)
- 单个 happy path：注册 → 登录 → /users/me 拿到 userId
- 跑通命令：AVD 启动后 `./gradlew :ai_photo:connectedDebugAndroidTest`

---

## 10. Risk Register

| Risk | Trigger | Mitigation |
|---|---|---|
| AGP/Kotlin plugin 版本冲突 | 现有 root build.gradle.kts 没声明 plugin 版本 | 用 stable 组合 AGP 8.2.0 + Kotlin 1.9.22；提交前 `./gradlew tasks` 验证 |
| 10.0.2.2 不通 | 防火墙 / AVD 网络配置 | README 提供 `adb reverse tcp:8000 tcp:8000` 作为 fallback |
| Glide 加载 webp 失败 | 后端 make_thumbnail 异常 | 已确认 backend 跑通 79 passed，webp 生成正常 |
| 现有 4 个 Java 文件包路径不对 | `com.ai_photo.ai.TaskItem` 包路径与现有可能不同 | 新 Fragment 用相同包路径 import，避免改动 |
| EncryptedSharedPreferences 初始化失败 | 设备不支持 AndroidKeyStore | catch → 退到普通 SharedPreferences + 日志告警 |
| Gradle 首次下载依赖慢 | 国内网络 | 已在 settings.gradle.kts 配 google() + mavenCentral()，无需额外镜像 |

---

## 11. Out of Scope (Explicit)

- ❌ Release 打包 / 签名 / 上架
- ❌ Kotlin / Compose / Coroutines
- ❌ Room 离线缓存
- ❌ 深色模式 / 多语言 / 无障碍优化
- ❌ 后端改动（worker 心跳、CORS 配置等）
- ❌ iOS / Web 版本
- ❌ 真实 ML 模型权重（继续走阿里云 LLM）
- ❌ `frontend/app/` 模块（无关 hello world）

---

## 12. Open Questions

无（4 段设计已与用户确认完毕）。
