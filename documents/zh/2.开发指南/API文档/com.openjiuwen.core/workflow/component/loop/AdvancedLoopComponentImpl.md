# com.openjiuwen.core.workflow.component.loop.AdvancedLoopComponentImpl

## 类 AdvancedLoopComponentImpl

```java
public class AdvancedLoopComponentImpl extends Executable<Object, Object> implements LoopController, AdvancedLoopComponent
```

高级循环组件实现，负责条件判断、循环路由与循环体调度。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public AdvancedLoopComponentImpl( Object body, Object conditionParam, List<? extends LoopBreakComponent> breakNodes, List<LoopCallback> callbacks)` | 创建 `AdvancedLoopComponentImpl` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public boolean isBroken()` | 返回当前循环是否已被中断。 |
| `public void breakLoop()` | 将当前循环标记为中断。 |
| `public Object onInvoke(Object inputs, BaseSession session, Object... kwargs)` | 执行当前节点的运行逻辑。 |
| `public boolean graphInvoker()` | 返回该类型是否通过图执行器调度。 |
| `public boolean skipTrace()` | 返回执行时是否跳过 trace 记录。 |
| `public HasDrawable getBody()` | 返回循环体对象。 |
| `public Executable<Object, Object> getBodyExecutable()` | 返回循环体对应的 `Executable`。 |
| `public void registerCallback(LoopCallback callback)` | 注册循环回调。 |
