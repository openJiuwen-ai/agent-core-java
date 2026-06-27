# com.openjiuwen.core.common.schema.BaseCard

## class BaseCard

```java
public class BaseCard
```

`BaseCard` 是卡片类对象的基础父类，提供标识、名称、描述及浅复制能力。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `String` | 随机 UUID 去掉连字符后的字符串 | 唯一标识。 |
| `name` | `String` | `""` | 卡片名称，也可作为命名空间中的唯一标识。 |
| `description` | `String` | `""` | 卡片用途与功能说明。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object toolInfo()` | 扩展点方法，默认返回 `null`，由子类按需覆写。 |
| `public BaseCard copy()` | 创建当前对象的浅复制副本，仅复制 `id`、`name` 与 `description`。 |
| `public String toString()` | 返回 `id=<id>,name=<name>` 形式的字符串。 |

## 说明

- `@Data`、`@SuperBuilder`、`@NoArgsConstructor` 与 `@AllArgsConstructor` 会生成访问器、构建器和构造器。
