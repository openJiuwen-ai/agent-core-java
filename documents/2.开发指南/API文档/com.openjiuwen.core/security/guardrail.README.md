# guardrail

`com.openjiuwen.core.security.guardrail` 提供基于 `CallbackFramework` 事件回调的安全护栏能力，包括可插拔风险分析后端、风险评估结果对象和面向用户输入的默认实现。

## Types

| 类型 | 说明 |
|---|---|
| [`BaseGuardrail`](./guardrail/BaseGuardrail.md) | 护栏抽象基类，负责事件监听、回调注册、风险分析委托和阻断异常抛出。 |
| [`GuardrailBackend`](./guardrail/GuardrailBackend.md) | 风险分析后端函数式接口，接收事件数据并返回 `RiskAssessment`。 |
| [`GuardrailResult`](./guardrail/GuardrailResult.md) | 护栏最终判定结果，描述是否安全、风险级别、风险类型和补充详情。 |
| [`RiskAssessment`](./guardrail/RiskAssessment.md) | 后端分析结果对象，包含 `hasRisk`、`confidence` 和风险元数据。 |
| [`RiskLevel`](./guardrail/RiskLevel.md) | 风险严重级别枚举及其小写字符串值。 |
| [`UserInputGuardrail`](./guardrail/UserInputGuardrail.md) | 面向 `user_input` 事件的默认护栏实现，对空输入做快速放行。 |

## Notes

- `BaseGuardrail` 的默认 `detect()` 会把 `event`、位置参数和 `kwargs` 整理成 `Map<String, Object>` 后交给 `GuardrailBackend.analyze(...)`。
- 当检测结果不安全时，注册到 `CallbackFramework` 的回调会抛出 `GuardrailError`，状态码固定为 `StatusCode.GUARDRAIL_BLOCKED`。
- 当前任务范围没有配套测试文件，文档内容完全基于 `src/main/java/com/openjiuwen/core/security/guardrail` 下的公开源码。
