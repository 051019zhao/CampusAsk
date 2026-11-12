# CampusAsk RAG 升级说明

基于 `051019zhao/CampusAsk` 的 `main` 提交 `b457d78c72ab08c60384fb2e9d542b2a3c6ff243`。

## 已实现的行为

聊天页和 `/api/qa/ask` 使用新的 RAG 流程：文档解析 → 重叠分块 → embedding → 持久化索引 → 语义与关键词混合检索 → 组装上下文 → 模型回答 → 返回来源片段。公开校园知识与用户上传资料参与同一次检索，用户上传资料限定在本人当前会话。游客可查询公开知识，文件操作和长时记忆需要登录。

| 能力 | 当前实现 |
| --- | --- |
| 数据源 | 原有 MySQL `knowledge` 表；聊天页上传文件 |
| 文件格式 | UTF-8 CSV、DOCX 正文段落与表格、UTF-8 TXT/Markdown |
| CSV | 支持表头、双引号转义、带逗号字段、带换行字段，每行保留列名 |
| 分块 | 每块至多 800 个 UTF-16 字符，重叠 120 字符，避开代理对边界 |
| 索引 | MySQL 保存片段、向量、来源、片段序号、模型版本；SHA-256 检测重复与知识变更 |
| 检索 | 余弦相似度占 80%，关键词覆盖占 20%，过滤分数低于 0.30 的候选，取前 6 个片段 |
| 多轮追问 | 短问题结合最近一个用户问题进行检索；生成阶段带入最近对话 |
| 引用 | 回答提示模型使用 `[S1]` 等编号；页面可展开文件名、片段序号及对应原文 |
| 故障处理 | 向量服务缺失或故障时使用关键词检索；回答模型故障时返回检索原文并标记降级，不写入记忆 |
| 数据隔离 | 用户身份来自已验证的登录信息，上传、查询和删除均带用户与会话条件 |
| 界面 | 导入进度状态、附件列表、移除按钮、记忆开关、删除记忆、来源展开 |

保留原有提问记录与统计入口；关闭旧流程的启动预热，避免新流程启动时产生旧回答和额外调用。原有联网搜索代码仍保留，但新 RAG 回答只使用已检索资料，不自动把网络搜索结果写入知识库。

知识条目的标题、分类、关键词和正文共同参与嵌入与关键词检索；更新分类或关键词也会触发增量重建。回答提示要求按事实标注真实支持的来源；无相关资料或资料冲突时说明依据不足和待核实条件。知识内容编写建议见 [知识库内容指南](docs/knowledge-authoring.md)。

## 长时记忆策略

1. **近期窗口**：MySQL 保存成功问答，生成时读取当前会话最近 6 轮，按时间顺序加入提示。
2. **滚动摘要**：保留当前会话最近 20 个用户表述，截取末尾至多 2400 字符，作为可重建的提取式摘要。它不是额外调用模型生成的摘要。
3. **跨话题召回**：把成功问答转为向量，从本人最近至多 200 条历史中检索 3 条相关记忆。新话题使用新会话 ID，仍可召回本人的历史。
4. **上下文预算**：历史单条片段与回答截断后送入模型，避免把全部历史直接拼接进去。
5. **遗忘与隔离**：关闭开关后，本轮不读取或新增记忆；删除历史记忆会删除当前用户的问答记忆和摘要。关闭开关不会自动删除以前保存的记忆。
6. **保留期限**：问答记忆与私人附件保留 90 天。独立定时维护默认每小时清理；查询会同步过滤过期记录，不依赖提问触发物理清理。摘要只使用有效历史重建。

记忆仅用于理解用户背景，不作为校园政策依据；系统提示要求优先采用当前用户的纠正。已有业务搜索日志不属于 RAG 记忆，删除记忆不会删除业务日志或浏览器的最近搜索列表。

同一浏览器标签页会按账号保存当前会话 ID，刷新后重新加载附件列表；回答气泡不做本地持久化。新话题不再使用旧话题的附件，旧附件会按保留期限清理；若需要立即删除，请先在旧话题移除附件。

## 启用方法

需要 Java 17、MySQL，以及现有项目的登录、数据库等配置。仓库没有提交原有 `application.yml`，请继续使用你本机的配置；不要把密码或 API key 写入代码。

数据库账号需要有创建四张 RAG 表的权限。启动时会执行幂等建表，也可由数据库管理员预先执行 `db/rag.sql`。不会修改或删除已有业务表。

新建 RAG 表包含 `rag_document_expiry(created_at)` 与 `rag_turn_expiry(created_at)` 索引，用于 90 天过期清理。若已有 RAG 表在本次更新前创建，`CREATE TABLE IF NOT EXISTS` 不会补加索引；请先用 `SHOW INDEX FROM rag_document` 和 `SHOW INDEX FROM rag_turn` 检查，再由数据库管理员按需执行 `ALTER TABLE rag_document ADD INDEX rag_document_expiry(created_at)`、`ALTER TABLE rag_turn ADD INDEX rag_turn_expiry(created_at)`。

复制以下配置到现有配置文件中，或启用新增的 `rag` profile：

```properties
rag.embedding.url=${RAG_EMBEDDING_URL:}
rag.embedding.key=${RAG_EMBEDDING_KEY:}
rag.embedding.model=${RAG_EMBEDDING_MODEL:}
rag.chat.url=${RAG_CHAT_URL:https://api.deepseek.com/chat/completions}
rag.chat.model=${RAG_CHAT_MODEL:deepseek-chat}
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

`RAG_EMBEDDING_URL` 必须是完整的 embedding 接口地址；请求格式为 `{"model":"模型名称","input":"文本"}`，响应需包含 `data[0].embedding` 数值数组。生成模型继续使用原有 `deepseek.api.key`，聊天接口需返回 `choices[0].message.content`。

在 PowerShell 中设置环境变量，值替换为你选择的模型服务配置：

```powershell
$env:RAG_EMBEDDING_URL = '你的完整向量接口地址'
$env:RAG_EMBEDDING_MODEL = '你的向量模型名称'
$env:RAG_EMBEDDING_KEY = '你的向量服务密钥'
$env:DEEPSEEK_API_KEY = '你的现有回答模型密钥'
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=rag'
```

前端：

```powershell
cd frontend
npm ci
npm run serve
```

单文件不超过 5 MB，解析后不超过 20 万字符，每会话最多 10 份附件。DOCX 不做图片 OCR，旧版 `.doc` 不支持；CSV 需要 UTF-8 编码。

首次提问会为已有知识建立向量索引；之后至少间隔 30 秒检查变更，刷新时一次读取公开知识指纹，仅重新嵌入内容或模型配置改变的知识条目。首次初始化可能较慢并产生 embedding 费用。切换向量模型后，公开知识会重建索引；已有私人附件可按关键词继续使用，若需新模型语义检索，请移除并重新导入。旧模型历史记忆可按关键词召回，新的问答使用新模型。

## 接口

| 方法 | 路径 | 参数与用途 |
| --- | --- | --- |
| POST | `/api/qa/ask` | JSON：`keyword`、`sessionId`、`memoryEnabled`；缺少会话 ID 时生成一次性 ID |
| POST | `/api/qa/rag/documents` | multipart：`file`、`sessionId`；解析与索引完成后返回 |
| GET | `/api/qa/rag/documents` | query：`sessionId`；当前用户的附件列表 |
| DELETE | `/api/qa/rag/documents/{id}` | query：`sessionId`；移除本人当前会话的附件及片段 |
| DELETE | `/api/qa/rag/memory` | 删除本人全部 RAG 记忆和摘要 |
| POST | `/api/qa/clear-session` | JSON：`sessionId`；删除本人该会话的记忆、摘要和附件 |

登录请求沿用项目原有的 `token` 请求头。回答保留 `answer`、`source`，新增 `citations`、`retrievalMode`、`memoryEnabled`、`memorySaved`。模型回答成功而记忆保存失败时，仍返回回答，并标记 `memorySaved=false`。

## 验证结果

2026-11-10 已执行：

- 后端 Java 17 完整验证与 61 项测试通过：`mvn verify`。
- 存储测试使用 H2 的 MySQL 兼容模式，验证重新创建服务对象后读取历史、事务回滚、用户/会话隔离、级联删除、窗口截取和过期清理。
- HTTP 测试使用本地测试服务器，验证真实请求序列化、向量解析、带引号与换行的回答、空响应与服务错误。
- CSV、DOCX 段落/表格、文件限制、跨话题召回、关闭记忆、失败不写半成品等核心行为有测试覆盖。
- 前端生产构建通过，变更文件的定向 ESLint 检查通过。路由已改为按需加载，入口资源约 143 KiB。

尚未进行真实模型质量评测、生产 MySQL 联调及浏览器端到端测试。内置 HTTP 测试不代表真实模型已经连通；没有使用或索取你的密钥。本次浏览器预览无法连接本机服务，因此没有宣称已完成视觉验收。

当前检索会在应用内扫描授权范围的向量，适合校园项目的小规模知识库，尚不是大规模向量索引服务。阈值和召回质量应在接入实际中文向量模型后，用校园问题集校准。代码没有编造准确率、耗时或性能提升数字。

## 应用更新包

更新包只包含新增/修改文件、SQL、测试与本文档。可在原仓库根目录执行：

```powershell
git apply --check CampusAsk-Professional.patch
git apply CampusAsk-Professional.patch
```

如当前分支已改动同一文件，请先比较补丁再合并。也可以将 ZIP 中的 `changes/` 内容合入仓库。更新包不包含原仓库的图片、Word 文档、依赖目录或密钥，部署与远程审核状态见本轮交付说明。
