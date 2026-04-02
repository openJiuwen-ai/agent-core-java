# com.openjiuwen.core.singleagent.skills.GitHubTree

## 类 GitHubTree

```java
public class GitHubTree
```

描述 GitHub 仓库、引用版本与目录范围的树对象。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `repoOwner` | `String` | `-` | 仓库所有者。 |
| `repoName` | `String` | `-` | 仓库名称。 |
| `treeRef` | `String` | `"HEAD"` | 当前查询使用的分支、标签或树 SHA。 |
| `directory` | `String` | `""` | 在仓库内进一步限定的目录前缀。 |

## 构造方法

| 签名 | 说明 |
|---|---|
| `public GitHubTree(String repoOwner, String repoName)` | 使用仓库所有者和仓库名初始化对象，并把 `treeRef` 设为 `HEAD`、`directory` 设为空字符串。 |

## 方法

| 签名 | 说明 |
|---|---|
| `public GitHubTree copy()` | 复制当前对象的 `repoOwner`、`repoName`、`treeRef` 和 `directory`。 |

## 说明

- 源码同时使用 Lombok `@NoArgsConstructor` 与 `@AllArgsConstructor` 生成无参和全参构造。
- `SkillUtilTest` 验证了默认 `treeRef = "HEAD"`、默认空目录以及 `copy()` 的复制行为。
