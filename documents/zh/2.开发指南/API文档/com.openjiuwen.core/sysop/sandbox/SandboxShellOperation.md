# com.openjiuwen.core.sys_operation.sandbox.SandboxShellOperation

## 类 SandboxShellOperation

```java
public class SandboxShellOperation extends BaseShellOperation
```

沙箱命令执行操作的占位实现，当前尚未实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SandboxShellOperation(Object runConfig)` | 创建 `SandboxShellOperation` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 执行命令；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 以流式方式执行命令；当前实现直接抛出 `UnsupportedOperationException`。 |

## 说明

- `SandboxOperationTest` 验证了代表性沙箱操作方法当前会抛出 `UnsupportedOperationException`。

## 相关测试

- `SandboxOperationTest`
