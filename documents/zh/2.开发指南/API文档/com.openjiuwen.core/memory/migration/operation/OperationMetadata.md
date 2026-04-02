# com.openjiuwen.core.memory.migration.operation.OperationMetadata

## 类 OperationMetadata

```java
public class OperationMetadata
```

`OperationMetadata` 保存迁移操作的版本号与可选描述文本。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | `int` | 当前操作对应的 schema 版本号。 |
| `description` | `String` | 面向日志与文档的可选说明文本。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public OperationMetadata(int schemaVersion, String description)` | 同时设置版本号与描述文本。 |
| `public OperationMetadata(int schemaVersion)` | 仅设置版本号，`description` 默认为 `null`。 |

## 使用说明

- `BaseOperation.getDescription()` 会读取该对象中的 `description` 字段。
- `@Data` 负责生成字段读写方法，便于迁移器或注册表读取版本与描述。
