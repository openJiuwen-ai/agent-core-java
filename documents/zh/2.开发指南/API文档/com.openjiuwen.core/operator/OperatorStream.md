# com.openjiuwen.core.operator.OperatorStream

## interface OperatorStream<T>

```java
public interface OperatorStream<T> extends Iterator<T>, AutoCloseable
```

`OperatorStream` 是带显式关闭钩子的迭代式流接口，用来包装算子的流式输出，并在提前结束时执行资源清理。

## 默认方法

### `default void close()`

默认空实现。具体清理逻辑通常由 `OperatorStream.wrap(...)` 返回的包装器承担。

## 静态方法

### `static <T> OperatorStream<T> wrap(Iterator<T> delegate, Runnable onClose)`

把普通 `Iterator` 包装成带清理保证的 `OperatorStream`。

**参数**

- `delegate`: 底层迭代器。
- `onClose`: 流结束时执行的清理动作。

**返回**

- `OperatorStream<T>`: 带自动清理语义的包装流。

## 说明

- 内部 `ContextClosingStream` 会在“元素消费完毕”“显式 `close()`”“迭代期间抛出运行时异常/错误”三种情况下执行 `onClose`。
- 该包装器还使用 `Cleaner` 作为兜底，避免流对象被遗弃时遗留 operator context。
- 清理动作只会执行一次，适合配合 `Session.setCurrentOperatorId(null)` 这类幂等收尾逻辑。
