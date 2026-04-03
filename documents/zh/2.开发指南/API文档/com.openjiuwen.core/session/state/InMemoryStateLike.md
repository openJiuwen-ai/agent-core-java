# com.openjiuwen.core.session.state.InMemoryStateLike

## 类 InMemoryStateLike

```java
public class InMemoryStateLike implements StateLike
```

`StateLike` 的内存实现，内部使用 `Map<String, Object>` 保存状态。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public InMemoryStateLike()` | 以空映射初始化状态。 |
| `public InMemoryStateLike(Map<String, Object> initialState)` | 使用给定初始状态创建内存状态对象。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public synchronized Object get(Object key)` | 使用 `SessionUtils.getBySchema(...)` 读取状态，并返回深拷贝结果。 |
| `public synchronized Object getByPrefix(Object key, String nestedPrefix)` | 在给定嵌套前缀下读取状态，并返回深拷贝结果。 |
| `public synchronized Object getByTransformer(Function<Object, Object> transformer)` | 通过转换函数直接处理当前状态对象。 |
| `public synchronized void update(Map<String, Object> data)` | 使用 `SessionUtils.updateDict(...)` 合并更新。 |
| `public synchronized Map<String, Object> getState()` | 返回当前状态的深拷贝。 |
| `public synchronized void setState(Map<String, Object> newState)` | 用给定映射覆盖当前状态。 |

## 说明

- 相关测试：`StateTest`。
