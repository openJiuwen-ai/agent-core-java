# com.openjiuwen.core.singleagent.AgentCallbackManager

## 类 AgentCallbackManager

```java
public class AgentCallbackManager
```

管理回调与 rail 的注册、注销和触发。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public AgentCallbackManager(String agentId)` | 使用 `agentId` 初始化回调命名空间。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public CompletionStage<AgentCallbackManager> registerCallback(AgentCallbackEvent event, AgentCallback callback, int priority)` | 为指定事件注册全局回调，并显式设置优先级。 |
| `public CompletionStage<AgentCallbackManager> registerRail(AgentRail rail, Object agent)` | 注册全局 rail，并接入全局 Runner callback framework。 |
| `public CompletionStage<AgentCallbackManager> registerInstanceRail(AgentRail rail, Object agent)` | 注册实例级 rail，仅接入当前 `AgentCallbackManager` 持有的实例回调表。 |
| `public CompletionStage<Void> unregisterRail(AgentRail rail, Object agent)` | 注销全局 rail，并从全局 Runner callback framework 撤销其回调。 |
| `public CompletionStage<Void> unregisterInstanceRail(AgentRail rail, Object agent)` | 注销实例级 rail，并使用注册时保存的 callback 对象身份从当前实例回调表撤销其回调；未注册时直接完成。 |
| `public CompletionStage<Void> unregister(AgentCallbackEvent event, AgentCallback callback)` | 从指定全局事件移除单个回调。 |
| `public CompletionStage<Void> clear(AgentCallbackEvent event)` | 清理某个全局事件或全部全局事件上的已注册钩子。 |
| `public CompletionStage<Void> clearInstance(AgentCallbackEvent event)` | 清理某个实例事件或全部实例事件上的已注册钩子。 |
| `public boolean hasHooks(AgentCallbackEvent event)` | 检查指定事件当前是否存在全局钩子。 |
| `public boolean hasInstanceHooks(AgentCallbackEvent event)` | 检查指定事件当前是否存在实例级钩子。 |
| `public CompletionStage<AgentCallbackContext> execute(AgentCallbackEvent event, AgentCallbackContext ctx)` | 先触发全局 callback，再触发当前 Agent 对象实例上的 callback。 |

## 说明

- 相关测试：`AgentCallbackManagerTest`。
- 该类型同时支持函数式回调和 `AgentRail` 回调。全局回调通过 `agentId + "_" + event` 生成事件名，并注册到全局 Runner callback framework；实例级 rail 不进入全局 Runner，也不用 `agentId` 做隔离，只绑定当前 `AgentCallbackManager` / Agent 对象实例。
- 同一事件固定先执行全局 callback，再执行当前 Agent 对象的实例 callback。全局阶段如果传播 `AbortError` / `Error`，会短路并且不进入实例阶段；普通异常记录或忽略后继续执行后续回调。
- 两个作用域内部分别按 priority 数字越大越先执行，同 priority 按注册顺序执行；全局和实例两个 scope 之间不做跨 scope priority 比较。
- `clearInstance(null)` 会清理所有实例事件；传入具体 `AgentCallbackEvent` 时只清理该实例事件。`hasInstanceHooks(event)` 只检查实例级钩子，`event == null` 时返回 `false`。
- 实例级 rail 注销使用注册时保存的 callback 对象身份，不会在注销时重新调用 `rail.getCallbacks()`；从未注册或已注销的 rail 会直接完成注销。
- SDK 不检测同一 rail 是否同时注册为全局 rail 和实例级 rail；同一 Agent 对象上的实例级 rail 也不承诺并发安全。
