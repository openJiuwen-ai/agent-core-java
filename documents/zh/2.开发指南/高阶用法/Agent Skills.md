# Agent Skills

`Agent Skills` 对应 `com.openjiuwen.core.singleagent.skills`。在 Java 版里，skill 负责两件事：

1. 把磁盘上的技能目录注册成可查询的元数据；
2. 把这些元数据拼成补充提示词，让 `ReActAgent` 在推理时知道“有哪些 skill、应该去哪里读 `SKILL.md`”。

它**不是**系统执行器，也不会自动替 Agent 挂载文件、代码或命令工具。和系统能力相关的内容，请继续阅读 [系统操作](系统操作.md) 与 [技能与系统操作](技能与系统操作.md)。

## 1. 核心对象

| 类型 | 作用 | 当前 Java 落点 |
| --- | --- | --- |
| `Skill` | skill 的轻量元数据对象 | 保存 `name`、`description`、`directory` |
| `SkillManager` | 本地 skill 注册表 | 负责扫描目录、读取描述、查询与注销 |
| `RemoteSkillUtil` | GitHub 远程 skill 拉取工具 | 负责搜索远程目录、下载文件到本地 |
| `SkillUtil` | 高层封装入口 | 组合 `SkillManager` 和 `RemoteSkillUtil`，并生成 skill prompt |
| `BaseAgent.registerSkill(...)` | Agent 侧本地注册入口 | 内部走 `SkillUtil.registerSkills(...)` |
| `BaseAgent.registerRemoteSkills(...)` | Agent 侧远程下载入口 | 内部走 `SkillUtil.registerRemoteSkills(...)` |

如果你只是想让一个 `ReActAgent` 使用 skills，通常不需要直接 new `SkillManager`，而是走 Agent 的高层入口即可。

## 2. 本地 skill 的目录与发现规则

Java 当前的本地发现逻辑由 `SkillManager` 提供，规则比较明确：

- 你可以把**单个 skill 目录**或**skills 根目录**交给注册器；
- 如果传入的是根目录，`SkillManager` 会扫描其**直接子目录**中的 `Skill.md` 或 `SKILL.md`；
- skill 名称默认取自**目录名**，不是 YAML 里的 `name` 字段；
- 当前真正会被解析并进入提示词的，是 front matter 里的 `description:`。

一个推荐的本地目录结构如下：

```text
skills/
└─ image_resizer/
   ├─ SKILL.md
   ├─ scripts/
   └─ assets/
```

`SKILL.md` / `Skill.md` 建议至少包含如下 front matter：

```markdown
---
description: 使用 Python/OpenCV 对图片进行缩放并输出到指定目录
---

这里写具体操作步骤、脚本说明和输入输出约定。
```

需要注意两点：

- `SkillManager.loadDescription(...)` 当前只提取 `description:`，所以这里必须写清楚触发条件与用途；
- Java 当前本地扫描接受 `Skill.md` 和 `SKILL.md`，但远程 GitHub 搜索只认 `SKILL.md`，两者有一个细微差异。

## 3. 注册到 Agent 的主线

对 Agent 来说，最常见的路径是：**先配置 Agent，再同步远程 skill（可选），最后注册本地 skill 目录**。

```java
Path skillsDir = Path.of("examples", "skill_use", "skills").toAbsolutePath().normalize();

ReActAgent agent = new ReActAgent(agentCard);
ReActAgentConfig config = ReActAgentConfig.builder().build();
config.setSysOperationId(sysOpCard.getId());
agent.configure(config);

agent.registerRemoteSkills(
        skillsDir.toString(),
        new GitHubTree(
                "dreamofapsychiccat",
                "remote-skills-test",
                "HEAD",
                "skills/image_resizer"
        ),
        token
);
agent.registerSkill(skillsDir.toString());
```

这里有三个运行时要点：

1. `BaseAgent.lazyInitSkill()` 只有在配置对象能提供 `getSysOperationId()` 时才会初始化 `SkillUtil`，因此建议**先 `configure(...)`，再注册 skill**；
2. `registerRemoteSkills(...)` 的职责是**把 GitHub 上的 skill 文件下载到本地目录**；
3. 真正让 Agent 在运行时“看见”这些 skill 的，是后续的 `registerSkill(...)`。

也就是说，远程 skill 在 Java 当前实现里是“**分发 / 同步渠道**”，而不是独立的运行时注册表。

## 4. GitHub 远程 skill 与本地 skill 的关系

可以把两者理解成“下载层”和“加载层”：

| 层次 | 关键对象 | 负责什么 | 不负责什么 |
| --- | --- | --- | --- |
| 远程分发层 | `GitHubTree`、`RemoteSkillUtil`、`registerRemoteSkills(...)` | 在 GitHub 仓库中定位 skill 目录并把文件下载到本地 `skillsDir` | 不把 skill 自动写进 `SkillManager` |
| 本地加载层 | `SkillManager`、`SkillUtil`、`registerSkill(...)` | 扫描本地目录、生成 `Skill` 元数据、参与 prompt 拼装 | 不负责联网下载 |

`GitHubTree` 主要描述四个信息：

- `repoOwner`
- `repoName`
- `treeRef`
- `directory`

Java 当前的 `RemoteSkillUtil.searchGitHubForSkills(...)` 会在远程目录里寻找包含 `SKILL.md` 的目录，并把对应文件写入本地 `skillsDir`。如果你已经提前把 skill 手动放到本地，则可以完全跳过远程步骤，直接 `registerSkill(...)`。

## 5. skill prompt 如何拼进 ReActAgent

`SkillUtil.getSkillPrompt()` 会生成一段补充提示词，内容包括：

- 说明当前 Agent equipped with skills；
- 列出每个 skill 的名称、描述、目录路径；
- 明确提示模型优先使用 `readFile` 去读取相应 `SKILL.md`。

在 Java 当前实现中，这段提示词并不是单独的一条消息，而是由 `ReActAgent.invoke(...)` 在运行时**追加到最后一条 system prompt** 上。满足条件包括：

- `promptTemplate` 中本来就有 system message；
- `getSkillUtil() != null`；
- `getSkillUtil().hasSkill()` 为真。

这意味着：

- 如果你没有配置 system prompt，skill prompt 不会自动注入；
- 如果已经注册了 skill，但 Agent 没有 `readFile` 能力，`ReActAgent` 只会发出告警，不会替你自动补工具。

因此，`Agent Skills` 页只负责说明 **“skill 如何被发现与提示”**；至于 **“Agent 靠什么工具真正读取 `SKILL.md` 或执行 skill 中的步骤”**，要回到系统能力页面来看。

## 6. 当前 Java 能力边界

为了避免把 skill 元数据层和执行层混在一起，这里明确列出当前边界：

- `SkillManager` 当前只提取 `description:`，不会解析完整 skill manifest；
- `SkillUtil.registerSkills(...)` 当前负责**本地注册与 prompt 拼装**，不负责自动把 `readFile`、`executeCode`、`executeCmd` 等工具挂到 Agent；
- `registerRemoteSkills(...)` 负责 GitHub 下载，不等于运行时注册；
- 当前最稳定的使用路径仍然是 `examples/skill_use/SkillUseExample.java` 里的做法：**先显式挂系统工具，再注册 skill**。

## 7. 推荐阅读顺序

如果你正在补齐一个真正可运行的 Java skill 方案，建议按下面顺序阅读：

1. 先看本页，理解 `SkillManager`、`SkillUtil`、`RemoteSkillUtil` 的职责；
2. 再看 [系统操作](系统操作.md)，理解 `SysOperation`、`OperationMode` 和工具卡片如何暴露；
3. 最后看 [技能与系统操作](技能与系统操作.md)，把 skill 元数据层和 sysop 执行层串成完整执行链。

## 示例入口

- [示例：skill_use](../../../../examples/skill_use/README.md)
- [示例：skill_create](../../../../examples/skill_create/README.md)

## 参考入口

- [API 文档：skills 总览](../API文档/com.openjiuwen.core/singleagent/skills.README.md)
- [API 文档：Skill](../API文档/com.openjiuwen.core/singleagent/skills/Skill.md)
- [API 文档：SkillManager](../API文档/com.openjiuwen.core/singleagent/skills/SkillManager.md)
- [API 文档：SkillUtil](../API文档/com.openjiuwen.core/singleagent/skills/SkillUtil.md)
- [API 文档：RemoteSkillUtil](../API文档/com.openjiuwen.core/singleagent/skills/RemoteSkillUtil.md)
- [API 文档：BaseAgent](../API文档/com.openjiuwen.core/singleagent/BaseAgent.md)
- [示例源码：SkillUseExample.java](../../../../examples/skill_use/SkillUseExample.java)
