# com.openjiuwen.core.workflow.ComponentExecutionParams

## 类 ComponentExecutionParams

```java
public class ComponentExecutionParams
```

`ComponentExecutionParams` 封装单组件执行时需要的节点标识、session、执行器、输入、schema 与上下文。

## 关键字段

| 字段 | 说明 |
| --- | --- |
| `nodeId` | 当前节点 id。 |
| `session` | 节点级 `NodeSessionApi`。 |
| `executor` | 组件执行器。 |
| `inputs` | 组件输入。 |
| `inputsSchema` | 可选输入 schema。 |
| `outputsSchema` | 可选输出 schema。 |
| `context` | 可选 `ModelContext`。 |

## 方法

- 提供全参构造和只含基础参数的简化构造。
- 通过各 getter 暴露只读访问。
