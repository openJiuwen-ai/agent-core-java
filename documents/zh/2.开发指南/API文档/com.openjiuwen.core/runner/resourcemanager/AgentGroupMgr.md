# com.openjiuwen.core.runner.resourcemanager.AgentGroupMgr

## 类 AgentGroupMgr

```java
public class AgentGroupMgr<T> extends AbstractManager<T>
```

`AgentGroupMgr` 负责 `AgentGroup` 资源 provider 的注册、获取与移除。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addAgentGroup(String agentGroupId, Supplier<? extends T> agentGroup)` | - |
| `public Supplier<? extends T> removeAgentGroup(String agentGroupId)` | - |
| `public T getAgentGroup(String agentGroupId)` | - |
