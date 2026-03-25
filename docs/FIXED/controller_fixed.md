# controller 模块第二轮缺漏复核

## 复核范围

- Python 基线：`F:\oepnjiuwen\agent-core-python\openjiuwen\core\controller`
- Java 对照：`F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\controller`

## 第二轮修正结论

第二轮按源码重查后，以下内容不能再继续算作“缺失”：

- 新版 `Controller`、`ControllerConfig`
- `modules` 主干：`EventHandler`、`EventQueue`、`TaskManager`、`TaskScheduler`、`IntentRecognizer`、`EventHandlerWithIntentRecognition`
- 新版 `schema` 主干：`DataFrame`、`Event`、`InputEvent`、`Intent`、`Task`、`ControllerOutput*`
- `legacy.task.Task` 及其嵌套数据结构

第二轮真正还存在的问题，已经集中到 `legacy` 兼容层和少量 API 语义差异，不再是“主干类不存在”。

## 第二轮仍存在的真实缺口

| 优先级 | 位置 | Python 基线 | Java 当前状态 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `legacy.BaseController.send_to_agent()` / `publish()` | Python 会委派到 `group_controller`，支持组内点对点和广播路由 | Java `sendToAgent()`、`publish()` 直接抛 `UnsupportedOperationException` | legacy controller 加入 group 后，跨 agent 路由能力不对齐 |
| `P1` | `legacy.IntentDetectionController.invoke()` 实时中断链路 | Python 在 `invoke()` 中会先取消旧 handler，再取消 `TaskQueue` 中旧任务，避免同一 `conversation_id` 的新请求被旧任务阻塞 | Java 没有覆写 `invoke()`，也没有 `_processing_handlers` 级别的取消跟踪 | legacy intent controller 在并发对话/连续输入场景下，行为弱于 Python |
| `P1` | `legacy.IntentDetectionController.handle_event()` / `handle_resume()` | Python 会调用 `MessageUtils.add_user_message()`，并在 resume 场景按中断组件重建 `InteractiveInput` | Java `handleEvent()` 只做简单分发，`handleResume()` 直接 `execTask()`，没有输入重建逻辑 | legacy workflow resume 场景无法完整复用 Python 的中断恢复能力 |
| `P1` | `legacy.event.Event` 工厂与便捷方法 | Python 具备 `create_agent_response()`、`create_agent_handoff()`、`set_correlation()`、`set_conversation()`、`is_from_user()`、`is_from_agent()`、`is_task_related()`、`is_workflow_related()`、`to_dict()` | Java 只保留 `createUserEvent/createTaskCompleted/createTaskInterrupted/createErrorEvent/createInfoEvent/getDisplayContent()` | legacy 事件模型的便捷构造、判断、序列化能力不完整 |
| `P1` | `legacy.reasoner` 默认实现 | Python 有 concrete `Planner`、`IntentDetector`，包含 `process_message()`、LLM 调用、意图解析、默认任务生成等逻辑 | Java 只有 `Planner`、`IntentDetector` 接口和一个轻量 `AgentReasoner` 委派壳 | legacy reasoner 只有契约，没有可直接复用的默认能力 |
| `P1` | `legacy.utils` | Python 有 `MessageHandlerUtils` 与 `ReasonerUtils`，覆盖 LLM 输入整理、tool call 解析、聊天历史处理、模型获取等公共逻辑 | Java 完全缺失同职责工具类 | legacy controller/reasoner 的公共辅助层未迁移 |
| `P2` | `legacy.config` 分层配置模型 | Python 提供 `IntentDetectionConfig`、`PlannerConfig`、`ProactiveIdentifierConfig`、`ReflectorConfig`、`ReasonerConfig` 以及 `get_default_template()` | Java 仅保留单个 `ReasonerConfig`，且没有默认模板 helper | legacy reasoner 配置面比 Python 收缩明显 |
| `P2` | `legacy.constants.IntentDetectionConstants` | Python 有 `DEFAULT_CLASS` 常量类 | Java 无同名常量类 | 依赖该常量的兼容代码需自行硬编码或改写 |
| `P2` | `controller.__init__` 聚合导出 | Python 顶层包将 `schema/modules/legacy` 统一重导出，并暴露完整 `__all__` | Java 无统一 facade，只能逐类 import | API 可发现性和兼容性弱于 Python 顶层模块 |
| `P2` | `modules.task_executor.TaskExecutor` 独立抽象 | Python 同时存在 `modules.task_executor.TaskExecutor.execute()` 与 `modules.task_scheduler.TaskExecutor.execute_ability()` 两套公开抽象 | Java 只保留一套 `TaskExecutor.executeAbility()` | 依赖 Python 旧抽象命名的迁移代码需要改造 |
| `P2` | `IntentRecognizer.recognize()` 上下文创建行为 | Python 若 session 对应上下文不存在，会自动 `create_context()` | Java 若 `ModelContext` 不存在直接抛错，要求调用方预先创建 | 调用约束更严格，无法完全按 Python 入口假设使用 |
| `P2` | `EventQueue.subscribe()/unsubscribe()` 返回值 | Python 会返回订阅/主题信息，便于上层调试和扩展 | Java 两个方法都返回 `void` | 扩展代码无法拿到 Python 同级别的订阅元数据 |
| `P2` | `Controller.invoke()/stream()` 扩展参数面 | Python `invoke()`、`stream()` 保留 `**kwargs`，`stream_modes` 也可省略 | Java 去掉 `kwargs`，`streamModes` 需显式传入 | 对上层自定义流控/扩展参数的兼容性较低 |

## 建议优先级

1. 先补 `legacy` 主链路：`BaseController` 路由委派、`IntentDetectionController` 实时中断与 resume 重建逻辑。
2. 再补 `legacy.event` 便捷工厂与判断/序列化 helper，恢复 Python 兼容面。
3. 然后补 `legacy.reasoner` 默认实现与 `legacy.utils` 公共工具层。
4. 最后再处理包级 facade、独立 `TaskExecutor` 旧抽象、以及 `Controller/EventQueue` 的扩展参数与返回值兼容问题。

## 备注

- 本文件只列第二轮复核后仍然成立的缺口，不重复记录已经存在的主干类和主干方法。
- 详细的类与方法映射关系，见 `docs/Reflect/controller.md`。