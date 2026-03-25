# JAVA 单元测试修复与补齐报告

生成时间: 2026-03-07

## 1. 范围与依据

- 对照依据: `docs/l0-l2-python-java-unit-test-check-report.md`
- Python 参考测试:
  - `agent-core-python/tests/unit_tests/core/session/*`
  - `agent-core-python/tests/unit_tests/core/graph/*`
  - `agent-core-python/tests/unit_tests/extensions/checkpointer/*`
  - `agent-core-python/tests/unit_tests/core/foundation/tool/test_restfulapi.py`
- 本次处理目标:
  - 修复 Java 侧已经确认有问题的单元测试
  - 对照 Python 侧已实现模块测试，补齐 Java 侧缺失的高价值单测
  - 让新增测试覆盖真实运行语义，而不是继续停留在 smoke/构造级别

## 2. 本轮处理结果

### 2.1 已修复的错误或失真测试

- `src/test/java/com/openjiuwen/core/foundation/tool/function/LocalFunctionTest.java`
  - 修正 `testStreamSingleResult` 的断言语义，改为与 Python `LocalFunction.stream` 一致: 非生成器函数不应被当成 stream 成功消费。

- `src/test/java/com/openjiuwen/core/foundation/tool/service_api/RestfulApiTest.java`
  - 原先 `testInvokeGetWithParams` 只断言“抛异常”，并没有验证 URL/path/query/header 拼接。
  - 现改为使用本地 `HttpServer` 做真实端到端校验，补上:
    - path 占位符替换
    - query 参数合并
    - header 透传
    - `reason/message/code` 返回值校验
    - `raise_for_status=false` 行为校验

- `src/test/java/com/openjiuwen/core/session/stream/StreamOutputTest.java`
  - 修复 `testWriterAfterEmitterClosed` 假覆盖问题，补上真实写入动作。
  - 增加 `streamIterator()` 增量消费校验。

- `src/test/java/com/openjiuwen/core/session/AgentSessionApiTest.java`
  - 补上 `streamIterator()` 的 producer/consumer 语义校验，防止再次退化成阻塞收集。

### 2.2 本轮新增的缺失测试

- `src/test/java/com/openjiuwen/core/session/checkpointer/InMemoryCheckpointerTest.java`
  - 覆盖 agent checkpoint 恢复
  - 覆盖 workflow raw interactive input 恢复
  - 覆盖 workflow node interactive input 恢复
  - 覆盖 workflow 异常保存、完成清理、release 清理
  - 覆盖 `graphStore()` 返回真实 store

- `src/test/java/com/openjiuwen/core/session/interaction/WorkflowInteractionTest.java`
  - 覆盖 workflow 级 raw input 直接消费
  - 覆盖无输入时写出 interaction output 并抛出 `GraphInterrupt`

- `src/test/java/com/openjiuwen/core/graph/stream_actor/StreamProcessorTest.java`
  - 覆盖 stream schema -> iterator 生成
  - 覆盖 chunk 路由、end frame 结束、callback 回调
  - 覆盖 end message/source helper 行为

- `src/test/java/com/openjiuwen/core/graph/CompiledGraphTest.java`
  - 覆盖 pre/post workflow checkpoint hook 调用
  - 覆盖普通 map 输入 commit 到 workflow state
  - 覆盖 `InteractiveInput` 走 checkpointer 分支
  - 覆盖 Pregel 异常传播

## 3. 为支撑真实测试而修复的源码问题

新增测试暴露出几处真实转译问题，如果不修复，测试只能得到“失败现象”，无法形成有效回归保护。本轮一并修正如下:

- `src/main/java/com/openjiuwen/core/session/checkpointer/InMemoryCheckpointer.java`
  - `preAgentExecute()` 原先把 `__interactive_input__` 塞进 `setState()`，但 `AgentStateCollection.setState()` 并不会接收该键，导致交互输入实际丢失。
  - 已改为写入 agent state 的 `update(...)` 路径。
  - `InMemoryWorkflowStorage` 补上 workflow `state_updates` 的保存与恢复。
  - workflow 恢复时补上 Python 对应的 interactive input 处理:
    - `rawInputs` 写入 workflow state
    - `userInputs` 写入对应 node state
  - `postWorkflowExecute()`、`release()`、force delete 路径补上 `graphStore.delete(...)`。
  - workflow 完成时不再无条件删除整个 session 下的 workflow store，而是只清当前 workflow。

- `src/main/java/com/openjiuwen/core/session/state/WorkflowStateCollection.java`
  - 新增 workflow-scoped state 的读取/更新能力，避免把 workflow input 错写到 comp state。

- `src/main/java/com/openjiuwen/core/session/state/WorkflowCommitState.java`
  - 新增 workflow state commit/update-and-commit 能力。
  - 新增 pending updates 的导出/恢复能力，便于 checkpointer 按 Python 语义恢复。

- `src/main/java/com/openjiuwen/core/session/interaction/WorkflowInteraction.java`
  - 改为从 `workflow_state` 而不是 `comp_state` 读取/清理 workflow interactive input。
  - 修复 `Map.of(..., null)` 导致的 `NullPointerException`。

- `src/main/java/com/openjiuwen/core/graph/Vertex.java`
  - 清理 interactive input 时改为操作 workflow state，并同步 commit。

## 4. 与 Python 测试对照后的当前结论

本轮之后，下面几类此前报告中最关键的缺口已经补上:

- `session`
  - `stream_iterator`
  - `workflow interaction`
  - `inmemory checkpointer` 基础恢复/清理语义

- `graph`
  - `CompiledGraph` 基础执行与 hook 语义
  - `StreamProcessor` 流路由与结束语义

- `foundation`
  - `RestfulApi.invoke()` 不再只有弱化 smoke test，已有真实 HTTP 行为校验

仍未完全对齐 Python 的测试差异主要还有:

- `session.tracer`
  - Python 侧复杂 workflow trace、并行/嵌套 trace、interactive trace 的断言规模仍明显更大。

- `graph`
  - Python 的 `subgraph_with_interrupt`、`nested_loop_with_inner_parallel`、更深层恢复链路，Java 侧还没有等价测试。

- `context_engine`
  - 当前仍以触发条件测试为主，压缩后内容/summary/reload 的真实结果对比不够。

## 5. 验证结果

已执行:

```bash
mvn -q "-Dtest=InMemoryCheckpointerTest,WorkflowInteractionTest,StreamProcessorTest,CompiledGraphTest" test
mvn -q clean test
```

结果:

- 新增测试通过
- 全量单元测试通过

## 6. 结论

这次不是只补“测试文件数量”，而是把报告里最关键的几类失真点变成了真实回归保护:

- 错误测试已修正，不再存在明显假覆盖
- `session/checkpointer/interaction/graph stream` 这些 Python 中高价值的运行时语义，Java 已有可执行单测
- 为保证这些单测有效，相关源码中的 workflow state / checkpoint / interactive input 转译偏差也一并修正
