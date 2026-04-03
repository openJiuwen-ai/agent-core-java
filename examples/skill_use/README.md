# Skill Use Java Example

这个目录对应 Python 版 `examples/skill_use/main.py` 和 `examples/skill_use/skill_use.py`，演示如何在 Java 框架里：

1. 创建 `ReActAgent`
2. 注册本地 skill
3. 从 GitHub 下载并注册远程 skill
4. 显式挂载 `readFile`、`executeCode`、`executeCmd` 系统工具
5. 运行一个需要 skill 的查询

## 文件说明

- `SkillUseExample.java`: 示例入口。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从 `f:\openJiuwenTT\agent-core-java-myfork` 目录运行命令。
3. 将待处理图片等输入文件放入 `FILES_BASE_DIR` 对应目录。
4. 本机需要可直接调用的 `python`，因为 skill 最终通过 `executeCode` 执行 Python/OpenCV 缩图代码。
5. 如本机 Python 环境里尚未安装 `opencv-python`，首次运行时需要允许安装，或者提前手动安装。

## 关键环境变量与属性

- `FILES_BASE_DIR`: 用户输入文件目录。默认是 `examples/skill_use/data`。
- `OUTPUT_DIR`: Agent 生成文件目录。默认是 `examples/skill_use/output`。
- `SKILLS_DIR`: 本地 skill 目录，同时也是远程 GitHub skill 下载目录。默认是 `examples/skill_use/skills`。
- `MAX_ITERATIONS`: 最大推理轮数。默认是 `40`。
- `GITHUB_TOKEN`: 可选。访问 GitHub 远程 skill 时使用。
- `SKILL_USE_SKIP_REMOTE`: 设为 `true` 时跳过远程 skill 注册，只使用本地 skills。
- `OPENJIUWEN_API_CONFIG`: 可选。显式指定 `apiconfig.json` 路径。
- `openjiuwen.example.config`: 可选。通过 JVM system property 显式指定 `apiconfig.json` 路径。

## 运行方式

建议先在 `agent-core-java-myfork` 目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/skill_examples.classpath"
javac -cp "target/classes;$(Get-Content target/skill_examples.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/skill_use/SkillUseExample.java
& java '-Dfile.encoding=UTF-8' -cp "target/classes;examples;examples/skill_use;$(Get-Content target/skill_examples.classpath -Raw)" SkillUseExample
```

也可以在最后一条命令后直接追加查询内容，例如：

```powershell
& java '-Dfile.encoding=UTF-8' -cp "target/classes;examples;examples/skill_use;$(Get-Content target/skill_examples.classpath -Raw)" SkillUseExample Downscale the provided image inside the examples/skill_use/data directory by 2x.
```

如果需要显式指定配置文件，也可以这样运行：

```powershell
& java '-Dfile.encoding=UTF-8' '-Dopenjiuwen.example.config=F:\openJiuwenTT\agent-core-java-myfork\examples\apiconfig.json' -cp "target/classes;examples;examples/skill_use;$(Get-Content target/skill_examples.classpath -Raw)" SkillUseExample
```

## 已验证的默认示例

- 默认输入目录：`examples/skill_use/data`
- 默认输入文件：`sample.png`
- 默认输出目录：`examples/skill_use/output`
- 一次真实 smoke run 已验证生成输出：`sample_downscaled_2x.png`

示例成功时，Agent 会：

1. 读取 `SKILLS_DIR` 中的 `image_resizer` skill 说明。
2. 必要时从 GitHub 下载同名远程 skill 到本地 `SKILLS_DIR`。
3. 枚举 `FILES_BASE_DIR` 中的输入图片。
4. 通过 `executeCode` 执行 Python/OpenCV 缩图逻辑。
5. 在 `OUTPUT_DIR` 中确认目标文件已生成后结束。

## 说明

- 示例默认尝试从 GitHub 下载 Python 示例里同一个 `image_resizer` skill。
- 如果远程注册失败，程序会打印提示并继续尝试本地 skills。
- Java 系统工具名采用框架内的 camelCase 形式：`readFile`、`executeCode`、`executeCmd`。
- 当前 Windows 运行时已经做过兼容修复：Python 代码会通过临时 `.py` 文件执行，而不是 `python -c`，因此包含 Windows 路径和引号的生成代码可以正常执行。
- 如果你只是想验证本地 skill 流程，可以把 `SKILL_USE_SKIP_REMOTE=true`，避免 GitHub 网络依赖。