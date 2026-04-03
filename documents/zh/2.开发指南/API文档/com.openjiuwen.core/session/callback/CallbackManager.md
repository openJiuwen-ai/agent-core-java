# com.openjiuwen.core.session.callback.CallbackManager

## 类 CallbackManager

```java
public class CallbackManager
```

负责注册回调处理器，并在事件触发时通过反射分派到对应方法。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public CallbackManager()` | 创建一个空的回调管理器。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void register(Map<String, BaseHandler> configs)` | 从 `handlerName -> handler` 映射中批量注册处理器，并缓存各处理器声明的触发事件。 |
| `public void trigger(String handlerClassName, String eventName, Map<String, Object> kwargs)` | 触发指定处理器上的某个事件；支持 `snake_case` 到 `camelCase` 的事件名兼容解析。 |
| `public BaseHandler getHandler(String handlerName)` | 按名称返回已注册的处理器。 |

## 说明

- 当处理器不存在或事件未注册时，源码会记录日志；事件未注册时还会抛出 `IllegalArgumentException`。
- 反射调用阶段如果目标方法抛出异常，`CallbackManager` 会尽量透传原始 `RuntimeException`。
