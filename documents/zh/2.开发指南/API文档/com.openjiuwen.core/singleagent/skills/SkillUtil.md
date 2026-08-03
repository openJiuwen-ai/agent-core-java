# com.openjiuwen.core.single_agent.skills.SkillUtil

## 类 SkillUtil

```java
public class SkillUtil
```

组合 `SkillManager` 与 `RemoteSkillUtil` 的高层技能工具入口。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public SkillUtil(String sysOperationId)` | 使用同一个 `sysOperationId` 初始化本地技能管理器和远程技能工具。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public void setSysOperationId(String sysOperationId)` | 同时更新内部 `SkillManager` 与 `RemoteSkillUtil` 的 `sysOperationId`。 |
| `public SkillManager getSkillManager()` | 返回本地技能管理器。 |
| `public RemoteSkillUtil getRemoteSkillUtil()` | 返回远程技能工具。 |
| `public void registerSkills(Object skillPath, BaseAgent agent)` | 接受 `String` 或 `List<String>` 形式的路径并调用 `SkillManager` 注册；`agent` 参数仅为兼容保留，源码中未使用。 |
| `public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token)` | 委托 `RemoteSkillUtil.uploadSkillFromGitHub(...)` 下载并注册远程技能目录。 |
| `public boolean hasSkill()` | 当 `SkillManager.count() > 0` 时返回 `true`。 |
| `public String getSkillPrompt()` | 生成包含系统提示和已注册技能列表的提示词文本。 |

## 说明

- `getSkillPrompt()` 输出英文系统提示，并把每个技能的名称、描述和目录拼接到技能列表中。
- `SkillUtilTest` 验证了初始状态、`setSysOperationId(...)` 同步更新以及提示词文本包含 `skill` 关键词。
