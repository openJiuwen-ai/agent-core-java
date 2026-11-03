# com.openjiuwen.core.single_agent.skills.GitHubError

## 类 GitHubError

```java
public class GitHubError extends RuntimeException
```

用于包装 GitHub API 访问、下载和落盘失败的运行时异常。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public GitHubError(String message)` | 使用错误消息创建异常。 |
| `public GitHubError(String message, Throwable cause)` | 使用错误消息和原始异常创建异常。 |

## 说明

- `RemoteSkillUtil` 会在 GitHub HTTP 状态异常、JSON 读取失败或文件写入失败时抛出该异常。
