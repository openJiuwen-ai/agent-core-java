# com.openjiuwen.core.memory.common.KvPrefixRegistry

## class KvPrefixRegistry

```java
public final class KvPrefixRegistry
```

Registry for managing KV store key prefixes used by memory modules.

## Fields

| Field | Type | Description |
| --- | --- | --- |
| `INSTANCE` | `KvPrefixRegistry` | instance. |
| `allPrefixes` | `Set<String>` | all prefixes. |
| `currentPrefixes` | `Set<String>` | current prefixes. |

## Constructors

| Signature | Description |
| --- | --- |
| `private KvPrefixRegistry()` | Create a new `KvPrefixRegistry` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `public static KvPrefixRegistry getInstance()` | Execute `getInstance`. |
| `public synchronized void registerCurrent(String prefix)` | Register a current (active) key prefix used by a memory module. |
| `public synchronized void registerLegacy(String prefix)` | Register a legacy (deprecated) key prefix for migration detection. |
| `public synchronized Set<String> getAllPrefixes()` | Get all registered prefixes (both current and legacy). |
| `public synchronized void unregister(String prefix)` | Unregister a prefix from both current and all prefixes. |
