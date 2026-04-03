# com.openjiuwen.core.context.schema.OffloadMixin

## interface OffloadMixin

```java
public interface OffloadMixin
```

`OffloadMixin` 用于标记“已经从上下文窗口卸载出去”的消息类型，统一暴露卸载位置、卸载句柄和元数据接口。

## 方法

### `String getOffloadType()`

返回卸载内容的存储类型，例如 `in_memory`。

### `String getOffloadHandle()`

返回后续重载原始消息时使用的唯一句柄。

### `Map<String, Object> getMetadata()`

返回与该卸载消息关联的元数据映射。

## 说明

- `OffloadMessages` 下的四个嵌套消息类型都会实现该接口。
