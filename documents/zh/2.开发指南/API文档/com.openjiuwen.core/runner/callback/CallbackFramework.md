# com.openjiuwen.core.runner.callback.CallbackFramework

## 过期页面

Java 0.1.14 源码中没有 `CallbackFramework.java`。早期文档把 Runner 回调框架主类写成 `CallbackFramework`，这是过期名称。

当前应使用：

- [`AsyncCallbackFramework`](./AsyncCallbackFramework.md)
- `DecoratorFramework`
- `Runner.getCallbackFramework()`
- `Runner.callbackFramework`

需要回调结果时使用 `AsyncCallbackFramework.triggerResults(...)`；`trigger(...)` 是 `DecoratorFramework` 兼容入口，返回 `void`。
