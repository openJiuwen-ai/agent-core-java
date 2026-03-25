# security 模块 Python / Java API 映射

## 对照范围

- Python: `agent-core-python/openjiuwen/core/security/**`
- Java: `agent-core-java/agent-core-java/src/main/java/com/openjiuwen/core/security/**`
- 统计口径:
  - Python 统计模块级导出、公开类、dataclass/enum 的公开字段、非下划线方法
  - Java 统计 `public`/`protected` 类型的公开方法，以及 Lombok 生成的 getter / builder 所承载的字段访问
- 命名约定:
  - `snake_case -> camelCase`
  - `property -> getter`
  - `async -> 同步方法`
  - `dataclass field -> getter / builder`

## 复核结论

- Java 侧 `com.openjiuwen.core.security.guardrail` 主体类型已经齐全，没有整类缺失。
- 主要差异集中在三类:
  - Python 包级导出门面在 Java 中改为“直接导入具体类”
  - Python `property` / `async` / dataclass 在 Java 中分别落为 getter、同步方法、Lombok 值对象
  - 少量行为细节已完成追平，详见 `docs/FIXED/security.md`
- 剩余差异仅为包级导出门面（Java 语言固有差异，影响较小）
- Python 内部 helper `_detect_callback()` 在 Java 中改为 `register()` 内联回调，不再单独暴露方法。

## 包级映射

| Python 模块 | Java 对应 | 状态 | 说明 |
| --- | --- | --- | --- |
| `openjiuwen.core.security.__init__` | 无单独 package facade | 适配映射 | Python 顶层 `__all__ = []`，本身不暴露类型；Java 也没有额外门面类，影响很小 |
| `openjiuwen.core.security.guardrail.__init__` | `com.openjiuwen.core.security.guardrail.*` | 适配映射 | Python 通过包级导出暴露 Guardrail 相关类型；Java 通过直接导入具体类使用 |
| `openjiuwen.core.security.guardrail.backends` | `com.openjiuwen.core.security.guardrail.GuardrailBackend` | 完全映射 | 后端风险分析接口已存在 |
| `openjiuwen.core.security.guardrail.enums` | `com.openjiuwen.core.security.guardrail.RiskLevel` | 完全映射 | 风险级别枚举已存在 |
| `openjiuwen.core.security.guardrail.models` | `GuardrailResult`、`RiskAssessment` | 完全映射 | 两个数据模型均已存在 |
| `openjiuwen.core.security.guardrail.guardrail` | `com.openjiuwen.core.security.guardrail.BaseGuardrail` | 完全映射 | Guardrail 抽象基类已存在 |
| `openjiuwen.core.security.guardrail.builtin` | `com.openjiuwen.core.security.guardrail.UserInputGuardrail` | 完全映射 | 内置用户输入 Guardrail 已存在 |

## 1. Guardrail 抽象与后端

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `GuardrailBackend` | `GuardrailBackend` | `analyze(data) -> analyze(Map<String, Object> data)` | 适配映射 | Python 是 `async abstractmethod`；Java 是同步 `@FunctionalInterface` |
| `BaseGuardrail` | `BaseGuardrail` | `DEFAULT_EVENTS -> defaultEvents()`；`__init__(backend=None, events=None, enable_logging=True) -> BaseGuardrail(GuardrailBackend, List<String>, boolean)`；`listen_events -> listenEvents()`；`with_events -> withEvents(...)`；`set_backend -> setBackend(...)`；`get_backend -> getBackend()`；`detect(event_name, *args, **kwargs) -> detect(String, Object[], Map<String, Object>)`；`register(framework) -> register(CallbackFramework)`；`unregister() -> unregister()`；`_detect_callback(...) -> register(...)` 内联回调 | 适配映射 | Python 用类属性 `DEFAULT_EVENTS` + `property` + `async`；Java 用抽象方法 `defaultEvents()` + 普通方法 + 同步回调来承载相同职责 |

### BaseGuardrail 关键对位说明

- Python `listen_events` 返回副本；Java `listenEvents()` 也返回新的 `ArrayList`，语义一致。
- Python 默认 `detect()` 会把 `event_name`、`args`、`kwargs` 整理为 `analysis_data` 后交给 `backend.analyze()`；Java `detect()` 也执行相同的数据拼装和后端委托。
- Python `register()` 会给每个事件注册 ERROR hook 和检测回调；Java `register()` 同样注册 ERROR hook，并在回调中抛出 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, ...)`。
- Python `_detect_callback()` 是单独内部方法；Java 把这段逻辑内联到了 `register()` 创建的 lambda 中。

## 2. 数据模型与枚举

| Python API | Java API | 方法 / 字段映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `GuardrailResult` | `GuardrailResult` | 字段 `is_safe/risk_level/risk_type/details/modified_data -> isSafe()/getRiskLevel()/getRiskType()/getDetails()/getModifiedData()`；`pass_(details) -> pass(details)`；`block(risk_level, risk_type, details, modified_data) -> block(riskLevel, riskType, details, modifiedData)` | 适配映射 | `pass_` 在 Java 中改名为 `pass`；Java 额外提供 `pass()` 重载和 `builder()` |
| `RiskAssessment` | `RiskAssessment` | 字段 `has_risk/risk_level/risk_type/confidence/details -> isHasRisk()/getRiskLevel()/getRiskType()/getConfidence()/getDetails()` | 适配映射 | Python dataclass 对位为 Java Lombok `@Value` + `@Builder` 值对象 |
| `RiskLevel` | `RiskLevel` | 枚举值 `SAFE/LOW/MEDIUM/HIGH/CRITICAL -> SAFE/LOW/MEDIUM/HIGH/CRITICAL`；Python `.value -> Java getValue()` | 完全映射 | 风险级别和字符串值都已对齐 |

### 数据模型补充说明

- Python dataclass 字段可直接访问；Java 通过 getter 暴露相同信息。
- Java `GuardrailResult` 和 `RiskAssessment` 通过 `builder()` 提供额外构造方式，属于 Java-only 补强，不是缺漏。
- Java `RiskLevel.getValue()` 对位 Python `Enum.value`。

## 3. 内置 Guardrail

| Python API | Java API | 方法映射 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| `UserInputGuardrail` | `UserInputGuardrail` | `DEFAULT_EVENTS -> defaultEvents()`；`UserInputGuardrail() -> UserInputGuardrail()`；`UserInputGuardrail(backend=None, events=None, enable_logging=True) -> UserInputGuardrail(GuardrailBackend, List<String>, boolean)`；`detect(event_name, *args, **kwargs) -> detect(String, Object[], Map<String, Object>)` | 适配映射 | 两边默认都监听 `user_input`，并在输入为空时直接放行 |

### UserInputGuardrail 行为对位

- Python 与 Java 都先读取 `text` 字段，再决定是直接放行还是委托给后端。
- 两边在有 backend 时都会复用 `BaseGuardrail.detect()` 的通用流程。
- 两边在 backend 为空时都会直接返回 pass 结果。
- 仍有一个细节差异（已修复）:
  - ~~Python 只有"空字符串 / `None` / 非字符串"才判为 `empty_input`~~
  - ~~Java 额外把"仅空白字符"的字符串也判为 `empty_input`~~
  - 已将 Java `isBlank()` 改为 `isEmpty()`，语义已对齐

## 4. 包级导出与跨目录对位

| Python 导出 / 类型 | Java 对位 | 状态 | 说明 |
| --- | --- | --- | --- |
| `guardrail.__all__` 暴露 `RiskLevel`、`GuardrailResult`、`RiskAssessment`、`GuardrailBackend`、`BaseGuardrail`、`UserInputGuardrail` | 直接导入 `com.openjiuwen.core.security.guardrail` 下具体类 | 适配映射 | Java 没有 package facade，需要显式导入具体类 |
| `guardrail.__all__` 暴露 `GuardrailError` | `com.openjiuwen.core.common.exception.GuardrailError` | 跨目录对位 | Java 对位类型存在，但不在 `com.openjiuwen.core.security.guardrail` 包下 |

## 5. 不计入公开 API 缺漏的内部差异

- Python `BaseGuardrail` 的 `_registered_events`、`_registered_callbacks`、`_framework` 在 Java 中分别由 `events`、`registeredCallbacks`、`framework` 等内部状态承载。
- Python `_detect_callback()` 在 Java 中被内联到 `register()` 里，不再保留独立方法名。
- Python `async` 调用栈在 Java 中系统性改为同步异常传播，这里按“语言适配”处理，不单独判为缺漏。

## 6. 总结

- 按“类职责 + 公开 API”对照，Java `security.guardrail` 已经具备完整主体映射。
- 当前仍需继续追齐的点主要不是“缺类”，而是包级门面、少量公开可见性差异和个别行为细节差异。
- 剩余缺口已汇总到 `docs/FIXED/security.md`。
