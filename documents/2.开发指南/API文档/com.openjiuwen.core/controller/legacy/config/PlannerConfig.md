# com.openjiuwen.core.controller.legacy.config.PlannerConfig

## class PlannerConfig

```java
public class PlannerConfig
```

`PlannerConfig` 是旧版 Planner 组件的占位配置类型。

## 说明

- 当前源码没有声明字段、构造方法或显式方法，是一个空的配置占位类型。
- `ReasonerConfig` 通过 `planner` 字段持有该类型实例，用来承载 legacy planner 的配置槽位。
- 如果后续 legacy Planner 增补配置项，应优先在此类上扩展。
