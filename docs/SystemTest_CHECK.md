# SystemTest 检查与修复报告

## 1. 范围

本次检查基于以下三部分：

- `docs/SystemTest.md`
- Java 生产代码 `src/main/java`
- Python 对照实现 `F:/oepnjiuwen/agent-core-python/openjiuwen`

目标有三件事：

1. 判断 `SystemTest.md` 中记录的问题，哪些是 Java 生产缺陷，哪些只是测试使用方式或文档差异
2. 修复能够直接落地到 Java 生产代码的问题
3. 对照 Python 版本，识别当前 SystemTest 尚未覆盖的 feature

## 2. 结论总览

| 项目 | 结论 | 处理结果 |
|------|------|---------|
| 4.1 Model SPI 工厂注册缺失 | 真实的 Java 生产缺陷 | **已修复** |
| 4.2 SimpleKnowledgeBase 必须提供 Chunker / Indexer | 不是 Java 独有缺陷，Python 也要求显式注入 | **未改生产代码，归类为 API 使用约束** |
| 4.3 Common 模块 API 差异 | 主要是文档与实现不一致，不是本次运行时 defect | **建议修文档，不建议为迎合旧文档改代码** |
| Python 对照下的系统测试缺口 | 存在明显缺口，尤其在 LLM 细节、Retrieval 扩展能力、Session、SysOp、Workflow 高阶组件 | **已整理清单** |

## 3. 已修复的 Java 生产问题

### 3.1 Model 默认 Provider 注册缺失

`SystemTest.md` 第 4.1 节记录的问题是成立的：原来的 `Model` 只有 `ServiceLoader` 框架，没有任何生产级 `ModelClientFactory` 实现，也没有 `META-INF/services` 注册文件，所以系统测试只能依赖 `src/test` 里的 `OpenAiCompatibleFactory.ensureRegistered()` 临时补救。

本次已做的修复：

- 增加生产级 `OpenAiCompatibleModelClient`
- 增加默认 provider factory：
  - `OpenAiModelClientFactory`
  - `OpenRouterModelClientFactory`
  - `SiliconFlowModelClientFactory`
  - `DashScopeModelClientFactory`
- 增加 `DefaultModelClientFactories.ensureRegistered()`，在 `Model` 静态初始化中兜底注册
- 增加 SPI 服务文件 `META-INF/services/com.openjiuwen.core.foundation.llm.Model$ModelClientFactory`
- `ProviderType` 补齐 Python 侧已有的 `OpenRouter`
- 系统测试中移除了对 `OpenAiCompatibleFactory.ensureRegistered()` 的硬依赖，改为直接走生产代码
- 新增 `ModelBootstrapSystemTest`，专门验证内置 provider 无需测试侧手动注册即可启动

本次涉及的核心文件：

- `src/main/java/com/openjiuwen/core/foundation/llm/Model.java`
- `src/main/java/com/openjiuwen/core/foundation/llm/schema/ProviderType.java`
- `src/main/java/com/openjiuwen/core/foundation/llm/model_clients/OpenAiCompatibleModelClient.java`
- `src/main/java/com/openjiuwen/core/foundation/llm/model_clients/DefaultModelClientFactories.java`
- `src/main/resources/META-INF/services/com.openjiuwen.core.foundation.llm.Model$ModelClientFactory`

验证结果：

```bash
mvn -q -Dtest=ModelFactoryRegistrationTest test
mvn -q test-compile
mvn -q "-Dtest=com.openjiuwen.core.systemtest.LLMToolCallingSystemTest#testLlmWithToolDefinitions" test
```

上述验证均通过。其中最后一条远程系统测试已经确认：移除测试侧手动注册后，`Model` 可以直接使用生产注册路径完成 LLM tool calling。

### 3.2 SimpleKnowledgeBase 依赖约束的判断

`SystemTest.md` 第 4.2 节记录的现象是对的，但它更像“构造方式不完整”而不是“Java 运行时 bug”。

对照 Python 实现后可以确认：

- Python `SimpleKnowledgeBase.add_documents()` 同样要求 `chunker`
- Python `SimpleKnowledgeBase.add_documents()` 同样要求 `index_manager`
- 当前 Java 与 Python 在这一点上语义一致

因此本次没有为了这个点去修改 Java 生产代码。当前更合理的处理是：

- 系统测试继续显式注入 `CharChunker` 和 `InMemoryIndexer`
- 在文档里明确 `addDocuments()` / `updateDocuments()` 的前置依赖
- 如果后续要优化体验，应增加工厂方法，而不是隐式塞默认依赖

### 3.3 Common 模块 API 差异的判断

`SystemTest.md` 第 4.3 节记录的差异主要是“文档期望”和“Java 实际命名”不一致，并不构成本次需要修的运行时缺陷。

对照 Python 后的结论：

- `JsonUtils` 的 `safeJsonLoads` / `safeJsonDumps` 与 Python `safe_json_loads` / `safe_json_dumps` 是同一套语义
- `DictUtils` 当前提供的 `createNestedMap` / `flattenMap` / `extractLeafNodes` 与 Python `create_nested_dict` / `flatten_dict` / `extract_leaf_nodes` 方向一致
- `ErrorHelper` 当前的 `buildError` / `raiseError` / `systemError` / `validateError` / `terminate` 与 Python `build_error` / `raise_error` / `system_error` / `validate_error` / `terminate` 也是对齐的

结论是：这里优先应该修 API 文档，而不是把 Java 再改回旧文档里的命名。

## 4. 对照 Python 后发现的 SystemTest 缺漏

下面这些 feature 在 Python 代码中明确存在，且 Java 侧已有实现或至少已有对应数据结构，但当前 SystemTest 没有覆盖到。

| 模块 | 当前 SystemTest 覆盖 | 缺漏 feature | 代表代码 |
|------|----------------------|-------------|---------|
| LLM / Foundation | 基本 `invoke`、`stream`、多轮、tool calling | `OpenRouter` provider、`output_parser`、`parser_content`、`reasoning_content`、`cache_tokens`、stream 中 tool-call delta 合并 | Python: `core/foundation/llm/model.py`, `model_clients/openai_model_client.py`；Java: `AssistantMessage.java`, `AssistantMessageChunk.java` |
| Tool | 仅覆盖 `LocalFunction` | `RestfulApi`、`McpTool`、`ParserRegistry`、响应解析链 | Java: `foundation/tool/service_api/RestfulApi.java`, `foundation/tool/mcp/McpTool.java` |
| Retrieval | `HashEmbedding`、`APIEmbedding`、`SimpleKnowledgeBase` 基础增删查、`LexicalReranker`、基础多 KB 检索 | `parseFiles`、`parseUrls`、`updateDocuments`、`retrieveMultiKbWithSource`、`agentic retrieval`、`GraphKnowledgeBase`、多种 parser / reranker / vector store / query rewriter | Java: `retrieval/KnowledgeBase.java`, `SimpleKnowledgeBase.java`, `GraphKnowledgeBase.java` |
| Context | 仅覆盖基础 window 创建、清空、多上下文 | compressor、offloader、`SessionModelContext`、`KVCacheManager`、`ContextMessageBuffer` | Java: `context/processor/*`, `context/context/*` |
| Session | 仅覆盖 session id / env / 基本创建 | checkpointer、stream writer、interactive input、callback、tracer、嵌套路径工具 | Java: `session/checkpointer/*`, `session/stream/*`, `session/tracer/*`, `session/utils/SessionUtils.java` |
| SysOp | 仅覆盖创建、文件基础操作、shell 可用性、card | code execution、sandbox 操作、上传/下载/搜索、`SysOperationToolAdapter`、`OperationRegistry` | Java: `sysop/local/*`, `sysop/sandbox/*`, `sysop/registry/*` |
| Workflow / Graph | 线性流、并行、分支、基础 EndToEnd | loop、advanced loop、sub-workflow、interaction / interrupt、trace、stream actor、graph visualization | Java: `workflow/component/loop/*`, `Workflow.java`, `graph/stream_actor/*`, `graph/visualization/*` |

## 5. Python 有但 Java 尚未完全对齐的产品缺口

下面这些项不应简单归类为“SystemTest 漏测”，因为更准确的说法是：Python 里有完整 feature，而 Java 当前看不到对应的生产实现或还未完全补齐。

| 类型 | Python 侧 | Java 侧情况 | 结论 |
|------|-----------|------------|------|
| Workflow 高阶组件 | `QuestionerComponent` / `QuestionerExecutable` | `src/main/java/com/openjiuwen/core/workflow` 下未发现对应类 | **产品能力缺口** |
| Workflow 检索组件 | `KnowledgeRetrievalComponent` / `KnowledgeRetrievalExecutable` | Java workflow 目录下未发现对应类 | **产品能力缺口** |
| Retrieval 行为控制 | `strict_validation`、`delete_collection` | Java `KnowledgeBase` 有 `validateIndex()`，但没有同等的 `strict_validation` 开关和 `deleteCollection()` API | **能力差异，需单独评估是否补齐** |

这部分建议不要直接往当前 SystemTest 里“补用例”，而应先确认 Java 版是否要补齐对应 feature。

## 6. 建议的下一轮 SystemTest 增补优先级

1. `P0`：补 LLM 回归测试  
   覆盖 provider alias、`output_parser`、`parser_content`、`reasoning_content`、stream tool-call 增量合并。

2. `P1`：补 Retrieval 生命周期测试  
   覆盖 `parseFiles` / `parseUrls` / `updateDocuments` / `retrieveMultiKbWithSource` / `agentic retrieval`。

3. `P1`：补 Session / Context / SysOp 高价值行为  
   覆盖 checkpointer、stream writer、tracer、compressor / offloader、sandbox / code execution。

4. `P2`：补 Workflow 高阶能力  
   覆盖 loop、advanced loop、sub-workflow、graph visualization / stream actor。

5. `P2`：单独立项评估 Python → Java 产品缺口  
   重点看 `QuestionerComponent`、`KnowledgeRetrievalComponent`、`strict_validation` / `deleteCollection`。

## 7. 本次实际代码变更

本次落地修改的核心内容如下：

- 新增生产级 OpenAI 兼容客户端与默认 provider factories
- `Model` 增加默认 factory 注册兜底
- `ProviderType` 增加 `OpenRouter`
- 新增 SPI 服务注册文件
- 新增 `ModelFactoryRegistrationTest`
- 系统测试移除对测试侧临时工厂注册的依赖，并新增 `ModelBootstrapSystemTest`

如果后续还要继续推进，下一步最值得做的是：先补一轮本地可执行的高价值系统测试，再决定哪些 Python feature 需要进入 Java 的正式补齐计划。
