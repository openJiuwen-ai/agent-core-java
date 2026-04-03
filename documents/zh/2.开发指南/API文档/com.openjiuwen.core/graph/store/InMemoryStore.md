# com.openjiuwen.core.graph.store.InMemoryStore

## 类 InMemoryStore

```java
public class InMemoryStore implements Store
```

使用 `ConcurrentHashMap` 将图状态保存在内存中，适合测试或单进程场景。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `storeCk` | `Map<String, Map<String, GraphStoreState>>` | `new ConcurrentHashMap<>()` | `sessionId -> (ns -> state)` 的多级内存映射。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Optional<GraphStoreState> get(String sessionId, String ns)` | 返回指定 session 与命名空间下的状态副本；未命中时返回空 `Optional`。 |
| `public void save(String sessionId, String ns, GraphStoreState state)` | 以深拷贝方式保存状态，避免调用方后续修改污染存储内容。 |
| `public void delete(String sessionId, String ns)` | `ns == null` 时删除整个 session；否则按 `startsWith(prefix)` 规则删除匹配命名空间。 |

## 相关测试

- `GraphStoreTest`
