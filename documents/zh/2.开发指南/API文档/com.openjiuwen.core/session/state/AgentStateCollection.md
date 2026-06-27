# com.openjiuwen.core.session.state.AgentStateCollection

## 类 AgentStateCollection

```java
public class AgentStateCollection implements State
```

管理 agent 私有状态与全局状态两套内存分区的 `State` 实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AgentStateCollection()` | 初始化空的 `globalState`、`agentState` 与 `traceState`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object get(Object key)` | 读取 agent 状态；`key = null` 时返回完整 agent 状态映射。 |
| `public void update(Map<String, Object> data)` | 更新 agent 状态分区。 |
| `public void updateTrace(Object span)` | 预留的 trace 更新入口；当前实现为空。 |
| `public void updateGlobal(Map<String, Object> data)` | 更新全局状态分区。 |
| `public Object getGlobal(Object key)` | 读取全局状态；`key = null` 时返回完整全局状态映射。 |
| `public Map<String, Object> getState()` | 导出 `global_state` 与 `agent_state` 组成的快照。 |
| `public void setState(Map<String, Object> state)` | 从快照恢复 `global_state` 与 `agent_state`。 |
| `public InMemoryStateLike getGlobalStateLike()` | 返回内部的全局状态对象。 |
| `public Map<String, Object> dump()` | 导出包含 `global_state`、`agent_state` 与 `trace_state` 的调试视图。 |

## 说明

- 相关测试：`StateTest`。
