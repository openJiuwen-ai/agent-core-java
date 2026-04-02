# com.openjiuwen.core.workflow.ComponentExecutionHelper

## 类 ComponentExecutionHelper

```java
public final class ComponentExecutionHelper
```

`ComponentExecutionHelper` 用于脱离完整工作流图执行单个组件，适合测试或局部调试。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public static Map<String, Object> executeSingleComponent(ComponentExecutionParams params)` | 基于 `ComponentExecutionParams` 创建 `WorkflowSession`、`NodeSession` 与 `Vertex`，再执行组件并返回 `Map` 结果。 |

## 说明

- 返回值只有在组件结果为 `Map` 时才会返回对应字典，否则返回 `null`。
