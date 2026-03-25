# Security 模块 API 文档

> 包路径：`com.openjiuwen.core.security.guardrail`

`security` 顶层包当前只包含 Guardrail 相关能力。`com.openjiuwen.core.common.security` 下的通用安全工具类仍归入 `common.md`，这里不重复展开。

---

## 目录

- [1. Guardrail 抽象](#1-guardrail-抽象)
- [2. 风险分析与结果模型](#2-风险分析与结果模型)
- [3. 内置 Guardrail](#3-内置-guardrail)

---

## 1. Guardrail 抽象

### 1.1 BaseGuardrail

Guardrail 抽象基类，用于把风险检测逻辑挂接到 `CallbackFramework` 的事件回调链上。

**源码位置**：`com.openjiuwen.core.security.guardrail.BaseGuardrail`

**构造方法**

```java
protected BaseGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging)
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `protected abstract List<String> defaultEvents()` | `List<String>` | 返回默认监听事件列表，由子类实现 |
| `public List<String> listenEvents()` | `List<String>` | 返回当前监听事件的副本 |
| `public BaseGuardrail withEvents(List<String> events)` | `BaseGuardrail` | 覆盖监听事件列表，支持链式调用 |
| `public BaseGuardrail setBackend(GuardrailBackend backend)` | `BaseGuardrail` | 动态替换风险分析后端 |
| `public GuardrailBackend getBackend()` | `GuardrailBackend` | 获取当前后端 |
| `public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception` | `GuardrailResult` | 统一检测入口；默认把事件数据整理成 `analysisData` 后交给 `backend.analyze()` |
| `public void register(CallbackFramework framework)` | `void` | 将 Guardrail 注册到回调框架，并为每个事件绑定 ERROR hook 与检测回调 |
| `public void unregister()` | `void` | 从已注册的 `CallbackFramework` 中撤销回调 |

**行为说明**

- 若未提供 `backend` 且子类未覆写 `detect()`，默认实现会抛出 `IllegalStateException`。
- `register()` 会在检测结果为不安全时抛出 `GuardrailError(StatusCode.GUARDRAIL_BLOCKED, params)`。
- `params` 中至少包含 `risk_type`、`risk_level`、`event`，并会透传 `GuardrailResult.details` 中的字段。

### 1.2 GuardrailBackend

可插拔的风险分析后端接口。

**源码位置**：`com.openjiuwen.core.security.guardrail.GuardrailBackend`

```java
@FunctionalInterface
public interface GuardrailBackend {
    RiskAssessment analyze(Map<String, Object> data) throws Exception;
}
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `analyze(Map<String, Object> data) throws Exception` | `RiskAssessment` | 对事件数据进行风险分析并返回评估结果 |

---

## 2. 风险分析与结果模型

### 2.1 GuardrailResult

Guardrail 最终输出对象，使用 Lombok `@Value` + `@Builder` 定义不可变结果模型。

**源码位置**：`com.openjiuwen.core.security.guardrail.GuardrailResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| `isSafe` | `boolean` | 当前输入/事件是否可放行 |
| `riskLevel` | `RiskLevel` | 风险等级；通过 `pass()` 创建时固定为 `SAFE` |
| `riskType` | `String` | 风险类别标识 |
| `details` | `Map<String, Object>` | 风险细节或补充信息 |
| `modifiedData` | `Map<String, Object>` | 后端返回的修正后数据；当前内置实现通常为 `null` |

**显式静态方法**

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public static GuardrailResult pass(Map<String, Object> details)` | `GuardrailResult` | 构造放行结果，`isSafe=true`、`riskLevel=SAFE` |
| `public static GuardrailResult pass()` | `GuardrailResult` | 无附加细节的放行结果 |
| `public static GuardrailResult block(RiskLevel riskLevel, String riskType, Map<String, Object> details, Map<String, Object> modifiedData)` | `GuardrailResult` | 构造拦截结果，`isSafe=false` |

**Lombok 生成方法**

- 按字段生成只读 getter
- Builder：`builder()`

### 2.2 RiskAssessment

风险分析后端返回的原始评估结果，同样使用 Lombok `@Value` + `@Builder`。

**源码位置**：`com.openjiuwen.core.security.guardrail.RiskAssessment`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `hasRisk` | `boolean` | - | 是否检测到风险 |
| `riskLevel` | `RiskLevel` | `RiskLevel.SAFE` | 风险等级 |
| `riskType` | `String` | - | 风险类型 |
| `confidence` | `double` | `0.0` | 评估置信度 |
| `details` | `Map<String, Object>` | - | 风险细节 |

**Lombok 生成方法**

- 按字段生成只读 getter
- Builder：`builder()`

### 2.3 RiskLevel

风险等级枚举。

**源码位置**：`com.openjiuwen.core.security.guardrail.RiskLevel`

**枚举常量**

- `SAFE`
- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `public String getValue()` | `String` | 返回枚举对应的小写字符串值 |

---

## 3. 内置 Guardrail

### 3.1 UserInputGuardrail

内置的用户输入 Guardrail，默认监听 `user_input` 事件。

**源码位置**：`com.openjiuwen.core.security.guardrail.UserInputGuardrail`

**继承**：`BaseGuardrail`

**构造方法**

```java
public UserInputGuardrail()
public UserInputGuardrail(GuardrailBackend backend, List<String> events, boolean enableLogging)
```

| 方法签名 | 返回类型 | 说明 |
|---|---|---|
| `protected List<String> defaultEvents()` | `List<String>` | 返回默认监听事件 `List.of("user_input")` |
| `public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception` | `GuardrailResult` | 针对用户输入做预处理；必要时再委托给父类默认实现 |

**行为说明**

- 当 `kwargs.get("text")` 不是非空字符串时，直接返回 `GuardrailResult.pass(Map.of("empty_input", true))`。
- 当 `backend == null` 时，直接放行，不调用父类默认检测逻辑。
- 其余情况会复用 `BaseGuardrail.detect()` 的通用分析流程。
