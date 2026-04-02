# com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO

## class MilvusAUTO

```java
public class MilvusAUTO extends MilvusVectorField
```

Milvus AUTO 索引配置对象。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getIndexType()` | 返回索引类型 `auto`。 |
| `public Map<String, Object> toDict(String stage)` | 导出当前阶段参数；当前实现返回空配置。 |
