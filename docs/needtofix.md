# Need To Fix

## 本轮修复范围

- `graph`: 修复当前主源码编译失败的阻塞点，并补齐与 Python 对齐所需的最小图存储/流处理语义。
- `session`: 修复 `streamIterator` 阻塞收集语义、agent interaction 中断检查点占位实现。
- `session.checkpointer`: 去掉 `graphStore()` 占位对象，接回真实 `InMemoryStore`。
- `sysop`: 修复 `SysOperationToolAdapter` 的占位实现，改成真实反射委派到底层子操作。
- `foundation.tool.service_api`: 修复 `RestfulApi` 的 GET/path/query 处理和错误响应语义。
- `tests`: 修复已确认的错误/失真测试，并为上述修复补充回归测试。

## 按依赖排序的问题清单

1. `graph/CompiledGraph.java`
   - `PregelConfig` 构造调用错误，直接导致编译失败。

2. `graph/stream_actor/StreamProcessor.java`
   - 仍按旧版 `DictUtils` 签名使用 `extractLeafNodes/rebuildDict`，与当前 Java 工具类不兼容，直接导致编译失败。

3. `graph/Vertex.java`
   - `preStream()` 返回类型与 `ActorManager.consume()` 不匹配。
   - 子工作流流式输出仍把 `BlockingQueue` 当成带 `send()` 方法的 Python 队列使用，直接导致编译失败。

4. `session/checkpointer/InMemoryCheckpointer.java`
   - `graphStore()` 返回占位 `Object`，没有接入真实图状态存储，和 Python `InMemoryCheckpointer._graph_store = InMemoryStore()` 不一致。

5. `session/interaction/AgentInteraction.java`
   - 调用 checkpointer interrupt 逻辑仍是占位注释，没有真正保存 agent checkpoint。

6. `session/interaction/SimpleAgentInteraction.java`
   - 同样缺少真实 `interruptAgentExecute()` 调用。

7. `session/stream/StreamWriterManager.java` 与 `session/AgentSessionApi.java`
   - `streamIterator()` 当前返回一次性 `collectStreamOutput()` 结果，和 Python `stream_iterator()` 的逐帧消费语义不一致。

8. `sysop/SysOperationToolAdapter.java`
   - 仍返回 `"Operation ... invoked with inputs ..."` 的占位字符串，没有反射委派到真实子操作方法。

9. `foundation/tool/service_api/RestfulApi.java`
   - GET/path/query 参数拼接行为与 Python 语义存在缺口。
   - 非 2xx 且关闭 `raise_for_status` 时的响应 message 语义不完整。

10. 测试问题
    - `session/stream/StreamOutputTest.testWriterAfterEmitterClosed` 是空断言，未执行被测代码。
    - 缺少对 `streamIterator`、agent interrupt checkpoint、`graphStore()`、`SysOperationToolAdapter`、`RestfulApi` 参数映射的回归测试。
