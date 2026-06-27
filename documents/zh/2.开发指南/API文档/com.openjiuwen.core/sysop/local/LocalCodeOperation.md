# com.openjiuwen.core.sys_operation.local.LocalCodeOperation

## 类 LocalCodeOperation

```java
public class LocalCodeOperation extends BaseCodeOperation
```

`LocalCodeOperation` 基于 `ProcessBuilder` 在本地运行代码，支持 `python` 与 `javascript` 两种语言，并根据源码长度或 `force_file` 选项在命令行执行与临时文件执行之间切换。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `WINDOWS_CMD_LIMIT` | `int` | `8000` | Windows 平台直接走命令行执行时允许的代码长度上限。 |
| `UNIX_CMD_LIMIT` | `int` | `100000` | Unix 类平台直接走命令行执行时允许的代码长度上限。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LocalCodeOperation(Object runConfig)` | 使用给定运行配置创建本地代码执行操作。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 同步执行代码；支持超时、环境变量和 `force_file` 选项，并返回完整 stdout、stderr 与退出码。 |
| `public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 流式执行代码；按 `stdout`、`stderr`、`error`、`exit` 事件返回 chunk。 |

## 说明

- 仅接受 `python` 和 `javascript`，其他语言会直接返回错误结果。
- `javascript` 执行前会注入 `NODE_DISABLE_COLORS=1`，`python` 执行前会注入 `PYTHONIOENCODING=utf-8` 与 `PYTHONUTF8=1`。
- `LocalCodeOperationTest` 覆盖了空代码、非法语言、环境变量、超时、大输出、特殊字符和 `force_file` 路径。

## 相关测试

- `CustomOperationExtensionTest`
- `LocalCodeOperationTest`
- `OperationRegistryTest`
