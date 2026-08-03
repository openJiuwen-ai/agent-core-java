# com.openjiuwen.core.single_agent.legacy.schema.PluginSchema

## 类 PluginSchema

```java
public class PluginSchema
```

旧版插件声明对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `id` | `String` | `""` | 插件标识。 |
| `version` | `String` | `""` | 插件版本。 |
| `name` | `String` | `""` | 插件名称。 |
| `description` | `String` | `""` | 插件描述。 |
| `inputs` | `Map<String, Object>` | `new LinkedHashMap<>()` | 插件输入参数定义。 |
| `pluginId` | `String` | `""` | 兼容旧结构保留的插件 ID。 |

## 说明

- 源码使用 Lombok `@Data` 与 `@Builder` 生成访问器和 builder。
