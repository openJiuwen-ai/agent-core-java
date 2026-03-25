# Workflow 模块转译与测试报告

## 1. 工作范围

本次工作基于 [workflowtransform.md](./workflowtransform.md)，对照：

- Python 实现：`agent-core-python/openjiuwen/core/workflow/`
- Python UT：`tests/unit_tests/core/workflow/test_workflow.py`
- Python 流式 UT：`tests/unit_tests/core/workflow/test_workflow_with_comp_stream.py`

目标是检查 Java 版 `workflow` 模块是否真正完成转译；若未完成，则修复实现缺漏，并将 Python 版的核心 UT 转译为 Java UT 后执行验证。

结论：`workflowtransform.md` 中“已完成转换”的结论不完全成立，Java 版在状态流转、子工作流、循环组件、流式链路、回调适配等处仍存在缺口。本次已完成这些核心缺口的修复，并补齐了对应的 Java UT。

## 2. 发现的问题与修复

### 2.1 Start / 状态输入输出语义未完全对齐

- `Start` 组件输出不符合 Python 版透传输入的行为。
- `WorkflowStateCollection` 的 `getInputs/getOutputs/commitUserInputs` 语义与 Python 状态模型不一致。

已修复：

- `Start` 改为按输入透传。
- `WorkflowStateCollection` 修正 `getInputs`、`getOutputs`、`commitUserInputs`，并补充 `commit()`。

### 2.2 Session 继承与子工作流状态挂载不完整

- 顶层 `Workflow.invoke/stream` 会话构造不完整。
- `SubWorkflowSession`、`NodeSession` 的 parent/workflow id/nesting depth 继承不正确。
- 子工作流输出节点与父节点 state 绑定关系错误。

已修复：

- 重建顶层 `WorkflowSession` 创建逻辑，补齐 `InMemoryState`、`ActorManager`、`StreamWriterManager`、`Tracer`。
- 修正 `NodeSession` 的 workflow 继承逻辑和 node state 派生逻辑。
- 修正 `SubWorkflowSession` 与 `Workflow.createSubWorkflowSession()` 的父子节点绑定。
- `SubWorkflowComponentImpl` 改为走 `invokeSubWorkflow(...)`。

### 2.3 Loop 组件转译不完整

- `AdvancedLoopComponentImpl` 与 Python 版 `_condition_invoke/on_invoke` 不一致。
- loop 内部 state 初始化、`LOOP_ID` 写入、body 执行后清理、父 state 回写不完整。
- 多处使用 `Map.of(..., null)`，在 loop 退出/清理路径上直接触发 `NullPointerException`。
- `LoopGroup` 子 session 的 node id 继承不正确，导致 loop 体内部引用路径偏移。

已修复：

- 对齐 loop 入口、condition、post-body、退出清理路径。
- 增加 loop state 快照和结果清理逻辑。
- 将所有 loop 相关的 `null` 更新改为可变 `HashMap`。
- 修正 `LoopGroup` 与 `AdvancedLoopComponentImpl` 的父节点/状态挂载关系。

### 2.4 回调与 tracer 事件适配不一致

- Java tracer 发出 snake_case 事件名，但 `CallbackManager` 仅按 Java 风格方法名查找，导致回调无法正确适配。

已修复：

- `CallbackManager` 增加 snake_case / camelCase 互转与参数名匹配逻辑，兼容 Python 风格事件。

### 2.5 流式链路缺漏

- `BaseWorkflow.addStreamConnection()` 未完整注册流消费者。
- `Vertex` 未完整实现 `StreamConsumer` 行为。
- End 节点在 `TRANSFORM/COLLECT` 模式下，不能像 Python 版那样消费流输入的 `Iterator` 叶子节点。
- 简单 streaming end 只返回 1 个 chunk，而不是 Python 等价的逐帧输出。

已修复：

- `Vertex` 实现 `StreamConsumer`，并补齐流式执行、chunk 分发、end-node 输出路径。
- `PregelGraph` 增加 `getVertex()`，配合 stream actor 注册。
- `End.transform()` / `End.collect()` 支持递归提取并消费流输入中的 `Iterator` 叶子节点。
- 修复简单 `STREAM -> END(TRANSFORM)` 场景的逐帧输出行为。

## 3. 新增/转译的 Java UT

新增文件：

- `src/test/java/com/openjiuwen/core/workflow/WorkflowTest.java`

本次从 Python UT 中提取并转译了 11 个核心用例：

1. `testSimpleWorkflow`
2. `testSimpleWorkflowWithParallelBranches`
3. `testSimpleWorkflowWithCondition`
4. `testSimpleWorkflowWithBranchCondition`
5. `testWorkflowWithWaitForAll`
6. `testWorkflowWithBranch`
7. `testWorkflowWithLoopNumberCondition`
8. `testSubWorkflow`
9. `testStreamingWorkflow`
10. `testStreamComponentWorkflow`
11. `testTransformWorkflow`

覆盖能力包括：

- 基础编排
- 条件路由 / Branch 路由
- `wait_for_all`
- 子工作流
- NumberCondition 循环
- `STREAM -> END`
- `STREAM -> COLLECT`
- `STREAM -> TRANSFORM -> COLLECT`

## 4. 验证结果

执行命令：

```powershell
mvn -q "-Djacoco.skip=true" "-Dtest=WorkflowTest" test
```

结果：

- `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

说明：

- 核心 workflow 转译链路已通过 Java 回归测试。
- 在修复前，`testWorkflowWithLoopNumberCondition` 与 `testStreamingWorkflow` 均失败；修复后已通过。
- 本次补充的 `STREAM -> COLLECT`、`STREAM -> TRANSFORM -> COLLECT` 用例也已通过。

## 5. 仍需注意的事项

本次已修复 `workflowtransform.md` 范围内最核心、最直接影响功能正确性的缺漏，但仍有两类内容未纳入本轮 Java UT 转译：

- Python `test_workflow_with_interrupt.py` 中的交互式中断 / checkpointer 恢复链路
- Python `test_workflow_with_comp_stream.py` 中更复杂的多源模板流式渲染场景

原因是这两类场景依赖更完整的互动恢复、checkpointer 生命周期、以及 End 模板在多数据源并发流中的渲染协调；它们超出了本次围绕 `workflowtransform.md` 做“核心功能补齐”的范围。

另外，当前运行中仍可见 `tracer_workflow.*` 的“Handler not found”日志，这不影响本次 11 个 UT 结果，但说明 tracer 回调链路还有进一步清理空间。

## 6. 结论

Java 版 `workflow` 模块此前并未真正完成与 Python 版的等价转译；本次已补齐核心缺漏，并通过 11 个转译后的 Java UT 完成验证。就 `workflowtransform.md` 覆盖的主流程能力而言，当前 Java 版本已经达到可回归、可验证的状态。
