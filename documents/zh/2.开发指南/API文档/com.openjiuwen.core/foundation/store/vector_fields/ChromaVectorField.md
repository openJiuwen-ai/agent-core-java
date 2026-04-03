# com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField

## class ChromaVectorField

```java
public class ChromaVectorField extends VectorField
```

Chroma HNSW 索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |
| `maxNeighbors` | `int` | `16` | HNSW 邻接数。 |
| `efConstruction` | `int` | `100` | 构建阶段参数。 |
| `efSearch` | `float` | `100` | 搜索阶段参数。 |
| `extraSearch` | `Map<String, Object>` | `{}` | 附加搜索参数。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public ChromaVectorField()` | 使用默认 Chroma HNSW 参数初始化。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDatabaseType()` | 返回数据库类型 `chroma`。 |
| `public String getIndexType()` | 返回索引类型 `hnsw`。 |
| `public int getMaxNeighbors()` | 返回邻接数。 |
| `public void setMaxNeighbors(int maxNeighbors)` | 设置邻接数。 |
| `public int getEfConstruction()` | 返回构建参数。 |
| `public void setEfConstruction(int efConstruction)` | 设置构建参数。 |
| `public float getEfSearch()` | 返回搜索参数。 |
| `public void setEfSearch(float efSearch)` | 设置搜索参数。 |
| `public Map<String, Object> getExtraSearch()` | 返回附加搜索参数。 |
| `public void setExtraSearch(Map<String, Object> extraSearch)` | 设置附加搜索参数。 |
| `public Map<String, Object> toDict(String stage)` | 按 `construct` 或 `search` 阶段导出参数。 |
