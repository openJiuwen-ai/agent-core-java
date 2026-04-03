# com.openjiuwen.core.security.guardrail.GuardrailBackend

## interface GuardrailBackend

```java
@FunctionalInterface
public interface GuardrailBackend
```

`GuardrailBackend` 定义可插拔的风险分析协议，供 `BaseGuardrail.detect()` 在接收到事件后委托执行。

## 函数式接口方法

### `RiskAssessment analyze(Map<String, Object> data) throws Exception`

分析一次事件数据并返回风险评估结果。

**参数**

- `data`: 待分析的事件上下文，通常至少包含 `event`、`args` 以及调用方透传的关键字段。

**返回**

- `RiskAssessment`: 风险评估结果；返回 `null` 时上层会被视为“未发现风险”。

**异常**

- `Exception`: 接口允许实现直接抛出异常；`BaseGuardrail.register()` 中的运行回调会把受检异常包装成 `RuntimeException`。

## 说明

- 因为是 `@FunctionalInterface`，可以直接使用 lambda 或方法引用实现。
- 后端只负责分析，不负责向 `CallbackFramework` 注册或抛出 `GuardrailError`；这些行为由 `BaseGuardrail` 统一处理。
