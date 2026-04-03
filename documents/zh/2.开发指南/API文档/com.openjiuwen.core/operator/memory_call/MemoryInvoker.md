# com.openjiuwen.core.operator.memory_call.MemoryInvoker

## interface MemoryInvoker

```java
public interface MemoryInvoker
```

`MemoryInvoker` 是面向非标准 memory 组件的回调接口，用于把自定义记忆调用逻辑接入 `MemoryCallOperator`。

## 核心方法

### `Object invoke(Map<String, Object> inputs) throws Exception`

执行一次自定义记忆调用。

**参数**

- `inputs`: 算子收到的输入映射。

**返回**

- `Object`: 记忆调用结果，由上层调用方自行解释。
