# com.openjiuwen.core.sysop.registry.Operation

## 注解 Operation

```java
public Operation
```

`@Operation` 用于把某个 `BaseOperation` 子类声明为可注册操作，并显式给出名称、运行模式和说明文本。

## 方法

| 签名 | 说明 |
| --- | --- |
| `String name()` | 返回操作唯一名称，例如 `fs`、`shell`、`code`。 |
| `OperationMode mode()` | 返回该操作所属的运行模式。 |
| `String description() default ""` | 返回操作的人类可读说明；默认值为空字符串。 |

## 相关测试

- `CustomOperationExtensionTest`
- `LocalCodeOperationTest`
- `LocalFsOperationTest`
- `LocalShellOperationTest`
- `OperationModeTest`
