# com.openjiuwen.core.graph.store.GraphStore

## 类 GraphStore

```java
public class GraphStore implements Store
```

为底层 `Store` 增加日志记录的装饰器，不改变实际持久化协议。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `delegate` | `Store` | `-` | 真实执行读取、保存和删除的底层存储实现。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public GraphStore(Store delegate)` | 基于指定底层存储创建日志装饰器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Optional<GraphStoreState> get(String sessionId, String ns)` | 调用底层 `Store.get(...)`；未命中时记 debug 日志，异常时记 error 日志并继续抛出。 |
| `public void save(String sessionId, String ns, GraphStoreState state)` | 记录 super-step 与命名空间信息后委托到底层 `Store.save(...)`。 |
| `public void delete(String sessionId, String ns)` | 记录删除范围后委托到底层 `Store.delete(...)`；异常会在记录 error 日志后继续抛出。 |

## 相关测试

- `GraphStoreTest`
