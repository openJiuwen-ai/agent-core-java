# com.openjiuwen.core.foundation.store.vector_fields.PGVectorField

## class PGVectorField

```java
public class PGVectorField extends VectorField
```

PGVector 索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |
| `indexType` | `String` | `"hnsw"` | 索引类型，支持 `hnsw` 与 `ivfflat`。 |
| `m` | `int` | `16` | HNSW 邻接参数。 |
| `efConstruction` | `int` | `64` | 构建参数。 |
| `efSearch` | `int` | `40` | 搜索参数。 |
| `lists` | `int` | `100` | IVF 列表数。 |
| `probes` | `int` | `1` | IVF 探测数。 |
| `extraSearch` | `Map<String, Object>` | `{}` | 附加搜索参数。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDatabaseType()` | 返回数据库类型 `pg`。 |
| `public String getIndexType()` | 返回索引类型。 |
| `public void setIndexType(String indexType)` | 设置索引类型。 |
| `public int getM()` | 返回 `m`。 |
| `public void setM(int m)` | 设置 `m`。 |
| `public int getEfConstruction()` | 返回构建参数。 |
| `public void setEfConstruction(int efConstruction)` | 设置构建参数。 |
| `public int getEfSearch()` | 返回搜索参数。 |
| `public void setEfSearch(int efSearch)` | 设置搜索参数。 |
| `public int getLists()` | 返回 `lists`。 |
| `public void setLists(int lists)` | 设置 `lists`。 |
| `public int getProbes()` | 返回 `probes`。 |
| `public void setProbes(int probes)` | 设置 `probes`。 |
| `public Map<String, Object> getExtraSearch()` | 返回附加搜索参数。 |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | 设置附加搜索参数。 |
| `public Map<String, Object> toDict(String stage)` | 按阶段导出参数。 |
