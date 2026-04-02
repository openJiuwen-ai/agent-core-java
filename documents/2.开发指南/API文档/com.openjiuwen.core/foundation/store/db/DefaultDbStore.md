# com.openjiuwen.core.foundation.store.db.DefaultDbStore

## class DefaultDbStore

```java
public class DefaultDbStore extends BaseDbStore<DataSource>
```

Lightweight JDBC-backed default DB store.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `dataSource` | `final DataSource` | `-` | Data source. |
| `DataSource` | `static final class SimpleDriverManagerDataSource implements` | `-` | Data source. |

## Constructors

| Signature | Description |
| --- | --- |
| `public DefaultDbStore(String jdbcUrl)` | Create a new `DefaultDbStore` instance. |
| `public DefaultDbStore(String jdbcUrl, String username, String password)` | Create a new `DefaultDbStore` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public DataSource getEngine()` | Return the engine. |
