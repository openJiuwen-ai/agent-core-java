# MultiAgent 模块 API 文档

> 包路径：`com.openjiuwen.core.multiagent`

多智能体分组、团队编排与兼容层接口。基于 `multiagent` 包源码逐页复核整理。

## 文档说明

- 本页覆盖 `12` 个公开类型（含嵌套公开类型）。
- 默认记录源码中显式声明的 public/protected API；接口中按语言规则公开的成员同样列出。
- Lombok 自动生成的 getter/setter/builder 不逐项展开，DTO/配置类改为记录显式字段。
- 标记为 `@Deprecated` 或位于 `legacy` 包的类型会在条目中注明兼容性。

## 包概览

| 包 | 公开类型数 |
|---|---:|
| `com.openjiuwen.core.multiagent` | 2 |
| `com.openjiuwen.core.multiagent.legacy` | 6 |
| `com.openjiuwen.core.multiagent.legacy.schema` | 2 |
| `com.openjiuwen.core.multiagent.schema` | 2 |

## `com.openjiuwen.core.multiagent`

公开类型：`2`

### `BaseGroup`

- 类型：`class`
- 声明：`public abstract class BaseGroup`
- 说明：Abstract base class for agent groups (new Card + Config pattern).

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseGroup(GroupCard card, GroupConfig config)` | Initialize the agent group. |
| `protected BaseGroup(GroupCard card)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public BaseGroup configure(GroupConfig config)` | `BaseGroup` | Set configuration (supports chaining). |
| `public BaseGroup addAgent(BaseAgent agent, String agentId)` | `BaseGroup` | Register agent to group (supports chaining). |
| `public BaseGroup addAgent(BaseAgent agent)` | `BaseGroup` | Register agent to group using agent's card name as ID. |
| `public BaseGroup removeAgent(String agentId)` | `BaseGroup` | Remove agent from group (supports chaining). |
| `public BaseGroup removeAgent(BaseAgent agent)` | `BaseGroup` | Remove agent from group by instance (supports chaining). |
| `public BaseAgent getAgent(String agentId)` | `BaseAgent` | Get agent by ID. |
| `public int getAgentCount()` | `int` | Get the number of agents in the group. |
| `public List<String> listAgents()` | `List<String>` | List all agent IDs. |
| `public GroupCard getCard()` | `GroupCard` | - |
| `public GroupConfig getConfig()` | `GroupConfig` | - |
| `public String getGroupId()` | `String` | - |
| `public Map<String, BaseAgent> getAgents()` | `Map<String, BaseAgent>` | - |
| `public abstract Object invoke(Object message, AgentGroupSessionApi session)` | `Object` | Execute synchronous operation on the agent group. |
| `public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | Execute streaming operation on the agent group. |

### `GroupConfig`

- 类型：`class`
- 声明：`public class GroupConfig`
- 说明：Group Runtime Configuration.

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `maxAgents` | `int` | `private` | `10` | - |
| `maxConcurrentMessages` | `int` | `private` | `100` | - |
| `messageTimeout` | `double` | `private` | `30.0` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GroupConfig()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public int getMaxAgents()` | `int` | - |
| `public void setMaxAgents(int maxAgents)` | `void` | - |
| `public int getMaxConcurrentMessages()` | `int` | - |
| `public void setMaxConcurrentMessages(int maxConcurrentMessages)` | `void` | - |
| `public double getMessageTimeout()` | `double` | - |
| `public void setMessageTimeout(double messageTimeout)` | `void` | - |
| `public GroupConfig configureMaxAgents(int maxAgents)` | `GroupConfig` | Configure maximum agents (supports chaining). |
| `public GroupConfig configureTimeout(double timeout)` | `GroupConfig` | Configure message timeout (supports chaining). |
| `public GroupConfig configureConcurrency(int maxConcurrent)` | `GroupConfig` | Configure concurrency limit (supports chaining). |

## `com.openjiuwen.core.multiagent.legacy`

公开类型：`6`

### `AgentGroupConfig`

- 类型：`class`
- 声明：`@Deprecated public class AgentGroupConfig`
- 说明：Legacy AgentGroup Configuration.
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `groupId` | `String` | `private final` | `-` | - |
| `maxAgents` | `int` | `private` | `-` | - |
| `maxConcurrentMessages` | `int` | `private` | `-` | - |
| `messageTimeout` | `double` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public AgentGroupConfig(String groupId)` | - |
| `public AgentGroupConfig(String groupId, int maxAgents, int maxConcurrentMessages, double messageTimeout)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getGroupId()` | `String` | - |
| `public int getMaxAgents()` | `int` | - |
| `public void setMaxAgents(int maxAgents)` | `void` | - |
| `public int getMaxConcurrentMessages()` | `int` | - |
| `public void setMaxConcurrentMessages(int maxConcurrentMessages)` | `void` | - |
| `public double getMessageTimeout()` | `double` | - |
| `public void setMessageTimeout(double messageTimeout)` | `void` | - |

### `BaseGroupController`

- 类型：`class`
- 声明：`@Deprecated public abstract class BaseGroupController`
- 说明：Message routing controller for AgentGroup (legacy pattern).
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected BaseGroupController(LegacyBaseGroup agentGroup)` | Initialize BaseGroupController. |
| `protected BaseGroupController()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void setupFromGroup(LegacyBaseGroup group)` | `void` | Setup controller from group \u2014 inject required attributes. |
| `public Object invoke(GroupEvent event, AgentGroupSessionApi session)` | `Object` | Synchronous invocation entry. |
| `protected abstract Object handleEvent(GroupEvent event, AgentGroupSessionApi session)` | `Object` | Core method for message processing (must be implemented). |
| `public void subscribe(String messageType, List<String> agentIds)` | `void` | Subscribe agents to a message type. |
| `public void unsubscribe(String messageType, List<String> agentIds)` | `void` | Unsubscribe agents from a message type. |
| `public List<String> getSubscribers(String messageType)` | `List<String>` | Get subscribers for a message type. |
| `public Object sendToAgent(GroupEvent event, String agentId, AgentGroupSessionApi session)` | `Object` | Send message to specified Agent (point-to-point, streaming). |
| `public List<Object> publish(GroupEvent event, AgentGroupSessionApi session)` | `List<Object>` | Publish message to all subscribers (broadcast). |
| `public void stop()` | `void` | Stop group controller \u2014 clean up all resources. |
| `public LegacyBaseGroup getAgentGroup()` | `LegacyBaseGroup` | - |
| `public Map<String, List<String>> getSubscriptionsMap()` | `Map<String, List<String>>` | - |

### `ControllerGroup`

- 类型：`class`
- 声明：`@Deprecated public class ControllerGroup extends LegacyBaseGroup`
- 说明：Agent Group with Controller (legacy pattern).
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `public ControllerGroup(AgentGroupConfig config, BaseGroupController groupController)` | Initialize ControllerGroup. |
| `public ControllerGroup(AgentGroupConfig config)` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public Object invoke(Object message, AgentGroupSessionApi session)` | `Object` | - |
| `public Iterator<Object> stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | - |
| `public BaseGroupController getGroupController()` | `BaseGroupController` | - |

### `DefaultGroupController`

- 类型：`class`
- 声明：`@Deprecated public class DefaultGroupController extends BaseGroupController`
- 说明：Default GroupController \u2014 routes messages based on subscription.
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `public DefaultGroupController(LegacyBaseGroup agentGroup)` | - |
| `public DefaultGroupController()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `protected Object handleEvent(GroupEvent event, AgentGroupSessionApi session)` | `Object` | - |

### `GroupEvent`

- 类型：`class`
- 声明：`@Deprecated public class GroupEvent`
- 说明：Event class for agent group message routing.
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `eventId` | `String` | `private` | `-` | - |
| `query` | `String` | `private` | `-` | - |
| `queryPayload` | `Object` | `private` | `-` | - |
| `conversationId` | `String` | `private` | `-` | - |
| `userId` | `String` | `private` | `-` | - |
| `receiverId` | `String` | `private` | `-` | - |
| `customEventType` | `String` | `private` | `-` | - |
| `metadata` | `Map<String, Object>` | `private` | `-` | - |

**构造方法**

| 签名 | 说明 |
|---|---|
| `public GroupEvent()` | - |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public static GroupEvent createUserEvent(String content, String conversationId)` | `GroupEvent` | Create a user event from content string. |
| `public static GroupEvent createUserEvent(String content, String conversationId, String userId)` | `GroupEvent` | Create a user event from content string with user ID. |
| `public static GroupEvent fromMap(Map<String, Object> map)` | `GroupEvent` | Create a GroupEvent from a Map (backward compatibility). |
| `public String getEventId()` | `String` | - |
| `public void setEventId(String eventId)` | `void` | - |
| `public String getQuery()` | `String` | - |
| `public void setQuery(String query)` | `void` | - |
| `public Object getQueryPayload()` | `Object` | - |
| `public void setQueryPayload(Object queryPayload)` | `void` | - |
| `public String getConversationId()` | `String` | - |
| `public void setConversationId(String conversationId)` | `void` | - |
| `public String getUserId()` | `String` | - |
| `public void setUserId(String userId)` | `void` | - |
| `public String getReceiverId()` | `String` | - |
| `public void setReceiverId(String receiverId)` | `void` | - |
| `public String getCustomEventType()` | `String` | - |
| `public void setCustomEventType(String customEventType)` | `void` | - |
| `public Map<String, Object> getMetadata()` | `Map<String, Object>` | - |
| `public void setMetadata(Map<String, Object> metadata)` | `void` | - |

### `LegacyBaseGroup`

- 类型：`class`
- 声明：`@Deprecated public abstract class LegacyBaseGroup`
- 说明：Abstract base class for implementing agent groups (legacy pattern).
- 注解：`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**构造方法**

| 签名 | 说明 |
|---|---|
| `protected LegacyBaseGroup(AgentGroupConfig config)` | Initialize the agent group. |

**方法**

| 签名 | 返回类型 | 说明 |
|---|---|---|
| `public void addAgent(String agentId, BaseAgent agent)` | `void` | Register agent to the group. |
| `public int getAgentCount()` | `int` | Get the number of agents in the group. |
| `public AgentGroupConfig getConfig()` | `AgentGroupConfig` | - |
| `public String getGroupId()` | `String` | - |
| `public Map<String, BaseAgent> getAgents()` | `Map<String, BaseAgent>` | - |
| `public abstract Object invoke(Object message, AgentGroupSessionApi session)` | `Object` | Execute synchronous operation on the agent group. |
| `public abstract Iterator<Object> stream(Object message, AgentGroupSessionApi session)` | `Iterator<Object>` | Execute streaming operation on the agent group. |

## `com.openjiuwen.core.multiagent.legacy.schema`

公开类型：`2`

### `LegacyEventDrivenGroupCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @Deprecated public class LegacyEventDrivenGroupCard extends LegacyGroupCard`
- 说明：Legacy Event-driven group card with subscription information.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `subscriptions` | `Map<String, List<String>>` | `private` | `new HashMap<>()` | Subscription mapping: {agent_id: [topic1, topic2, ...]}. |

### `LegacyGroupCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) @Deprecated public class LegacyGroupCard extends BaseCard`
- 说明：Legacy Group Card.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`、`@Deprecated`
- 兼容性：`@Deprecated`、`legacy` 包/说明

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agentCard` | `List<AgentCard>` | `private` | `new ArrayList<>()` | - |
| `topic` | `String` | `private` | `""` | - |

## `com.openjiuwen.core.multiagent.schema`

公开类型：`2`

### `EventDrivenGroupCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class EventDrivenGroupCard extends GroupCard`
- 说明：Event-driven group card with subscription information.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `subscriptions` | `Map<String, List<String>>` | `private` | `new HashMap<>()` | Subscription mapping: {agent_id: [topic1, topic2, ...]}. |

### `GroupCard`

- 类型：`class`
- 声明：`@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true) public class GroupCard extends BaseCard`
- 说明：Group Identity Card.
- 注解：`@Data`、`@SuperBuilder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@EqualsAndHashCode`

**字段**

| 字段 | 类型 | 修饰符 | 默认值 | 说明 |
|---|---|---|---|---|
| `agentCards` | `List<AgentCard>` | `private` | `new ArrayList<>()` | - |
| `topic` | `String` | `private` | `""` | - |
| `version` | `String` | `private` | `"1.0.0"` | - |
| `tags` | `List<String>` | `private` | `new ArrayList<>()` | - |

