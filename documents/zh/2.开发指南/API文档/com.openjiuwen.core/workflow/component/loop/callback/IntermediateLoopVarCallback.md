# com.openjiuwen.core.workflow.component.loop.callback.IntermediateLoopVarCallback

## 类 IntermediateLoopVarCallback

```java
public class IntermediateLoopVarCallback extends LoopCallback
```

循环回调实现，将每轮中间变量写入指定根路径。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar, String intermediateLoopVarRoot)` | 创建 `IntermediateLoopVarCallback` 实例。 |
| `public IntermediateLoopVarCallback(Map<String, Object> intermediateLoopVar)` | 创建 `IntermediateLoopVarCallback` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object firstInLoop(BaseSession session)` | 在第一轮循环开始前执行。 |
| `public Object outLoop(BaseSession session)` | 在循环退出时执行。 |
| `public Object startRound(BaseSession session)` | 在每轮循环开始时执行。 |
| `public Object endRound(BaseSession session, int loopTimes)` | 在每轮循环结束时执行。 |
