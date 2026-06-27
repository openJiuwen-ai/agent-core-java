# com.openjiuwen.core.foundation.store.vector_fields.MilvusIVF

## class MilvusIVF

```java
public class MilvusIVF extends MilvusVectorField
```

Milvus IVF 索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |
| `nlist` | `int` | `128` | IVF 聚类数。 |
| `nprobe` | `int` | `8` | 搜索探测数。 |
| `variant` | `String` | `"FLAT"` | 变体，支持 `FLAT`、`SQ8`、`PQ`、`RABITQ`。 |
| `extraConstruct` | `Map<String, Object>` | `{}` | 附加构建参数。 |
| `extraSearch` | `Map<String, Object>` | `{}` | 附加搜索参数。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型 `ivf`。 |
| `public int getNlist()` | 返回 `nlist`。 |
| `public void setNlist(int nlist)` | 设置 `nlist`。 |
| `public int getNprobe()` | 返回 `nprobe`。 |
| `public void setNprobe(int nprobe)` | 设置 `nprobe`。 |
| `public String getVariant()` | 返回变体名称。 |
| `public void setVariant(String variant)` | 设置变体名称。 |
| `public Map<String, Object> getExtraConstruct()` | 返回附加构建参数。 |
| `public void setExtraConstruct(Map<String, Object> extraConstruct)` | 设置附加构建参数。 |
| `public Map<String, Object> getExtraSearch()` | 返回附加搜索参数。 |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | 设置附加搜索参数。 |
| `public void validate()` | 校验当前参数组合。 |
| `public Map<String, Object> toDict(String stage)` | 按阶段导出参数。 |
