# MySQL + Milvus 与 LangGraph

## 数据职责

- **MySQL 是主库**：用户、权限、会话、反馈、知识审核、爬虫来源链接、文档元信息、问答记忆及持久化的向量同步任务。
- **Milvus 是可重建索引**：切片文本、标题、向量和匿名化权限范围；负责语义近邻检索。每个命中的切片 ID 仍回到 MySQL 校验用户、会话和 90 天有效期，再组装回答与原文链接。
- 当前迁移阶段 MySQL `rag_chunk` 仍保留切片和 embedding，用于同步失败重放、重建和降级检索；因此尚未节省 MySQL 存储。需要把大文件正文迁到对象存储时再做下一次迁移。
- 官网文章先进入待审记录；管理员发布到 `knowledge` 后才生成切片及向量。删除、覆盖或过期时会在同一 MySQL 事务里写入 `rag_vector_job`，后台批量同步到 Milvus。失败不会丢任务，下一轮重试。

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

将 `rag.milvus.*` 配置复制到本机 `application.properties`，或使用 `application-rag.properties` 的 `rag` profile。不要提交密钥。生产环境使用 HTTPS 或私有网络，并限制 Milvus 端口。首次启用时已有 MySQL 切片会排队导入；资料越多，首次同步越久。`rag_vector_job` 清空表示已确认同步，问答在同步期间可能仍按旧路径降级。更换 embedding 模型或维度时使用新的 collection 名称，删除 `rag_vector_state` 中的 `bootstrapped` 标记后重建公开知识索引；旧模型的私人附件需重新上传才能参加新模型的 Milvus 检索。若 Milvus 数据卷丢失，先恢复备份，再清除该标记并重新启动同步；在验证新集合前不要删除 MySQL 中的切片和向量。

不依赖真实 embedding 模型也可先验证 Java 与本机 Milvus 的同步链路。该测试使用隔离的 H2 数据库、临时 collection 和固定测试向量，结束时删除自己的 collection：

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp' # 仅 Codex Windows 终端需要
$env:MILVUS_LIVE_URL = 'http://127.0.0.1:19530'
$env:MILVUS_LIVE_TOKEN = '你的本机 Milvus 令牌'
cd backend
.\mvnw.cmd '-Dtest=MilvusLiveSmokeTest' test
```

## LangGraph Agent

Java Spring Boot 仍负责账户、审核、知识检索、模型密钥和对外 API。`agent/` 是独立的 Python LangGraph 服务，状态图执行「模型决定工具 → 调用只读工具 → 模型总结」。目前工具为校园知识检索、当前会话附件列表、北京时间；最多四轮模型调用、四次工具调用、两次知识检索。回答必须有可核实的检索来源。Agent 故障时 Java 回退到现有 RAG 问答。当前 Agent 模式不读写长时记忆，不执行发帖、知识审核或外部写操作。

```powershell
cd agent
python -m pip install -r requirements.txt
$env:AGENT_SHARED_KEY = '两服务共用的至少32字符随机密钥'
$env:CAMPUSASK_BACKEND_URL = 'http://127.0.0.1:8080'
python -m uvicorn agent.app:app --app-dir .. --host 127.0.0.1 --port 8090
```

同一个 `AGENT_SHARED_KEY` 注入 Java 后端，配置 `AGENT_SERVICE_URL=http://127.0.0.1:8090/agent/ask`，再重启 Java 服务。前端勾选「Agent 模式」后调用 `/api/qa/agent/ask`。`/internal/agent/**` 仅供 Python 服务调用，必须在反向代理和防火墙中保持私有；共享密钥不能写入前端。若后端运行在 8082，改 `CAMPUSASK_BACKEND_URL` 为对应地址。

若在 Codex Windows 内置终端启动 Java 时出现 `Unable to establish loopback connection`，可仅在该终端设置 `$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'` 后重试；这是启动环境的 JDK 套接字问题，不属于数据库故障。[问题与验证记录](https://github.com/openai/codex/issues/40902)

## 规模化前还需验证

当前知识变更检查仍会遍历 `knowledge`，MySQL 故障降级仍会扫描授权切片。百万级数据前需要把知识变更改为增量事件或游标、为降级检索建立 MySQL 全文或独立关键词索引，并对实际问题集测召回率、引用准确率、同步延迟与删除时效。Milvus 支持更进一步的 BM25 混合检索，但当前代码只使用向量近邻加 Java 关键词重排，不能宣称已经启用 Milvus BM25。
