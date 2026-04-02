# com.openjiuwen.core.graph.store.Store

## 接口 Store

```java
public interface Store
```

定义图状态持久化所需的最小操作集合：读取、保存和删除。

## 方法

| 签名 | 说明 |
| --- | --- |
| `Optional<GraphStoreState> get(String sessionId, String ns)` | 读取指定 session 与命名空间下的状态快照；不存在时返回空 `Optional`。 |
| `void save(String sessionId, String ns, GraphStoreState state)` | 保存指定 session 与命名空间下的状态快照。 |
| `void delete(String sessionId, String ns)` | 删除指定 session 的状态；`ns` 为 `null` 时删除全部命名空间，具体命名空间匹配策略由实现类决定。 |

## 相关测试

- `GraphStoreTest`
