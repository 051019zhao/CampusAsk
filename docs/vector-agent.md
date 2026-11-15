# MySQL + Milvus 与 LangGraph

## 数据职责

- **MySQL 是主库**：用户、权限、会话、反馈、知识审核、爬虫来源链接、文档元信息、问答记忆及持久化的向量同步任务。
- **Milvus 是可重建索引**：切片文本、标题、向量和匿名化权限范围；负责语义近邻检索。每个命中的切片 ID 仍回到 MySQL 校验用户、会话和 90 天有效期，再组装回答与原文链接。
- 当前迁移阶段 MySQL `rag_chunk` 仍保留切片和 embedding，用于同步失败重放、重建和降级检索；因此尚未节省 MySQL 存储。需要把大文件正文迁到对象存储时再做下一次迁移。
- 官网文章先进入待审记录；管理员发布到 `knowledge` 后才生成切片及向量。删除、覆盖或过期时会在同一 MySQL 事务里写入 `rag_vector_job`，后台批量同步到 Milvus。失败不会丢任务，下一轮重试。问答只在首次请求构建知识索引，此后由后台每分钟检查变更，避免每个问题都扫描知识表。
- 同步队列里有少量任务时，问答把当前身份可见的待同步切片与 Milvus 命中合并，仍由 MySQL 做权限和有效期校验；超过 100 个待同步可见切片或 Milvus 故障时回退到原检索路径。切换 collection 或 embedding 模型会重新排队已有切片。

## 本机启用 Milvus

需要 Docker Desktop 的 Linux 容器引擎和一个兼容 OpenAI embedding 格式的中文向量模型。Milvus 官方 Windows 文档提供版本化 Compose 文件；Standalone Compose 实际包含 Milvus、etcd、MinIO 三个常驻容器，不只是一个容器。[官方 Windows 安装步骤](https://milvus.io/docs/install_standalone-windows.md)

可用本机 Ollama 作为 embedding 服务，例如下载支持中文的 `bge-m3`，将 `RAG_EMBEDDING_URL` 设为 `http://127.0.0.1:11434/v1/embeddings`、`RAG_EMBEDDING_MODEL` 设为 `bge-m3`；Ollama 的本地兼容接口支持该路径。[Ollama 接口文档](https://docs.ollama.com/api/openai-compatibility)

在单独目录下载官方版本化 Compose 文件，检查内容后启动。以官方文档当前给出的版本为例：

```powershell
mkdir milvus-local
cd milvus-local
Invoke-WebRequest -Uri 'https://github.com/milvus-io/milvus/releases/download/v3.0.5/milvus-standalone-docker-compose.yaml' -OutFile docker-compose.yml
docker compose up -d
docker compose ps
```

在后端启动环境中设置以下变量，然后重启 Java 服务：

```powershell
$env:RAG_EMBEDDING_URL = '完整的 embedding API 地址'
$env:RAG_EMBEDDING_MODEL = '中文 embedding 模型名'
$env:RAG_EMBEDDING_KEY = 'embedding API 密钥'
$env:RAG_MILVUS_URL = 'http://127.0.0.1:19530'
$env:RAG_MILVUS_TOKEN = '你的 Milvus 用户名:密码或服务令牌'
$env:RAG_MILVUS_COLLECTION = 'campusask_chunks_v1'
```

将 `rag.milvus.*` 配置复制到本机 `application.properties`，或使用 `application-rag.properties` 的 `rag` profile。不要提交密钥。首次启用时已有 MySQL 切片会排队导入；资料越多，首次同步越久。`rag_vector_job` 清空表示已确认同步。更换 embedding 模型或维度时使用新的 collection 名称；程序会自动重新排队已有切片，公开知识在后台检查时重新生成新向量；旧模型的私人附件需重新上传才能参加新模型的 Milvus 检索。若 Milvus 数据卷丢失，先恢复备份，再清除 `rag_vector_state` 中的 `bootstrapped` 标记并重新启动同步；在验证新集合前不要删除 MySQL 中的切片和向量。

不依赖真实 embedding 模型也可先验证 Java 与本机 Milvus 的同步链路。该测试使用隔离的 H2 数据库、临时 collection 和固定测试向量，结束时删除自己的 collection：

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp' # 仅 Codex Windows 终端需要
$env:MILVUS_LIVE_URL = 'http://127.0.0.1:19530'
$env:MILVUS_LIVE_TOKEN = '你的本机 Milvus 令牌'
cd backend
.\mvnw.cmd '-Dtest=MilvusLiveSmokeTest' test
```

## LangGraph Agent

登录用户勾选“使用长时记忆”后，Agent 将用户问题单独存入 `rag_agent_memory`（最多 200 条，保留 90 天），新会话可召回最多 3 条相关提问。历史回答不会进入长时记忆，记忆只用于理解意图，不能作为校园事实。关闭开关的本轮不读取或保存长时记忆；删除记忆与清除会话会同步清理。回答会先核对引用编号和链接，再由模型对照资料逐条审查事实句；无依据的表述会要求改写，仍未通过则回退。模型审查仍可能漏判，重要事项请查看原始来源。

Java Spring Boot 仍负责账户、审核、知识检索、模型密钥和对外 API。`agent/` 是独立的 Python LangGraph 服务。状态图执行「读取会话上下文 → 模型选择只读工具 → 观察结果并继续查证 → 校验引用 → 必要时补检或修正」。模型可以按问题自主选择校园知识检索、当前会话附件列表、已命中来源的详情、北京时间；多个主题可分别检索。若模型直接回答且没有证据，图会先作一次分题检索，再让模型重答。知识检索最多三次，模型最多五轮，工具最多六次。来源详情读取只接受本轮已检索的文档 ID；Java 再校验用户和会话权限。公开知识详情来自当前已发布的知识正文，私人附件来自有权限的索引切片；该工具不会联网打开官网原网页，Agent 不应声称已核对原网页全文。回答只返回实际引用的来源，并拒绝模型写出的未知来源 URL。Agent 故障时 Java 回退到现有 RAG 问答。`rag_agent_run` 只记录运行状态、检索方式、引用数、模型轮数、工具次数和耗时，不保存提问或回答；`agentRunId` 可用于定位这条本机记录。已登录用户的成功回答写入独立的 `rag_agent_turn`，同一话题最多使用最近四轮理解追问，90 天过期；访客不保存。会话历史不能作为校园事实证据，删除记忆与清除会话也会清理它。Agent 不执行发帖、知识审核或外部写操作。

```powershell
cd agent
python -m pip install -r requirements.txt
$env:AGENT_SHARED_KEY = '两服务共用的至少32字符随机密钥'
$env:CAMPUSASK_BACKEND_URL = 'http://127.0.0.1:8080'
python -m uvicorn agent.app:app --app-dir .. --host 127.0.0.1 --port 8090
```

同一个 `AGENT_SHARED_KEY` 注入 Java 后端，配置 `AGENT_SERVICE_URL=http://127.0.0.1:8090/agent/ask`，再重启 Java 服务。前端的「多步查证」默认开启，调用 `/api/qa/agent/ask`；上传图片时会使用原问答接口。`/internal/agent/**` 仅供 Python 服务调用，必须在反向代理和防火墙中保持私有；共享密钥不能写入前端。若后端运行在 8082，改 `CAMPUSASK_BACKEND_URL` 为对应地址。

若在 Codex Windows 内置终端启动 Java 时出现 `Unable to establish loopback connection`，可仅在该终端设置 `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'` 后重试；这是启动环境的 JDK 套接字问题，不属于数据库故障。[问题与验证记录](https://github.com/openai/codex/issues/40902)

## 规模化前还需验证

后台知识变更检查仍会遍历 `knowledge`，MySQL 故障降级仍会扫描授权切片。百万级数据前需要把知识变更改为增量事件或游标、为降级检索建立 MySQL 全文或独立关键词索引，并对实际问题集测召回率、引用准确率、同步延迟与删除时效。Milvus 支持更进一步的 BM25 混合检索，但当前代码只使用向量近邻加 Java 关键词重排，不能宣称已经启用 Milvus BM25。当前引用校验验证编号属于真实检索结果，无法自动证明每句话都被对应片段充分支持，仍需标注问题集和人工复核。
