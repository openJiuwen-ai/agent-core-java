# com.openjiuwen.core.workflow.component.tool.ToolExecutable

## 类 ToolExecutable

```java
public class ToolExecutable extends ComponentExecutable
```

Tool 组件执行器，负责校验输入、调用工具并包装输出。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ToolExecutable(ToolComponentConfig config)` | 创建 `ToolExecutable` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ToolExecutable setTool(Tool tool)` | 设置执行器使用的 `Tool` 实例。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 执行当前组件的运行逻辑。 |
