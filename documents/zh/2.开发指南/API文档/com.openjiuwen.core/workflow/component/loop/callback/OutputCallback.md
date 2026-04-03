# com.openjiuwen.core.workflow.component.loop.callback.OutputCallback

## 类 OutputCallback

```java
public class OutputCallback extends LoopCallback
```

循环回调实现，按输出 schema 聚合每轮结果并写回会话。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OutputCallback(Map<String, Object> outputsFormat, String roundResultRoot, String resultRoot)` | 创建 `OutputCallback` 实例。 |
| `public OutputCallback(Map<String, Object> outputsFormat)` | 创建 `OutputCallback` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object firstInLoop(BaseSession session)` | 在第一轮循环开始前执行。 |
| `public Object outLoop(BaseSession session)` | 在循环退出时执行。 |
| `public Object startRound(BaseSession session)` | 在每轮循环开始时执行。 |
| `public Object endRound(BaseSession session, int loopTimes)` | 在每轮循环结束时执行。 |
