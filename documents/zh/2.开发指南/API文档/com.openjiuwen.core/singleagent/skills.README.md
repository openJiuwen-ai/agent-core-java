# skills

`com.openjiuwen.core.single_agent.skills` 提供技能元数据、技能注册、GitHub 远程拉取以及系统提示词拼装相关能力。

## 类型

| 类型 | 说明 |
|---|---|
| [`GitHubError`](./skills/GitHubError.md) | GitHub API 访问或下载落盘失败时抛出的运行时异常。 |
| [`GitHubTree`](./skills/GitHubTree.md) | 描述仓库、引用版本与目录范围的 GitHub 树对象。 |
| [`RemoteSkillUtil`](./skills/RemoteSkillUtil.md) | 从 GitHub 搜索并下载技能目录的工具类。 |
| [`Skill`](./skills/Skill.md) | 技能名称、描述和目录路径的元数据对象。 |
| [`SkillManager`](./skills/SkillManager.md) | 负责本地技能的注册、查询和注销。 |
| [`SkillUtil`](./skills/SkillUtil.md) | 组合 `SkillManager` 与 `RemoteSkillUtil` 的高层工具入口。 |

## 说明

- `SkillManagerTest` 验证了本地 `SKILL.md` / `Skill.md` 注册、重复注册和注销行为。
- `SkillUtilTest` 验证了 `GitHubTree` 默认值、复制逻辑以及 `SkillUtil` 的基本提示词输出。
