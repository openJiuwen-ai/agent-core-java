# com.openjiuwen.core.retrieval.common.StoreType

## 枚举 StoreType

```java
public enum StoreType
```

向量库提供方枚举。

## 枚举值

| 值 | 说明 |
| --- | --- |
| `MILVUS` | `milvus` 向量库。 |
| `CHROMA` | `chroma` 向量库。 |
| `PGVECTOR` | `pgvector` 向量库。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String value()` | 返回底层字符串值。 |
| `public static StoreType fromValue(String value)` | 从字符串解析枚举值。 |
