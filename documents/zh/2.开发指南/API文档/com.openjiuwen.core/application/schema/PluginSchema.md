# com.openjiuwen.core.application.schema.PluginSchema

## class PluginSchema

```java
public class PluginSchema
```

`PluginSchema` 描述应用层 Agent 配置中的一个工具/插件引用。

## 字段

| 字段 | 类型 | 默认值 | JSON 别名 | 说明 |
|---|---|---|---|---|
| `id` | `String` | `""` | - | 插件记录 ID。 |
| `version` | `String` | `""` | - | 插件版本。 |
| `name` | `String` | `""` | - | 插件展示名或能力名。 |
| `description` | `String` | `""` | - | 插件描述。 |
| `inputs` | `Map<String, Object>` | `new LinkedHashMap<>()` | - | 输入参数 Schema。 |
| `pluginId` | `String` | `""` | `plugin_id` / `pluginId` | 底层插件资源标识。 |

## 说明

- 该类使用 `@Data`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor` 生成常规访问器与构造器。
- `ApplicationTranslationRegressionTest` 验证了 `plugin_id` 与 `inputs` 能正确映射到本类。
