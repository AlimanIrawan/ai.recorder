# AI记录员 · 安卓本地录音与文字同步保存

## 项目目标
- 在本机安卓手机上本地录音，并将用户输入的文字与录音以同名文件保存（扩展名不同）
- 固定保存目录为手机“文件”App中的 `Download/airecording` 公开目录，无需每次选择目录
- 支持将本地生成的同名文件实时同步到指定 Google Drive 文件夹
- 使用 Android Studio 直接部署到个人设备

## 存储目录与命名
- 公开目录：`Download/airecording/`
- 命名规则：以“开始录音时间”为基名，格式 `YYYYMMDDHHMMSS`（例如 `20251114163415`）
- 同步保存两类文件：
  - 音频：`<basename>.m4a`（AAC，容器 m4a）
  - 文本：`<basename>.txt`
- 写入方式：通过 `MediaStore.Downloads` 插入，并设置 `RELATIVE_PATH = "Download/airecording/"`，使音频与文本都存储在同一文件夹

## 技术栈与版本
- 语言与框架：Kotlin + Android SDK（targetSdk 34），minSdk 26
- UI：Jetpack Compose 1.7.0（单页表单，快速迭代）
- 录音：`MediaRecorder`（AAC/M4A）
- 存储：`MediaStore` + `ContentResolver`
- 云同步：WorkManager + OkHttp（客户端 Service Account 签发 JWT 获取令牌，Drive v3 上传）
- 编译器：Kotlin 插件 1.9.25；Compose 编译器扩展 1.5.15
- AndroidX：已启用（`gradle.properties` 设置 `android.useAndroidX=true`，`android.enableJetifier=true`）

## 权限与合规
- 运行时权限：`RECORD_AUDIO`
- 不使用旧版 `WRITE_EXTERNAL_STORAGE`（Android 10+ 不再需要）
- 如仅保存文件无需读取列表，则不需要 `READ_MEDIA_AUDIO`（Android 13+）
- 仅本地数据，不采集或上传任何隐私信息

## 目标设备
- 机型：Redmi Note 14 5G（MIUI/HyperOS，Android 14 系列）
- 目录要求：固定到“文件”App中的 `Recordings`，不弹出目录选择器（不使用 SAF）
- 适配说明：如个别机型对顶级 `Recordings/` 写入有限制，将自动回退到 `Music/Recordings/`，并在应用内提示（保持同名基名）

## 功能流程
- 主界面：文本输入框 + `开始录音` / `结束记录` 按钮 + 目标目录展示
- 开始录音：请求 `RECORD_AUDIO` 权限，初始化并启动 `MediaRecorder`
- 结束记录：
  - 生成 `<basename>`（开始录音时间）
  - 停止并释放录音器
  - 保存音频与文本到 `Download/airecording/<basename>.m4a/.txt`
  - 返回两者 `Uri` 并提示“保存成功”，同时入队 Drive 上传任务（网络恢复自动执行）

## 架构设计
- 模块：单模块 `app`
- 包结构：
  - `ui`：Compose 屏幕与状态（输入、按钮、提示）
  - `recording`：`Recorder` 封装 `MediaRecorder` 生命周期（准备/开始/停止/释放）
  - `storage`：`NoteRepository` 通过 `MediaStore` 保存音频与文本；`FileNamer` 生成基名
  - `domain`：`NoteEntry`（`displayName`、`timestamp`、`audioUri`、`textUri`）

## 构建与部署（Android Studio）
- 打开本项目根目录下子文件夹 `android-recorder`
- 连接手机（开启开发者模式与 USB 调试）
- 选择设备后点击 `Run` 直接安装运行
- 生成本地 APK：`Build > Build APK(s)`，在设备上启用“允许安装未知来源”后手动安装
- 云同步配置：
  - 将你的 Service Account JSON 复制为 `app/src/main/assets/sa.json`
  - 在 `android-recorder/local.properties` 添加 `DRIVE_FOLDER_ID=<你的folderId>`，重新同步后运行

## 验证标准
- 在系统“文件”App看到 `Download/airecording/<basename>.m4a` 与 `Download/airecording/<basename>.txt`
- Drive 目标文件夹出现同名文件（扩展名对应）
- 无网络保存后，在网络恢复时自动补传成功
- 多次记录不覆盖（时间戳保证唯一）
- 权限拒绝与异常录音时提示明确，不崩溃

## 兼容性与回退策略
- 若 `Download/airecording/` 写入受限：回退到 `Download/Recording/` 或 `Music/Recordings/` 并提示
- 若 `MediaRecorder` 不稳定：回退至 3GP/AAC 默认参数
- 云同步失败：保留队列并自动重试；可关闭同步开关恢复纯本地

## 后续开发计划
- 阶段 1（当前）：建立工程骨架与核心保存逻辑，完成单页 UI
- 阶段 2：设置页（可选），允许修改相对路径与音频参数
- 阶段 3：稳定性与边界验证，完善错误提示与日志

---

说明：本 README 已根据“固定保存到手机‘文件’App中的 Recordings、仅本地保存、Android Studio 直接部署、无需每次选择目录”的确认要求而撰写。收到您的确认后，将按此方案开始编码实现与验证。
- 方案B：后端代理 + Service Account 上传
  - 客户端行为：本地保存成功后，将音频与文本通过 `multipart/form-data` POST 到你提供的后端 `BACKEND_UPLOAD_URL`
  - 后端行为：持有 Service Account JSON，调用 Drive v3 将文件写入 `folderId`，返回 200 表示成功
  - 配置：在 `android-recorder/local.properties` 添加 `BACKEND_UPLOAD_URL=https://<你的后端>/upload` 与 `DRIVE_FOLDER_ID=<folderId>`
  - 后端接口要求：
    - 方法：`POST /upload`
    - 表单字段：`folderId`、`name`、`mime`、`file`（二进制）
    - 返回：`200` 成功，其它为失败，返回 JSON 错误体
  - 安全：Service Account JSON 仅存放在后端；Drive 文件夹需共享给该 Service Account 邮箱并授予“编辑”权限
