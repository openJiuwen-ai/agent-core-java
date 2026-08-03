# com.openjiuwen.core.security.guardrail.GuardrailBackend

## abstract class GuardrailBackend

```java
public abstract class GuardrailBackend
```

`GuardrailBackend` 定义可插拔的风险分析协议。它对应 Python 文档中的检测后端概念，但在 Java 0.1.14 中是抽象类，不是函数式接口。

## Methods

### `public abstract RiskAssessment analyze(GuardrailContext ctx)`

分析一次护栏检测上下文并返回风险评估结果。

**参数**

- `ctx`: `GuardrailContext`，包含内容类型、待检测内容、事件名和元数据。

**返回**

- `RiskAssessment`: 后端分析结果。`hasRisk = false` 表示放行，`hasRisk = true` 表示护栏应阻断。

### `protected static String contextText(GuardrailContext ctx)`

从 `GuardrailContext` 提取文本内容。若上下文没有显式文本，则退回到 `content.toString()`；上下文为空时返回空字符串。

## 说明

- 后端只负责判断风险，不负责注册事件或抛出 `GuardrailError`。
- `BaseGuardrail.detect(...)` 会调用 `backend.analyze(ctx)`，再把 `RiskAssessment` 转成 `GuardrailResult`。
- 如需最小自定义后端，请继承本类并覆写 `analyze(GuardrailContext ctx)`。
