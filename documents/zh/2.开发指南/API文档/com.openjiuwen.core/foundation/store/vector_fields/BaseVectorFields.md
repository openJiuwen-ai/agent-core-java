# com.openjiuwen.core.foundation.store.vector_fields.BaseVectorFields

## class BaseVectorFields

```java
public final class BaseVectorFields
```

默认向量 schema 生成工具。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static CollectionSchema defaultSchema(String vectorFieldName, int dimension)` | 生成默认 schema，包含 `id`、向量字段、`text`、`metadata` 四个字段。 |
