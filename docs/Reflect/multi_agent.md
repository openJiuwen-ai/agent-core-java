# multi_agent 模块 Python / Java API 映射

## 对照范围

- Python: `F:\oepnjiuwen\agent-core-python\openjiuwen\core\multi_agent`
- Java: `F:\oepnjiuwen\agent-core-java\agent-core-java\src\main\java\com\openjiuwen\core\multiagent`
- 统计口径:
  - Python 统计包级导出、公共类、公共字段、公共方法，以及模块内实际承载公开语义的内部辅助方法
  - Java 统计 `public`/`protected` 类型、构造器、公开方法、公开静态工厂，以及对 Python 公开语义承担映射责任的补充类型
- 映射约定:
  - `snake_case -> camelCase`
  - Python 字段 / property -> Java getter / setter / builder 字段
  - Python `async` -> Java 同步 `Object` / `Iterator<Object>`
  - Python 顶层导出 -> Java 显式导入具体类

## 总体结论

- multi_agent 新版主干已经对齐: `GroupConfig`、`BaseGroup`、`GroupCard`、`EventDrivenGroupCard` 都有直接 Java 对位实现。
- legacy 兼容层也已基本成形: `AgentGroupConfig`、`ControllerGroup`、`BaseGroupController`、`DefaultGroupController`、legacy `GroupCard` 均已存在并承接 Python 旧接口。
- Java 为摆脱 Python 侧对 `controller.legacy.Event` 和异步事件循环的依赖，引入了 `GroupEvent` 与 `AgentGroupSessionApi` 作为适配层，属于“结构对齐、运行时适配实现”。
- 仍有少量公开 API 未完全对齐，集中在包级门面与 legacy 兼容入口，详见 `docs/FIXED/multi_agent_fixed.md`。

## 1. 包级导出与门面

### 1.1 新版 `openjiuwen.core.multi_agent`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__all__ = [GroupCard, EventDrivenGroupCard, GroupConfig, Session, BaseGroup, create_agent_group_session]` | 无统一 package facade；分别使用 `multiagent.BaseGroup`、`multiagent.GroupConfig`、`multiagent.schema.GroupCard`、`multiagent.schema.EventDrivenGroupCard`、`session.AgentGroupSessionApi` | 包门面导出 -> 显式导入具体类 | 适配映射 | Java 没有 Python 风格 `__all__` |
| `GroupCard` | `schema.GroupCard` | 同名类型映射 | 完全映射 | - |
| `EventDrivenGroupCard` | `schema.EventDrivenGroupCard` | 同名类型映射 | 完全映射 | - |
| `GroupConfig` | `GroupConfig` | 同名类型映射 | 完全映射 | - |
| `BaseGroup` | `BaseGroup` | 同名类型映射 | 完全映射 | - |
| `Session` | `session.AgentGroupSessionApi` | Python 会话别名 -> Java 用户态会话 API | 部分映射 | Java 不在 `multiagent` 包下重导出 |
| `create_agent_group_session(...)` | `AgentGroupSessionApi.create(...)` | 顶层工厂函数 -> 静态工厂方法 | 部分映射 | 功能存在，但没有 `multiagent` 包下便捷门面 |

### 1.2 旧版 `openjiuwen.core.multi_agent.legacy`

| Python API | Java API | 映射关系 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__all__ = [AgentGroupConfig, AgentGroupSession, BaseGroup, ControllerGroup, GroupCard, EventDrivenGroupCard, BaseGroupController, DefaultGroupController]` | 无统一 package facade；分别使用 `legacy.AgentGroupConfig`、`legacy.LegacyBaseGroup`、`legacy.ControllerGroup`、`legacy.BaseGroupController`、`legacy.DefaultGroupController`、`legacy.schema.LegacyGroupCard`、`legacy.schema.LegacyEventDrivenGroupCard`、`session.AgentGroupSessionApi` | 旧包导出 -> 显式导入具体类 | 适配映射 | Java 没有 Python 风格 `__all__` 和导入别名层 |
| `AgentGroupConfig` | `legacy.AgentGroupConfig` | 同名类型映射 | 完全映射 | - |
| `AgentGroupSession` | `session.AgentGroupSessionApi` | legacy 会话包装器 -> 通用组会话 API | 部分映射 | Java 没有 legacy 独立别名类 |
| `BaseGroup` | `legacy.LegacyBaseGroup` | 旧版抽象基类重命名映射 | 完全映射 | Java 避免与新版 `BaseGroup` 冲突 |
| `ControllerGroup` | `legacy.ControllerGroup` | 同名类型映射 | 完全映射 | - |
| `GroupCard` | `legacy.schema.LegacyGroupCard` | 旧版卡片重命名映射 | 完全映射 | Java 用 `Legacy*` 前缀区分新旧模型 |
| `EventDrivenGroupCard` | `legacy.schema.LegacyEventDrivenGroupCard` | 同名语义映射 | 完全映射 | - |
| `BaseGroupController` | `legacy.BaseGroupController` | 同名类型映射 | 完全映射 | - |
| `DefaultGroupController` | `legacy.DefaultGroupController` | 同名类型映射 | 完全映射 | - |

## 2. 新版核心类型

### 2.1 `GroupConfig`

| Python API | Java API | 方法/字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 字段 `max_agents` | 字段 `maxAgents` + `getMaxAgents()/setMaxAgents()` | `snake_case -> camelCase` | 完全映射 | - |
| 字段 `max_concurrent_messages` | 字段 `maxConcurrentMessages` + `getMaxConcurrentMessages()/setMaxConcurrentMessages()` | `snake_case -> camelCase` | 完全映射 | - |
| 字段 `message_timeout` | 字段 `messageTimeout` + `getMessageTimeout()/setMessageTimeout()` | `snake_case -> camelCase` | 完全映射 | - |
| `configure_max_agents(max_agents)` | `configureMaxAgents(int)` | 同名语义映射 | 完全映射 | - |
| `configure_timeout(timeout)` | `configureTimeout(double)` | 同名语义映射 | 完全映射 | - |
| `configure_concurrency(max_concurrent)` | `configureConcurrency(int)` | 同名语义映射 | 完全映射 | - |
| `model_config = {"extra": "allow"}` | 无对位公开设置 | Pydantic 额外字段容忍 -> Java 普通 POJO | 语言适配 | Java 不存在运行时 schema 校验配置 |

### 2.2 `BaseGroup`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(card, config=None)` | `BaseGroup(GroupCard, GroupConfig)` / `BaseGroup(GroupCard)` | 默认参数 -> 构造器重载 | 完全映射 | - |
| `_create_default_config()` | `createDefaultConfig()` | 内部默认配置工厂 | 适配映射 | Java 为 `private`，Python 为实例辅助方法 |
| 字段 `card` | `getCard()` | 字段 -> getter | 完全映射 | - |
| 字段 `config` | `getConfig()` | 字段 -> getter | 完全映射 | - |
| 字段 `group_id` | `getGroupId()` | 字段 -> getter | 完全映射 | - |
| 字段 `agents` | `getAgents()` | 字段 -> getter | 完全映射 | Java 额外暴露整个映射表 |
| `configure(config)` | `configure(GroupConfig)` | 同名语义映射 | 完全映射 | - |
| `add_agent(agent, agent_id=None)` | `addAgent(BaseAgent, String)` / `addAgent(BaseAgent)` | 默认参数 -> 重载 | 完全映射 | 两侧都支持用 `agent.card.name` 作为默认 ID |
| `remove_agent(agent_id_or_agent)` | `removeAgent(String)` / `removeAgent(BaseAgent)` | `Union[str, BaseAgent]` -> 重载 | 完全映射 | - |
| `get_agent(agent_id)` | `getAgent(String)` | 同名语义映射 | 完全映射 | - |
| `get_agent_count()` | `getAgentCount()` | 同名语义映射 | 完全映射 | - |
| `list_agents()` | `listAgents()` | 同名语义映射 | 完全映射 | - |
| `invoke(message, session=None)` | `invoke(Object, AgentGroupSessionApi)` | `async` -> 同步调用 | 完全映射 | Java 将返回值统一为 `Object` |
| `stream(message, session=None)` | `stream(Object, AgentGroupSessionApi)` | `AsyncIterator[Any]` -> `Iterator<Object>` | 完全映射 | - |

### 2.3 `GroupCard`

| Python API | Java API | 字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `agent_cards: List[AgentCard]` | `agentCards: List<AgentCard>` | `snake_case -> camelCase` | 完全映射 | - |
| `topic: str` | `topic: String` | 同名语义映射 | 完全映射 | - |
| `version: str` | `version: String` | 同名语义映射 | 完全映射 | - |
| `tags: List[str]` | `tags: List<String>` | 同名语义映射 | 完全映射 | - |

### 2.4 `EventDrivenGroupCard`

| Python API | Java API | 字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `subscriptions: Dict[str, List[str]]` | `subscriptions: Map<String, List<String>>` | 同名语义映射 | 完全映射 | - |

## 3. 会话 API 映射

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `session.agent_group.Session` | `AgentGroupSessionApi` | 用户态组会话类型映射 | 完全映射 | Java 类型位于 `com.openjiuwen.core.session` |
| `create_agent_group_session()` | `AgentGroupSessionApi.create(null, null)` / `new AgentGroupSessionApi()` | 工厂函数 -> 静态工厂 / 构造器 | 完全映射 | - |
| `create_agent_group_session(session_id=...)` | `AgentGroupSessionApi.create(sessionId, envs)` / `new AgentGroupSessionApi(sessionId)` | 参数化创建语义映射 | 完全映射 | Java `envs` 为显式 `Map<String,Object>` |

## 4. 旧版兼容层

### 4.1 `AgentGroupConfig`

| Python API | Java API | 方法/字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| 字段 `group_id` | 字段 `groupId` + `getGroupId()` | `snake_case -> camelCase` | 完全映射 | Java 构造器要求显式传入 |
| 字段 `max_agents` | 字段 `maxAgents` + getter/setter | `snake_case -> camelCase` | 完全映射 | - |
| 字段 `max_concurrent_messages` | 字段 `maxConcurrentMessages` + getter/setter | `snake_case -> camelCase` | 完全映射 | - |
| 字段 `message_timeout` | 字段 `messageTimeout` + getter/setter | `snake_case -> camelCase` | 完全映射 | - |

### 4.2 `AgentGroupSession`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentGroupSession(config=None)` | `AgentGroupSessionApi()` / `AgentGroupSessionApi(String)` / `AgentGroupSessionApi(String, Map<String,Object>)` | legacy 会话类 -> 通用组会话 API | 部分映射 | Java 没有 legacy 独立会话子类 |
| 继承 `AgentSession.write_stream()` 等能力 | `getInner().streamWriterManager()` 等内部对象 | 继承 API -> 显式访问内部会话 | 部分映射 | Java 用户态 API 更薄，需要通过 `getInner()` 下钻 |

### 4.3 `legacy.BaseGroup`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(config)` | `LegacyBaseGroup(AgentGroupConfig)` | 构造语义一致 | 完全映射 | - |
| 字段 `config` | `getConfig()` | 字段 -> getter | 完全映射 | - |
| 字段 `group_id` | `getGroupId()` | 字段 -> getter | 完全映射 | - |
| 字段 `agents` | `getAgents()` | 字段 -> getter | 完全映射 | - |
| `add_agent(agent_id, agent)` | `addAgent(String, BaseAgent)` | 同名语义映射 | 完全映射 | - |
| `get_agent_count()` | `getAgentCount()` | 同名语义映射 | 完全映射 | - |
| `invoke(message, session=None)` | `invoke(Object, AgentGroupSessionApi)` | `async` -> 同步 | 完全映射 | - |
| `stream(message, session=None)` | `stream(Object, AgentGroupSessionApi)` | `AsyncIterator[Any]` -> `Iterator<Object>` | 完全映射 | - |

### 4.4 `ControllerGroup`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(config, group_controller=None)` | `ControllerGroup(AgentGroupConfig, BaseGroupController)` / `ControllerGroup(AgentGroupConfig)` | 默认参数 -> 构造器重载 | 完全映射 | - |
| 字段 `group_controller` | `getGroupController()` | 字段 -> getter | 完全映射 | - |
| `_setup_group_controller()` | `setupGroupController()` | 内部控制器注入 | 适配映射 | Java 为私有辅助方法 |
| `_convert_message(message)` | `convertMessage(Object)` | `dict/Event` 兼容 -> `Map/GroupEvent/String` 兼容 | 完全映射 | Java 引入 `GroupEvent` 作为 legacy 路由事件 |
| `invoke(message, session=None)` | `invoke(Object, AgentGroupSessionApi)` | 同名主语义映射 | 完全映射 | 都会在缺省会话时自动创建 session |
| `stream(message, session=None)` | `stream(Object, AgentGroupSessionApi)` | 同名主语义映射 | 完全映射 | 都以后台执行控制器 + 前台读取共享流的方式实现 |

### 4.5 `BaseGroupController`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `__init__(agent_group=None)` | `BaseGroupController(LegacyBaseGroup)` / 无参构造 | 默认参数 -> 构造器重载 | 完全映射 | - |
| 字段 `agent_group` | `getAgentGroup()` | 字段 -> getter | 完全映射 | - |
| 字段 `_subscriptions` | `getSubscriptionsMap()` | 内部表 -> getter | 完全映射 | Java 公开度更高 |
| `setup_from_group(group)` | `setupFromGroup(LegacyBaseGroup)` | 同名语义映射 | 完全映射 | - |
| `invoke(event, session)` | `invoke(GroupEvent, AgentGroupSessionApi)` | `Event` -> `GroupEvent` | 完全映射 | Java 用专用事件类型承接路由字段 |
| `_handle_message_wrapper(request)` | `handleMessageWrapper(Object)` | 内部消息包装处理 | 适配映射 | Java 为私有方法 |
| `handle_event(event, session)` | `handleEvent(GroupEvent, AgentGroupSessionApi)` | 抽象处理入口 | 完全映射 | - |
| `subscribe(message_type, agent_ids)` | `subscribe(String, List<String>)` | 同名语义映射 | 完全映射 | - |
| `unsubscribe(message_type, agent_ids)` | `unsubscribe(String, List<String>)` | 同名语义映射 | 完全映射 | - |
| `get_subscribers(message_type)` | `getSubscribers(String)` | 同名语义映射 | 完全映射 | - |
| `send_to_agent(event, agent_id, session)` | `sendToAgent(GroupEvent, String, AgentGroupSessionApi)` | 同名语义映射 | 完全映射 | 两侧都复用共享 session 转发 stream chunk |
| `publish(event, session)` | `publish(GroupEvent, AgentGroupSessionApi)` | 同名语义映射 | 完全映射 | Python 用 `asyncio.gather`，Java 用 `CompletableFuture` / virtual thread |
| `stop()` | `stop()` | 同名语义映射 | 完全映射 | - |

### 4.6 `DefaultGroupController`

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `handle_event(event, session)` | `handleEvent(GroupEvent, AgentGroupSessionApi)` | 同名语义映射 | 完全映射 | 都是“指定 `receiver_id` 单播，否则按订阅关系广播” |

### 4.7 legacy schema

| Python API | Java API | 字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `legacy.schema.GroupCard.agent_card` | `LegacyGroupCard.agentCard` | 同名语义映射 | 完全映射 | 保留旧字段名而不改成 `agentCards` |
| `legacy.schema.GroupCard.topic` | `LegacyGroupCard.topic` | 同名语义映射 | 完全映射 | - |
| `legacy.schema.EventDrivenGroupCard.subscriptions` | `LegacyEventDrivenGroupCard.subscriptions` | 同名语义映射 | 完全映射 | - |

## 5. Java 适配补充类型

| Java API | Python 对位 | 状态 | 说明 |
| --- | --- | --- | --- |
| `legacy.GroupEvent` | 对位 Python `controller.legacy.Event` 在 multi_agent 中实际使用的那部分字段 | Java 适配 | Java 用本地轻量事件模型切断对 controller legacy 事件实现的直接耦合 |
| `session.AgentGroupSessionApi.create(...)` | `create_agent_group_session(...)` | Java 适配 | Java 把顶层工厂函数落到 session API 的静态工厂方法上 |

## 6. 小结

- 如果按“核心能力是否齐备”评估，multi_agent 的 Java 版已经覆盖新版与 legacy 版主要公开类型和执行路径。
- 如果按“Python 导出层是否一字不差对齐”评估，Java 仍有少量门面型差异，主要是会话导出位置和 legacy 兼容别名层。
- 功能主链路的对应关系已经比较清晰: 新版看 `GroupConfig/BaseGroup/GroupCard`，旧版看 `AgentGroupConfig/ControllerGroup/BaseGroupController/Legacy*Card`，会话和事件由 `AgentGroupSessionApi` 与 `GroupEvent` 承接。