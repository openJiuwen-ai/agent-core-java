# com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig

## class GraphStoreStorageConfig

```java
public class GraphStoreStorageConfig
```

Graph Database Storage Limits.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `uuid` | `final int` | `-` | Uuid. |
| `name` | `final int` | `-` | Name. |
| `content` | `final int` | `-` | Content. |
| `language` | `final int` | `-` | Language. |
| `userId` | `final int` | `-` | User id. |
| `entities` | `final int` | `-` | Entities. |
| `relations` | `final int` | `-` | Relations. |
| `episodes` | `final int` | `-` | Episodes. |
| `objType` | `final int` | `-` | Obj type. |

## Nested Types

| Declaration | Description |
| --- | --- |
| `public static class Builder` | Builder for configuring `GraphStoreStorageConfig` instances. |

## Constructors

| Signature | Description |
| --- | --- |
| `private GraphStoreStorageConfig(Builder builder)` | Create a new `GraphStoreStorageConfig` instance. |
| `public GraphStoreStorageConfig()` | Create a new `GraphStoreStorageConfig` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public int getUuid()` | Return the uuid. |
| `public int getName()` | Return the name. |
| `public int getContent()` | Return the content. |
| `public int getLanguage()` | Return the language. |
| `public int getUserId()` | Return the user id. |
| `public int getEntities()` | Return the entities. |
| `public int getRelations()` | Return the relations. |
| `public int getEpisodes()` | Return the episodes. |
| `public int getObjType()` | Return the obj type. |
| `public static Builder builder()` | Build the configured result. |
