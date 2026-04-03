# com.openjiuwen.core.sysop.OperationMode

## 枚举 OperationMode

```java
public enum OperationMode
```

定义系统操作的运行模式，可通过 `getValue()` 读取对应的内部字符串值，并可通过 `fromString()` 做大小写不敏感解析。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getValue()` | 返回当前运行模式对应的内部字符串值。 |
| `public static OperationMode fromString(String text)` | 按大小写不敏感规则把字符串解析为运行模式；无法匹配时抛出 `IllegalArgumentException`。 |

## 枚举值

| 值 | 说明 |
| --- | --- |
| `LOCAL` | 本地执行模式。 |
| `SANDBOX` | 沙箱执行模式。 |

## 相关测试

- `CustomOperationExtensionTest`
- `LocalCodeOperationTest`
- `LocalFsOperationTest`
- `LocalShellOperationTest`
- `OperationModeTest`
