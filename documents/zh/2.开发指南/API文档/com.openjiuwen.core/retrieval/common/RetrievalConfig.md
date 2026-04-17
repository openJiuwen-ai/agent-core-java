# com.openjiuwen.core.retrieval.common.RetrievalConfig

## 类 RetrievalConfig

```java
public class RetrievalConfig
```

单次检索请求配置，控制返回条数、阈值、图检索开关、agentic 模式、图扩展开关与过滤条件。

## 说明

- 默认 `topK = 5`。
- `scoreThreshold` 可为空；若存在则必须为有限数值。
- `filters` 在读写时都会复制，避免外部直接共享内部 `Map`。
