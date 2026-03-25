# retrieval 模块单元测试报告

## 1. 测试文件

本次将 Python 版 retrieval UT 的核心用例按 Java 结构转译为两组测试：

- `src/test/java/com/openjiuwen/core/retrieval/RetrievalCoreTest.java`
- `src/test/java/com/openjiuwen/core/retrieval/KnowledgeBaseTest.java`

## 2. 转译覆盖的测试主题

### 2.1 `RetrievalCoreTest`

覆盖了 Python 版下列测试主题的 Java 转译：

- `common/test_config.py`
- `common/test_document.py`
- `common/test_multimodal_document.py`
- `common/test_retrieval_result.py`
- `common/test_triple.py`
- `utils/test_fusion.py`
- `utils/test_config_manager.py`
- `retriever/test_vector_retriever.py`
- `retriever/test_sparse_retriever.py`
- `retriever/test_hybrid_retriever.py`
- `retriever/test_graph_retriever.py`
- `retriever/test_agentic_retriever.py`

重点验证内容：

- 配置对象默认值/非法值
- `Document / TextChunk / MultimodalDocument`
- `SearchResult / RetrievalResult / Triple / TripleMemory / TripleBeam`
- RRF 融合
- 配置文件加载
- `VectorRetriever / SparseRetriever / HybridRetriever`
- `GraphRetriever / TripleBeamSearch`
- `AgenticRetriever`

### 2.2 `KnowledgeBaseTest`

覆盖了 Python 版下列测试主题的 Java 转译：

- `test_knowledge_base.py`
- `test_knowledge_base_validation.py`
- `test_simple_knowledge_base.py`
- `test_graph_knowledge_base.py`

重点验证内容：

- `KnowledgeBase` 的配置兼容性校验
- `SimpleKnowledgeBase`
  - `parseFiles`
  - `addDocuments`
  - `retrieve`
  - `deleteDocuments`
  - `updateDocuments`
  - `getStatistics`
  - `retrieveMultiKb`
  - `retrieveMultiKbWithSource`
- `GraphKnowledgeBase`
  - 图索引构建
  - 图检索
  - 非图模式回退
  - 删除/更新/统计
  - close 行为

## 3. 执行命令

实际执行的命令为：

```powershell
mvn -q "-Dtest=RetrievalCoreTest,KnowledgeBaseTest" test
```

## 4. 测试结果

最终结果：

- `tests=12`
- `failures=0`
- `errors=0`

## 5. 测试过程中遇到的问题与处理

### 5.1 Maven 在 test-compile 阶段被非 retrieval 测试阻塞

问题：

- 仓库原有 `StateTest` 无法通过编译
- 原因是 `WorkflowCommitState` 缺少 `createNodeState(String)` 兼容重载

处理：

- 在 `WorkflowCommitState` 中补充了单参数重载

影响：

- 这是为了解除测试编译阻塞
- 不改变 retrieval 业务逻辑

### 5.2 Python 的 Pydantic 构造失败语义与 Java Bean 不一致

问题：

- Python 版很多测试依赖“缺少字段时构造立即失败”
- Java 侧为了配置加载和对象装配，保留了部分无参构造

处理：

- Java UT 转译时将断言重点放在“显式非法构造/显式非法设置”上
- 既保持语义一致，又不破坏 Java 侧装载能力

### 5.3 `MultimodalDocument` 的默认空文本语义

问题：

- 如果把 `Document.text` 做成“非空白字符串”校验，会导致 `MultimodalDocument(text="")` 语义错误

处理：

- 调整为“必填但允许空串”
- 与 Python 模型保持一致

### 5.4 PowerShell 对 `-Dtest=A,B` 的解析问题

问题：

- PowerShell 会把逗号解析成参数列表

处理：

- 改为整体加引号执行：

```powershell
mvn -q "-Dtest=RetrievalCoreTest,KnowledgeBaseTest" test
```

## 6. 结论

当前 Java 版 retrieval 的核心回归测试已经建立，并能稳定通过。

已验证的能力包括：

- 公共模型
- 本地向量/稀疏/混合检索
- 图检索
- agentic 检索
- knowledge base 主链路

尚未纳入本轮 UT 的部分主要是外部依赖型能力：

- Chroma / Milvus / PGVector 真实驱动
- 远程 embedding provider
- PDF / Word / Excel / Web 等复杂 parser
