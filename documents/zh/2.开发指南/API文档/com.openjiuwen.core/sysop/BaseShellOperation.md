# com.openjiuwen.core.sysop.BaseShellOperation

## 类 BaseShellOperation

```java
public abstract class BaseShellOperation extends BaseOperation
```

`BaseShellOperation` 是命令执行能力的抽象基类，约定了同步命令执行和流式命令执行两套公开接口，并默认把两者注册为工具。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<ToolCard> listTools()` | 返回 `executeCmd` 与 `executeCmdStream` 两个标准工具卡片。 |
| `public abstract ExecuteCmdResult executeCmd( String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 同步执行 shell 命令并返回完整结果。 |
| `public abstract Iterator<ExecuteCmdStreamResult> executeCmdStream( String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 流式执行 shell 命令并返回输出 chunk。 |

## 相关测试

- `LocalShellOperationTest`
