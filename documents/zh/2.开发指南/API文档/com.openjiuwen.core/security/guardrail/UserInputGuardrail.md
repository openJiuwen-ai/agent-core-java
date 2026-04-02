# com.openjiuwen.core.security.guardrail.UserInputGuardrail

## class UserInputGuardrail

```java
public class UserInputGuardrail extends BaseGuardrail
```

`UserInputGuardrail` 是面向用户文本输入的默认护栏实现。它继承 `BaseGuardrail` 的注册与回调机制，并把默认监听事件固定为 `user_input`。

## 构造方法

### `public UserInputGuardrail()`

创建默认实例，相当于 `new UserInputGuardrail(null, null, true)`。

### `public UserInputGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging)`

按指定后端、事件列表和日志开关创建实例。

**参数**

- `backend`: 风险分析后端；传 `null` 时允许对象先创建、后绑定。
- `events`: 自定义监听事件列表；传 `null` 时退回到默认的 `user_input` 事件。
- `enableLogging`: 是否在 `register()` 成功后输出日志。

## 核心方法

| 方法 | 返回 | 说明 |
|---|---|---|
| `detect(String eventName, Object[] args, Map<String, Object> kwargs)` | `GuardrailResult` | 读取 `kwargs["text"]` 作为待检测文本，并在满足条件时委托父类检测。 |

## 默认行为

- `defaultEvents()` 固定返回 `List.of("user_input")`。
- 当 `kwargs` 中不存在 `text`，或 `text` 不是非空字符串时，直接返回 `GuardrailResult.pass(Map.of("empty_input", true))`。
- 当 `text` 非空但 `backend` 为空时，直接返回 `GuardrailResult.pass()`，不会抛出 `IllegalStateException`。
- 只有在 `text` 为非空字符串且 `backend` 已配置时，才会调用 `super.detect(eventName, args, kwargs)` 执行后端分析。

## 继承能力

- 通过 `withEvents(...)` 可以覆盖默认的 `user_input` 事件列表。
- 通过继承自 `BaseGuardrail` 的 `register()` / `unregister()` 将当前 guardrail 接入 `CallbackFramework`。
