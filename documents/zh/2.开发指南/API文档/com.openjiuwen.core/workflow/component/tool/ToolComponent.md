# com.openjiuwen.core.workflow.component.tool.ToolComponent

## 类 ToolComponent

```java
public class ToolComponent implements ComponentComposable
```

Tool 组件封装类型，负责绑定 `Tool` 并生成 `ToolExecutable`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolComponent(ToolComponentConfig config)` | 创建 `ToolComponent` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Executable<?, ?> toExecutable()` | 构造并返回可执行节点。 |
| `public ToolComponent bindTool(Tool tool)` | 绑定要调用的 `Tool` 实例。 |
