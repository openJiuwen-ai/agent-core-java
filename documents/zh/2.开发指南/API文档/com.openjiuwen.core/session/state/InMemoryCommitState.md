# com.openjiuwen.core.session.state.InMemoryCommitState

## 类 InMemoryCommitState

```java
public class InMemoryCommitState implements CommitStateLike
```

带待提交缓冲区的内存状态实现，适合把节点级更新先缓存，再按节点或全局统一提交。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InMemoryCommitState()` | 使用默认的 `InMemoryStateLike` 作为底层状态存储。 |
| `public InMemoryCommitState(StateLike state)` | 使用给定的 `StateLike` 作为底层状态存储。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public synchronized void update(Map<String, Object> data)` | 直接调用会抛出错误，要求调用方显式提供 `nodeId`。 |
| `public synchronized void updateById(String nodeId, Map<String, Object> data)` | 把一份深拷贝后的更新追加到指定节点的待提交列表。 |
| `public synchronized void commit(String nodeId)` | 提交指定节点，或在 `nodeId = null` 时提交全部节点的待提交更新。 |
| `public synchronized void rollback(String nodeId)` | 清空指定节点的待提交更新。 |
| `public Object getByTransformer(Function<Object, Object> transformer)` | 通过转换函数读取底层状态。 |
| `public Object get(Object key)` | 从已提交状态中按键读取数据。 |
| `public Object getByPrefix(Object key, String nestedPrefix)` | 在给定前缀下读取嵌套状态。 |
| `public synchronized Map<String, Object> getUpdates()` | 返回当前待提交更新映射。 |
| `public synchronized void setUpdates(Map<String, Object> newUpdates)` | 用给定映射恢复待提交更新。 |
| `public Map<String, Object> getState()` | 返回已提交状态快照。 |
| `public void setState(Map<String, Object> newState)` | 覆盖底层已提交状态。 |

## 说明

- 相关测试：`StateTest`。
