# com.openjiuwen.core.foundation.store.vector_fields.VectorField

## abstract class VectorField

```java
public abstract class VectorField
```

向量索引配置抽象基类。

## 常量

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `STAGE_SEARCH` | `"search"` | 搜索阶段标识。 |
| `STAGE_CONSTRUCT` | `"construct"` | 构建阶段标识。 |

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getVectorField()` | 返回向量字段名。 |
| `public void setVectorField(String vectorField)` | 设置向量字段名。 |
| `public abstract String getDatabaseType()` | 返回目标数据库类型。 |
| `public abstract String getIndexType()` | 返回索引类型。 |
| `public abstract Map<String, Object> toDict(String stage)` | 按阶段导出配置。 |
