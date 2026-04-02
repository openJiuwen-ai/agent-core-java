# com.openjiuwen.core.retrieval.common.RetrievalValidation

## 类 RetrievalValidation

```java
public final class RetrievalValidation
```

retrieval 模块公共校验工具，集中校验空白字符串、正数、索引模式、距离度量、向量库类型与数据库名格式。

## 说明

- 内置常量集合：`INDEX_TYPES`、`DISTANCE_METRICS`、`STORE_TYPES`。
- `validateDatabaseName(...)` 允许空字符串，但非空时必须匹配 `^[A-Za-z0-9_]*$`。
