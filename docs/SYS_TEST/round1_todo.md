# Round 1 系统测试继续检查与修补记录

## 1. 本轮目标

基于 `docs/SYS_TEST/round0_todo.md` 的结果，继续对照 Python 版实现，优先处理两类问题：

1. Round 0 中已识别但尚未真正打通的生产能力
2. Java 已有实现、但系统测试仍未覆盖的本地可执行行为

本轮重点聚焦：

- Workflow 可视化链路
- Retrieval 的 `parseFiles()` / `parseUrls()`

## 2. 本轮结论

| 项目 | 结论 | 处理结果 |
|------|------|---------|
| `Workflow.draw()` 仅返回空串 | 真实生产缺陷 | **已修复** |
| `BaseWorkflow` 的 drawable 记录链路未接通 | 真实生产缺陷 | **已修复** |
| `SubWorkflowComponentImpl.getSubWorkflowInternal()` 返回 `null` | 真实生产缺陷 | **已修复** |
| `BranchRouter` 仅读取环境变量，测试/本地调试不易启用 drawable | 可用性缺陷 | **已修复** |
| `KnowledgeBase.parseFiles()` / `parseUrls()` | Java 已实现，但系统测试缺失 | **已补测试** |

## 3. 本轮生产修复

### 3.1 Workflow 可视化链路打通

对齐 Python `workflow.draw() -> _internal.to_mermaid() -> drawable` 的路径，本轮修复了 Java 版中“类已经存在但没有连起来”的问题：

- `BaseWorkflow`
  - 实现 `HasDrawable`
  - 在启用 `WORKFLOW_DRAWABLE` 时创建 `Drawable`
  - 在 `addWorkflowComp` / `startComp` / `endComp` / `addConnection` / `addStreamConnection` / `addConditionalConnection` 中同步写入可视化图
  - 新增 `toMermaid()` 入口
- `Workflow`
  - `draw()` 改为真正返回 Mermaid 文本
  - 支持 `expandSubgraph` 的 `Boolean` / `Number` 语义映射
  - 暴露内部 drawable 访问入口，供子工作流组件使用
- `SubWorkflowComponentImpl`
  - `getSubWorkflowInternal()` 不再返回 `null`，改为返回子工作流内部 drawable 容器
- `BranchRouter`
  - drawable 开关同时支持 `System.getenv("WORKFLOW_DRAWABLE")` 与 `System.getProperty("WORKFLOW_DRAWABLE")`
  - 便于本地测试与 IDE/命令行调试

### 3.2 影响范围

本轮改动的核心文件：

- `src/main/java/com/openjiuwen/core/workflow/BaseWorkflow.java`
- `src/main/java/com/openjiuwen/core/workflow/Workflow.java`
- `src/main/java/com/openjiuwen/core/workflow/BranchRouter.java`
- `src/main/java/com/openjiuwen/core/workflow/component/SubWorkflowComponentImpl.java`

## 4. 本轮新增/增强系统测试

### 4.1 新增测试类

| 测试类 | 新增方法数 | 覆盖内容 |
|-------|----------|---------|
| `WorkflowVisualizationSystemTest` | 3 | `Workflow.draw()` Mermaid 输出、子工作流展开、条件边/流式边可视化 |

### 4.2 增强现有测试类

| 测试类 | 新增方法数 | 覆盖内容 |
|-------|----------|---------|
| `RetrievalAdvancedSystemTest` | 2 | `KnowledgeBase.parseFiles()`、`KnowledgeBase.parseUrls()` |

### 4.3 本轮新增测试合计

- **新增 1 个系统测试类**
- **新增/补强 5 个系统测试方法**

## 5. 验证结果（2026-03-10）

### 5.1 定向系统测试

执行命令：

```bash
mvn -q "-Dtest=com.openjiuwen.core.systemtest.WorkflowVisualizationSystemTest,com.openjiuwen.core.systemtest.WorkflowAdvancedSystemTest,com.openjiuwen.core.systemtest.WorkflowSystemTest,com.openjiuwen.core.systemtest.RetrievalAdvancedSystemTest" "-Dsurefire.excludedGroups=" test
```

结果统计：

| 测试范围 | 通过数 | 失败数 | 错误数 |
|---------|-------|-------|-------|
| `WorkflowVisualizationSystemTest` | 3 | 0 | 0 |
| `WorkflowAdvancedSystemTest` | 7 | 0 | 0 |
| `WorkflowSystemTest` | 7 | 0 | 0 |
| `RetrievalAdvancedSystemTest` | 11 | 0 | 0 |
| **合计** | **28** | **0** | **0** |

### 5.2 编译验证

执行命令：

```bash
mvn -q test-compile
```

结果：

- ✅ 测试代码整体编译通过

## 6. 本轮新发现的剩余缺口

### 6.1 Workflow / Visualization

| 缺口 | 当前情况 | 优先级 |
|------|---------|-------|
| `draw()` 的 PNG / SVG 输出 | Python 支持，Java 当前 `Workflow.draw()` 仍仅返回 Mermaid 文本 | P1 |
| 非 `BranchRouter` 条件路由的可视化 target 推断 | Java 仅支持 `BranchRouter` 或显式 `TargetProvider`，与 Python 的类型提示推断仍有差距 | P2 |
| Workflow interrupt / interaction / checkpointer 端到端系统测试 | Python 有完整对照，Java 系统测试仍未补齐 | P1 |

### 6.2 Retrieval

| 缺口 | 当前情况 | 优先级 |
|------|---------|-------|
| `GraphKnowledgeBase` 生命周期系统测试 | Java 有实现，但系统测试仍未覆盖 add / retrieve / delete / update / statistics | P1 |
| Agentic / Graph Retriever 本地替身测试 | Python 有对照，Java 仍缺系统级验证 | P2 |

### 6.3 Session / Tracer

| 缺口 | 当前情况 | 优先级 |
|------|---------|-------|
| 子工作流执行时 tracer 回调日志报错 | 本轮定向测试中出现 `Handler not found: tracer_workflow.sub` 错误日志，虽未导致测试失败，但需要进一步确认是否为真实 tracing 缺陷 | P1 |

## 7. Round 2 建议

1. 先补 Workflow 的 interactive / interrupt / checkpointer 端到端系统测试  
   对齐 Python `test_workflow_with_interrupt.py` 的核心路径。

2. 补 `GraphKnowledgeBase` 的本地系统测试  
   优先覆盖 `addDocuments`、`retrieve(use_graph/use_agentic)`、`deleteDocuments`、`updateDocuments`、`getStatistics`。

3. 评估是否补齐 `draw()` 的 PNG / SVG 导出能力  
   该项目前属于 Java 与 Python 的明确产品差异。
