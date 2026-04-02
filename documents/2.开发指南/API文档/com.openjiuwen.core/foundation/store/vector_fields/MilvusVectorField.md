# com.openjiuwen.core.foundation.store.vector_fields.MilvusVectorField

## abstract class MilvusVectorField

```java
public abstract class MilvusVectorField extends VectorField
```

Milvus 向量索引配置的抽象基类。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public String getDatabaseType()` | 返回数据库类型 `milvus`。 |

## 说明

- 该类型不可直接实例化，通常通过 `MilvusAUTO`、`MilvusFLAT`、`MilvusHNSW`、`MilvusIVF`、`MilvusSCANN` 等子类使用。
