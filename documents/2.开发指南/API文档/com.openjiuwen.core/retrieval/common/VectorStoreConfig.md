# com.openjiuwen.core.retrieval.common.VectorStoreConfig

## class VectorStoreConfig

```java
public class VectorStoreConfig
```

Vector store configuration.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `storeProvider` | `String` | `-` | store provider. |
| `databaseName` | `String` | `""` | database name. |
| `collectionName` | `String` | `-` | collection name. |
| `distanceMetric` | `String` | `"cosine"` | distance metric. |

## Constructors

| Signature | Description |
| --- | --- |
| `public VectorStoreConfig()` | Create a new `VectorStoreConfig` instance. |
| `public VectorStoreConfig(String storeProvider, String collectionName)` | Create a new `VectorStoreConfig` instance. |
| `public VectorStoreConfig(StoreType storeProvider, String collectionName)` | Create a new `VectorStoreConfig` instance. |
| `public VectorStoreConfig(String storeProvider, String databaseName, String collectionName, String distanceMetric)` | Create a new `VectorStoreConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public void validate()` | Execute `validate`. |
| `public String getStoreProvider()` | Return the store provider. |
| `public StoreType getStoreType()` | Return the store type. |
| `public void setStoreProvider(String storeProvider)` | Update the store provider. |
| `public String getDatabaseName()` | Return the database name. |
| `public void setDatabaseName(String databaseName)` | Update the database name. |
| `public String getCollectionName()` | Return the collection name. |
| `public void setCollectionName(String collectionName)` | Update the collection name. |
| `public String getDistanceMetric()` | Return the distance metric. |
| `public void setDistanceMetric(String distanceMetric)` | Update the distance metric. |

## Notes

- Related tests: `ConfigTest.java`, `InMemoryIndexerTest.java`, `InMemoryVectorStoreTest.java`, `IndexerFactoryTest.java`, `MilvusIndexerTest.java`, `MilvusKnowledgeBaseTest.java`.
