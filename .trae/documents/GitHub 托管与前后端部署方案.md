# 目标
- 将现有后端整理并托管到 GitHub 仓库 `ai.recorder`
- 部署后端到 Render（继续支持 iPhone 快捷指令上传→Whisper→DeepSeek→写入 Supabase）
- 新建前端（Next.js）部署到 Vercel，读取 Supabase 展示“时间轴卡片”：上方标题+时间，下面摘要+话题标签，点击进入详情页查看全文与元数据

# 目录结构
```
ai.recorder/
  backend/            # 已有：Express 服务（上传/转录/总结/入库/测试页）
    package.json
    src/server.js
  frontend/           # 新建：Next.js App Router
    package.json
    next.config.js
    src/app/
      page.tsx        # 时间轴列表页（分页）
      [id]/page.tsx   # 详情页
    src/lib/supabase.ts
  .gitignore          # 忽略 node_modules、临时文件
  .env.example        # 两端环境变量示例（不含真实密钥）
```

# 后端部署准备（Render）
- 保持 `backend/package.json` 的 `start` 使用 `node src/server.js`
- 使用 `process.env.PORT` 监听端口；上传目录使用临时目录（如 `/tmp`）
- 环境变量：
  - `OPENAI_API_KEY`（可选；也支持表单传入）
  - `SUPABASE_URL`、`SUPABASE_SERVICE_ROLE`
  - `DEEPSEEK_BASE_URL`（可选）和 `DEEPSEEK_MODEL`（可选）
- 路由：
  - `POST /api/upload-audio`（multipart）
  - `POST /api/upload-audio-url`（JSON，后端拉取音频）
  - `GET /test`（表单上传页面，便于线上验证）

# 前端实现（Vercel）
- 框架：Next.js（App Router），`@supabase/supabase-js` 读取数据
- 首页时间轴：
  - 查询 `records`（或只读视图 `records_public`），按 `started_at` 倒序
  - 卡片：标题（`title`）与时间（`started_at`），摘要（`summary`），话题标签（`tags`），位置与时长可选展示
  - 支持分页/无限滚动
- 详情页：
  - 展示全文（`text`）与全部元数据（语言、时间、位置、时长）
- 环境变量（Vercel）：
  - `NEXT_PUBLIC_SUPABASE_URL`、`NEXT_PUBLIC_SUPABASE_ANON_KEY`

# 数据安全（RLS）
- 推荐在 Supabase 建只读视图 `records_public`：仅选择安全字段；
- 为 `anon` 角色添加 `SELECT` 策略或改用 `Postgres Function + Edge Function` 输出只读数据
- 若暂时简单：对 `records` 添加 `SELECT` 策略允许匿名只读（后续再收紧）

# 推送到 GitHub
- 在本地根目录初始化 Git、添加远程 `origin` 指向 `https://github.com/AlimanIrawan/ai.recorder.git`
- 提交当前 `backend` 代码与新建 `frontend` 脚手架与通用忽略文件

# Render 配置步骤
- 新建 Web Service：
  - 部署来源：GitHub 仓库 `ai.recorder`
  - Root Directory：`backend`
  - Start Command：`npm start`
  - Node 版本：自动或指定 LTS
- 环境变量：
  - `SUPABASE_URL`、`SUPABASE_SERVICE_ROLE`
  - （可选）`OPENAI_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`
- 健康检查：`GET /test` 返回 200

# Vercel 配置步骤
- 新建项目，导入 GitHub 仓库 `ai.recorder`
- Root Directory：`frontend`
- 环境变量：`NEXT_PUBLIC_SUPABASE_URL`、`NEXT_PUBLIC_SUPABASE_ANON_KEY`
- 构建输出：自动（Next.js）

# 验证
- 后端：访问 `/test` 上传音频验证转录/总结/入库；
- 前端：访问首页查看时间轴列表，点击卡片进入详情页；

# 之后可选增强
- iPhone 直传 Supabase Storage（预签名）→ 后端按 URL 转录
- 标签点击过滤、语言标记、地图位置点
- 说话人分离与主题聚类