# com.openjiuwen.core.session.state.CommitStateLike

## 接口 CommitStateLike

```java
public interface CommitStateLike extends StateLike
```

为 `StateLike` 增加提交、回滚与待提交更新访问能力的接口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void updateById(String nodeId, Map<String, Object> data)` | 按节点 ID 追加一组待提交更新。 |
| `void commit(String nodeId)` | 提交指定节点的待提交更新；`nodeId = null` 时由实现决定是否提交全部更新。 |
| `default void commit()` | 默认委托到 `commit(null)`。 |
| `void rollback(String nodeId)` | 回滚指定节点的待提交更新。 |
| `Map<String, Object> getUpdates()` | 返回当前缓冲的待提交更新。 |
| `void setUpdates(Map<String, Object> updates)` | 用给定映射恢复待提交更新。 |
