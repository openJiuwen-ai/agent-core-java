# com.openjiuwen.core.security.guardrail.RuleBasedPromptInjectionBackend

## class RuleBasedPromptInjectionBackend

```java
public class RuleBasedPromptInjectionBackend extends GuardrailBackend
```

基于正则规则检测提示词注入的后端。默认规则覆盖常见的“忽略之前指令”“暴露系统提示词”“扮演角色”等攻击表达。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `RuleBasedPromptInjectionBackend()` | 使用默认规则和 `RiskLevel.HIGH`。 |
| `RuleBasedPromptInjectionBackend(RuleBasedBackendConfig config)` | 从配置读取规则和风险级别。 |
| `RuleBasedPromptInjectionBackend(List<String> patterns, RiskLevel riskLevel)` | 直接传入规则和风险级别。 |

## Methods

### `RiskAssessment analyze(GuardrailContext ctx)`

从上下文提取文本，逐条执行正则匹配。命中时返回：

- `hasRisk = true`
- `riskLevel = configured risk level`
- `riskType = "prompt_injection"`
- `details.matched_pattern = <命中的规则>`

未命中时返回 `RiskLevel.SAFE`。
