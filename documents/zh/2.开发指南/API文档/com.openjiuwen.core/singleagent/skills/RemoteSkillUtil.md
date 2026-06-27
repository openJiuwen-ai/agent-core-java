# com.openjiuwen.core.single_agent.skills.RemoteSkillUtil

## 类 RemoteSkillUtil

```java
public class RemoteSkillUtil
```

负责从 GitHub 搜索技能目录、下载文件并写入本地技能目录的工具类。

## 构造方法

| 签名 | 说明 |
|---|---|
| `public RemoteSkillUtil(String sysOperationId)` | 使用给定的 `sysOperationId` 初始化实例。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public String getSysOperationId()` | 返回当前 `sysOperationId`。 |
| `public void setSysOperationId(String sysOperationId)` | 更新当前 `sysOperationId`。 |
| `public static byte[] downloadFileFromGitHub(GitHubTree tree, String filePath, String token)` | 调用 GitHub contents API 下载指定文件，并以 `application/vnd.github.raw` 返回原始字节。 |
| `public List<String> uploadSkillFromGitHub(GitHubTree tree, String skillsDir, String token)` | 搜索远程技能目录，下载文件到本地 `skillsDir`，并返回技能目录路径列表。 |
| `public SearchResult searchGitHubForSkills(GitHubTree tree, String token)` | 遍历仓库文件树，找出包含 `SKILL.md` 的目录，并返回文件清单与技能目录清单。 |
| `public List<GitHubBlob> listGitHubFiles(GitHubTree tree, String token)` | 根据 `treeRef` 和 `directory` 列出当前范围内的全部文件 blob。 |

## 嵌套类型

- `GitHubBlob`：记录 GitHub 文件路径的只读 record。
- `SkillFile`：记录远程原始路径与本地相对路径的只读 record。
- `SearchResult`：记录搜索得到的文件列表与技能目录列表。

## 说明

- `uploadSkillFromGitHub(...)` 会在写入前自动创建目标父目录。
- `searchGitHubForSkills(...)` 只把包含 `SKILL.md` 的目录识别为技能根目录，并按父目录名生成本地技能目录名。
