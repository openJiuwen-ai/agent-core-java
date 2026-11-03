# com.openjiuwen.core.security.guardrail.LocalModelBackend

## class LocalModelBackend

```java
public class LocalModelBackend extends GuardrailBackend
```

`LocalModelBackend` 是本地模型检测后端入口。它保留了模型路径、parser、设备和模型生命周期状态，但当前 Java 0.1.14 没有真正接入本地推理运行时。

## Constructors

| 构造函数 | 说明 |
| --- | --- |
| `LocalModelBackend(LocalModelBackendConfig config)` | 从配置对象读取本地模型参数。 |
| `LocalModelBackend(String modelPath, ModelOutputParser parser, String device, String riskType)` | 直接指定模型路径、parser 和设备。 |

## Methods

### `RiskAssessment analyze(GuardrailContext ctx)`

- 上下文文本为空时返回安全结果。
- `parser == null` 时抛 `IllegalStateException`。
- 调用 `ensureModelLoaded()` 后执行 `inference(text)`，并把结果交给 parser。

### `cleanup()`

清理 `model`、`tokenizer` 和 `modelLoaded` 状态。

### `getModelInfo()`

返回 `model_path`、`device`、`model_loaded`、`has_model`、`has_tokenizer`。

## 当前边界

`loadModel()` 和 `inference(String text)` 当前都会抛 `UnsupportedOperationException`。如需实际本地推理，需要在应用侧继承并接入具体模型运行时。
