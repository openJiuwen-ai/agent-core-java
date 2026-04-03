# com.openjiuwen.core.runner.callback.CallbackChain

## class CallbackChain

```java
public class CallbackChain
```

Manages sequential execution of callbacks with rollback support.

## 字段

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `name` | `String` | `-` | - |
| `callbacks` | `List<CallbackInfo>` | `new ArrayList<>()` | - |
| `rollbackHandlers` | `Map<Function<Map<String, Object>, Object>, Consumer<ChainContext>>` | `new HashMap<>()` | - |
| `errorHandlers` | `Map<Function<Map<String, Object>, Object>, Function<ExceptionContext, Object>>` | `new HashMap<>()` | - |

## 构造方法

| Signature | Description |
| --- | --- |
| `public CallbackChain(String name)` | - |

## 方法

| Signature | Description |
| --- | --- |
| `public String getName()` | - |
| `public List<CallbackInfo> getCallbacks()` | - |
| `public void add(CallbackInfo callbackInfo, Consumer<ChainContext> rollbackHandler, Function<ExceptionContext, Object> errorHandler)` | Add callback to the chain. |
| `public void remove(Function<Map<String, Object>, Object> callback)` | Remove callback from the chain. |
| `public ChainResult execute(ChainContext context)` | Execute the callback chain. |

## 嵌套类型

- `ExceptionContext`: Context passed to error handlers: the exception + the chain context.

## 相关测试

- `CallbackChainTest`
