# com.openjiuwen.core.singleagent.legacy.config.MemoryConfig

## 类 MemoryConfig

```java
public class MemoryConfig
```

旧版记忆模块的启用开关、作用域和附加参数配置。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | `boolean` | `true` | 是否启用记忆能力。 |
| `scope` | `String` | `""` | 记忆作用域标识。 |
| `config` | `Map<String, Object>` | `new LinkedHashMap<>()` | 额外记忆参数。 |

## 说明

- 源码使用 Lombok `@Data`、`@Builder`、`@NoArgsConstructor` 和 `@AllArgsConstructor` 生成访问器与构建器。
