# com.openjiuwen.core.retrieval.common.StoreType

## enum StoreType

```java
public enum StoreType
```

Supported vector store providers.

## Enum Values

| Value | Serialized Form |
| --- | --- |
| `MILVUS` | `MILVUS` |
| `CHROMA` | `CHROMA` |
| `PGVECTOR` | `PGVECTOR` |

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `value` | `final String` | value. |

## Constructors

| Signature | Description |
| --- | --- |
| `StoreType(String value)` | Create a new `StoreType` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public String value()` | Execute `value`. |
| `public static StoreType fromValue(String value)` | Execute `fromValue`. |
| `} throw RetrievalExceptions.validation("unsupported store type: " + value)` | Execute `validation`. |

## Notes

- Related tests: `ConfigTest.java`, `VectorStoreFactoryTest.java`.
