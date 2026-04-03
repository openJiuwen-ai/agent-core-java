# com.openjiuwen.core.foundation.store.vector_fields.MilvusSCANN

## class MilvusSCANN

```java
public class MilvusSCANN extends MilvusVectorField
```

Milvus SCANN 索引配置对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vectorField` | `String` | `"embedding"` | 向量字段名。 |
| `nlist` | `int` | `128` | 聚类数。 |
| `nprobe` | `int` | `8` | 搜索探测数。 |
| `withRawData` | `boolean` | `true` | 是否保留原始数据。 |
| `reorderK` | `Integer` | `null` | 搜索阶段重排数量。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型 `scann`。 |
| `public int getNlist()` | 返回 `nlist`。 |
| `public void setNlist(int nlist)` | 设置 `nlist`。 |
| `public int getNprobe()` | 返回 `nprobe`。 |
| `public void setNprobe(int nprobe)` | 设置 `nprobe`。 |
| `public boolean isWithRawData()` | 返回是否保留原始数据。 |
| `public void setWithRawData(boolean withRawData)` | 设置是否保留原始数据。 |
| `public Integer getReorderK()` | 返回重排数量。 |
| `public void setReorderK(Integer reorderK)` | 设置重排数量。 |
| `public Map<String, Object> toDict(String stage)` | 按阶段导出参数。 |
