# com.openjiuwen.core.foundation.store.query.ChromaQueryDialect

## class ChromaQueryDialect

```java
public final class ChromaQueryDialect
```

Chroma 查询方言定义提供器。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static QueryLanguageDefinition definition()` | 返回 Chroma 查询方言定义。 |

## 使用说明

- 该方言输出 `where` 与 `where_document` 两部分过滤结构。
- 当前支持 comparison、`in`、`and/or`、文本匹配等表达式。
- arithmetic、null check、JSON 嵌套字段与数组索引等表达式在当前实现中会被明确拒绝。
