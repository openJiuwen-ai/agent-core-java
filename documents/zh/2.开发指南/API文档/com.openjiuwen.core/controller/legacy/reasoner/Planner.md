# com.openjiuwen.core.controller.legacy.reasoner.Planner

## interface Planner

```java
public interface Planner
```

`Planner` 是旧版任务规划接口。

## 方法

### `Task plan(IntentDetectionController.Intent intent, Session session)`

根据旧版 `Intent` 和会话生成一个待执行任务。

## 说明

- `AgentReasoner.plan()` 会把意图直接委托给实现类。
- `DefaultPlanner` 提供了默认实现。
