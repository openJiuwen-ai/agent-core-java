# com.openjiuwen.core.workflow.component.loop.LoopController

## 接口 LoopController

```java
public interface LoopController
```

循环控制接口，定义查询和中断循环的能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `void breakLoop()` | 将当前循环标记为中断。 |
| `boolean isBroken()` | 返回当前循环是否已被中断。 |
