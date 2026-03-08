# L0-L2 Python / Java 单元测试对比检查报告

生成时间: 2026-03-07

## 范围与口径

- Python 对照范围: `agent-core-python/tests/unit_tests/core/{common,foundation,context_engine,graph,session,sys_operation}`。
- 补充参考范围: `agent-core-python/tests/unit_tests/extensions/checkpointer/*`，用于对照 Java 已放入 `core.session.checkpointer` 的持久化/恢复语义。
- Java 检查范围: `agent-core-java/agent-core-java/src/test/java/com/openjiuwen/core/{common,foundation,context,graph,session,sysop}`。
- 仅把 Java 当前已经存在源码实现的模块纳入对比；Python 侧存在但 Java 侧完全未落地的模块，会单列说明，但不计入“已实现模块测试缺失”统计。
- 检查方式: 测试文件静态对照 + `mvn test -q` 可执行性验证。

## 总体结论

| 模块 | 结论 | 说明 |
| --- | --- | --- |
| `common` | 基本对齐 | 错误码、异常、结构化日志、字典/Schema 工具大体都有对应单测，缺口主要在日志辅助 API 和更细的异常路径。 |
| `foundation` | 明显缺口 | 已实现子模块有测试，但 `RestfulApi`、输出解析器、工具层仍存在大量弱化覆盖；Python 侧 `MarkdownOutputParser`、`StreamableHttpClient`、`store` 相关测试在 Java 侧无对应实现。 |
| `context_engine` | 中等缺口 | Context/ModelContext 主流程覆盖尚可，但压缩器、摘要 offloader、KV cache 相关测试明显弱于 Python，很多只测触发条件，不测真实行为结果。 |
| `graph` | 中等缺口且当前不可执行 | `Pregel`/channel/store/task pool 的基础用例不少，但中断恢复、嵌套 loop/并行场景不完整；同时图模块主源码当前编译失败，整套 Java 单测无法实际运行。 |
| `session` | 最大缺口 | Java 偏重状态对象和 span 数据结构；Python 中关于 `stream_iterator`、workflow interaction、checkpointer、trace 集成的关键测试基本没有落地。 |
| `sys_operation` | 基础行为较强，集成回归不足 | 本地 FS/Shell/Code 的单操作测试很多，但 Python 里的 tool adapter / ResourceMgr / 批量生命周期集成测试没有对应 Java 用例。 |

## Java 单测当前执行状态

执行 `mvn test -q` 时，Java 工程尚未进入测试执行阶段，先在主源码编译阶段失败，关键报错如下：

- `src/main/java/com/openjiuwen/core/graph/CompiledGraph.java:55,60`
- `src/main/java/com/openjiuwen/core/graph/stream_actor/StreamProcessor.java:94,134,139,149`
- `src/main/java/com/openjiuwen/core/graph/Vertex.java:468,511,548`

这意味着当前 Java 单测即使编写存在，也还不能形成可执行的回归保护网。特别是上述 3 个图模块类，在 `src/test/java/com/openjiuwen/core/graph` 下也没有对应单测覆盖。

## 模块级对照

### 1. `common`

Python 侧主要参考：

- `tests/unit_tests/core/common/test_errors.py`
- `tests/unit_tests/core/common/test_status_code.py`
- `tests/unit_tests/core/common/log/test_logger.py`
- `tests/unit_tests/core/common/log/test_structured_log.py`
- `tests/unit_tests/core/common/utils/test_dict.py`
- `tests/unit_tests/core/common/utils/test_schema_utils.py`

Java 侧已有对应：

- `ErrorTest.java`
- `StatusCodeTest.java`
- `LogManagerTest.java`
- `StructuredLogEventTest.java`
- `DictUtilsTest.java`
- `SchemaUtilsTest.java`

结论：

- `ErrorTest`、`StatusCodeTest`、`DictUtilsTest` 与 Python 参考用例大体一致，甚至补充了更多 builder / toMap / range 校验分支。
- `StructuredLogEventTest` 对事件对象本身的创建、序列化、注册/注销做了较充分覆盖。
- `common` 层当前没有发现明显的“错误测试用例”。

缺失或弱化覆盖：

- Python `test_logger.py` 中的 `register_logger_type_check`、日志配置加载、文件输出、日志目录创建/失败路径等，在 Java `LogManagerTest` 中没有等价用例。
- Python `test_structured_log.py` 中对“记录日志”入口的测试较多，例如 `test_log_string_message`、`test_log_with_event_type`、`test_log_json_format`、`test_register_invalid_class_raises_error`；Java 当前更偏向事件对象测试，对真正的日志写入入口覆盖不足。

### 2. `foundation`

Python 侧主要参考：

- `tests/unit_tests/core/foundation/llm/test_model_client_config.py`
- `tests/unit_tests/core/foundation/output_parser/test_json_output_parser.py`
- `tests/unit_tests/core/foundation/output_parser/test_markdown_output_parser.py`
- `tests/unit_tests/core/foundation/prompt/test_template_assemble.py`
- `tests/unit_tests/core/foundation/tool/test_api_param_mapper.py`
- `tests/unit_tests/core/foundation/tool/test_restfulapi.py`
- `tests/unit_tests/core/foundation/tool/test_streamable_http_client.py`
- `tests/unit_tests/core/foundation/tool/test_tool_decorator.py`
- `tests/unit_tests/core/foundation/store/*`

Java 侧已有对应：

- `ModelClientConfigTest.java`
- `JsonOutputParserTest.java`
- `PromptAssembleTest.java`
- `ToolCardTest.java`
- `LocalFunctionTest.java`
- `McpToolTest.java`
- `ApiParamMapperTest.java`
- `RestfulApiTest.java`
- `ResponseParserTest.java`

Java 侧当前不存在源码/测试的 Python 能力：

- `MarkdownOutputParser`: `src/main/java/com/openjiuwen/core/foundation/llm/output_parsers/MarkdownOutputParser.java` 不存在。
- `StreamableHttpClient`: `src/main/java/com/openjiuwen/core/foundation/tool/service_api/StreamableHttpClient.java` 不存在。
- `foundation.store`: `src/main/java/com/openjiuwen/core/foundation/store` 不存在。

结论：

- `ModelClientConfig`、`JsonOutputParser`、`PromptAssemble`、`ApiParamMapper` 基本有对应用例，但 Java 侧仍偏重 happy path 和结构测试。
- `foundation` 的主要问题不在“完全没写测试”，而在很多测试只验证对象能创建、能抛异常，却没有覆盖 Python 里的真实交互语义。

缺失或弱化覆盖：

- Python `test_restfulapi.py` 对 HTTP 响应格式、错误码、HTML/XML/文本响应、gzip、chunked、参数映射位置、超时和响应体大小限制都有用例；Java `RestfulApiTest` 只覆盖了构造、默认值、不可达主机、以及一个非常弱的 GET 参数 smoke case。
- 虽然 Java 有 `ResponseParserTest.java`，但它只能补 parser 分支，不能替代 `RestfulApi.invoke()` 端到端覆盖。
- Python `test_streamable_http_client.py` 和 `test_markdown_output_parser.py` 在 Java 没有任何对应测试，因为对应能力尚未落地。
- Python `test_tool_decorator.py` 的函数工具 schema 推导能力，在 Java 没有等价测试；`LocalFunctionTest` 只覆盖手工包装后的函数调用。

明确的失真用例：

- `src/test/java/com/openjiuwen/core/foundation/tool/service_api/RestfulApiTest.java:166-186`
  - 用例名是“`Invoke GET with path and query params builds correct URL`”，但实际只对不可达地址做 `assertThrows(Throwable.class, ...)`。
  - 这不会验证 URL 是否真的正确拼接，也不会验证 path/query 映射是否符合 Python 行为，只会验证“调用时抛了某个异常”。

### 3. `context_engine`

Python 侧主要参考：

- `tests/unit_tests/core/context_engine/test_context_engine.py`
- `tests/unit_tests/core/context_engine/test_context_model.py`
- `tests/unit_tests/core/context_engine/test_current_round_compressor.py`
- `tests/unit_tests/core/context_engine/test_dialogue_compressor.py`
- `tests/unit_tests/core/context_engine/test_round_level_compressor.py`
- `tests/unit_tests/core/context_engine/test_message_offloader.py`
- `tests/unit_tests/core/context_engine/test_message_summary_offloader.py`
- `tests/unit_tests/core/context_engine/test_kv_cache_manager.py`

Java 侧已有对应：

- `ContextEngineTest.java`
- `ModelContextTest.java`
- `SessionModelContextTest.java`
- `CurrentRoundCompressorTest.java`
- `DialogueCompressorTest.java`
- `RoundLevelCompressorTest.java`
- `MessageOffloaderTest.java`
- `MessageSummaryOffloaderTest.java`
- `KVCacheManagerTest.java`

结论：

- `ContextEngine` 生命周期、`ModelContext` 滑窗/统计、`MessageOffloader` 基础行为在 Java 里已有比较扎实的覆盖。
- 但与 Python 相比，Java 在“压缩/摘要/缓存释放是否真的发生并产出正确结果”这条链路上的验证明显不足。

缺失或弱化覆盖：

- `ContextEngineTest` 缺少 Python 中的异常路径对照：未知 processor 类型、processor 初始化失败、`save_contexts(session=None)`、部分 context id 缺失时的保存行为、默认 context id 等。
- `CurrentRoundCompressor`：Python 有真实压缩结果测试，如 `test_large_message_compression_triggered`、`test_compression_with_assistant_and_tool_messages`；Java 只覆盖“不压缩”“触发条件”“配置 builder”，没有断言压缩后消息内容或 offload/reload 语义。
- `DialogueCompressor` / `RoundLevelCompressor`：Python 有“保留最后一轮”“自定义压缩 prompt”“reloader 恢复原消息”等用例；Java 主要停留在 trigger 级别。
- `MessageSummaryOffloader`：Python 会验证默认/自定义 summary prompt、不同 role、原消息保留；Java 只有 config 校验和 builder 测试。
- `KVCacheManager`：Python 用 fake `InferenceAffinityModel` 明确验证 `release()` 调用时机；Java `KVCacheManager` 当前实现本身就是 no-op，因此对应测试几乎都只是在确认“不抛异常”。

明显弱化、不能算有效行为校验的用例：

- `src/test/java/com/openjiuwen/core/context/context/KVCacheManagerTest.java:68-84`
- `src/test/java/com/openjiuwen/core/context/context/KVCacheManagerTest.java:88-101`

这两组用例的显示名称分别是“modified messages/tools trigger release detection”，但断言层面没有验证 release 是否发生，只是调用 `manager.release(...)` 后不报错。它们更像 smoke test，而不是行为回归测试。

### 4. `graph`

Python 侧主要参考：

- `tests/unit_tests/core/graph/test_channel.py`
- `tests/unit_tests/core/graph/test_graph_store.py`
- `tests/unit_tests/core/graph/test_pregel.py`
- `tests/unit_tests/core/graph/test_task.py`
- 补充参考：`tests/unit_tests/extensions/checkpointer/test_graph_store.py`

Java 侧已有对应：

- `ChannelTest.java`
- `GraphStoreTest.java`
- `PregelTest.java`
- `TaskExecutorPoolTest.java`

结论：

- Java `PregelTest` 对 barrier、conditional routing、multi-routing、builder/router/config 常规路径覆盖比 Python 更细。
- 但 Python 侧最值钱的几类“恢复与中断”测试，在 Java 里没有真正补齐。

缺失或弱化覆盖：

- Python `test_pregel.py` 中的 `test_subgraph_with_interrupt`、`test_nested_loop_with_inner_parallel`，Java `PregelTest` 没有对应场景。
- Python `test_recursion_limit_recovery` 测的是嵌套子图恢复；Java 当前只有 `testRecursionLimitExceeded`，语义明显更弱。
- `GraphInterrupt` 在 Java 里只有值对象级别的 `testGraphInterrupt()`，没有对“可恢复中断 -> 断点恢复 -> 继续执行”链路做回归测试。
- Java 主源码里当前实际编译失败的 `CompiledGraph`、`graph/stream_actor/StreamProcessor`、`Vertex` 没有任何对应用例，这也是为什么 `mvn test` 甚至跑不起来。

### 5. `session`

Python 侧主要参考：

- `tests/unit_tests/core/session/test_session.py`
- `tests/unit_tests/core/session/test_wrapper.py`
- `tests/unit_tests/core/session/interaction/test_interactive_input.py`
- `tests/unit_tests/core/session/stream/test_stream_output.py`
- `tests/unit_tests/core/session/tracer/test_agent.py`
- `tests/unit_tests/core/session/tracer/test_decorator.py`
- `tests/unit_tests/core/session/tracer/test_workflow_tracer.py`
- 补充参考：`tests/unit_tests/extensions/checkpointer/*`

Java 侧已有对应：

- `SessionTest.java`
- `SessionBasicTest.java`
- `AgentSessionApiTest.java`
- `SessionUtilsTest.java`
- `InteractiveInputTest.java`
- `InteractiveInputFullTest.java`
- `StreamOutputTest.java`
- `StreamOutputFullTest.java`
- `StateTest.java`
- `TracerTest.java`

结论：

- Java `session` 对 state merge / dump / commit、stream schema 对象、span 数据结构测试很足。
- 但 Python 中真正体现 session 运行时语义的 4 类关键用例，在 Java 侧基本缺位：流式迭代、workflow interaction、checkpointer、workflow tracer 集成。

缺失或弱化覆盖：

- `stream_iterator` 语义不一致且无回归测试。
  - Python `openjiuwen/core/session/agent.py:72-73` 返回异步迭代器，`tests/unit_tests/core/session/test_wrapper.py:11-33` 直接按 producer/consumer 方式消费。
  - Java `src/main/java/com/openjiuwen/core/session/AgentSessionApi.java:127-134` 的 `streamIterator()` 返回的是 `collectStreamOutput()` 的阻塞收集结果，而不是逐帧迭代；当前没有任何测试去验证与 Python 的流式消费对齐。
- Java `core.session.checkpointer` 下已有 `CheckpointerFactory`、`InMemoryCheckpointer` 等实现，但 `src/test/java/com/openjiuwen/core/session` 下没有任何 checkpointer 单测；而 Python 在 `tests/unit_tests/extensions/checkpointer/*` 下有一整套 graph/workflow/agent/persistence/provider 测试。
- Java 已实现 `WorkflowInteraction`，并在 `src/main/java/com/openjiuwen/core/session/interaction/WorkflowInteraction.java:74-100` 中通过 `GraphInterruptRuntimeWrapper` 抛出恢复型中断，但测试侧没有对应的 interaction / resume / stream output 语义回归。
- Java `TracerTest` 基本只覆盖 span / manager 的字段更新，而 Python `test_workflow_tracer.py`、`test_agent.py` 会验证 stream workflow、并行 workflow、嵌套 workflow、interactive workflow 的 trace 输出。

明确的错误测试：

- `src/test/java/com/openjiuwen/core/session/stream/StreamOutputTest.java:149-161`
  - 用例名是“writer discards data if emitter is closed”。
  - 但 `assertDoesNotThrow(() -> { })` 的 lambda 是空的，真正的 `customWriter.write(...)` 根本没有执行。
  - 这条用例当前形成的是假覆盖，必须修正。

### 6. `sys_operation`

Python 侧主要参考：

- `tests/unit_tests/core/sys_operation/local/test_code_operation.py`
- `tests/unit_tests/core/sys_operation/local/test_fs_operation.py`
- `tests/unit_tests/core/sys_operation/local/test_shell_operation.py`
- `tests/unit_tests/core/sys_operation/local/test_custom_operation_extension.py`
- `tests/unit_tests/core/sys_operation/local/test_operation_as_tool.py`

Java 侧已有对应：

- `LocalCodeOperationTest.java`
- `LocalFsOperationTest.java`
- `LocalShellOperationTest.java`
- `CustomOperationExtensionTest.java`
- `OperationRegistryTest.java`
- `SysOperationTest.java`
- `SysOperationCardTest.java`
- `SandboxOperationTest.java`

结论：

- 对单个本地操作的行为验证，Java 做得并不差，尤其 FS/Shell/Code 子操作的参数组合覆盖比较细。
- 最大缺口出在“把 sys_operation 当成 tool/资源注册到运行时”这一层的集成测试。

缺失或弱化覆盖：

- Python `test_operation_as_tool.py` 覆盖了 ResourceMgr 中的工具注册、工具 ID 生成、tool invoke、binary/text round-trip，以及批量 sys operation 生命周期；Java 侧没有任何对等测试文件。
- Java 虽然已经有 `src/main/java/com/openjiuwen/core/sysop/SysOperationToolAdapter.java`，但当前实现仍是 placeholder：`extractTools()` 最终包装出的 `LocalFunction` 只返回字符串 `"Operation ... invoked with inputs ..."`，没有真实委派到底层子操作；这一层既没有行为测试，也没有端到端集成测试。

## 明确需要优先补齐的测试清单

建议优先级从高到低如下：

1. 修复当前明显错误/失真的测试。
   - `StreamOutputTest.testWriterAfterEmitterClosed`
   - `RestfulApiTest.testInvokeGetWithParams`
2. 先让 Java 图模块恢复可编译，并补 `CompiledGraph` / `StreamProcessor` / `Vertex` 的最小单测，否则整套测试无法执行。
3. 为 `session` 补齐 4 类集成回归。
   - `streamIterator` 增量消费
   - `WorkflowInteraction` + `GraphInterruptRuntimeWrapper`
   - `checkpointer` 的 pre/post/interruption/restore
   - workflow / nested / parallel tracer 输出
4. 为 `context_engine` 补齐真实行为测试。
   - compressor 压缩结果
   - summary offloader 摘要结果
   - KV cache release 调用时机
5. 为 `sys_operation` 补齐 tool adapter / 资源管理器集成回归。
6. 为 `foundation.RestfulApi` 补齐与 Python 对齐的端到端 HTTP 响应测试。

## 最终判断

如果只看“有没有写测试文件”，Java 版并不算空白；但如果按 Python 版的回归保护力度来衡量，Java 版目前仍有 3 个明显短板：

- `session` 运行时语义测试严重不足。
- `context_engine` 的高价值行为测试被大量弱化成 trigger/smoke test。
- `graph` 模块虽然写了不少测试，但工程当前无法编译，且中断恢复类场景仍未补齐。

另外，已经明确定位到 2 条应直接修复的错误/失真测试：

- `src/test/java/com/openjiuwen/core/session/stream/StreamOutputTest.java:149-161`
- `src/test/java/com/openjiuwen/core/foundation/tool/service_api/RestfulApiTest.java:166-186`
