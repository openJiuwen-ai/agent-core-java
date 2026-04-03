# com.openjiuwen.core.sysop.sandbox.SandboxCodeOperation

## 类 SandboxCodeOperation

```java
public class SandboxCodeOperation extends BaseCodeOperation
```

沙箱代码执行操作的占位实现，当前尚未实现。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public SandboxCodeOperation(Object runConfig)` | 创建 `SandboxCodeOperation` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ExecuteCodeResult executeCode(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 执行代码；当前实现直接抛出 `UnsupportedOperationException`。 |
| `public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout, Map<String, String> environment, Map<String, Object> options)` | 以流式方式执行代码；当前实现直接抛出 `UnsupportedOperationException`。 |

## 说明

- `SandboxOperationTest` 验证了代表性沙箱操作方法当前会抛出 `UnsupportedOperationException`。

## 相关测试

- `SandboxOperationTest`
