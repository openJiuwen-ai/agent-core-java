# security 模块缺漏复核清单

## 复核口径

- 基线: `agent-core-python/openjiuwen/core/security/**`
- 对照: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/security/**`
- 本文只记录“仍未完全对齐的公开 API / 调用语义差异”
- 以下适配默认不计入缺漏:
  - `snake_case -> camelCase`
  - `property -> getter`
  - `async -> 同步方法`
  - dataclass 字段 -> getter / builder

## 复核结论

- `security.guardrail` 主体类型已经补齐，未发现新的“整类缺失”。
- 已修复 3 项缺漏:
  - `BaseGuardrail.enableLogging` 已增加公开 `isEnableLogging()` / `setEnableLogging(boolean)` 方法
  - `register()` 已改用完整重载，传入 `namespace="guardrail"` 和 `tags={"guardrail", <className>}`
  - `UserInputGuardrail.detect()` 已将 `isBlank()` 改为 `isEmpty()`，与 Python 空值判定语义对齐
- 当前剩余问题仅 1 类:
  - 包级导出门面未对齐（属 Java 语言适配，影响较小）

## 当前仍缺 / 未完全对齐的部分

| 优先级 | 位置 | Python 基线 | Java 现状 | 影响 |
| --- | --- | --- | --- | --- |
| `P1` | `openjiuwen.core.security.guardrail.__init__` 包级门面 | 包级 `__all__` 可直接导出 `RiskLevel`、`GuardrailResult`、`RiskAssessment`、`GuardrailBackend`、`BaseGuardrail`、`UserInputGuardrail`、`GuardrailError` | Java 只能逐个导入具体类；`GuardrailError` 的对位类型也不在 `com.openjiuwen.core.security.guardrail` 包内，而在 `com.openjiuwen.core.common.exception` | Python 风格的“从包入口统一导入”无法原样迁移，文档和示例需要改写 |
| ~~`P1`~~ | ~~`BaseGuardrail.enable_logging`~~ | ~~Python `enable_logging` 是公开实例属性，构造后仍可读写~~ | ~~已修复: 增加 `isEnableLogging()` / `setEnableLogging(boolean)` 公开方法~~ | ~~已对齐~~ |
| ~~`P2`~~ | ~~`BaseGuardrail.register()` 回调元数据~~ | ~~Python 注册时固定写入 `priority=100`、`namespace="guardrail"`、`tags={"guardrail", <class_name>}`~~ | ~~已修复: 改用完整 `register()` 重载，传入 `namespace="guardrail"` 和 `Set.of("guardrail", getClass().getSimpleName())`~~ | ~~已对齐~~ |
| ~~`P2`~~ | ~~`UserInputGuardrail.detect()` 空输入判定~~ | ~~Python 仅在 `text` 为假值或非字符串时返回 `empty_input=True`~~ | ~~已修复: `isBlank()` 改为 `isEmpty()`，仅空白字符串不再被视作空输入~~ | ~~已对齐~~ |

## 不应再判成缺漏的部分

### 已经存在的类

- `GuardrailBackend`
- `BaseGuardrail`
- `GuardrailResult`
- `RiskAssessment`
- `RiskLevel`
- `UserInputGuardrail`

### 已经完成的 API 对位

- `BaseGuardrail.listen_events -> listenEvents()`
- `BaseGuardrail.with_events -> withEvents(...)`
- `BaseGuardrail.set_backend -> setBackend(...)`
- `BaseGuardrail.get_backend -> getBackend()`
- `BaseGuardrail.enable_logging -> isEnableLogging() / setEnableLogging(boolean)` *(本次修复)*
- `BaseGuardrail.detect(...) -> detect(String, Object[], Map<String, Object>)`
- `BaseGuardrail.register(...) -> register(CallbackFramework)` *(本次修复: namespace/tags 已对齐)*
- `BaseGuardrail.unregister() -> unregister()`
- `GuardrailBackend.analyze(...) -> analyze(Map<String, Object>)`
- `GuardrailResult.pass_ -> GuardrailResult.pass`
- `GuardrailResult.block -> GuardrailResult.block`
- `RiskLevel.<enum>.value -> RiskLevel.getValue()`
- `UserInputGuardrail.DEFAULT_EVENTS -> defaultEvents()`
- `UserInputGuardrail.detect() 空值语义 -> isEmpty()` *(本次修复)*

### 属于语言适配、不计入缺漏

- Python `DEFAULT_EVENTS` 类属性在 Java 中改为覆写 `defaultEvents()`
- Python dataclass 在 Java 中改为 Lombok `@Value` + `@Builder`
- Python `async` 方法在 Java 中统一落为同步方法
- Python 内部 helper `_detect_callback()` 在 Java 中内联进 `register()`，不计入公开 API 缺漏

## 建议优先级

1. ~~`P1`: `enableLogging` 的公开访问能力~~ → **已修复**
2. ~~`P2`: `register()` 的 namespace/tags 写入~~ → **已修复**
3. ~~`P2`: `UserInputGuardrail` 的空白字符串判定~~ → **已修复**
4. `P1`: 包级导出门面 — 属 Java 语言固有差异，Python `__all__` 在 Java 中无直接对应，影响较小，可视需要后续处理。
