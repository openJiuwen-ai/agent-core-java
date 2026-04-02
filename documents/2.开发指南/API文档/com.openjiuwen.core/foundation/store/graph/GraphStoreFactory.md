# com.openjiuwen.core.foundation.store.graph.GraphStoreFactory

## class GraphStoreFactory

```java
public final class GraphStoreFactory
```

Factory class to assemble graph store instances.

## Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `CLASS_MAP` | `static final Map<String, Class<? extends GraphStore>>` | `new ConcurrentHashMap<>()` | Class map. |
| `LOCK` | `static final ReentrantLock` | `new ReentrantLock()` | Lock. |

## Constructors

| Signature | Description |
| --- | --- |
| `private GraphStoreFactory()` | Create a new `GraphStoreFactory` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static void registerBackend(String name, Class<? extends GraphStore> backend, boolean force)` | Register a graph store backend. |
| `public static void registerBackend(String name, Class<? extends GraphStore> backend)` | Register a graph store backend (no force). |
| `public static GraphStore fromConfig(GraphConfig config, String backendName)` | Fetch a GraphStore instance by configuration. |
| `public static GraphStore fromConfig(GraphConfig config)` | Fetch a GraphStore instance by configuration using config's default backend. |
