# Agent Skills

`Agent Skills` 对应 `com.openjiuwen.core.single_agent.skills`。在 Java 版里，skill 负责两件事：

1. 把 classpath、filesystem、workspace 三层技能来源合并成可查询的元数据；
2. 按配置把元数据拼成补充提示词，并在默认按需模式下让模型通过 `skill_tool` 读取正文。

它**不是**系统执行器，也不会自动替 Agent 挂载文件、代码或命令工具。和系统能力相关的内容，请继续阅读 [系统操作](系统操作.md) 与 [技能与系统操作](技能与系统操作.md)。

## 1. 核心对象

| 类型 | 作用 | 当前 Java 落点 |
| --- | --- | --- |
| `Skill` | skill 的轻量元数据对象 | 保存 `name`、`description`、`directory`、`source`、`id`、`protectedSkill` |
| `SkillManager` | 本地 skill 注册表 | 负责扫描目录、合并三级仓库、读取描述、查询与注销 |
| `RemoteSkillUtil` | GitHub 远程 skill 拉取工具 | 负责搜索远程目录、下载文件到本地 |
| `SkillUtil` | 高层封装入口 | 组合 `SkillManager` 和 `RemoteSkillUtil`，生成 skill prompt，并传播 `SkillConfig` |
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
- 明确提示模型优先使用 `skill_tool` 去读取相应 `SKILL.md`；关闭按需加载时保留旧 `read_file` 兼容提示。

在 Java 当前实现中，这段提示词并不是单独的一条消息，而是由 `ReActAgent.invoke(...)` 在运行时**追加到最后一条 system prompt** 上。满足条件包括：

- `promptTemplate` 中本来就有 system message；
- `getSkillUtil() != null`；
- `getSkillUtil().hasSkill()` 为真。

这意味着：

- 如果你没有配置 system prompt，skill prompt 不会自动注入；
- 按需加载默认开启时，Agent 会注册内置 `skill_tool`；关闭时才依赖旧 `read_file` 兼容路径。

因此，`Agent Skills` 页负责说明 **“skill 如何被发现、提示与按需读取”**；至于 **“Agent 靠什么工具真正执行 skill 中的步骤”**，要回到系统能力页面来看。

## 6. 当前 Java 能力边界

为了避免把 skill 元数据层和执行层混在一起，这里明确列出当前边界：

- `SkillManager` 当前只提取 `description:`，不会解析完整 skill manifest；
- `BaseAgent.registerSkill(...)` 会注册元数据并自动注册内置 `skill_tool`，但不负责把 `executeCode`、`executeCmd` 等业务工具挂到 Agent；
- `registerRemoteSkills(...)` 负责 GitHub 下载，不等于运行时注册；
- 当前最稳定的使用路径仍然是 `examples/skill_use/SkillUseExample.java` 里的做法：**先显式挂系统工具，再注册 skill**。

## 7. 三级仓库、安全配置与按需加载（Issue #196）

三层优先级为 `classpath < filesystem < workspace`。同名 Skill 按高优先级覆盖；protected classpath Skill 默认禁止被覆盖。`SkillConfig` 支持三类配置：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `protectedOverrideBlocked` | `true` | 保护 classpath Skill 不被更高层覆盖 |
| `workspaceMaxBytes` | `262144` | 限制 workspace 主文档大小，超限拒绝并审计 |
| `onDemandLoading` | `true` | prompt 只渲染元数据，正文通过 `skill_tool` 读取 |

上述配置支持 Java 系统属性和环境变量：`skill.protected-override-blocked` / `SKILL_PROTECTED_OVERRIDE_BLOCKED`、`skill.workspace-max-bytes` / `SKILL_WORKSPACE_MAX_BYTES`、`skill.on-demand-loading` / `SKILL_ON_DEMAND_LOADING`。系统属性优先；非法值保持安全默认值。

`skill_tool` 的参数为 `skill_name`、`skill_id`、`skill_path` 和 `relative_path`。默认按 `skill_name` 读取最高优先级同名 Skill；显式提供 `skill_id` 或 `skill_path` 时可选择已注册的低优先级版本，但不会绕过 protected 覆盖保护。`relative_path` 留空读取主文档；读取相对文档时禁止 `..`、绝对路径、盘符、UNC 和 symlink 逃逸。Harness 侧工具仍兼容旧参数 `relative_file_path`。

## 8. 与 ToolGroup 联动

`BaseAgent.registerSkillTools(...)` 会把 Skill 绑定的工具注册到保留组 `skill:<skill-name>`。激活 Skill 后，这些工具才会进入当前 session 的有效工具列表；停用 Skill 后从列表移除，并在执行层拒绝直连调用。

如果 Skill 绑定的工具同时属于显式 ToolGroup，显式停用优先。也就是说，`skill:<skill-name>` 不能绕过 `research`、`danger` 等显式组的停用规则。完整示例见 [`examples/toolgroup/README.md`](../../../../examples/toolgroup/README.md)。

## 9. 推荐阅读顺序

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
