# Java Agent-Core 系统集成测试报告

## 1. 概述

本报告记录了 Java 版 agent-core 模块的系统集成测试结果。测试参考了 Python 版 examples 和 API 文档，
覆盖了 Foundation (LLM)、Prompt Template、Tool、Operator、Workflow、Retrieval、Context Engine、
Session、Graph Store、SysOp、Common 以及端到端 Workflow+LLM 等模块。

- **测试类总数**: 14 个测试类文件（含嵌套测试类）
- **测试方法总数**: 64 个
- **最终结果**: ✅ **全部通过（64/64, 0 Failures, 0 Errors, 0 Skipped）**
- **总耗时**: ~102 秒（含 LLM API 远程调用）

## 2. 测试环境

| 项目 | 值 |
|------|-----|
| Java版本 | 21 |
| 构建工具 | Maven 3.x + Surefire 3.2.5 |
| 测试框架 | JUnit 5.10.2 + AssertJ 3.25.3 |
| LLM API | SiliconFlow (OpenAI兼容接口) |
| LLM模型 | Pro/zai-org/GLM-4.7 |
| Embedding模型 | Qwen/Qwen3-Embedding-8B |
| API配置 | 从 classpath `APIKEY/apiconfig.json` 读取，无硬编码密钥 |

## 3. 测试结果详情

### 3.1 ModelBootstrapSystemTest (Foundation/LLM 启动与注册)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testBuiltInProviderRegistration | <10ms | ✅ 通过 |
| **合计: 1 test** | **<0.01s** | **全部通过** |

### 3.2 FoundationLLMSystemTest (Foundation/LLM模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testLlmInvoke | ~15s | ✅ 通过 |
| testLlmWithSystemPrompt | ~10s | ✅ 通过 |
| testLlmStream | ~15s | ✅ 通过 |
| testLlmTemperatureControl | ~10s | ✅ 通过 |
| testMultiTurnConversation | ~12s | ✅ 通过 |
| **合计: 5 tests** | **72.64s** | **全部通过** |

### 3.3 PromptTemplateSystemTest (Prompt模板模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testBasicSubstitution | <1ms | ✅ 通过 |
| testToUserMessage | <1ms | ✅ 通过 |
| testToMessageList | <1ms | ✅ 通过 |
| testCustomDelimiters | <1ms | ✅ 通过 |
| testMultiplePlaceholders | <1ms | ✅ 通过 |
| testUnresolvedPlaceholders | <1ms | ✅ 通过 |
| **合计: 6 tests** | **0.007s** | **全部通过** |

### 3.4 ToolSystemTest (工具模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testLocalFunctionInvoke | <1ms | ✅ 通过 |
| testLocalFunctionMapResult | <1ms | ✅ 通过 |
| testToolCardInfo | <1ms | ✅ 通过 |
| testToolStreaming | <1ms | ✅ 通过 |
| testToolIdValidation | <1ms | ✅ 通过 |
| **合计: 5 tests** | **0.009s** | **全部通过** |

### 3.5 LLMToolCallingSystemTest (LLM工具调用模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testLlmWithToolDefinitions | ~2s | ✅ 通过 |
| testLlmWithMultipleTools | ~1.5s | ✅ 通过 |
| **合计: 2 tests** | **3.316s** | **全部通过** |

### 3.6 OperatorSystemTest (算子模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testLlmCallOperatorInvoke | ~5s | ✅ 通过 |
| testLlmCallOperatorFrozenPrompt | ~4s | ✅ 通过 |
| testOperatorStateSnapshot | ~3s | ✅ 通过 |
| testToolCallOperator | <1ms | ✅ 通过 |
| testOperatorTunables | ~1s | ✅ 通过 |
| **合计: 5 tests** | **13.44s** | **全部通过** |

### 3.7 WorkflowSystemTest (工作流模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testLinearWorkflow | <10ms | ✅ 通过 |
| testWorkflowEndTemplate | <10ms | ✅ 通过 |
| testParallelBranches | <10ms | ✅ 通过 |
| testBranchRouter | <10ms | ✅ 通过 |
| testBranchComponent | <10ms | ✅ 通过 |
| testWorkflowCard | <10ms | ✅ 通过 |
| testDataPipeline | <10ms | ✅ 通过 |
| **合计: 7 tests** | **0.072s** | **全部通过** |

### 3.8 RetrievalSystemTest (检索模块)
| 嵌套类 | 测试数 | 耗时 | 结果 |
|--------|--------|------|------|
| HashEmbeddingTests | 3 | 0.004s | ✅ 全部通过 |
| APIEmbeddingTests | 2 | 0.874s | ✅ 全部通过 |
| KnowledgeBaseTests | 3 | 0.025s | ✅ 全部通过 |
| RerankerTests | 1 | 0.003s | ✅ 全部通过 |
| MultiKBTests | 1 | 0.005s | ✅ 全部通过 |
| DocumentTests | 2 | 0.004s | ✅ 全部通过 |
| **合计: 12 tests** | **~0.9s** | **全部通过** |

### 3.9 ContextEngineSystemTest (上下文引擎模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testContextWindowCreation | <1ms | ✅ 通过 |
| testContextWindowDefaults | <1ms | ✅ 通过 |
| testContextWindowClear | <1ms | ✅ 通过 |
| testMultipleContexts | <1ms | ✅ 通过 |
| **合计: 4 tests** | **0.029s** | **全部通过** |

### 3.10 SessionSystemTest (会话模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testSessionCreation | <1ms | ✅ 通过 |
| testSessionAutoId | <1ms | ✅ 通过 |
| testSessionFromString | <1ms | ✅ 通过 |
| testMultipleSessions | <1ms | ✅ 通过 |
| testSessionEnvs | <1ms | ✅ 通过 |
| **合计: 5 tests** | **0.008s** | **全部通过** |

### 3.11 GraphStoreSystemTest (图存储模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testSaveAndGet | <1ms | ✅ 通过 |
| testMissingKeyReturnsEmpty | <1ms | ✅ 通过 |
| testOverwrite | <1ms | ✅ 通过 |
| testDeleteByNamespace | <1ms | ✅ 通过 |
| testDeleteBySession | <1ms | ✅ 通过 |
| **合计: 5 tests** | **0.008s** | **全部通过** |

### 3.12 SysOpSystemTest (系统操作模块)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testSysOpCreation | <1ms | ✅ 通过 |
| testFileOperations | <1ms | ✅ 通过 |
| testShellAvailability | <1ms | ✅ 通过 |
| testSysOpCard | <1ms | ✅ 通过 |
| **合计: 4 tests** | **0.041s** | **全部通过** |

### 3.13 CommonModuleSystemTest (公共模块)
| 嵌套类 | 测试数 | 耗时 | 结果 |
|--------|--------|------|------|
| ConstantsTests | 2 | 0.082s | ✅ 全部通过 |
| JsonUtilsTests | 2 | 1.116s | ✅ 全部通过 |
| DictUtilsTests | 2 | 0.009s | ✅ 全部通过 |
| HashUtilTests | 3 | 0.048s | ✅ 全部通过 |
| ErrorTests | 2 | 0.046s | ✅ 全部通过 |
| **合计: 11 tests** | **~1.3s** | **全部通过** |

### 3.14 WorkflowLLMEndToEndSystemTest (端到端测试)
| 测试方法 | 耗时 | 结果 |
|----------|------|------|
| testWorkflowWithLLM | ~4s | ✅ 通过 |
| testMultiStepWorkflow | ~5s | ✅ 通过 |
| **合计: 2 tests** | **8.928s** | **全部通过** |

## 4. 测试过程中发现的问题及解决方案

### 4.1 Model SPI 工厂注册缺失

| 项目 | 内容 |
|------|------|
| **问题** | `new Model(clientConfig, requestConfig)` 抛出 `Unsupported client_type: 'OpenAI', Supported types: []` |
| **根因** | Model 使用 Java SPI (`ServiceLoader`) 加载 `ModelClientFactory` 实现，但项目中：1) 未创建 `META-INF/services/` SPI 注册文件；2) 无生产环境的 `ModelClientFactory` 实现类 |
| **影响范围** | FoundationLLMSystemTest、LLMToolCallingSystemTest、OperatorSystemTest、WorkflowLLMEndToEndSystemTest（4个测试类，共14个远程测试方法） |
| **现状** | **已在生产代码修复**：框架已提供默认 `ModelClientFactory` 实现，并通过 SPI + 默认注册兜底完成装配 |
| **测试同步** | 系统测试已移除测试侧 `ensureRegistered()` 手动注册逻辑，并新增 `ModelBootstrapSystemTest` 验证内置 provider 可直接启动 |
| **建议** | 后续继续保留该启动测试，避免 SPI 注册链路回归 |

### 4.2 SimpleKnowledgeBase 必须提供 Chunker 和 Indexer

| 项目 | 内容 |
|------|------|
| **问题** | `kb.addDocuments()` 抛出 `[155501] chunker is required`；修复后再抛 `[155502] index_manager is required` |
| **根因** | `SimpleKnowledgeBase` 8参数构造函数中 chunker 和 indexManager 参数传了 `null`，而 `addDocuments()` 内部强制校验这两个依赖 |
| **影响范围** | RetrievalSystemTest 中 KnowledgeBaseTests (3 tests) 和 MultiKBTests (1 test) |
| **解决方案** | 使用 `new CharChunker(512, 64)` 提供 chunker，使用 `new InMemoryIndexer(vectorStore)` 提供 indexer |
| **建议** | 1) API 文档中明确标注 `addDocuments()` 的必需依赖；2) 考虑在构造时校验或提供带有默认 chunker/indexer 的工厂方法 |

### 4.3 Common 模块 API 差异（在编码阶段发现）

| 类 | 问题 | 详情 |
|----|------|------|
| `JsonUtils` | 包路径不同 | 实际在 `com.openjiuwen.core.common.security.JsonUtils`，而非 `common.utils`；方法名为 `safeJsonDumps()`/`safeJsonLoads()`，非 `toJson()`/`fromJson()` |
| `DictUtils` | 方法不存在 | API 文档中描述的 `getNestedValue()` 不存在；实际提供 `createNestedMap()`、`flattenMap()`、`extractLeafNodes()` 等 |
| `HashUtil` | 参数签名不同 | `generateKey()` 需要 2-3 个参数 `(apiKey, apiBase[, modelProvider])`，而非单一字符串 |
| `ErrorHelper` | 方法不存在 | 无 `throwExecutionError()` 方法；实际使用 `raiseError(StatusCode)`、`buildError(StatusCode, String...)` |
| `StatusCode` | 枚举值不同 | 无 `INTERNAL_ERROR` 枚举值；通用错误码为 `StatusCode.ERROR` |

**建议**: 更新 API 文档使其与 Java 实现一致，或按照 API 文档调整 Java 实现的方法命名。

### 4.4 其他发现

| 项目 | 内容 |
|------|------|
| **ProviderType 与 SPI 分离** | `ProviderType` 枚举定义了 `OpenAI`、`SiliconFlow`、`DashScope`，但这仅是数据定义，不影响 SPI 查找。两者需要手动保持一致 |
| **LLM 超时参数** | `Model.invoke()` 的 `timeout` 参数类型为 `Float`（秒），`ModelClientConfig.timeout` 为 `double`（秒），但 `HttpClient.connectTimeout` 只支持整数秒。长时间 LLM 调用（如 GLM-4.7 流式输出）可能需要 60-120 秒 |
| **InMemoryVectorStore 检索** | InMemoryVectorStore + HashEmbedding 方案仅适合功能验证，HashEmbedding 的向量是确定性哈希而非语义表示，检索排序无语义意义 |
| **Workflow 组件** | Workflow 的 Pregel 执行引擎日志详尽，包含每个节点的 INVOKE 调用和批处理流程，便于调试 |

## 5. 测试文件清单

| 文件 | 测试数 | 模块 | 是否需要网络 |
|------|--------|------|-------------|
| `ApiConfigLoader.java` | - | 工具类 | 否 |
| `ModelBootstrapSystemTest.java` | 1 | Foundation/LLM Bootstrap | 否 |
| `FoundationLLMSystemTest.java` | 5 | Foundation/LLM | **是** (LLM API) |
| `PromptTemplateSystemTest.java` | 6 | Foundation/Prompt | 否 |
| `ToolSystemTest.java` | 5 | Foundation/Tool | 否 |
| `LLMToolCallingSystemTest.java` | 2 | Foundation/LLM+Tool | **是** (LLM API) |
| `OperatorSystemTest.java` | 5 | Operator | **是** (LLM API) |
| `WorkflowSystemTest.java` | 7 | Workflow | 否 |
| `RetrievalSystemTest.java` | 12 | Retrieval | **是** (Embedding API) |
| `ContextEngineSystemTest.java` | 4 | Context Engine | 否 |
| `SessionSystemTest.java` | 5 | Session | 否 |
| `GraphStoreSystemTest.java` | 5 | Graph Store | 否 |
| `SysOpSystemTest.java` | 4 | SysOp | 否 |
| `CommonModuleSystemTest.java` | 11 | Common | 否 |
| `WorkflowLLMEndToEndSystemTest.java` | 2 | 端到端 | **是** (LLM API) |

## 6. 运行指南

```bash
# 编译测试
mvn test-compile

# 运行所有系统集成测试
mvn test -Dtest="com.openjiuwen.core.systemtest.**"

# 仅运行本地测试（不需要API）
mvn test -Dtest="com.openjiuwen.core.systemtest.PromptTemplateSystemTest, \
  com.openjiuwen.core.systemtest.ToolSystemTest, \
  com.openjiuwen.core.systemtest.WorkflowSystemTest, \
  com.openjiuwen.core.systemtest.ContextEngineSystemTest, \
  com.openjiuwen.core.systemtest.SessionSystemTest, \
  com.openjiuwen.core.systemtest.GraphStoreSystemTest, \
  com.openjiuwen.core.systemtest.SysOpSystemTest, \
  com.openjiuwen.core.systemtest.CommonModuleSystemTest"
```

## 7. 结论

Java 版 agent-core 各模块功能完整，64 个集成测试全部通过。主要发现的问题集中在：

1. **Model SPI 工厂注册机制历史上缺少开箱即用实现** — 现已在生产代码修复，并同步到系统测试
2. **KnowledgeBase 依赖注入不完整** — 构造时必须显式提供 Chunker 和 Indexer，缺少便捷的工厂方法
3. **Common 模块 API 文档与实际实现存在差异** — 方法签名、包路径、枚举值均有不一致

建议后续重点关注 SPI 注册机制的完善和 API 文档的同步更新。
