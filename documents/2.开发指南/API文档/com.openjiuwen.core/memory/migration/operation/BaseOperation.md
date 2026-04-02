# com.openjiuwen.core.memory.migration.operation.BaseOperation

## 类 BaseOperation

```java
public abstract class BaseOperation
```

`BaseOperation` 是所有迁移操作的抽象基类，统一封装元数据并提供版本号、说明文本的访问入口。

## 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `metadata` | `OperationMetadata` | 当前操作的元数据，包含 schema 版本号与可选描述。 |

## 公开方法

| 签名 | 说明 |
| --- | --- |
| `public int getSchemaVersion()` | 返回当前操作对应的 schema 版本号。 |
| `public String getDescription()` | 返回元数据中的描述；若描述为空则退回到当前类名。 |

## 使用说明

- 该类只有受保护构造方法，供具体迁移操作子类调用，不作为公开实例化入口。
- `@Data` 会为 `metadata` 生成常规访问器，但显式公开方法仍以源码中的 `getSchemaVersion()`、`getDescription()` 为主。
