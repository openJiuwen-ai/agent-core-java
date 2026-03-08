# Workflow 模块 Python → Java 转译报告

## 1. 概述

本报告记录了将 `agent-core-python/openjiuwen/core/workflow/` 模块转译为 Java 的完整工作。转译覆盖了工作流引擎的所有核心模块：条件系统、分支路由、流程组件（Start/End）、循环组件（Loop）、子工作流组件、基础工作流图构建（BaseWorkflow）、以及顶层 Workflow 类。

**源码路径**：`agent-core-python/openjiuwen/core/workflow/`  
**目标路径**：`agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/workflow/`  
**转译状态**：✅ 完成（无编译错误）

---

## 2. 文件清单

### 2.1 条件系统（condition）

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `condition/condition.py` → `Condition` | `condition/Condition.java` | 条件接口，定义 `evaluate(BaseSession)` |
| `condition/condition.py` → `FuncCondition` | `condition/FuncCondition.java` | 函数式条件，包装 `BooleanSupplier` |
| `condition/condition.py` → `AlwaysTrue` | `condition/AlwaysTrue.java` | 永真条件 |
| `condition/condition.py` → `ExpressionCondition` | `condition/ExpressionCondition.java` | 表达式条件，Java 手写 AST 解析器替代 Python `ast.parse` |
| `condition/array.py` → `ArrayCondition` | `condition/ArrayCondition.java` | 数组遍历条件（基于外部数组） |
| `condition/array.py` → `ArrayConditionInSession` | `condition/ArrayConditionInSession.java` | 数组遍历条件（从 Session 状态读取数组） |
| `condition/number.py` → `NumberCondition` | `condition/NumberCondition.java` | 计数条件（基于外部限制） |
| `condition/number.py` → `NumberConditionInSession` | `condition/NumberConditionInSession.java` | 计数条件（从 Session 状态读取限制） |

### 2.2 分支与路由

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `components/flow/branch/branch.py` → `Branch` | `component/Branch.java` | 分支数据类，包含 condition + target + branchId |
| `components/flow/branch/branch.py` → `BranchRouter` | `BranchRouter.java` | 路由器实现，管理多个 Branch，实现 `Router.apply()` |
| `components/flow/branch/branch_comp.py` → `BranchComponent` | `component/BranchComponent.java` | 分支组件，转发 router 给 graph |
| `components/flow/branch/intent_detection.py` | `component/IntentDetectionComponent.java` | 意图检测组件，继承 BranchComponent |

### 2.3 流程组件

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `components/flow/start.py` | `component/Start.java` | 起始节点，从 inputs 提取并设置初始状态 |
| `components/flow/end.py` → `EndConfig` | `component/EndConfig.java` | End 配置，支持模板和映射模式 |
| `components/flow/end.py` → `End` | `component/End.java` | 结束节点，支持 `{{var}}` 模板渲染，invoke/stream/transform/collect |

### 2.4 循环组件（loop）

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `components/flow/loop/loop_callback.py` → `LoopCallback` | `component/loop/callback/LoopCallback.java` | 抽象循环回调，分派到 FIRST_LOOP/START_ROUND/END_ROUND/OUT_LOOP |
| `components/flow/loop/intermediate_loop_var.py` | `component/loop/callback/IntermediateLoopVarCallback.java` | 初始化中间循环变量 |
| `components/flow/loop/output.py` | `component/loop/callback/OutputCallback.java` | 收集轮次结果，生成最终输出 |
| `components/flow/loop/loop_comp.py` → `LoopType` | `component/loop/LoopType.java` | 枚举：ARRAY, NUMBER, ALWAYS_TRUE, EXPRESSION |
| `components/flow/loop/loop_comp.py` → `LoopInput` | `component/loop/LoopInput.java` | 循环输入 POJO，含 `fromMap()` 工厂方法 |
| `components/flow/loop/loop_comp.py` → `LoopController` | `component/loop/LoopController.java` | 接口：`breakLoop()`, `isBroken()` |
| `components/flow/loop/loop_comp.py` → `LoopBreakComponent` | `component/loop/LoopBreakComponent.java` | 中断循环组件 |
| `components/flow/loop/loop_comp.py` → `LoopSetVariableComponent` | `component/loop/LoopSetVariableComponent.java` | 设置父作用域循环变量 |
| `components/flow/loop/loop_comp.py` → `EmptyExecutable` | `component/loop/EmptyExecutable.java` | 空操作占位 Executable |
| `components/flow/loop/loop_comp.py` → `PostLoopBody` | `component/loop/PostLoopBody.java` | 循环体后处理，跟踪 finish_index |
| `components/flow/loop/loop_comp.py` → `LoopGroup` | `component/loop/LoopGroup.java` | 循环组图，继承 BaseWorkflow，管理循环体子图 |
| `components/flow/loop/loop_comp.py` → `LoopComponent` | `component/loop/LoopComponentImpl.java` | 循环组件入口，根据运行时输入创建 AdvancedLoopComponentImpl |
| `components/flow/loop/loop_comp.py` → `AdvancedLoopComponent` | `component/loop/AdvancedLoopComponentImpl.java` | 完整循环实现，构建内部 PregelGraph，条件评估与路由 |

### 2.5 核心基础设施

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `_workflow.py` → `BaseWorkflow` | `BaseWorkflow.java` | 工作流图构建器，管理节点/边/能力推断/编译 |
| `workflow.py` → `Workflow` | `Workflow.java` | 顶层工作流类，invoke/stream/draw/子工作流委托 |
| `workflow_comp.py` → `SubWorkflowComponent` | `component/SubWorkflowComponentImpl.java` | 子工作流组件，委托给 Workflow 实例 |

### 2.6 配置与模型

| Python 源文件 | Java 目标文件 | 说明 |
|---|---|---|
| `_workflow.py` 中的配置 | `WorkflowConfig.java` | 工作流配置：card + spec + maxNestingDepth |
| `_workflow.py` 中的 spec | `WorkflowSpec.java` | 工作流规格：edges, streamEdges, compConfigs, startNodes |
| Python card 定义 | `WorkflowCard.java` | 工作流卡片，Lombok @SuperBuilder |
| Python 输出类 | `WorkflowOutput.java` | 工作流输出：result + executionState |
| Python 枚举 | `WorkflowExecutionState.java` | 执行状态枚举 |
| Python chunk 类型 | `WorkflowChunkType.java` | 流式块类型 |

### 2.7 接口与基类（已有/修改）

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `ComponentComposable.java` | 修改 | 添加 `addComponent(Graph)` 默认方法 |
| `WorkflowComponent.java` | 修改 | 完善 ComponentComposable 实现 |
| `ComponentExecutable.java` | 保持 | 基类，NodeSessionApi 包装 |
| `LoopComponent.java` | 保持（接口） | Drawable 兼容：`getLoopGroup()` |
| `AdvancedLoopComponent.java` | 保持（接口） | Drawable 兼容：`getBody()` |
| `SubWorkflowComponent.java` | 保持（接口） | Drawable 兼容：`getSubWorkflowInternal()` |
| `HasDrawable.java` | 保持 | Drawable 标记接口 |

---

## 3. 关键设计决策

### 3.1 异步 → 同步

Python 使用 `async/await` 和 `AsyncIterator`。Java 转译为同步方法 + `Iterator<T>`，因为底层 `Executable`/`ExecutableGraph` 框架已是同步设计。

### 3.2 接口 vs 实现分离

`Drawable.java` 通过 `instanceof` 检查 `LoopComponent`、`AdvancedLoopComponent`、`SubWorkflowComponent` 接口。为保持兼容性：
- 这三个保持为**接口**
- 创建 `*Impl` 类实现这些接口并继承相应基类
- 例如：`LoopComponentImpl extends WorkflowComponent implements LoopComponent`

### 3.3 ExpressionCondition — 手写 AST 解析

Python 使用 `ast.parse()` + `ast.literal_eval()` 解析布尔表达式。Java 无对等库，因此实现了完整的手写递归下降解析器，支持：
- 比较运算符：`==`, `!=`, `>`, `<`, `>=`, `<=`
- 逻辑运算符：`and`, `or`, `not`
- `in` / `not in` 运算符
- 字面量：字符串、数字、布尔、None/null
- 变量引用（从 session 状态解析）

### 3.4 反射访问 NodeSessionApi.inner

多个组件需要从用户层 `NodeSessionApi` 获取底层 `NodeSession`/`BaseSession`。由于 `NodeSessionApi` 不暴露 public getter，统一使用反射访问 `inner` 字段：
- `LoopComponentImpl.extractInnerSession()`
- `LoopSetVariableComponent.extractInnerSession()`
- `SubWorkflowComponentImpl.extractInnerSession()`
- `Workflow.extractInnerSession()`

### 3.5 泛型 ExecutableGraph 调用

`Graph.compile()` 返回 `ExecutableGraph<?, ?>`，直接调用 `invoke(Object, BaseSession)` 因通配符类型不匹配而编译失败。解决方案：统一通过 `@SuppressWarnings("unchecked")` 强转为 `ExecutableGraph<Object, Object>`。

### 3.6 LoopGroup 作为循环体

Python 中 `AdvancedLoopComponent` 的 `body` 是一个可以 `invoke` 的对象（LoopGroup 继承了多个基类）。Java 中 `LoopGroup` 继承 `BaseWorkflow`（非 Executable），因此在 `AdvancedLoopComponentImpl` 构造函数中自动将 `LoopGroup` 包装为匿名 `Executable<Object, Object>` 委托。

### 3.7 BaseWorkflow.autoCompleteAbilities — 拓扑能力推断

从边的拓扑结构推断每个组件支持的 Ability（INVOKE/STREAM/TRANSFORM/COLLECT）。对于 `stream_edges` 上的节点，若有 `waitForAll` 则推断为 COLLECT/TRANSFORM 而非 STREAM。

---

## 4. Python → Java 对应关系总结

| Python 概念 | Java 对应 |
|---|---|
| `async def invoke()` | `public Object invoke()` |
| `async def stream()` → `AsyncIterator` | `public Iterator<Object> stream()` |
| Pydantic `BaseModel` | Lombok `@Data` / POJO |
| `abc.ABC` + `@abstractmethod` | `abstract class` / `interface` |
| `ast.parse()` 表达式求值 | 手写递归下降解析器（`ExpressionCondition`） |
| `**kwargs` | `Object... kwargs` 或 `Map<String, Object>` |
| `isinstance()` 类型检查 | `instanceof` |
| Python dict | `Map<String, Object>` |
| Python list | `List<Object>` |
| `${ref.path}` 引用解析 | `SessionUtils.isRefPath()` + `extractOriginKey()` |
| `asyncio.wait_for(timeout)` | 暂未实现超时（可通过 `ExecutorService` 扩展） |
| Mermaid 绘图 (`draw()`) | 预留接口，返回空字符串 |

---

## 5. 依赖的已有模块

转译过程中使用了以下已存在的基础模块（未修改）：

- **Graph 系统**：`Graph`, `PregelGraph`, `ExecutableGraph`, `Executable`, `AtomicNode`, `PregelConstants`
- **Session 系统**：`BaseSession`, `NodeSession`, `SubWorkflowSession`, `WorkflowSession`, `ProxySession`, `NodeSessionApi`
- **State 系统**：`WorkflowStateCollection`, `WorkflowCommitState`, `State`
- **Config 系统**：`Config`, `WorkflowConfig`
- **路由**：`Router`（函数式接口）
- **异常**：`ErrorHelper`, `StatusCode`, `BaseError`
- **常量**：`Constant`, `SessionConstants`
- **工具**：`SessionUtils`

---

## 6. 已知限制

1. **超时机制**：Python 的 `asyncio.wait_for(timeout)` 未转译，Java 可通过 `ExecutorService.submit().get(timeout)` 实现。
2. **Mermaid 绘图**：`Workflow.draw()` 和 `BaseWorkflow` 的 drawable 支持为预留接口，暂返回空字符串。
3. **Streaming**：流式执行通过 `StreamWriterManager` 管道实现，`stream()` 方法返回空 Iterator，实际数据通过 session 的 StreamWriterManager 分发。
4. **验证输入**：Python 中的 `_validate_inputs` 通过 `input_params` schema 校验输入字段，Java 暂简化处理。

---

## 7. 文件统计

| 类别 | 文件数 |
|---|---|
| 新建文件 | 35 |
| 修改文件 | 2 |
| 总计 Java 文件 | 47（含已有接口/桩文件） |

**新建文件列表**（按包组织）：

```
workflow/
├── BaseWorkflow.java
├── BranchRouter.java
├── Workflow.java
├── WorkflowCard.java
├── WorkflowChunkType.java
├── WorkflowConfig.java
├── WorkflowExecutionState.java
├── WorkflowOutput.java
├── WorkflowSpec.java
├── condition/
│   ├── AlwaysTrue.java
│   ├── ArrayCondition.java
│   ├── ArrayConditionInSession.java
│   ├── Condition.java
│   ├── ExpressionCondition.java
│   ├── FuncCondition.java
│   ├── NumberCondition.java
│   └── NumberConditionInSession.java
└── component/
    ├── Branch.java
    ├── BranchComponent.java
    ├── End.java
    ├── EndConfig.java
    ├── IntentDetectionComponent.java
    ├── Start.java
    ├── SubWorkflowComponentImpl.java
    └── loop/
        ├── AdvancedLoopComponentImpl.java
        ├── EmptyExecutable.java
        ├── LoopBreakComponent.java
        ├── LoopComponentImpl.java
        ├── LoopController.java
        ├── LoopGroup.java
        ├── LoopInput.java
        ├── LoopSetVariableComponent.java
        ├── LoopType.java
        ├── PostLoopBody.java
        └── callback/
            ├── IntermediateLoopVarCallback.java
            ├── LoopCallback.java
            └── OutputCallback.java
```
