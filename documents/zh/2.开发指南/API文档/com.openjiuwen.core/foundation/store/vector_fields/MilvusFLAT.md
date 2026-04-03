# com.openjiuwen.core.foundation.store.vector_fields.MilvusFLAT

## class MilvusFLAT

```java
public class MilvusFLAT extends MilvusVectorField
```

Milvus FLAT 索引配置对象。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型 `flat`。 |
| `public Map<String, Object> toDict(String stage)` | 导出当前阶段参数；当前实现返回空配置。 |
