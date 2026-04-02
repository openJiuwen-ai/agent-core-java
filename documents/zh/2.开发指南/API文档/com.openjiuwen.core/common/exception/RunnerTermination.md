# com.openjiuwen.core.common.exception.RunnerTermination

## 类 RunnerTermination

```java
public class RunnerTermination extends Termination
```

`RunnerTermination` 是 `Termination` 的特化类型，会额外保存一个 `reason` 字符串。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `reason` | `String` | 终止原因文本。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public RunnerTermination(String reason, StatusCode status, Map<String, Object> params)` | 保存终止原因、状态码与模板参数。 |
| `public RunnerTermination(String reason, StatusCode status)` | 仅保存终止原因与状态码。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getReason()` | 返回构造时保存的终止原因。 |

## 说明

- 该类型沿用 `Termination` 的默认语义：`isRecoverable()` 为 `false`，`isFatal()` 为 `false`。
- `ErrorTest` 验证 `getReason()` 会原样返回构造时传入的文本。
