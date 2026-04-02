# com.openjiuwen.core.memory.manage.mem_model.DbModel

## class DbModel

```java
public final class DbModel
```

Database model: table definitions and creation logic.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `MEMORY_LOGGER` | `LoggerProtocol` | memory logger. |
| `USER_MESSAGE_TABLE` | `String` | user message table. |
| `SCOPE_USER_MAPPING_TABLE` | `String` | scope user mapping table. |
| `MEMORY_META_TABLE` | `String` | memory meta table. |
| `MEMORY_TABLES_CONFIG` | `String[][]` | Table configs for migration tracking. |

## Constructors

| Signature | Description |
| --- | --- |
| `private DbModel()` | Create a new `DbModel` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void createTables(BaseDbStore<?> dbStore)` | Create memory tables if they don't exist. |

## Notes

- Related tests: `DbModelTest.java`
