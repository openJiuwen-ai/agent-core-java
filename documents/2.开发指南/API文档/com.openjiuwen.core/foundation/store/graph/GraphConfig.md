# com.openjiuwen.core.foundation.store.graph.GraphConfig

## class GraphConfig

```java
public class GraphConfig
```

Configuration of Graph Store.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `LOGGER` | `static final Logger` | `Logger.getLogger(GraphConfig.class.getName())` | Logger. |
| `uri` | `final String` | `-` | Uri. |
| `name` | `final String` | `-` | Name. |
| `user` | `final String` | `-` | User. |
| `password` | `final String` | `-` | Password. |
| `token` | `final String` | `-` | Token. |
| `backend` | `final String` | `-` | Backend. |
| `timeout` | `final double` | `-` | Timeout. |
| `extras` | `final Map<String, Object>` | `-` | Extras. |
| `workerThreads` | `final int` | `-` | Worker threads. |
| `embedDim` | `final int` | `-` | Embed dim. |
| `embedBatchSize` | `final int` | `-` | Embed batch size. |
| `embeddingCls` | `final Class<? extends Embedding>` | `-` | Embedding cls. |
| `embeddingConfig` | `final EmbeddingConfig` | `-` | Embedding config. |
| `dbStorageConfig` | `final GraphStoreStorageConfig` | `-` | Db storage config. |
| `dbEmbedConfig` | `final GraphStoreIndexConfig` | `-` | Db embed config. |
| `wipeAtStartup` | `final boolean` | `-` | Wipe at startup. |
| `requestMaxRetries` | `final int` | `-` | Request max retries. |
| `requestRetryWait` | `final double` | `-` | Request retry wait. |

## Nested Types

| Declaration | Description |
| --- | --- |
| `public static class Builder` | Builder for configuring `GraphConfig` instances. |

## Constructors

| Signature | Description |
| --- | --- |
| `private GraphConfig(Builder builder)` | Create a new `GraphConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `private void checkValidity()` | Execute `checkValidity`. |
| `public String getUri()` | Return the uri. |
| `public String getName()` | Return the name. |
| `public String getUser()` | Return the user. |
| `public String getPassword()` | Return the password. |
| `public String getToken()` | Return the token. |
| `public String getBackend()` | Return the backend. |
| `public double getTimeout()` | Return the timeout. |
| `public Map<String, Object> getExtras()` | Return the extras. |
| `public int getWorkerThreads()` | Return the worker threads. |
| `public int getEmbedDim()` | Return the embed dim. |
| `public int getEmbedBatchSize()` | Return the embed batch size. |
| `public Class<? extends Embedding> getEmbeddingCls()` | Return the embedding cls. |
| `public EmbeddingConfig getEmbeddingConfig()` | Return the embedding config. |
| `public GraphStoreStorageConfig getDbStorageConfig()` | Return the db storage config. |
| `public GraphStoreIndexConfig getDbEmbedConfig()` | Return the db embed config. |
| `public boolean isWipeAtStartup()` | Return whether the wipe at startup is enabled. |
| `public int getRequestMaxRetries()` | Return the request max retries. |
| `public double getRequestRetryWait()` | Return the request retry wait. |
| `public static Builder builder()` | Build the configured result. |
