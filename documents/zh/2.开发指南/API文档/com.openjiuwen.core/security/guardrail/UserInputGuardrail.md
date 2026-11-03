# com.openjiuwen.core.security.guardrail.UserInputGuardrail

## 过期页面

Java 0.1.14 源码中没有 `UserInputGuardrail.java`。旧文档曾把它描述成默认用户输入护栏，这是从早期翻译遗留来的过期内容。

当前应使用：

- [`PromptInjectionGuardrail`](./PromptInjectionGuardrail.md)
- [`PromptInjectionGuardrailConfig`](./PromptInjectionGuardrailConfig.md)
- [`GuardrailBackend`](./GuardrailBackend.md)

如果需要监听自定义用户输入事件，请用 `PromptInjectionGuardrail(List<?> events, GuardrailBackend backend, boolean enableLogging)` 指定事件列表，或继承 `BaseGuardrail` 实现自己的上下文提取逻辑。
