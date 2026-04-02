# com.openjiuwen.core.foundation.store.StoreFactory

## class StoreFactory

```java
public final class StoreFactory
```

foundation 层向量存储工厂。它根据短名称创建内建的向量存储适配器。

## 构造说明

- 构造方法为私有，不能直接实例化。

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public static BaseVectorStore createVectorStore(String storeType)` | 使用空 `options` 创建向量存储。 |
| `public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> options)` | 按 `storeType` 和附加参数创建向量存储。 |

## 支持的类型

| `storeType` | 返回实现 |
| --- | --- |
| `in_memory`, `memory` | `InMemoryVectorStore` |
| `chroma` | `ChromaVectorStore` |
| `milvus` | `MilvusVectorStore` |
| `pgvector`, `pg` | `PGVectorStore` |

## 使用说明

- `storeType == null` 时返回 `null`。
- 未识别的 `storeType` 同样返回 `null`，不会主动抛异常。
