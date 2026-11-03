# guardrail

`com.openjiuwen.core.security.guardrail` 提供基于 Runner 回调框架的安全护栏能力。当前 Java 0.1.14 的主线与 Python 文档一致：护栏监听模型调用、工具调用等事件，把事件内容交给可插拔后端分析，再根据风险等级放行或阻断。

## Types

| 类型 | 说明 |
| --- | --- |
| [`BaseGuardrail`](./guardrail/BaseGuardrail.md) | 护栏抽象基类，负责事件监听、回调注册、风险分析委托和阻断异常抛出。 |
| [`GuardrailBackend`](./guardrail/GuardrailBackend.md) | 风险分析后端抽象类，通过 `analyze(GuardrailContext ctx)` 返回 `RiskAssessment`。 |
| `GuardrailContext` | 检测上下文，封装内容类型、内容、事件名和元数据。 |
| [`GuardrailResult`](./guardrail/GuardrailResult.md) | 护栏最终判定结果，描述是否安全、风险级别、风险类型和补充详情。 |
| [`PromptInjectionGuardrail`](./guardrail/PromptInjectionGuardrail.md) | 内置提示词注入护栏，默认监听 LLM 输入和工具输出事件。 |
| [`PromptInjectionGuardrailConfig`](./guardrail/PromptInjectionGuardrailConfig.md) | 提示词注入护栏配置，支持 `rules`、`api` 和 `local` 三种模式。 |
| [`RiskAssessment`](./guardrail/RiskAssessment.md) | 后端分析结果对象，包含 `hasRisk`、`riskLevel`、`riskType`、`confidence` 和详情。 |
| [`RiskLevel`](./guardrail/RiskLevel.md) | 风险严重级别枚举。 |
| [`RuleBasedPromptInjectionBackend`](./guardrail/RuleBasedPromptInjectionBackend.md) | 基于正则规则的提示词注入检测后端。 |
| [`APIModelBackend`](./guardrail/APIModelBackend.md) | 调用远端模型 API 的检测后端。 |
| [`LocalModelBackend`](./guardrail/LocalModelBackend.md) | 本地模型检测后端入口；当前需要应用侧接入推理运行时。 |
| [`LLMPromptInjectionBackend`](./guardrail/LLMPromptInjectionBackend.md) | LLM 检测形态的提示词注入后端；当前 Java 实现保留规则回退路径。 |

## Notes

- `PromptInjectionGuardrail.DEFAULT_EVENTS` 为 `LLMCallEvents.LLM_INVOKE_INPUT` 和 `ToolCallEvents.TOOL_INVOKE_OUTPUT`。
- `BaseGuardrail.register(...)` 接收 `DecoratorFramework`，实际常用入口是 `Runner.getCallbackFramework()` 返回的 `AsyncCallbackFramework`。
- `RiskLevel.CRITICAL` 会触发 `AbortError`；其他风险级别会触发 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, ...)`。
- 当前源码没有 `UserInputGuardrail.java`；旧页面保留为过期说明，不应作为 0.1.14 API 入口。
