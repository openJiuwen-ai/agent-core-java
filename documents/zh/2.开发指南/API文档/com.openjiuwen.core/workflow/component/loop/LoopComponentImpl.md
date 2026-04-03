# com.openjiuwen.core.workflow.component.loop.LoopComponentImpl

## 类 LoopComponentImpl

```java
public class LoopComponentImpl extends WorkflowComponent implements LoopComponent
```

循环组件实现，根据 `LoopInput` 组装条件、回调和底层 `AdvancedLoopComponentImpl`。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LoopComponentImpl(LoopGroup loopGroup, Map<String, Object> outputSchema)` | 创建 `LoopComponentImpl` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 执行当前组件的运行逻辑。 |
| `public boolean graphInvoker()` | 返回该类型是否通过图执行器调度。 |
| `public LoopGroup getLoop()` | 返回`loop` 字段。 |
| `public HasDrawable getLoopGroup()` | 返回`loopGroup` 字段。 |
