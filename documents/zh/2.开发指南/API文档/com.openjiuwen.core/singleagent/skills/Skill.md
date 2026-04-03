# com.openjiuwen.core.singleagent.skills.Skill

## 类 Skill

```java
public class Skill
```

技能名称、描述和目录路径的元数据对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | `String` | `-` | 技能名称。 |
| `description` | `String` | `-` | 技能描述。 |
| `directory` | `String` | `-` | 技能目录路径。 |

## 方法

| 签名 | 说明 |
|---|---|
| `@Override public String toString()` | 以多行文本输出技能名称、描述和目录。 |
| `public String toRepr()` | 生成单行紧凑表示；当描述长度超过 `30` 个字符时会截断并追加 `...`。 |

## 说明

- 源码使用 Lombok `@Data` 与 `@Builder` 生成访问器和 builder。
- `SkillManagerTest` 验证了 builder 赋值和 `toString()` 输出会包含名称、描述与目录。
