# com.openjiuwen.core.runner.resourcemanager.AgentMgr

## 类 AgentMgr

```java
public class AgentMgr<T> extends AbstractManager<T>
```

`AgentMgr` 负责 `Agent` 资源 provider 的注册、获取与移除。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addAgent(String agentId, Supplier<? extends T> agent)` | - |
| `public T getAgent(String agentId)` | - |
| `public Supplier<? extends T> removeAgent(String agentId)` | - |
