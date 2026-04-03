# com.openjiuwen.core.retrieval.common.RRFRankConfig

## 类 RRFRankConfig

```java
public class RRFRankConfig extends BaseRankConfig
```

RRF 融合配置，控制 `k` 值以及各路结果是否参与融合。

## 说明

- 默认 `name = "rrf"`、`higherIsBetter = true`、`k = 40`。
- `denseName`、`denseContent`、`sparseContent` 三个开关决定对应结果列表是否参与融合。
- `getArgs()` 仅导出 `k` 作为位置参数。
