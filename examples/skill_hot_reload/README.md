# Skill Hot-Reload Java Example (DeepAgent 版本)

这个目录演示如何在 Java 框架里通过 DeepAgent 实现 Skill 热重载：

1. 创建 `DeepAgent` 并配置 `SkillUseRail` rail
2. 挂载 `readFile`、`executeCode`、`executeCmd` 系统工具
3. `SkillUseRail` 在 `beforeModelCall` 回调中自动检测 skill 目录的文件变更
4. 变更时自动增量刷新 `SkillManager`，无需手动调用或重启服务
5. 演示 skill 新增、修改和删除场景下的自动热重载

## 核心机制

`DeepAgent` 使用 `SkillUseRail` 作为 rail，在每次模型调用前自动检查 skill 目录的 mtime 签名：

- 签名未变化 → 无操作，继续推理
- 签名变化（新增 / 修改 / 删除 skill） → 自动调用 `skillManager.refreshIncrementally()`
- 更新的 skill 信息自动注入系统提示词

与 `ReActAgent` 版本手动注册回调不同，`DeepAgent` + `SkillUseRail` 将热加载逻辑封装在 rail 内，使用更简洁：

```java
// 创建 SkillUseRail 并配置 skill 目录
SkillUseRail skillUseRail = new SkillUseRail(
    List.of(skillsDir.toString()), "all", List.of(), List.of());

// 配置 DeepAgent 并添加 rail
DeepAgentConfig config = DeepAgentConfig.builder()
    .rails(List.of(skillUseRail))
    .skillDirectories(List.of(skillsDir.toString()))
    .build();

// 创建 DeepAgent - 热加载自动启用
DeepAgent deepAgent = HarnessFactory.createDeepAgent(card, config, workspace);
deepAgent.ensureInitialized();
```

## 文件说明

- `DeepAgentSkillHotReloadExample.java`: 示例入口，使用 DeepAgent + SkillUseRail。
- `run.ps1`: 一键编译运行脚本（PowerShell）。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。
- `skills/skill_a/SKILL.md`: 示例初始 skill。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。

## 关键环境变量与属性

- `FILES_BASE_DIR`: 用户输入文件目录。默认是 `examples/skill_hot_reload/data`。
- `OUTPUT_DIR`: Agent 生成文件目录。默认是 `examples/skill_hot_reload/output`。
- `SKILLS_DIR`: 本地 skill 目录。默认是 `examples/skill_hot_reload/skills`。
- `MAX_ITERATIONS`: 最大推理轮数。默认是 `5`。
- `OPENJIUWEN_API_CONFIG`: 可选。显式指定 `apiconfig.json` 路径。
- `openjiuwen.example.config`: 可选。通过 JVM system property 显式指定 `apiconfig.json` 路径。

## 运行方式

### 方式一：一键脚本（推荐）

直接运行 `run.ps1`，脚本会自动编译项目、构建 classpath、编译示例并运行：

```powershell
cd examples/skill_hot_reload
.\run.ps1
```

### 方式二：手动编译运行

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -DskipTests -q
javac -source 17 -target 17 -cp "target/classes;target/dependency/*" -d examples/skill_hot_reload/build examples/utils/SharedExampleApiConfigLoader.java examples/skill_hot_reload/DeepAgentSkillHotReloadExample.java
java -Dfile.encoding=UTF-8 -cp "examples/skill_hot_reload/build;target/classes;target/dependency/*" examples.skill_hot_reload.DeepAgentSkillHotReloadExample
```

## 示例执行流程

示例程序会自动演示以下热重载场景：

1. **Step 1**：启动时从 `SKILLS_DIR` 加载初始 skills（如已有 `skill_a`）。
2. **Step 2**：运行 DeepAgent 查询，Agent 自动感知当前已注册的 skill。
3. **Step 3**：在磁盘上新增 `skill_b` 并修改 `skill_a`，再次运行 Agent — `SkillUseRail.beforeModelCall` 自动检测签名变化，增量刷新 SkillManager。
4. **Step 4**：确认 SkillManager 已更新为 `skill_a` + `skill_b`。
5. **Step 5**：从磁盘删除 `skill_b`，再次运行 Agent — `SkillUseRail` 自动检测删除，增量刷新 SkillManager。
6. **Step 6**：确认 SkillManager 已恢复为仅 `skill_a`。

预期输出示例：

```
=== DeepAgent Skill Hot-Reload Demo ===
[Step 1] Initial skills loaded: [skill_a] (count: 1)
[Step 2] Running DeepAgent with 1 skills...
[Step 2] DeepAgent response: ...
[Step 3] Running DeepAgent again after adding skill_b and modifying skill_a...
[Step 3] DeepAgent response: ...
[Step 4] Skills after add hot-reload: [skill_a, skill_b] (count: 2)
[Step 5] Running DeepAgent again after deleting skill_b...
[Step 5] DeepAgent response: ...
[Step 6] Skills after delete hot-reload: [skill_a] (count: 1)
=== Demo Complete ===
```

## 与 ReActAgent 版本的对比

| 对比项 | SkillHotReloadExample (ReActAgent) | DeepAgentSkillHotReloadExample |
|--------|-----------------------------------|-------------------------------|
| Agent 类型 | `ReActAgent` | `DeepAgent` |
| 热加载机制 | 手动注册 `BEFORE_MODEL_CALL` 回调 | `SkillUseRail` 内置 `beforeModelCall` |
| Signature 检查 | 手动代码实现 | `SkillUseRail` 自动处理 |
| Skill 刷新 | 手动调用 `skillManager.refreshIncrementally()` | 自动调用 |
| 配置方式 | agent.registerCallback() | DeepAgentConfig.rails |

## 说明

- 本示例不依赖 GitHub 远程 skill，所有 skill 变更均在本地磁盘完成。
- Java 系统工具名采用框架内的 camelCase 形式：`readFile`、`executeCode`、`executeCmd`。
- 热重载的核心是 `SkillUseRail.beforeModelCall` 中的 mtime 签名比较，无需手动注册回调。
- `refreshIncrementally()` 只处理变更的 skill（新增 / 修改 / 删除），不会全量重建，性能开销极小。
- 该模式与生产环境 `SkillUseRail` 的热重载行为一致，代码更简洁。