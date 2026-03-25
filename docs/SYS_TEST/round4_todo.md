# Round 4 Java / Python 对照补漏与收口记录

## 本轮目标

基于 `docs/SYS_TEST/round3_todo.md` 继续对照 Python，实现这一轮剩余缺口的彻底收口：

1. 修复 tracer span 发送仍按引用复用、历史帧会被后续更新污染的问题。
2. 修复 workflow / component trace 对 raw payload 的包装、丢失与类型收窄问题。
3. 补齐 loop + sub-workflow 更深一层 namespace 的中断 / 恢复系统测试。
4. 补齐 graph retriever / agentic retriever 的本地系统测试。

## 对照 Python 本轮确认的实际缺口

### 运行时语义

1. `TraceBaseHandler.sendData()` 之前直接把同一个 `Span` 实例写入 trace stream，导致 start / running / done 历史帧会被后续更新覆盖；Python 会发送 deep-copy snapshot。
2. `Span.inputs`、`TracerWorkflowUtils.traceWorkflowStart()`、`traceWorkflowDone()`、`traceComponentOutputs()` 仍偏向 `Map<String, Object>`，导致 top-level raw/list/string payload 会被包装或类型收窄。
3. `Vertex.traceComponentOutputs()` 会把非 `Map` 输出直接丢掉，component trace 无法保留 raw outputs。
4. `WorkflowStateCollection.setOutputs()` 之前忽略非 `Map` 输出，导致 workflow final outputs 与 workflow done trace 无法稳定拿到 raw 结果。

### 系统测试覆盖

1. 之前缺少 loop + sub-workflow 混合嵌套下，更深层 `parent_ns/ns` checkpoint namespace 的中断 / 恢复验证。
2. 之前缺少本地可重复的 agentic graph retrieval 覆盖，无法确认 Java 分支确实走到了与 Python 对齐的 LLM-guided graph path。

## 本轮已完成修补

### Tracer / Span

1. `Span`、`TraceAgentSpan`、`TraceWorkflowSpan` 增加 snapshot / deep-copy 发送语义。
2. `TraceBaseHandler.sendData()` 改为发送 detached snapshot，避免历史 trace 帧被后续更新污染。
3. `Span.inputs` 改为 `Object`，允许直接保留 raw/list/string 输入。
4. `TraceWorkflowSpan` 现在会稳定复制 `interactive_inputs`、`stream_inputs`、`stream_outputs` 等复合字段。

### Workflow / State / Trace Payload

1. `Workflow.traceWorkflowStart()` / `traceWorkflowDone()` 不再把 raw payload 强行包装为 `inputs` / `result`。
2. `TracerWorkflowUtils.traceWorkflowStart()` / `traceWorkflowDone()` / `traceComponentOutputs()` 全部放宽到 `Object`。
3. `Vertex.traceComponentOutputs()` 不再丢弃非 `Map` 输出。
4. `WorkflowStateCollection.setOutputs()` 现在会统一保存任意类型输出，workflow final outputs 与 trace done 都能拿到 raw 值。

### 嵌套 Workflow Tracing

1. 子工作流节点开始执行时会注册独立 workflow span manager，保证嵌套 trace 生命周期完整。
2. workflow metadata 继续沿用上一轮已补齐的 `workflow_name` / `workflow_version` 透传逻辑。

## 新增 / 增强系统测试

### `src/test/java/com/openjiuwen/core/systemtest/WorkflowTraceSystemTest.java`

1. 新增 workflow trace snapshot 断言，确认 start span 不会在结束后被回填 `end_time` / `outputs`。
2. 新增 raw workflow payload trace 断言，确认 top-level trace 能保留 `List` / `String`。
3. 新增 raw component outputs trace 断言，确认 component / workflow final trace 都能保留 `List` 输出。

### `src/test/java/com/openjiuwen/core/systemtest/WorkflowInterruptSystemTest.java`

1. 新增 loop + sub-workflow 混合嵌套中断恢复场景。
2. 新增多级 namespace 断言：
   - `outerWorkflowId`
   - `outerWorkflowId:loop:1`
   - `outerWorkflowId:loop:1:body:1`
   - `outerWorkflowId:loop:1:body:1:sub:1`
3. 断言恢复后 outer / inner workflow 都不会从头重跑。

### `src/test/java/com/openjiuwen/core/systemtest/RetrievalAdvancedSystemTest.java`

1. 新增本地 fake LLM client。
2. 新增 `GraphKnowledgeBase + RetrievalConfig.agentic=true` 场景。
3. 断言 agentic graph retrieval 确实走到本地 `read -> read -> rewrite` 路径，而不是退化为普通检索。

## 验证结果

已通过：

```powershell
mvn -q -DskipTests compile
mvn -q "-Dtest=WorkflowTraceSystemTest,WorkflowInterruptSystemTest,RetrievalAdvancedSystemTest" test
mvn -q "-Dtest=TracerTest,WorkflowAdvancedSystemTest,RetrievalSystemTest" test
mvn -q "-Dtest=WorkflowSystemTest,SessionAdvancedSystemTest" test
```

## 本轮收口结论

1. round3 剩余的 tracer snapshot、raw trace payload、deep namespace、agentic retrieval 覆盖缺口已全部落地。
2. 本轮对照下来，没有再留下新的已确认功能尾巴。
3. 当前仅剩的注意项是 Windows 下大批量 Maven + JaCoCo 合并执行时偶发文件锁，属于测试基础设施问题，不是本轮代码语义缺陷。
