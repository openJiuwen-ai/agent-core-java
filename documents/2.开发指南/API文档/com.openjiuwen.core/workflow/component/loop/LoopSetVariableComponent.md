# com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent

## 类 LoopSetVariableComponent

```java
public class LoopSetVariableComponent extends WorkflowComponent
```

循环变量写回组件，根据映射规则把值写回父会话。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LoopSetVariableComponent(Map<String, Object> variableMapping)` | 创建 `LoopSetVariableComponent` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 执行当前组件的运行逻辑。 |
| `public static Object generateValue(NodeSessionApi session, Object value)` | 根据会话状态生成变量值。 |
| `public static Object generateOutput(String[] keys, Object value)` | 按键路径生成输出对象。 |
