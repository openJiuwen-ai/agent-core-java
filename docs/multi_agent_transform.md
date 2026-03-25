# multi_agent 模块转译报告

> **版本**: v1.0  
> **日期**: 2026-03-09  
> **模块**: multi_agent (多Agent编排)  
> **层级**: L4 - Agent层  
> **优先级**: P2  

---

## 1. 概述

本报告记录了 `multi_agent` 模块从 Python v0.1.7 到 Java 21 的完整转译过程。该模块实现多 Agent 编排和组管理，支持多个 Agent 协同工作，包含新版 Card + Config 模式和旧版 Legacy 兼容模式。

### 1.1 转译范围

| 类别 | Python 文件数 | Java 文件数 | 说明 |
|:----:|:----:|:----:|------|
| 新 API (Card + Config) | 4 | 4 | 推荐使用的新模式 |
| Legacy API | 5 | 5 | 向后兼容，标记 @Deprecated |
| Session 依赖 | 1 | 1 | AgentGroupSessionApi |
| Schema | 2 | 2 | 新版 + Legacy schema |
| **合计** | **12** | **12** | |

---

## 2. 文件映射表

### 2.1 新 API（推荐）

| Python 文件 | Java 文件 | 说明 |
|------------|----------|------|
| `multi_agent/__init__.py` | — (Java 无需 __init__) | 包导出 |
| `multi_agent/config.py` | `multiagent/GroupConfig.java` | 组运行时配置 |
| `multi_agent/group.py` | `multiagent/BaseGroup.java` | 抽象基类 |
| `multi_agent/schema/group_card.py` (GroupCard) | `multiagent/schema/GroupCard.java` | 已存在 ✅ |
| `multi_agent/schema/group_card.py` (EventDrivenGroupCard) | `multiagent/schema/EventDrivenGroupCard.java` | 事件驱动组卡片 |

### 2.2 Legacy API（向后兼容）

| Python 文件 | Java 文件 | 说明 |
|------------|----------|------|
| `multi_agent/legacy/__init__.py` | — (Java 无需) | 包导出 + 废弃警告 |
| `multi_agent/legacy/config.py` | `multiagent/legacy/AgentGroupConfig.java` | 旧版配置 |
| `multi_agent/legacy/agent_group.py` (BaseGroup) | `multiagent/legacy/LegacyBaseGroup.java` | 旧版基类 |
| `multi_agent/legacy/agent_group.py` (ControllerGroup) | `multiagent/legacy/ControllerGroup.java` | 控制器组 |
| `multi_agent/legacy/agent_group.py` (AgentGroupSession) | — (合并到 AgentGroupSessionApi) | 会话 |
| `multi_agent/legacy/group_controller.py` (BaseGroupController) | `multiagent/legacy/BaseGroupController.java` | 抽象控制器 |
| `multi_agent/legacy/group_controller.py` (DefaultGroupController) | `multiagent/legacy/DefaultGroupController.java` | 默认控制器 |
| `multi_agent/legacy/group_controller.py` (GroupEvent) | `multiagent/legacy/GroupEvent.java` | 路由事件 **新增** |
| `multi_agent/legacy/schema/group_card.py` (GroupCard) | `multiagent/legacy/schema/LegacyGroupCard.java` | 旧版卡片 |
| `multi_agent/legacy/schema/group_card.py` (EventDrivenGroupCard) | `multiagent/legacy/schema/LegacyEventDrivenGroupCard.java` | 旧版事件卡片 |

### 2.3 Session 依赖

| Python 文件 | Java 文件 | 说明 |
|------------|----------|------|
| `session/agent_group.py` | `session/AgentGroupSessionApi.java` | 组会话 API |

---

## 3. 类映射表

### 3.1 核心类

| Python 类 | Java 类 | 包路径 |
|----------|--------|--------|
| `GroupConfig` | `GroupConfig` | `com.openjiuwen.core.multiagent` |
| `BaseGroup` (new) | `BaseGroup` | `com.openjiuwen.core.multiagent` |
| `GroupCard` | `GroupCard` | `com.openjiuwen.core.multiagent.schema` |
| `EventDrivenGroupCard` | `EventDrivenGroupCard` | `com.openjiuwen.core.multiagent.schema` |
| `Session` (agent_group) | `AgentGroupSessionApi` | `com.openjiuwen.core.session` |

### 3.2 Legacy 类

| Python 类 | Java 类 | 包路径 |
|----------|--------|--------|
| `AgentGroupConfig` | `AgentGroupConfig` | `com.openjiuwen.core.multiagent.legacy` |
| `BaseGroup` (legacy) | `LegacyBaseGroup` | `com.openjiuwen.core.multiagent.legacy` |
| `ControllerGroup` | `ControllerGroup` | `com.openjiuwen.core.multiagent.legacy` |
| `BaseGroupController` | `BaseGroupController` | `com.openjiuwen.core.multiagent.legacy` |
| `DefaultGroupController` | `DefaultGroupController` | `com.openjiuwen.core.multiagent.legacy` |
| `Event` (legacy controller) | `GroupEvent` | `com.openjiuwen.core.multiagent.legacy` |
| `GroupCard` (legacy) | `LegacyGroupCard` | `com.openjiuwen.core.multiagent.legacy.schema` |
| `EventDrivenGroupCard` (legacy) | `LegacyEventDrivenGroupCard` | `com.openjiuwen.core.multiagent.legacy.schema` |

---

## 4. 方法映射表

### 4.1 GroupConfig

| Python 方法 | Java 方法 | 说明 |
|-------------|----------|------|
| `GroupConfig()` | `new GroupConfig()` | 构造函数 |
| `configure_max_agents(n)` | `configureMaxAgents(n)` | 链式配置最大Agent数 |
| `configure_timeout(t)` | `configureTimeout(t)` | 链式配置超时 |
| `configure_concurrency(n)` | `configureConcurrency(n)` | 链式配置并发数 |

### 4.2 BaseGroup (新版)

| Python 方法 | Java 方法 | 说明 |
|-------------|----------|------|
| `__init__(card, config)` | `BaseGroup(card, config)` | 构造 |
| `configure(config)` | `configure(config)` | 设置配置 |
| `add_agent(agent, agent_id)` | `addAgent(agent, agentId)` | 添加Agent |
| `remove_agent(agent_id)` | `removeAgent(agentId)` | 移除Agent |
| `get_agent(agent_id)` | `getAgent(agentId)` | 获取Agent |
| `get_agent_count()` | `getAgentCount()` | Agent计数 |
| `list_agents()` | `listAgents()` | 列出Agent ID |
| `async invoke(message, session)` | `invoke(message, session)` | 同步执行 |
| `async stream(message, session)` | `stream(message, session)` → `Iterator` | 流式执行 |

### 4.3 BaseGroupController (Legacy)

| Python 方法 | Java 方法 | 说明 |
|-------------|----------|------|
| `setup_from_group(group)` | `setupFromGroup(group)` | 注入Group引用 |
| `async invoke(event, session)` | `invoke(event, session)` | 调用入口 |
| `async handle_event(event, session)` | `handleEvent(event, session)` | 抽象事件处理 |
| `subscribe(msg_type, agent_ids)` | `subscribe(msgType, agentIds)` | 订阅消息类型 |
| `unsubscribe(msg_type, agent_ids)` | `unsubscribe(msgType, agentIds)` | 取消订阅 |
| `get_subscribers(msg_type)` | `getSubscribers(msgType)` | 获取订阅者 |
| `async send_to_agent(event, agent_id, session)` | `sendToAgent(event, agentId, session)` | 点对点发送 |
| `async publish(event, session)` | `publish(event, session)` | 广播发布 |
| `async stop()` | `stop()` | 停止控制器 |

---

## 5. 关键技术决策

### 5.1 异步模型转换

| Python 模式 | Java 实现 | 说明 |
|------------|----------|------|
| `async/await` | 同步方法 + Virtual Thread | Java 21 Virtual Thread 替代 asyncio |
| `AsyncIterator` | `Iterator<Object>` | Java 同步迭代器 |
| `asyncio.create_task()` | `CompletableFuture.runAsync()` + Virtual Thread | 异步任务 |
| `asyncio.gather()` | `CompletableFuture.supplyAsync()` + `join()` | 并发执行 |
| `async for chunk in stream_iterator()` | `Iterator` 模式 | 流式迭代 |

### 5.2 Python Event → Java GroupEvent

Python 的 legacy `Event` 类（位于 `controller/legacy/event/event.py`）包含 `content`、`context`、`source`、`receiver_id`、`custom_event_type` 等字段。Java 的 controller 模块已有不同结构的 `Event` 类。

**决策**: 为 legacy multi_agent 模块创建独立的 `GroupEvent` 类，包含组路由所需的最小字段集：
- `query` — 查询文本（对应 Python `event.content.get_query()`）
- `conversationId` — 会话ID
- `userId` — 用户ID
- `receiverId` — 目标Agent ID（点对点路由）
- `customEventType` — 自定义事件类型（订阅路由）

提供 `fromMap()` 工厂方法保持向后兼容。

### 5.3 Session 架构

| Python 类 | Java 类 | 说明 |
|----------|--------|------|
| `session.agent_group.Session` | `AgentGroupSessionApi` | 简化的组会话 |
| `AgentGroupSession` (legacy) | 合并到 `AgentGroupSessionApi` | 无需单独的 legacy session |
| `create_agent_group_session()` | `AgentGroupSessionApi.create()` | 工厂方法 |

Python 中的 `AgentGroupSession` 继承自 `AgentSession`，Java 中简化为 `AgentGroupSessionApi` 直接包装 `AgentSession`，保持相同的会话能力。

### 5.4 消息队列复用

Legacy `BaseGroupController` 直接复用已存在的 `MessageQueueInMemory`（`com.openjiuwen.core.runner.mq`包），无需新增基础设施代码。

### 5.5 类型系统映射

| Python 类型 | Java 类型 | 说明 |
|------------|----------|------|
| `Dict[str, BaseAgent]` | `Map<String, BaseAgent>` | Agent 存储 |
| `List[AgentCard]` | `List<AgentCard>` | 卡片列表 |
| `Optional[GroupConfig]` | `@Nullable GroupConfig` | 可选配置 |
| `Dict[str, List[str]]` | `Map<String, List<String>>` | 订阅映射 |
| `BaseModel` (pydantic) | Lombok `@Data` + `@SuperBuilder` | 数据类 |

---

## 6. 依赖关系

### 6.1 外部模块依赖

| 依赖模块 | 使用的类 | 状态 |
|---------|---------|:----:|
| `common.exception` | `ErrorHelper`, `StatusCode`, `BaseError` | ✅ 已存在 |
| `common.logging` | `Loggers.MULTI_AGENT`, `LoggerProtocol` | ✅ 已存在 |
| `common.schema` | `BaseCard` | ✅ 已存在 |
| `common.constants` | `Constant.INTERACTION` | ✅ 已存在 |
| `singleagent` | `BaseAgent`, `AgentCard` | ✅ 已存在 |
| `session` | `Session`, `AgentSession`, `Config`, `OutputSchema` | ✅ 已存在 |
| `runner.mq` | `MessageQueueInMemory`, `InvokeQueueMessage`, `SubscriptionBase` | ✅ 已存在 |

### 6.2 新增依赖（本模块创建）

| 新增文件 | 被依赖方 |
|---------|---------|
| `AgentGroupSessionApi` | `BaseGroup`, `LegacyBaseGroup`, `ControllerGroup`, `BaseGroupController` |
| `GroupEvent` | `BaseGroupController`, `DefaultGroupController`, `ControllerGroup` |

### 6.3 依赖无需占位符

所有依赖模块均已在 Java 项目中实现，未使用任何占位符。

---

## 7. 目录结构

```
src/main/java/com/openjiuwen/core/
├── multiagent/
│   ├── BaseGroup.java                    # 新版抽象基类 (Card + Config)
│   ├── GroupConfig.java                  # 组运行时配置
│   ├── schema/
│   │   ├── GroupCard.java                # 组卡片（已存在）
│   │   └── EventDrivenGroupCard.java     # 事件驱动组卡片
│   └── legacy/
│       ├── AgentGroupConfig.java         # @Deprecated 旧版配置
│       ├── LegacyBaseGroup.java          # @Deprecated 旧版基类
│       ├── ControllerGroup.java          # @Deprecated 控制器组
│       ├── BaseGroupController.java      # @Deprecated 抽象控制器
│       ├── DefaultGroupController.java   # @Deprecated 默认控制器
│       ├── GroupEvent.java               # @Deprecated 路由事件
│       └── schema/
│           ├── LegacyGroupCard.java      # @Deprecated 旧版卡片
│           └── LegacyEventDrivenGroupCard.java  # @Deprecated 旧版事件卡片
└── session/
    └── AgentGroupSessionApi.java         # 组会话 API
```

---

## 8. 编译验证

- [x] `mvn compile` 通过，无编译错误
- [x] 所有依赖模块已存在，无缺失
- [x] 类型安全检查通过
- [x] Lombok 注解正确应用

---

## 9. 与 Python 版本的差异

### 9.1 命名差异

| 差异 | Python | Java | 原因 |
|------|--------|------|------|
| 命名风格 | `snake_case` | `camelCase` | Java 编码规范 |
| 旧版基类 | `BaseGroup` | `LegacyBaseGroup` | 避免与新版 `BaseGroup` 命名冲突 |
| Event 类 | `Event` (from controller.legacy) | `GroupEvent` | 避免与 controller.schema.Event 冲突 |
| Session 类 | `Session` | `AgentGroupSessionApi` | 与项目中 SessionApi 命名一致 |

### 9.2 结构差异

| 差异 | Python | Java | 原因 |
|------|--------|------|------|
| 异步模型 | `async/await` | 同步方法 | Virtual Thread 自动协程 |
| 流式返回 | `AsyncIterator` | `Iterator<Object>` | Java 同步迭代 |
| Pydantic 验证 | `BaseModel` | Lombok `@Data` | Java 惯用数据类 |
| groups 子目录 | `groups/__init__.py`（空） | 未创建 | 空文件无需转译 |

### 9.3 功能等价性

| 功能 | Python | Java | 状态 |
|------|:------:|:----:|:----:|
| Card + Config 新模式 | ✅ | ✅ | 完全对等 |
| Agent 添加/移除/查询 | ✅ | ✅ | 完全对等 |
| 最大Agent数限制 | ✅ | ✅ | 完全对等 |
| Controller 自动注入 | ✅ | ✅ | 反射实现 |
| 链式配置 | ✅ | ✅ | 完全对等 |
| 消息队列路由 (Legacy) | ✅ | ✅ | 完全对等 |
| 发布-订阅模式 (Legacy) | ✅ | ✅ | 完全对等 |
| 点对点发送 (Legacy) | ✅ | ✅ | 完全对等 |
| 并发广播 (Legacy) | ✅ | ✅ | Virtual Thread 实现 |
| Interaction 中断处理 | ✅ | ✅ | 完全对等 |
| 组会话管理 | ✅ | ✅ | 完全对等 |
| 废弃标记 | `warnings.warn` | `@Deprecated` | Java 标准注解 |

---

## 10. 风险点与建议

### 10.1 已知风险

| 风险 | 级别 | 描述 | 缓解措施 |
|------|:----:|------|---------|
| Controller 注入依赖反射 | 低 | `addAgent` 中通过反射调用 `setGroup` | 异常安全处理，失败静默 |
| 消息队列线程安全 | 低 | BaseGroupController 使用 synchronized 确保 | 已在 `ensureQueueStarted` 中处理 |
| ControllerGroup.stream() 简化 | 中 | Python 版通过 session.stream_iterator() 实现真实流式，Java 版简化为队列桥接 | 后续可增强 StreamWriter 桥接 |

### 10.2 后续建议

1. **单元测试**: 参照 Python `tests/unit_tests/test_multi_agent/` 编写 JUnit 5 测试
2. **集成测试**: 测试 ControllerGroup 与真实 Agent 的端到端协作
3. **流式增强**: ControllerGroup.stream() 可优化为利用 AgentSessionApi 的 StreamWriter 实现真正的流式桥接
4. **新 API 推广**: Legacy 模块已全部标记 @Deprecated，建议新代码使用 Card + Config 模式
