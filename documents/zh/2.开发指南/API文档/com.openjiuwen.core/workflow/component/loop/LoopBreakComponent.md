# com.openjiuwen.core.workflow.component.loop.LoopBreakComponent

## 类 LoopBreakComponent

```java
public class LoopBreakComponent extends WorkflowComponent
```

循环中断组件，在执行时通知 `LoopController` 结束当前循环。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void setController(LoopController loopController)` | 设置`controller` 字段。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 执行当前组件的运行逻辑。 |
