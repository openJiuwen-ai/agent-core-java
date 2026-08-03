# com.openjiuwen.core.sys_operation.config.LocalWorkConfig

## 类 LocalWorkConfig

```java
public class LocalWorkConfig
```

`LocalWorkConfig` 描述本地系统操作使用的工作目录和命令白名单策略。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `shellAllowlist` | `List<String>` | 预置常见命令列表 | 允许直接执行的命令前缀集合；当该字段为 `null` 或空列表时，命令执行不会做白名单拦截。 |
| `workDir` | `String` | - | 本地执行默认工作目录；文件路径和相对 `cwd` 都会基于它解析。 |

## Lombok 说明

- 该类型使用 `Data`、`Builder`、`NoArgsConstructor`、`AllArgsConstructor` 生成访问器、构建器和构造辅助方法。
- `shellAllowlist` 通过 `@Builder.Default` 提供默认命令列表。

## 相关测试

- `LocalWorkConfigTest`
- `LocalFsOperationTest`
- `LocalShellOperationTest`
- `SysOperationCardTest`
- `SysOperationTest`
