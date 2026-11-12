# CampusAsk

面向校园生活场景的问答与社区应用。包含知识管理、资料问答、长时记忆、论坛互动，以及可审核的官网知识采集。

## 技术与模块

| 层次 | 实现 |
| --- | --- |
| 前端 | Vue 3、Vue Router、Vue CLI、Axios；页面按路由加载 |
| 后端 | Java 17、Spring Boot 4、MyBatis、JdbcTemplate |
| 数据 | MySQL；H2 用于隔离的自动化测试 |
| 问答 | 文档分块、语义向量与关键词混合检索、来源片段、模型故障降级 |
| 记忆 | 最近 6 轮、提取式摘要、本人历史召回、90 天保留及删除 |
| 官网采集 | jsoup 正文提取、定时任务、robots 规则、内容指纹、审核发布 |
| 工程检查 | GitHub Actions；后端测试与打包、前端静态检查与生产构建 |

目录：`backend/` 后端，`frontend/` 前端，`db/` 建表脚本，`docs/` 运维与接口说明。

## 本地启动

需要 Java 17、Node.js 22 或 24、MySQL 8。数据库使用 UTF-8。以下是本地开发步骤，不会连接生产环境。

1. 创建空数据库和具有该数据库权限的专用账号。在空数据库执行 `db/schema.sql`。该脚本不包含示例账号和 `DROP TABLE`；已有数据库请先比较表结构，勿重新导入旧的历史数据导出文件。
2. 将 `backend/src/main/resources/application-example.properties` 复制为同目录下的 `application.properties`，该本地文件已被忽略。
3. 在运行后端的终端设置环境变量：

```powershell
$env:DB_USER = '你的数据库账号'
$env:DB_PASSWORD = '你的数据库密码'
$env:JWT_SECRET = '替换为至少32字节的随机密钥'
# 可选：启用模型服务；不配置时仍可返回关键词检索原文。
$env:DEEPSEEK_API_KEY = '你的回答服务密钥'
$env:RAG_EMBEDDING_URL = '你的完整向量接口地址'
$env:RAG_EMBEDDING_MODEL = '你的中文向量模型名称'
$env:RAG_EMBEDDING_KEY = '你的向量服务密钥'
cd backend
.\mvnw.cmd spring-boot:run
```

4. 在另一终端启动前端：

```powershell
cd frontend
npm ci
npm run serve
```

前端代理 `/api` 和 `/auth` 到 `localhost:8080`。使用前端命令显示的本机地址访问页面。反向代理部署可使用同源请求；跨域部署需设置 `CORS_ALLOWED_ORIGINS`，多个完整 origin 用逗号分隔。

首次 RAG 请求会增量建立公开知识的向量索引。资料上传需要有效的向量接口和登录身份，支持 CSV、DOCX、TXT、Markdown。详细格式及记忆策略见 [RAG.md](RAG.md)。

## 官网知识采集

默认关闭，不会在未经配置时自动访问网站。启用、执行计划、白名单与审核流程见 [爬虫运行说明](docs/crawler.md)。

流程为：配置的官网入口 → robots 与网络地址检查 → 有界抓取 → 正文与来源暂存 → 管理员整理和审核 → 原有 `knowledge` 表 → RAG 增量索引。

进入后台的「官网知识采集」可查看任务记录、立即执行、筛选待审内容、整理正文并入库。网页变化会再次进入审核；相同内容不会重复创建知识条目。原文不会直接覆盖人工确认的知识。

## 账号与配置

- 新密码使用带随机盐的 PBKDF2-HMAC-SHA256；已有 MD5 密码在成功登录后使用条件更新升级，无需批量重置。
- 新设密码长度为 8～128 字符。旧账号登录兼容原密码，不强制用新密码规则拒绝已有短密码。
- 管理员注册和旧密钥恢复功能默认关闭。确需使用时从环境注入独立的 `ADMIN_REGISTRATION_KEY`、`ADMIN_RESET_KEY`；不要将密钥提交到仓库。日常重置优先使用已认证的管理员用户管理页面。
- 不再打印登录/注册请求对象，不在重置响应中返回新密码。请求编号通过 `X-Request-ID` 返回，可关联服务日志。
- 新增模块只创建 RAG 与采集表，不改写已有用户数据。`db/notification.sql` 补齐原有通知表；`db/rag.sql`、`db/crawler.sql` 可供数据库管理员预先建表。

## 验证

```powershell
cd backend
.\mvnw.cmd verify
# 另一终端
cd frontend
npm run lint:check
npm run build
```

测试不需要模型密钥、校园网络或生产数据库。模型接口使用本地 HTTP 测试服务器，存储使用 H2 的 MySQL 兼容模式。CI 执行相同检查。

当前 CI 验证：65 项后端测试通过，后端可执行 JAR 打包通过，前端全项目静态检查和生产构建通过。构建入口资源合计约 143 KiB，上轮约 460 KiB；这是构建文件大小比较，不是页面实测耗时，也不包含页面异步加载资源。

## 边界与后续工作

- 向量保存在 MySQL，并由应用扫描授权范围；适合当前小规模校园知识库，大规模部署需向量索引和独立异步导入任务。
- 采集支持公开、无需登录、可静态解析的 HTML。不执行 JavaScript，不处理验证码，不抓登录内容，不跟随重定向，也不解析网页附件。
- 已验证常见 robots 分组、通配符、最长匹配与 Allow 优先；未声称覆盖 RFC 9309 的全部编码边界。建议用已授权、范围明确的公开公告页面做种子。
- 接入实际中文向量模型后，需要使用人工标注问题集评估召回质量与引用准确性，当前没有真实模型质量指标。
- 当前结果未替代真实 MySQL 联调、浏览器端到端验收、部署环境安全审计或校园官网访问测试。部署前先在测试环境验证配置与站点选择器。

知识条目编写和检索关键词建议见 [知识库内容指南](docs/knowledge-authoring.md)。

接口与数据说明见 [API 文档](docs/api.md)，变更说明见 [工程升级说明](docs/engineering-upgrade.md)。
