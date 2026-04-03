# com.openjiuwen.core.retrieval.common.WeightedRankConfig

## 类 WeightedRankConfig

```java
public class WeightedRankConfig extends BaseRankConfig
```

按权重融合稠密与稀疏结果的配置对象。

## 说明

- 默认权重为 `denseName = 0.15`、`denseContent = 0.6`、`sparseContent = 0.25`。
- `getArgs()` 会忽略不大于 `0` 的权重，并把有效权重归一化后作为位置参数输出。
- 每个权重必须位于 `[0, 1]`。
