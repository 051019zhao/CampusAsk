# 新增与调整的 API

响应沿用 `{code,message,data}`。新问答及采集接口使用匹配的 HTTP 错误状态：400 参数问题、401 未登录、403 非管理员、404 记录不存在、409 任务/版本冲突、413 文件过大、503 外部服务不可用。未知内部错误返回通用消息，不返回数据库异常细节。响应头 `X-Request-ID` 可用于排查。

## 问答与资料

| 方法 | 路径 | 参数 |
| --- | --- | --- |
| POST | `/api/qa/ask` | JSON `keyword`（1～2000 字符）、可选 `sessionId`、`memoryEnabled` |
| POST | `/api/qa/rag/documents` | multipart `file`、`sessionId`；需登录 |
| GET | `/api/qa/rag/documents` | `sessionId`；仅本人当前会话 |
| DELETE | `/api/qa/rag/documents/{id}` | `sessionId`；仅本人当前会话 |
| DELETE | `/api/qa/rag/memory` | 删除本人 RAG 记忆与摘要 |
| POST | `/api/qa/clear-session` | JSON `sessionId`；删除本人该话题记忆与附件 |
| GET | `/api/qa/history` | 返回当前登录用户的业务提问历史，忽略外部传入的他人身份 |

登录沿用 `token` 请求头。问答结果包含 `answer`、`source`、`citations`、`retrievalMode`、`memoryEnabled`、`memorySaved`。`retrievalMode=lexical` 表示本轮使用关键词降级检索。关闭记忆不影响使用当前话题的已上传资料。

## 官网采集（管理员）

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/admin/crawler/status` | 启用状态、当前实例状态、种子与最近 20 次运行 |
| POST | `/api/admin/crawler/run` | 异步启动一次抓取，返回 `runId`；未配置或已有租约返回 409 |
| GET | `/api/admin/crawler/candidates?status=PENDING&page=1` | 状态可为 PENDING、APPROVED、REJECTED，每页 20 条 |
| POST | `/api/admin/crawler/candidates/{id}/approve` | JSON `fingerprint`、`title`、`content`、`category` |
| POST | `/api/admin/crawler/candidates/{id}/reject` | JSON `fingerprint` |

审批参数：标题至多 500 字符、正文至多 4000 字符、分类至多 50 字符，均不能为空。审核 API 同时核对当前内容指纹，防止旧版本提交。审核成功返回关联知识 ID，重复批准同一已通过版本不会重复插入。

## 密码兼容

原注册、登录、修改和管理员重置接口路径保留；新设置密码统一使用 PBKDF2，成功登录旧账号时条件升级 MD5。管理员重置接口现在要求显式提交密码，不再使用固定默认密码，也不会在响应中回显密码。原密钥恢复功能必须单独配置后才能使用，已有登录权限规则保持不变。
