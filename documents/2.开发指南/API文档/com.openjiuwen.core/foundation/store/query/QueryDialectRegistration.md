# com.openjiuwen.core.foundation.store.query.QueryDialectRegistration

## class QueryDialectRegistration

```java
public final class QueryDialectRegistration
```

Registers built-in query dialect implementations for Milvus and Chroma.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `registered` | `static volatile boolean` | `false` | Registered. |

## Constructors

| Signature | Description |
| --- | --- |
| `private QueryDialectRegistration()` | Create a new `QueryDialectRegistration` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void ensureRegistered()` | Register built-in query dialect implementations (idempotent). |
