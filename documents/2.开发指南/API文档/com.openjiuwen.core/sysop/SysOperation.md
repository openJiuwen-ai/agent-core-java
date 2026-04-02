# com.openjiuwen.core.sysop.SysOperation

## 类 SysOperation

```java
public class SysOperation
```

`SysOperation` 是系统操作门面。它根据 `SysOperationCard` 选择本地或沙箱模式，准备对应的运行配置，并按操作名懒加载和缓存具体实现。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `mode` | `OperationMode` | - | 当前门面实例采用的运行模式。 |
| `runConfig` | `Object` | - | 当前门面实例持有的运行配置对象。 |
| `instances` | `Map<String, BaseOperation>` | - | 已按操作名创建过的具体操作实例缓存。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SysOperation(SysOperationCard card)` | 使用卡片配置创建门面；当卡片未显式设置模式时默认采用 `OperationMode.LOCAL`。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public BaseFsOperation fs()` | 返回名为 `fs` 的文件系统操作实例。 |
| `public BaseCodeOperation code()` | 返回名为 `code` 的代码执行操作实例。 |
| `public BaseShellOperation shell()` | 返回名为 `shell` 的命令执行操作实例。 |
| `public BaseOperation getOperation(String name)` | 按操作名从注册表创建并缓存实例；未注册时返回 `null`。 |
| `public OperationMode getMode()` | 返回当前门面实例的运行模式。 |

## 相关测试

- `LocalCodeOperationTest`
- `LocalFsOperationTest`
- `LocalShellOperationTest`
- `SysOperationCardTest`
- `SysOperationTest`
