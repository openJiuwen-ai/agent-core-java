# com.openjiuwen.core.retrieval.utils.ConfigManager

## 类 ConfigManager

```java
public class ConfigManager
```

retrieval 配置管理器，支持 `KnowledgeBaseConfig` 的加载、保存、查询与更新。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ConfigManager()` | 创建空配置管理器。 |
| `public ConfigManager(String configPath)` | 创建后立即从文件加载配置。 |
| `public void loadFromFile(String path)` | 从 JSON、YAML 或 YML 文件加载配置。 |
| `public void saveToFile(String path)` | 将当前知识库配置写回文件。 |
| `public <T> T getConfig(Class<T> configType)` | 按类型查询已保存的配置对象。 |
| `public KnowledgeBaseConfig getKnowledgeBaseConfig()` | 返回当前知识库配置。 |
| `public void updateConfig(Object config)` | 更新某个配置对象。 |

## 说明

- 仅支持 JSON、YAML、YML 文件格式。
- 测试确认：可通过构造器直接加载 JSON，也可显式加载 YAML。
