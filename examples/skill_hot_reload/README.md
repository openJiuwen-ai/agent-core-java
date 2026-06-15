# Skill Hot-Reload Java Example

这个目录演示如何在 Java 框架里实现 Skill 热重载：

1. 创建 `ReActAgent` 并注册本地 skill
2. 挂载 `readFile`、`executeCode`、`executeCmd` 系统工具
3. 通过 `BEFORE_MODEL_CALL` 回调自动检测 skill 目录的文件变更
4. 变更时自动增量刷新 `SkillManager`，无需手动调用或重启服务
5. 演示 skill 新增、修改和删除场景下的自动热重载

## 核心机制

Agent 在每次模型调用前，通过回调自动检查 skill 目录的 mtime 签名：

- 签名未变化 → 无操作，继续推理
- 签名变化（新增 / 修改 / 删除 skill） → 自动调用 `skillManager.refreshIncrementally()`
- `ReActAgent` 内置的 `updateSkillPromptBuilderSection()` 自动将最新 SkillManager 状态注入系统提示词

无需手动刷新 SkillManager 或更新提示词，Agent 在执行循环中自动感知磁盘上的 skill 变化。

## 文件说明

- `SkillHotReloadExample.java`: 示例入口。
- `run.ps1`: 一键编译运行脚本（PowerShell）。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。

## 关键环境变量与属性

- `FILES_BASE_DIR`: 用户输入文件目录。默认是 `examples/skill_hot_reload/data`。
- `OUTPUT_DIR`: Agent 生成文件目录。默认是 `examples/skill_hot_reload/output`。
- `SKILLS_DIR`: 本地 skill 目录。默认是 `examples/skill_hot_reload/skills`。
- `MAX_ITERATIONS`: 最大推理轮数。默认是 `10`。
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
mvn dependency:build-classpath "-Dmdep.outputFile=target/skill_examples.classpath"
javac -cp "target/classes;$(Get-Content target/skill_examples.classpath -Raw)" examples/utils/SharedExampleApiConfigLoader.java examples/skill_hot_reload/SkillHotReloadExample.java
java '-Dfile.encoding=UTF-8' '-Dopenjiuwen.example.config=examples/apiconfig.json' -cp "target/classes;.;$(Get-Content target/skill_examples.classpath -Raw)" examples.skill_hot_reload.SkillHotReloadExample
```

## 示例执行流程

示例程序会自动演示以下热重载场景：

1. **Step 1**：启动时从 `SKILLS_DIR` 加载初始 skills（如已有 `skill_a`）。
2. **Step 2**：运行 Agent 查询，Agent 自动感知当前已注册的 skill。
3. **Step 3**：在磁盘上新增 `skill_b` 并修改 `skill_a`，再次运行 Agent — 回调自动检测签名变化，增量刷新 SkillManager。
4. **Step 4**：确认 SkillManager 已更新为 `skill_a` + `skill_b`。
5. **Step 5**：从磁盘删除 `skill_b`，再次运行 Agent — 回调自动检测删除，增量刷新 SkillManager。
6. **Step 6**：确认 SkillManager 已恢复为仅 `skill_a`。

预期输出示例：

```
=== Skill Hot-Reload Demo ===
[Step 1] Initial skills loaded: [skill_a] (count: 1)
[Step 2] Running agent with 1 skills...
[Step 2] Agent response: ...
[Hot-Reload] Skill signature changed, refreshing incrementally...
[Hot-Reload] Updated skills: [skill_a, skill_b]
[Step 3] Running agent again after adding skill_b and modifying skill_a...
[Step 3] Agent response: ...
[Step 4] SkillManager after add hot-reload: [skill_a, skill_b] (count: 2)
[Hot-Reload] Skill signature changed, refreshing incrementally...
[Hot-Reload] Updated skills: [skill_a]
[Step 5] Running agent again after deleting skill_b...
[Step 5] Agent response: ...
[Step 6] SkillManager after delete hot-reload: [skill_a] (count: 1)
=== Demo Complete ===
```

## 说明

- 本示例不依赖 GitHub 远程 skill，所有 skill 变更均在本地磁盘完成。
- Java 系统工具名采用框架内的 camelCase 形式：`readFile`、`executeCode`、`executeCmd`。
- 热重载的核心是 `BEFORE_MODEL_CALL` 回调中的 mtime 签名比较，优先级设为 100 以确保在模型调用前执行。
- `refreshIncrementally()` 只处理变更的 skill（新增 / 修改 / 删除），不会全量重建，性能开销极小。
- 该模式与生产环境 `SkillUseRail` 的热重载行为一致。
