# Java 版系统测试用例总览

> 文档生成日期：2026-03-11
> 测试包路径：`com.openjiuwen.core.systemtest`

---

## 1. 总体统计

| 指标 | 数量 |
|------|------|
| 测试类（含 @Test 方法） | 28 |
| 支撑/工具类（无测试方法） | 2 |
| 测试方法总计 | **168** |
| 需要网络（远程 LLM/Embedding API）的测试 | ~19 |
| 纯本地可执行的测试 | ~149 |
| 统一 Tag | `@Tag("system-test")` |

---

## 2. 模块覆盖分布

| 模块 | 测试类数 | 测试方法数 | 是否需要网络 |
|------|---------|----------|-------------|
| LLM 基础 | 4 | 29 | 部分 |
| Prompt 模板 | 1 | 6 | 否 |
| Tool 工具 | 2 | 14 | 否 |
| Operator 算子 | 1 | 5 | 部分 |
| Workflow 工作流 | 6 | 31 | 部分 |
| Retrieval 检索 | 2 | 26 | 部分 |
| Context Engine 上下文引擎 | 2 | 19 | 否 |
| Session 会话 | 2 | 28 | 否 |
| GraphStore 图存储 | 1 | 5 | 否 |
| SysOp 系统操作 | 2 | 15 | 否 |
| Common 公共模块 | 1 | 11 | 否 |
| Agent 智能体 | 3 | 6 | 部分 |
| Runner 运行器 | 1 | 2 | 部分 |

---

## 3. 支撑类

### 3.1 `SystemTestSupport.java`

抽象基类，为所有需要远程模型的系统测试提供共享能力：

- `assumeRemoteModelAvailable()` — 检测远程模型可用性，不可用时跳过测试
- `uniqueId()` — 生成唯一标识
- `trackSessionId()` / `@AfterEach` 清理 — 自动清理测试产生的 session
- `registerWorkflow()` / `registerAgent()` / `registerGroup()` — 注册测试用资源
- `invokeAgent()` / `streamAgent()` — 统一 Agent 调用入口
- `flattenText()` / `containsIgnoreCase()` — 断言辅助方法
- `newRemoteLlmAgent()` / `newRemoteReActAgent()` — 远程 Agent 构造工厂

### 3.2 `ApiConfigLoader.java`

工具类，从 `APIKEY/apiconfig.json` 加载 API 密钥配置，不包含测试方法。

---

## 4. 全部测试用例明细

### 4.1 LLM 基础模块

#### 4.1.1 `ModelBootstrapSystemTest` (1 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testBuiltInProviderRegistration` | 验证 OpenAI/OpenRouter/SiliconFlow/DashScope 等内置 Provider 注册无异常 |

#### 4.1.2 `FoundationLLMSystemTest` (5 个方法 · 需要网络)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testLlmInvoke` | 调用 LLM 并验证返回非空响应 |
| 2 | `testLlmInvokeWithSystemPrompt` | 带系统提示词调用 LLM，验证回答中包含 Java 相关内容 |
| 3 | `testLlmStream` | 流式调用 LLM，验证收到多个 chunk |
| 4 | `testLlmTemperatureControl` | 低温度调用验证确定性回答（回答 "2"） |
| 5 | `testLlmMultiTurnConversation` | 多轮对话，验证 LLM 能回忆之前轮次提到的名字 |

#### 4.1.3 `LLMSchemaSystemTest` (16 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | AssistantMessageTests | `testReasoningContent` | 验证 AssistantMessage 的 reasoningContent 字段 |
| 2 | AssistantMessageTests | `testParserContent` | 验证 parserContent 字段承载解析数据 |
| 3 | AssistantMessageTests | `testToolCalls` | 验证 toolCalls 列表 |
| 4 | AssistantMessageTests | `testToApiFormat` | 验证 toApiFormat 包含 tool_calls |
| 5 | UsageMetadataTests | `testCacheTokens` | 验证 UsageMetadata 的 cacheTokens 字段 |
| 6 | UsageMetadataTests | `testDefaults` | 验证 UsageMetadata 默认值 |
| 7 | UsageMetadataTests | `testModelAndLatency` | 验证 modelName 和 totalLatency 字段 |
| 8 | ChunkMergeTests | `testMergeTextChunks` | 合并两个文本 chunk，验证内容拼接 |
| 9 | ChunkMergeTests | `testMergeToolCallDeltas` | 合并带 tool call 参数增量的 chunk |
| 10 | ChunkMergeTests | `testMergeReasoningContent` | 验证 reasoningContent 的 last-wins 合并 |
| 11 | JsonOutputParserTests | `testParseJsonCodeBlock` | 从 Markdown 代码块中解析 JSON |
| 12 | JsonOutputParserTests | `testParsePlainJson` | 解析纯 JSON 字符串 |
| 13 | JsonOutputParserTests | `testParseInvalidJson` | 无效 JSON 返回 null |
| 14 | JsonOutputParserTests | `testStreamParse` | 流式解析分块 JSON |
| 15 | ProviderTypeTests | `testOpenRouterProvider` | 验证 ProviderType 包含 OpenRouter |
| 16 | ProviderTypeTests | `testStandardProviders` | 验证至少存在 4 个标准 Provider |

#### 4.1.4 `LLMToolCallingSystemTest` (2 个方法 · 需要网络 · 超时 90s)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testLlmWithToolDefinitions` | 向 LLM 发送带天气工具定义的查询，验证返回工具调用或文本 |
| 2 | `testLlmWithMultipleTools` | 向 LLM 发送带天气+计算器双工具的查询，验证响应 |

---

### 4.2 Prompt 模板模块

#### 4.2.1 `PromptTemplateSystemTest` (6 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testBasicPlaceholderSubstitution` | 替换 `{{name}}` 和 `{{topic}}` 占位符 |
| 2 | `testStringToUserMessage` | 纯字符串转换为 UserMessage |
| 3 | `testMessageListPreserved` | 消息列表经模板传递后保持原样 |
| 4 | `testCustomDelimiters` | 测试 `<<>>` 自定义分隔符 |
| 5 | `testMultiplePlaceholders` | 单个模板中 3 个占位符替换 |
| 6 | `testUnresolvedPlaceholders` | 部分占位符未填充时保留原样 |

---

### 4.3 Tool 工具模块

#### 4.3.1 `ToolSystemTest` (5 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testLocalFunctionInvoke` | LocalFunction 加法工具：3+5=8.0 |
| 2 | `testLocalFunctionMapResult` | LocalFunction 返回天气 Map 结果 |
| 3 | `testToolCardInfo` | ToolCard 生成有效 ToolInfo |
| 4 | `testLocalFunctionStream` | LocalFunction 流式返回 3 个元素 |
| 5 | `testToolIdValidation` | 空工具 ID 应抛出异常 |

#### 4.3.2 `ToolAdvancedSystemTest` (9 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | RestfulApiTests | `testRestfulApiCardConstruction` | 构建 RestfulApiCard 并验证字段有效 |
| 2 | RestfulApiTests | `testRestfulApiCardDefaults` | 验证默认 method=POST, timeout=60 |
| 3 | RestfulApiTests | `testRestfulApiRejectsInvalidUrlScheme` | 拒绝 ftp:// URL |
| 4 | RestfulApiTests | `testRestfulApiRejectsUnsupportedMethod` | 拒绝 TRACE HTTP 方法 |
| 5 | McpToolCardTests | `testMcpToolCardConstruction` | 构建 McpToolCard 并设置服务器信息 |
| 6 | McpToolCardTests | `testMcpToolCardToolInfo` | 从 McpToolCard 获取 McpToolInfo |
| 7 | ParserRegistryTests | `testParserRegistrySingleton` | 验证 ParserRegistry 为单例 |
| 8 | ParserRegistryTests | `testParserRegistryJsonParse` | 解析 JSON content-type |
| 9 | ParserRegistryTests | `testParserRegistryTextParse` | 解析 text/plain content-type |

---

### 4.4 Operator 算子模块

#### 4.4.1 `OperatorSystemTest` (5 个方法 · 部分需要网络)

| # | 方法名 | 说明 | 网络 |
|---|--------|------|------|
| 1 | `testLlmCallOperatorInvoke` | LLMCallOperator 调用，验证响应 | 是 |
| 2 | `testLlmCallOperatorFrozenPrompt` | 冻结提示词的 LLMCallOperator，验证无可调参数 | 是 |
| 3 | `testLlmCallOperatorState` | 获取与恢复 LLMCallOperator 状态快照 | 否 |
| 4 | `testToolCallOperatorInvoke` | ToolCallOperator 使用 LocalFunction 乘法工具 | 否 |
| 5 | `testLlmCallOperatorTunables` | 验证可调参数（system_prompt 可调, user_prompt 冻结） | 否 |

---

### 4.5 Workflow 工作流模块

#### 4.5.1 `WorkflowSystemTest` (7 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testSimpleLinearWorkflow` | 线性 Start→Process→End，透传 "hello" |
| 2 | `testWorkflowEndTemplate` | End 组件进行响应模板替换 |
| 3 | `testWorkflowParallelBranches` | 并行分支汇聚到 End |
| 4 | `testWorkflowBranchRouter` | BranchRouter 条件路由（score >= 60） |
| 5 | `testWorkflowBranchComponent` | BranchComponent 条件分支（value > 100 / <= 100） |
| 6 | `testWorkflowWithCard` | 带 WorkflowCard 元数据的工作流 |
| 7 | `testWorkflowDataPipeline` | 数据转换流水线：add 15+27=42 |

#### 4.5.2 `WorkflowAdvancedSystemTest` (7 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | SubWorkflowTests | `testSubWorkflowExecution` | 子工作流组件执行内部工作流（值翻倍） |
| 2 | SubWorkflowTests | `testSubWorkflowDrawable` | SubWorkflowComponentImpl 暴露子工作流 |
| 3 | LoopSetVariableTests | `testLoopSetVariableConstruction` | LoopSetVariableComponent 变量映射构造 |
| 4 | VisualizationTests | `testDrawableGraphConstruction` | DrawableGraph 带节点和边的构造 |
| 5 | VisualizationTests | `testDrawableEdgeFlags` | DrawableEdge source/target 验证 |
| 6 | VisualizationTests | `testDrawableNodeMetadata` | DrawableNode 元数据验证 |
| 7 | VisualizationTests | `testEmptyDrawableGraph` | 空 DrawableGraph 构造 |

#### 4.5.3 `WorkflowInterruptSystemTest` (5 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `interactiveWorkflowReturnsInputRequiredAndResumesFromCheckpoint` | 交互式工作流返回 INPUT_REQUIRED，然后从 checkpoint 恢复 |
| 2 | `forceDeleteWorkflowStateRestartsInterruptedWorkflow` | 强制删除工作流状态后从头重新执行 |
| 3 | `nestedSubWorkflowInterruptResumesWithParentNamespaceCheckpoint` | 嵌套子工作流中断在父命名空间下保存/恢复 checkpoint |
| 4 | `interactiveWorkflowAcceptsRawInteractiveInput` | 交互式工作流接受原始字符串 InteractiveInput（兼容 Python） |
| 5 | `loopSubWorkflowInterruptKeepsDeepCheckpointNamespaces` | Loop + 子工作流中断保持深层 checkpoint 命名空间并原地恢复 |

#### 4.5.4 `WorkflowTraceSystemTest` (3 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `workflowStreamEmitsWorkflowLevelTraceSpans` | 工作流 stream 产出 workflow 级 trace start/done span |
| 2 | `workflowTraceEmitsDetachedSpanSnapshots` | start 快照不可变；最终 span 带 endTime 和 outputs |
| 3 | `workflowTraceKeepsRawTopLevelPayloadsAndRawComponentOutputs` | trace 保留 raw payload（List 等）在工作流和组件级别 |

#### 4.5.5 `WorkflowVisualizationSystemTest` (3 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testDrawLinearWorkflow` | `Workflow.draw` 返回线性 start→process→end 的 Mermaid |
| 2 | `testDrawExpandedSubWorkflow` | expand=true 展开子图渲染内部节点 |
| 3 | `testDrawConditionalAndStreamingEdges` | 条件边（虚线 `-.->`) 和流式边（粗线 `==>`) |

#### 4.5.6 `WorkflowLLMEndToEndSystemTest` (2 个方法 · 需要网络)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testWorkflowWithLLMCall` | 端到端：Start→LLMCaller→End 使用真实 LLM |
| 2 | `testMultiStepWorkflowWithLLM` | 多步工作流：Start→LLM→大写转换→End |

---

### 4.6 Retrieval 检索模块

#### 4.6.1 `RetrievalSystemTest` (12 个方法 · 部分需要网络)

| # | 子类 | 方法名 | 说明 | 网络 |
|---|------|--------|------|------|
| 1 | HashEmbeddingTests | `testHashEmbeddingConsistency` | 相同文本 → 相同向量 | 否 |
| 2 | HashEmbeddingTests | `testHashEmbeddingDifferentTexts` | 不同文本 → 不同向量 | 否 |
| 3 | HashEmbeddingTests | `testHashEmbeddingBatch` | 批量嵌入 3 段文本 | 否 |
| 4 | APIEmbeddingTests | `testApiEmbeddingSingleQuery` | 远程 Embedding API 单条查询 | 是 |
| 5 | APIEmbeddingTests | `testApiEmbeddingBatch` | 远程 Embedding API 批量 3 条 | 是 |
| 6 | KnowledgeBaseTests | `testKnowledgeBaseAddAndRetrieve` | 添加文档到 KB 并使用 HashEmbedding 检索 | 否 |
| 7 | KnowledgeBaseTests | `testKnowledgeBaseStatistics` | 获取 KB 统计信息 | 否 |
| 8 | KnowledgeBaseTests | `testKnowledgeBaseDeleteDocuments` | 添加并删除文档 | 否 |
| 9 | RerankerTests | `testLexicalReranker` | LexicalReranker 按 token 重叠度重排，topK=2 | 否 |
| 10 | MultiKBTests | `testMultiKBRetrieval` | 跨 2 个 KB 联合检索 | 否 |
| 11 | DocumentTests | `testDocumentAutoId` | Document 自动生成 ID | 否 |
| 12 | DocumentTests | `testDocumentMetadata` | Document 自定义元数据 | 否 |

#### 4.6.2 `RetrievalAdvancedSystemTest` (14 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | KnowledgeBaseParsingTests | `testParseFiles` | 验证 parseFiles 委托到解析器并注入 file_name |
| 2 | KnowledgeBaseParsingTests | `testParseUrls` | 验证 parseUrls 跳过不支持的 URL |
| 3 | UpdateDocumentsTests | `testUpdateDocumentsAdd` | updateDocuments 添加新文档 |
| 4 | UpdateDocumentsTests | `testUpdateDocumentsOverwrite` | updateDocuments 覆盖已有文档 |
| 5 | GraphKnowledgeBaseLifecycleTests | `testGraphKnowledgeBaseLifecycleWithGraph` | 完整生命周期：添加/检索/更新/删除，含图三元组 |
| 6 | GraphKnowledgeBaseLifecycleTests | `testGraphKnowledgeBaseWithoutGraphUsesChunkIndexOnly` | 禁用图 → chunk-only 检索路径 |
| 7 | AgenticGraphRetrievalTests | `testGraphKnowledgeBaseAgenticRetrievalRunsLocalGraphPath` | Agentic graph 检索使用 fake LLM，验证 3 次本地 LLM 调用 |
| 8 | MultiKBWithSourceTests | `testRetrieveMultiKbWithSource` | 多 KB 检索返回带来源 KB ID 的结果 |
| 9 | CharChunkerTests | `testCharChunkerBasic` | 文本切分为重叠 chunk，验证重叠 |
| 10 | CharChunkerTests | `testCharChunkerEmpty` | 空文本 → 无 chunk |
| 11 | CharChunkerTests | `testCharChunkerShortText` | 短文本 → 单个 chunk |
| 12 | CharChunkerTests | `testCharChunkerDocuments` | chunkDocuments 生成带 ID 和文档引用的 TextChunk |
| 13 | TextChunkTests | `testTextChunkFromDocument` | 从 Document 创建 TextChunk 并含元数据 |
| 14 | TextChunkTests | `testTextChunkAutoId` | TextChunk 自动生成 ID |

---

### 4.7 Context Engine 上下文引擎模块

#### 4.7.1 `ContextEngineSystemTest` (4 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testCreateContextAndAddMessages` | 创建上下文引擎并添加消息历史 |
| 2 | `testCreateContextDefaults` | 使用默认参数创建上下文 |
| 3 | `testClearContext` | 清除特定或全部 session 上下文 |
| 4 | `testMultipleContextsGet` | 创建多个上下文并按 ID 分别获取 |

#### 4.7.2 `ContextEngineAdvancedSystemTest` (15 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | ContextMessageBufferTests | `testAddAndGetBack` | 向缓冲区添加消息并获取 |
| 2 | ContextMessageBufferTests | `testBufferSize` | 验证缓冲区大小反映内容计数 |
| 3 | ContextMessageBufferTests | `testPopBack` | 从缓冲区尾部弹出消息 |
| 4 | ContextMessageBufferTests | `testSetMessages` | 整体替换缓冲区内容 |
| 5 | ContextMessageBufferTests | `testGetBackWithSizeLimit` | 获取缓冲区最后 N 条消息 |
| 6 | SessionModelContextTests | `testAddAndGetMessages` | 向 SessionModelContext 添加消息并获取 |
| 7 | SessionModelContextTests | `testClearMessages` | 清除上下文全部消息 |
| 8 | SessionModelContextTests | `testContextIdentifiers` | 验证 contextId 和 sessionId 访问器 |
| 9 | ContextEngineSaveTests | `testCreateWithHistoryAndRetrieve` | 创建带历史的上下文并检索 |
| 10 | CompressorConfigTests | `testCurrentRoundCompressorDefaults` | 验证 CurrentRoundCompressorConfig 默认值 |
| 11 | CompressorConfigTests | `testDialogueCompressorBuilder` | 测试 DialogueCompressorConfig builder 模式 |
| 12 | CompressorConfigTests | `testRoundLevelCompressorBuilder` | 测试 RoundLevelCompressorConfig builder |
| 13 | OffloaderConfigTests | `testMessageOffloaderDefaults` | 验证 MessageOffloaderConfig 默认值 |
| 14 | OffloaderConfigTests | `testMessageOffloaderBuilder` | 自定义值的 MessageOffloaderConfig builder |
| 15 | OffloaderConfigTests | `testMessageSummaryOffloaderBuilder` | MessageSummaryOffloaderConfig builder |

---

### 4.8 Session 会话模块

#### 4.8.1 `SessionSystemTest` (5 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testWorkflowSessionCreation` | 使用显式 session ID 创建 WorkflowSessionApi |
| 2 | `testWorkflowSessionAutoId` | 自动生成 session ID |
| 3 | `testWorkflowSessionStringConstructor` | 字符串构造函数 |
| 4 | `testMultipleSessions` | 多个 session 拥有独立 ID |
| 5 | `testWorkflowSessionWithEnvs` | 带环境变量的 session |

#### 4.8.2 `SessionAdvancedSystemTest` (23 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | CheckpointerTests | `testCheckpointerCreation` | 创建 InMemoryCheckpointer，验证 graphStore |
| 2 | CheckpointerTests | `testSessionNotExists` | sessionExists 对未知 session 返回 false |
| 3 | CheckpointerTests | `testReleaseUnknownSession` | release 未知 session 不抛异常 |
| 4 | CheckpointerTests | `testBuildKey` | Checkpointer.buildKey 拼接键 |
| 5 | CheckpointerTests | `testNamespaceConstants` | 验证命名空间常量值 |
| 6 | StreamEmitterTests | `testEmitAndClose` | StreamEmitter emit/close 生命周期 |
| 7 | StreamEmitterTests | `testStreamQueue` | 验证流队列可访问性 |
| 8 | StreamWriterManagerTests | `testCreationWithModes` | 创建支持 OUTPUT+TRACE 模式的 manager |
| 9 | StreamWriterManagerTests | `testTypedWriters` | 获取 output/trace/custom writer |
| 10 | StreamWriterManagerTests | `testCollectStreamOutput` | 收集流式输出 |
| 11 | StreamModeTests | `testStreamModeValues` | 验证 StreamMode 枚举模式字符串 |
| 12 | StreamModeTests | `testStreamModeDescriptions` | 验证 StreamMode 描述非空 |
| 13 | CallbackManagerTests | `testRegisterAndRetrieve` | 注册并按名称获取 handler |
| 14 | CallbackManagerTests | `testTrigger` | 验证通过 @TriggerEvent 触发事件 |
| 15 | TracerTests | `testTracerCreation` | Tracer 生成唯一 traceId |
| 16 | TracerTests | `testTracerUniqueness` | 两个 Tracer 有不同 traceId |
| 17 | TracerTests | `testTracerInit` | Tracer 使用 StreamWriterManager 和 CallbackManager 初始化 |
| 18 | InteractiveInputTests | `testDefaultConstruction` | 默认 InteractiveInput 构造 |
| 19 | InteractiveInputTests | `testRawInputs` | 带原始字符串输入的 InteractiveInput |
| 20 | InteractiveInputTests | `testUpdateNodeInput` | 更新特定工作流节点的输入 |
| 21 | AgentInterruptTests | `testAgentInterruptException` | AgentInterrupt 是 RuntimeException |
| 22 | AgentInterruptTests | `testAgentInterruptThrow` | AgentInterrupt 可正确抛出和捕获 |
| 23 | AgentInterruptTests | `testAgentInterruptDefault` | 默认 AgentInterrupt 构造 |

---

### 4.9 GraphStore 图存储模块

#### 4.9.1 `GraphStoreSystemTest` (5 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testInMemoryStoreSaveGet` | 按 session+namespace 保存和获取 GraphStoreState |
| 2 | `testInMemoryStoreGetMissing` | 缺失键返回空 Optional |
| 3 | `testInMemoryStoreOverwrite` | 覆盖已有状态并验证最新值 |
| 4 | `testInMemoryStoreDelete` | 删除特定 namespace，验证其他 ns 不受影响 |
| 5 | `testInMemoryStoreDeleteSession` | 删除整个 session 的状态 |

---

### 4.10 SysOp 系统操作模块

#### 4.10.1 `SysOpSystemTest` (4 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testSysOperationCreation` | 以 LOCAL 模式创建 SysOperation |
| 2 | `testSysOpFileOperations` | 从 SysOperation 获取 FS 操作 |
| 3 | `testSysOpShellOperation` | 从 SysOperation 获取 Shell 操作 |
| 4 | `testSysOperationCardConfig` | SysOperationCard builder 配合 LocalWorkConfig |

#### 4.10.2 `SysOpAdvancedSystemTest` (11 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | OperationRegistryTests | `testLocalOperationsRegistered` | 验证 LOCAL 模式操作已注册 |
| 2 | OperationRegistryTests | `testSandboxOperationsRegistered` | 验证 SANDBOX 模式操作存在 |
| 3 | OperationRegistryTests | `testGetFsOperationInfo` | 获取 fs LOCAL 操作信息 |
| 4 | OperationRegistryTests | `testGetShellOperationInfo` | 获取 shell LOCAL 操作信息 |
| 5 | OperationRegistryTests | `testGetCodeOperationInfo` | 获取 code LOCAL 操作信息 |
| 6 | ToolAdapterTests | `testExtractTools` | SysOperationToolAdapter 从 SysOperation 提取工具 |
| 7 | ToolAdapterTests | `testToolIdPrefix` | 验证工具 ID 前缀格式 |
| 8 | LocalFsOperationTests | `testWriteAndReadFile` | 通过 LocalFsOperation 写入后读取文件 |
| 9 | LocalFsOperationTests | `testWriteCreatesFile` | 写入操作自动创建文件 |
| 10 | LocalShellOperationTests | `testExecuteEcho` | 通过 LocalShellOperation 执行 echo 命令 |
| 11 | LocalCodeOperationTests | `testExecutePython` | 执行 Python 代码片段（可能被跳过） |

---

### 4.11 Common 公共模块

#### 4.11.1 `CommonModuleSystemTest` (11 个方法 · 本地)

| # | 子类 | 方法名 | 说明 |
|---|------|--------|------|
| 1 | ConstantsTests | `testControllerType` | 验证 ControllerType 枚举包含 REACT_CONTROLLER, WORKFLOW_CONTROLLER |
| 2 | ConstantsTests | `testTaskType` | 验证 TaskType 枚举包含 PLUGIN, WORKFLOW, MCP |
| 3 | JsonUtilsTests | `testJsonSerializeDeserialize` | Map 序列化为 JSON 后反序列化 |
| 4 | JsonUtilsTests | `testJsonSafeDefault` | safeJsonDumps 输入为 null 时返回默认值 |
| 5 | DictUtilsTests | `testDictUtilsCreateNested` | 从点分隔键创建嵌套 Map |
| 6 | DictUtilsTests | `testDictUtilsFlatten` | 展平嵌套 Map |
| 7 | HashUtilTests | `testHashUtilConsistency` | SHA-256 哈希对相同输入一致 |
| 8 | HashUtilTests | `testHashUtilDifferentInputs` | 不同输入产生不同哈希 |
| 9 | HashUtilTests | `testHashUtilWithProvider` | 带 model provider 参数的哈希生成 |
| 10 | ErrorTests | `testErrorHelperBuild` | 构建 StatusCode.ERROR 错误 |
| 11 | ErrorTests | `testErrorHelperRaise` | 验证 raiseError 正确抛出 BaseError |

---

### 4.12 Agent 智能体模块

#### 4.12.1 `ApplicationAgentSystemTest` (2 个方法 · 部分需要网络)

| # | 方法名 | 说明 | 网络 |
|---|--------|------|------|
| 1 | `testLlmAgentDirectAnswer` | LlmAgent 调用远程模型回答查询，验证响应含 "ORBIT_OK" | 是 |
| 2 | `testWorkflowAgentInvokesRegisteredWorkflow` | WorkflowAgent 注册并执行 echo 工作流，验证响应含原始查询 | 否 |

#### 4.12.2 `SingleAgentSystemTest` (2 个方法 · 需要网络)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testReActAgentInvokeAndCallbacks` | ReActAgent 调用远程模型，验证 BEFORE_INVOKE/AFTER_INVOKE 回调触发及 "ASTRA_ACK" |
| 2 | `testReActAgentConversationPersistence` | 两轮对话，验证 Agent 跨轮次记住 "KITE_CODE" |

#### 4.12.3 `MultiAgentSystemTest` (2 个方法 · 本地)

| # | 方法名 | 说明 |
|---|--------|------|
| 1 | `testBaseGroupInvokeAggregatesChildAgentOutputs` | BaseGroup 含 2 个子 Agent，invoke 聚合所有输出 |
| 2 | `testBaseGroupStreamEmitsChildChunks` | BaseGroup stream 每个子 Agent 产出一个 chunk |

---

### 4.13 Runner 运行器模块

#### 4.13.1 `RunnerModuleSystemTest` (2 个方法 · 部分需要网络)

| # | 方法名 | 说明 | 网络 |
|---|--------|------|------|
| 1 | `testRunnerRunWorkflowById` | Runner 按资源 ID 执行已注册工作流 | 否 |
| 2 | `testRunnerRunManagedRemoteAgentById` | Runner 按 ID 执行已注册的远程 ReActAgent | 是 |

---

## 5. 需要网络的测试汇总

以下测试需要远程 LLM / Embedding API 可用才能运行：

| 测试类 | 需要网络的方法 | 方法数 |
|--------|--------------|-------|
| FoundationLLMSystemTest | 全部 | 5 |
| LLMToolCallingSystemTest | 全部 | 2 |
| OperatorSystemTest | `testLlmCallOperatorInvoke`, `testLlmCallOperatorFrozenPrompt` | 2 |
| RetrievalSystemTest | `testApiEmbeddingSingleQuery`, `testApiEmbeddingBatch` | 2 |
| ApplicationAgentSystemTest | `testLlmAgentDirectAnswer` | 1 |
| SingleAgentSystemTest | 全部 | 2 |
| WorkflowLLMEndToEndSystemTest | 全部 | 2 |
| RunnerModuleSystemTest | `testRunnerRunManagedRemoteAgentById` | 1 |
| **合计** | | **~17** |

---

## 6. 迭代演进历程

| 轮次 | 起始测试数 | 新增/增强 | 主要覆盖内容 |
|------|----------|----------|-------------|
| 初始 | ~64 | — | 14 个测试类覆盖基础 LLM/Prompt/Tool/Workflow/Retrieval/Context/Session/Graph/SysOp/Common |
| Round 0 | ~82 | +18 | 补 LLM Schema、Tool Advanced、Session Advanced、SysOp Advanced、Context Advanced、Agent/Runner 等 |
| Round 1 | ~87 | +5 | Workflow 可视化链路修复 + 测试、Retrieval parseFiles/parseUrls |
| Round 2 | ~95 | +8 | Workflow 中断/恢复/Checkpointer、GraphKnowledgeBase 生命周期 |
| Round 3 | ~103 | +8 | 子工作流嵌套中断/恢复、Workflow Trace start/done、raw interact input |
| Round 4 | ~168 | +65 | Tracer snapshot、raw trace payload、深层 namespace、agentic retrieval、全面补齐 |

---

## 7. 执行方式参考

### 全量执行（本地测试）

```bash
mvn -q "-Dtest=com.openjiuwen.core.systemtest.*SystemTest" "-Dsurefire.excludedGroups=" test
```

### 定向执行指定类

```bash
mvn -q "-Dtest=WorkflowSystemTest,WorkflowAdvancedSystemTest" test
```

### 跳过需要网络的测试

测试基类 `SystemTestSupport` 中的 `assumeRemoteModelAvailable()` 会在网络不可用时自动跳过相关测试。
