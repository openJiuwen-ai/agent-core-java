# MultiAgent 模块 API 文档

> 包路径：`com.openjiuwen.core.multiagent`

MultiAgent 模块提供多智能体编排能力。当前源码同时包含两套接口：一套是新的 Card + Config 风格 `BaseGroup`/`GroupCard` 体系，另一套是为兼容旧设计保留的 `legacy` 分组与路由控制器实现。

---

## 目录

- [1. 新版分组 API](#1-新版分组-api)
- [2. Legacy 分组 API](#2-legacy-分组-api)
- [3. Schema 模型](#3-schema-模型)

---

## 1. 新版分组 API

### 1.1 BaseGroup

多智能体分组基类，负责持有 `GroupCard`、运行时配置以及分组内 Agent 注册表。

**包路径**：`com.openjiuwen.core.multiagent`

**构造方法**
```java
BaseGroup(GroupCard card, GroupConfig config)
BaseGroup(GroupCard card)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configure(GroupConfig config)` | `BaseGroup` | 更新运行时配置，支持链式调用 |
| `addAgent(BaseAgent agent, String agentId)` | `BaseGroup` | 添加 Agent，可显式指定 ID |
| `addAgent(BaseAgent agent)` | `BaseGroup` | 使用 `agent.card.name` 作为 ID 添加 |
| `removeAgent(String agentId)` | `BaseGroup` | 按 ID 移除 Agent |
| `removeAgent(BaseAgent agent)` | `BaseGroup` | 按实例移除 Agent |
| `getAgent(String agentId)` | `BaseAgent` | 获取指定 Agent |
| `getAgentCount()` | `int` | 获取分组内 Agent 数量 |
| `listAgents()` | `List<String>` | 列出全部 Agent ID |
| `getCard()` | `GroupCard` | 获取分组卡片 |
| `getConfig()` | `GroupConfig` | 获取运行时配置 |
| `getGroupId()` | `String` | 获取分组 ID（来源于 `card.name`） |
| `getAgents()` | `Map<String, BaseAgent>` | 获取内部 Agent 映射 |
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 同步执行入口（抽象方法） |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 流式执行入口（抽象方法） |

**行为说明**

- 添加 Agent 时会校验重复 ID 与 `maxAgents` 限制。
- 若 Agent 暴露 `getController().setGroup(...)`，会通过反射自动回填组引用。

### 1.2 GroupConfig

分组运行时配置对象。

**包路径**：`com.openjiuwen.core.multiagent`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxAgents` | `int` | `10` | 最大 Agent 数量 |
| `maxConcurrentMessages` | `int` | `100` | 并发消息数上限 |
| `messageTimeout` | `double` | `30.0` | 消息超时时间（秒） |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `configureMaxAgents(int maxAgents)` | `GroupConfig` | 配置最大 Agent 数 |
| `configureTimeout(double timeout)` | `GroupConfig` | 配置超时时间 |
| `configureConcurrency(int maxConcurrent)` | `GroupConfig` | 配置并发上限 |

---

## 2. Legacy 分组 API

### 2.1 LegacyBaseGroup

旧版多智能体分组抽象类，负责持有 `AgentGroupConfig` 和 `agents` 注册表。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**状态**：`@Deprecated`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `addAgent(String agentId, BaseAgent agent)` | `void` | 向旧版分组注册 Agent |
| `getAgentCount()` | `int` | 获取 Agent 数量 |
| `getConfig()` | `AgentGroupConfig` | 获取旧版配置 |
| `getGroupId()` | `String` | 获取分组 ID |
| `getAgents()` | `Map<String, BaseAgent>` | 获取 Agent 映射 |
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 同步执行入口（抽象） |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 流式执行入口（抽象） |

### 2.2 AgentGroupConfig

旧版分组配置对象。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**状态**：`@Deprecated`

**构造方法**
```java
AgentGroupConfig(String groupId)
AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `groupId` | `String` | 分组 ID |
| `maxAgents` | `int` | 最大 Agent 数量 |
| `maxConcurrentMessages` | `int` | 最大并发消息数 |
| `messageTimeout` | `double` | 超时时间 |

### 2.3 ControllerGroup

旧版控制器驱动的 AgentGroup，实现消息对象转换、缺省会话创建以及对 `BaseGroupController` 的同步/流式委托。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**继承**：`LegacyBaseGroup`  
**状态**：`@Deprecated`

**构造方法**
```java
ControllerGroup(AgentGroupConfig config, BaseGroupController groupController)
ControllerGroup(AgentGroupConfig config)
```

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `invoke(Object message, AgentGroupSessionApi session)` | `Object` | 同步调用，支持 `GroupEvent`/`Map`/`String` 输入 |
| `stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | 流式调用，后台启动控制器并回放 Session 流 |
| `getGroupController()` | `BaseGroupController` | 获取底层控制器 |

### 2.4 BaseGroupController

旧版消息路由控制器，基于 `MessageQueueInMemory` 与主题订阅机制组织多 Agent 协作。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**状态**：`@Deprecated`

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `setupFromGroup(LegacyBaseGroup group)` | `void` | 注入所属分组 |
| `invoke(GroupEvent event, AgentGroupSessionApi session)` | `Object` | 向分组主题发送消息并等待处理结果 |
| `subscribe(String messageType, List<String> agentIds)` | `void` | 为消息类型订阅 Agent |
| `unsubscribe(String messageType, List<String> agentIds)` | `void` | 取消订阅 |
| `getSubscribers(String messageType)` | `List<String>` | 获取订阅者列表 |
| `sendToAgent(GroupEvent event, String agentId, AgentGroupSessionApi session)` | `Object` | 点对点路由到指定 Agent |
| `publish(GroupEvent event, AgentGroupSessionApi session)` | `List<Object>` | 基于 `customEventType` 广播到订阅者 |
| `stop()` | `void` | 停止控制器与消息队列 |
| `getAgentGroup()` | `LegacyBaseGroup` | 获取绑定分组 |
| `getSubscriptionsMap()` | `Map<String, List<String>>` | 获取订阅映射 |

### 2.5 DefaultGroupController

默认旧版路由控制器，根据 `receiverId` 决定点对点发送或广播。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**继承**：`BaseGroupController`  
**状态**：`@Deprecated`

**构造方法**
```java
DefaultGroupController(LegacyBaseGroup agentGroup)
DefaultGroupController()
```

### 2.6 GroupEvent

旧版分组消息事件模型，用于跨 Agent 路由。

**包路径**：`com.openjiuwen.core.multiagent.legacy`  
**状态**：`@Deprecated`

**构造/工厂方法**
```java
GroupEvent()
createUserEvent(String content, String conversationId)
createUserEvent(String content, String conversationId, String userId)
fromMap(Map<String, Object> map)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventId` | `String` | 事件 ID |
| `query` | `String` | 文本查询 |
| `queryPayload` | `Object` | 原始查询负载 |
| `conversationId` | `String` | 会话 ID |
| `userId` | `String` | 用户 ID |
| `receiverId` | `String` | 点对点目标 Agent |
| `customEventType` | `String` | 自定义消息类型 |
| `metadata` | `Map<String, Object>` | 附加元数据 |

| 方法签名 | 返回类型 | 说明 |
|----------|----------|------|
| `getQuery()/setQuery(...)` | `String` / `void` | 读写查询文本 |
| `getQueryPayload()/setQueryPayload(...)` | `Object` / `void` | 读写原始负载 |
| `getConversationId()/setConversationId(...)` | `String` / `void` | 读写会话 ID |
| `getUserId()/setUserId(...)` | `String` / `void` | 读写用户 ID |
| `getReceiverId()/setReceiverId(...)` | `String` / `void` | 读写目标 Agent |
| `getCustomEventType()/setCustomEventType(...)` | `String` / `void` | 读写消息类型 |
| `getMetadata()/setMetadata(...)` | `Map<String, Object>` / `void` | 读写元数据 |

---

## 3. Schema 模型

### 3.1 GroupCard

新版分组卡片，描述分组身份、主题、标签以及成员 Agent 卡片列表。

**包路径**：`com.openjiuwen.core.multiagent.schema`  
**继承**：`BaseCard`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agentCards` | `List<AgentCard>` | 空列表 | 成员 Agent 卡片列表 |
| `topic` | `String` | `""` | 分组主题 |
| `version` | `String` | `"1.0.0"` | 分组版本 |
| `tags` | `List<String>` | 空列表 | 标签列表 |

### 3.2 EventDrivenGroupCard

带订阅信息的新版事件驱动分组卡片。

**包路径**：`com.openjiuwen.core.multiagent.schema`  
**继承**：`GroupCard`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `subscriptions` | `Map<String, List<String>>` | 空映射 | `agentId -> topics` 订阅关系 |

### 3.3 LegacyGroupCard

旧版分组卡片。

**包路径**：`com.openjiuwen.core.multiagent.legacy.schema`  
**继承**：`BaseCard`  
**状态**：`@Deprecated`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `agentCard` | `List<AgentCard>` | 空列表 | 成员 Agent 卡片列表 |
| `topic` | `String` | `""` | 分组主题 |

### 3.4 LegacyEventDrivenGroupCard

旧版事件驱动分组卡片。

**包路径**：`com.openjiuwen.core.multiagent.legacy.schema`  
**继承**：`LegacyGroupCard`  
**状态**：`@Deprecated`

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `subscriptions` | `Map<String, List<String>>` | 空映射 | 订阅关系映射 |
