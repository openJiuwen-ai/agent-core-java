# com.openjiuwen.core.workflow.component.loop.LoopCallback

## 类 LoopCallback

```java
public abstract class LoopCallback extends AtomicNode
```

循环回调抽象基类，定义首轮、每轮开始/结束与出循环阶段事件。

## 字段

| 签名 | 说明 |
| --- | --- |
| `public static final String FIRST_LOOP = "first_in_loop"` | 首轮进入事件名。 |
| `public static final String START_ROUND = "start_round"` | 每轮开始事件名。 |
| `public static final String END_ROUND = "end_round"` | 每轮结束事件名。 |
| `public static final String OUT_LOOP = "out_loop"` | 出循环事件名。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void call(String loopStage, BaseSession session, Integer loopTimes)` | 按循环阶段分发回调。 |
| `public void call(String loopStage, BaseSession session)` | 按循环阶段分发回调。 |
| `public abstract Object firstInLoop(BaseSession session)` | 在第一轮循环开始前执行。 |
| `public abstract Object outLoop(BaseSession session)` | 在循环退出时执行。 |
| `public abstract Object startRound(BaseSession session)` | 在每轮循环开始时执行。 |
| `public abstract Object endRound(BaseSession session, int loopTimes)` | 在每轮循环结束时执行。 |
