# com.openjiuwen.core.workflow.component.Start

## 类 Start

```java
public class Start extends WorkflowComponent
```

`Start` 是工作流起始节点，`invoke(...)` 会把输入原样透传给后续节点。

## 说明

- `WorkflowTest` 中大多数流程都以 `Start` 作为入口节点。
