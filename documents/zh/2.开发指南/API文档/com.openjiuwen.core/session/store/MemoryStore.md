# com.openjiuwen.core.session.MemoryStore

## 类 MemoryStore

```java
public class MemoryStore extends Store
```

基于 `HashMap` 的内存存储实现。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object read(Object key)` | 使用 `SessionUtils.getBySchema(...)` 从当前内存数据中读取值。 |
| `public void write(Map<String, Object> value)` | 使用 `SessionUtils.updateDict(...)` 把更新合并到当前内存数据。 |
| `public Map<String, Object> getData()` | 返回底层数据映射。 |

## 说明

- 相关测试：`InMemoryCheckpointerTest`。
