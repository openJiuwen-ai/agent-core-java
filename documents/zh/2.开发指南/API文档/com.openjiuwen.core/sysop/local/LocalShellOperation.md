# com.openjiuwen.core.sysop.local.LocalShellOperation

## 类 LocalShellOperation

```java
public class LocalShellOperation extends BaseShellOperation
```

`LocalShellOperation` 通过系统 shell 执行命令，支持工作目录、环境变量、超时和白名单限制，并可按流式方式返回 stdout/stderr。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public LocalShellOperation(Object runConfig)` | 使用给定运行配置创建本地命令执行操作。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 同步执行命令；若配置了 `shellAllowlist`，会先校验首命令是否允许。 |
| `public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout, Map<String, String> environment, Map<String, Object> options)` | 流式执行命令；按 `stdout`、`stderr`、`error`、`exit` 事件返回 chunk。 |

## 说明

- Windows 平台通过 `cmd.exe /c` 执行，其他平台通过 `/bin/sh -c` 执行。
- 当 `LocalWorkConfig.workDir` 存在时，空 `cwd` 会回落到该目录；相对 `cwd` 会在该目录下解析。
- 非 Windows 平台会额外包装命令，以减少流式读取时的缓冲延迟。
- `LocalShellOperationTest` 覆盖了环境变量、相对/绝对 `cwd`、超时、白名单和流式输出场景。

## 相关测试

- `LocalShellOperationTest`
