# Round 2 系统测试与 Python 对照补漏记录

## 本轮目标

基于 `docs/SYS_TEST/round1_todo.md`，继续补齐 Java 版在以下方向的缺漏：

1. 工作流中断 / 恢复 / checkpointer 的端到端系统测试
2. `GraphKnowledgeBase` 生命周期系统测试
3. 对照 Python 版本修补工作流中断、checkpointer、tracer 相关实现缺口

## 对照 Python 发现的主要缺口

### 已修补

1. 顶层 `Workflow.invoke()` 之前没有把交互中断识别成 `INPUT_REQUIRED`
2. 顶层工作流执行时没有真正走 checkpointer 主流程，导致恢复语义不完整
3. `InMemoryCheckpointer.postWorkflowExecute()` 会把中断场景误当成正常完成并直接清空 checkpoint
4. `TraceWorkflowHandler` 缺少 `onInteract`，交互输入不会进入 workflow trace
5. tracer 对 `GraphInterrupt` 没有中断态语义，状态会被误判为普通完成
6. `TraceWorkflowSpan` 缺少 `interactive_inputs`
7. `TracerWorkflowUtils` 缺少 workflow span manager 注册能力
8. 子工作流 / loop 内部 workflow tracer span manager 没有注册
9. `GraphKnowledgeBase` 缺少覆盖图索引启用/关闭两种生命周期路径的系统测试

### 仍待继续

1. `NodeSessionApi.interact()` 仍偏向 `Map<String, Object>` 交互输入，和 Python 的原始输入兼容能力还有差距
2. 顶层 workflow 级 trace start/done 语义仍未完全对齐 Python
3. `SubWorkflowComponentImpl` / `Workflow.invokeSubWorkflow()` 尚未把外层 Pregel config 显式透传到子工作流，嵌套 workflow 的 namespace / parentNs 语义仍可继续完善

## 本轮已完成修补

### 运行时语义

1. `Workflow.invoke()` 现在会根据 Pregel interrupt 结果和交互输出块，返回 `WorkflowExecutionState.INPUT_REQUIRED`
2. 顶层工作流执行改为使用 `config = null` 进入主流程 checkpointer 生命周期
3. 每次 workflow 执行结束后重置 `PregelGraph` 顶点状态，避免同一个 workflow 对象复用时残留执行态
4. `CompiledGraph` 改为按 session 类型区分主工作流，确保顶层 `WorkflowSession` 能触发 checkpointer 前后置钩子
5. `InMemoryCheckpointer` 现在会在 interrupt 时保留 workflow checkpoint，只在真正完成时清理

### Tracer 对齐

1. 新增 `NodeStatus.INTERRUPTED`
2. `TraceWorkflowHandler` 现在支持 `onInteract`
3. `TraceWorkflowHandler` 现在会把 `GraphInterrupt` 记为 `interrupted`，并避免被后续状态覆盖
4. `TraceWorkflowSpan` 新增 `interactiveInputs`
5. `TracerWorkflowUtils` 新增 `registerWorkflowSpanManager()`
6. 子工作流节点与 loop 组件现在都会注册 workflow span manager
7. workflow metadata 现在会补充 `workflow_name` 和 `workflow_version`

## 新增 / 增强系统测试

### 新增

1. `src/test/java/com/openjiuwen/core/systemtest/WorkflowInterruptSystemTest.java`
2. 覆盖场景：
   - 交互式 workflow 首次执行返回 `INPUT_REQUIRED`
   - checkpoint / graphStore 在中断后保留
   - 使用 `InteractiveInput` 恢复后继续执行并清理 checkpoint
   - `FORCE_DEL_WORKFLOW_STATE_KEY` 能强制删除旧状态并重新从头执行

### 增强

1. `src/test/java/com/openjiuwen/core/systemtest/RetrievalAdvancedSystemTest.java`
2. 新增 `GraphKnowledgeBase` 生命周期验证：
   - `use_graph=true` 时同时构建 chunk / triple index
   - `use_graph=false` 时退化为 chunk-only 检索路径
   - 补充 `addDocuments` / `retrieve` / `updateDocuments` / `deleteDocuments` / `getStatistics` 覆盖

## 验证结果

已通过：

```powershell
mvn -q -DskipTests compile
mvn -q "-Dtest=WorkflowInterruptSystemTest,RetrievalAdvancedSystemTest" test
mvn -q "-Dtest=WorkflowSystemTest,WorkflowAdvancedSystemTest,WorkflowInterruptSystemTest,SessionAdvancedSystemTest,RetrievalAdvancedSystemTest" test
```

## 建议的 Round 3 继续项

1. 补子工作流嵌套中断 / 恢复系统测试，重点验证 parentNs / namespace 传递是否与 Python 一致
2. 补 workflow-level trace start/done 对齐测试
3. 评估是否要让 Java 版 `interact()` 支持 raw input 恢复语义
4. 继续按 Python 检查 graph retriever / agentic retriever 更深层系统测试缺口
