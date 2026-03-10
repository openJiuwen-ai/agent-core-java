# Round 3 系统测试与 Python 对照补漏记录

## 本轮目标

基于 `docs/SYS_TEST/round2_todo.md`，继续补齐 Java 版在以下方向的缺漏：

1. 子工作流嵌套中断 / 恢复系统测试
2. 顶层 workflow trace start/done 语义对齐测试
3. 对照 Python 修补子工作流 `PregelConfig` 透传与 `interact()` raw input 兼容性

## 对照 Python 发现的主要缺口

### 已修补

1. `SubWorkflowComponentImpl` / `Workflow.invokeSubWorkflow()` 之前没有把外层 `PregelConfig` 显式透传给子工作流，导致嵌套 graph checkpoint namespace 偏离 Python 的 `parent_ns/ns` 语义
2. 顶层 `WorkflowSession` 在 workflow trace start 之前尚未注册 workflow config，导致 `workflow_name` / `workflow_version` metadata 不完整
3. 顶层 `Workflow.invoke()` / `Workflow.stream()` 之前没有补齐 workflow-level trace start/done 生命周期
4. `NodeSessionApi.interact()` 之前固定返回 `Map<String, Object>`，无法像 Python 一样承接 raw input 恢复结果

### 仍待继续

1. Java tracer 仍按引用把 `Span` 写入 trace stream；Python 会发送快照，Java 当前历史 start/done 帧仍可能被后续更新覆盖
2. `TracerWorkflowUtils.traceWorkflowStart()` / `traceWorkflowDone()` 与 `Span.inputs` 仍偏向 `Map<String, Object>`，顶层 raw/list/string 输入的 trace 语义还未完全对齐 Python 的 `Any`
3. 仍缺更深一层的 loop + sub-workflow 多级 namespace 系统测试
4. 仍需继续按 Python 补 graph retriever / agentic retriever 的更深层系统测试

## 本轮已完成修补

### 运行时语义

1. `Workflow.createWorkflowSession()` 现在会在 trace start 之前注册 workflow config，保证 workflow metadata 可用
2. `Workflow.invoke()` / `Workflow.stream()` 现在会在顶层执行前后显式触发 `traceWorkflowStart()` / `traceWorkflowDone()`
3. `SubWorkflowComponentImpl` 现在会把 `Constant.CONFIG_KEY` 透传给 `Workflow.invokeSubWorkflow()` / `streamSubWorkflow()`
4. `Workflow.invokeSubWorkflow()` / `streamSubWorkflow()` 现在支持接收外层 `PregelConfig`，嵌套 graph checkpoint 会沿用父级 namespace
5. `NodeSessionApi.interact()` 现在改为泛型返回，允许组件像 Python 一样直接接收 raw input
6. `TracerWorkflowUtils.traceComponentInteractiveInputs()` 现在支持记录任意类型的交互输入，而不再局限于 `Map<String, Object>`

### 新增 / 增强系统测试

#### 增强

1. `src/test/java/com/openjiuwen/core/systemtest/WorkflowInterruptSystemTest.java`
2. 新增覆盖场景：
   - 子工作流中断后会在父 workflow namespace 下保留 nested graph checkpoint
   - 使用 `InteractiveInput` 恢复子工作流时，外层 / 内层 workflow 都不会从头重跑
   - raw interactive input 可以像 Python 一样直接恢复 workflow

#### 新增

1. `src/test/java/com/openjiuwen/core/systemtest/WorkflowTraceSystemTest.java`
2. 新增覆盖场景：
   - 顶层 workflow stream 会产出 workflow-level trace start/done span
   - workflow trace metadata 会补齐 `workflow_id` / `workflow_name` / `workflow_version`
   - workflow done span 会携带最终 outputs

## 验证结果

已通过：

```powershell
mvn -q -DskipTests compile
mvn -q "-Dtest=WorkflowInterruptSystemTest,WorkflowTraceSystemTest" test
mvn -q "-Dtest=WorkflowSystemTest,WorkflowAdvancedSystemTest,WorkflowInterruptSystemTest,WorkflowTraceSystemTest,SessionAdvancedSystemTest" test
```

## 建议的 Round 4 继续项

1. 给 Java tracer 增加 span snapshot / deep-copy 发送语义，避免历史 trace 帧被覆盖
2. 评估是否把 workflow-level trace 的 inputs / outputs 类型从 `Map<String, Object>` 放宽到 `Object`
3. 补 loop + sub-workflow 混合嵌套、多级 `parentNs/ns` 的系统测试
4. 继续按 Python 检查 graph retriever / agentic retriever 的系统测试缺口
