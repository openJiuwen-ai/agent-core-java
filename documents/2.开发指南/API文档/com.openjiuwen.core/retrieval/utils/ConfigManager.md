# com.openjiuwen.core.retrieval.utils.ConfigManager

## class ConfigManager

```java
public class ConfigManager
```

Unified configuration manager for retrieval module.

## Constructors

| Signature | Description |
| --- | --- |
| `public ConfigManager()` | Create a new `ConfigManager` instance. |
| `public ConfigManager(String configPath)` | Create a new `ConfigManager` instance. |

## Methods

| Signature | Description |
| --- | --- |
| `} public void loadFromFile(String path)` | Load from file. |
| `String lowerName = file.getFileName().toString().toLowerCase()` | Return the file name. |
| `else if (lowerName.endsWith(".yaml") || lowerName.endsWith(".yml"))` | Execute `if`. |

## Notes

- Related tests: `RetrievalCoreTest.java`.
