# com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW

## class MilvusHNSW

```java
public class MilvusHNSW extends MilvusVectorField
```

Milvus HNSW 索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |
| `m` | `int` | `30` | HNSW 的 `M` 参数。 |
| `efConstruction` | `int` | `360` | 构建参数。 |
| `efSearchFactor` | `Float` | `null` | 搜索阶段因子。 |
| `variant` | `String` | `null` | 可选变体，支持 `SQ`、`PQ`、`PRQ`。 |
| `extraConstruct` | `Map<String, Object>` | `{}` | 附加构建参数。 |
| `extraSearch` | `Map<String, Object>` | `{}` | 附加搜索参数。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型 `hnsw`。 |
| `public int getM()` | 返回 `M` 参数。 |
| `public void setM(int m)` | 设置 `M` 参数。 |
| `public int getEfConstruction()` | 返回构建参数。 |
| `public void setEfConstruction(int efConstruction)` | 设置构建参数。 |
| `public Float getEfSearchFactor()` | 返回搜索因子。 |
| `public void setEfSearchFactor(Float efSearchFactor)` | 设置搜索因子。 |
| `public String getVariant()` | 返回变体名称。 |
| `public void setVariant(String variant)` | 设置变体名称。 |
| `public Map<String, Object> getExtraConstruct()` | 返回附加构建参数。 |
| `public void setExtraConstruct(Map<String, Object> extraConstruct)` | 设置附加构建参数。 |
| `public Map<String, Object> getExtraSearch()` | 返回附加搜索参数。 |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | 设置附加搜索参数。 |
| `public void validate()` | 校验当前参数组合。 |
| `public Map<String, Object> toDict(String stage)` | 按阶段导出参数。 |
