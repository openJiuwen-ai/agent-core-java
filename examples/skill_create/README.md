# Skill Create Java Example

这个目录演示如何在 Java 框架里完成以下流程：

1. 下载一个 PDF 文件
2. 从 PDF 提取文本并生成一个 Markdown 辅助文件
3. 调用 `SkillCreator` 基于该 Markdown 生成 skill

## 文件说明

- `SkillCreateExample.java`: 示例入口。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行下面的命令，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。
3. 需要外网访问 PDF URL 和大模型接口。
4. 输出目录下需要有写权限，因为示例会下载 PDF、生成 Markdown、再写出最终 skill 目录。

## 关键环境变量与属性

- `SKILL_CREATE_PDF_URL`: 要下载的 PDF 链接。默认是代码里内置的公开 PDF：`11_Best_Practices_for_Peer_Code_Review.pdf`。
- `FILES_BASE_DIR`: 下载 PDF 与生成 Markdown 的目录。默认是 `examples/skill_create/data`。
- `OUTPUT_DIR`: skill 输出目录。默认是 `examples/skill_create/output`。
- `MAX_ITERATIONS`: `SkillCreator` 最大迭代次数。默认沿用框架默认值。
- `OPENJIUWEN_API_CONFIG`: 可选。显式指定 `apiconfig.json` 路径。
- `openjiuwen.example.config`: 可选。通过 JVM system property 显式指定 `apiconfig.json` 路径。

`SkillCreateExample` 会从 `examples/apiconfig.json` 读取 `API_BASE`、`API_KEY`、`MODEL_PROVIDER`、`MODEL_NAME` 和 `LLM_SSL_VERIFY`，并通过 system property 传给 `SkillCreator`。

## 运行方式

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/skill_examples.classpath"
javac -cp "target/classes;$(Get-Content target/skill_examples.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/skill_create/SkillCreateExample.java
& java '-Dfile.encoding=UTF-8' -cp "target/classes;examples;examples/skill_create;$(Get-Content target/skill_examples.classpath -Raw)" SkillCreateExample
```

也可以在最后一条命令后指定 PDF URL，例如：

```powershell
& java '-Dfile.encoding=UTF-8' -cp "target/classes;examples;examples/skill_create;$(Get-Content target/skill_examples.classpath -Raw)" SkillCreateExample https://example.com/manual.pdf
```

如果需要显式指定配置文件，也可以这样运行：

```powershell
& java '-Dfile.encoding=UTF-8' '-Dopenjiuwen.example.config=examples/apiconfig.json' -cp "target/classes;examples;examples/skill_create;$(Get-Content target/skill_examples.classpath -Raw)" SkillCreateExample
```

## 输出说明

- 下载的 PDF 默认写入 `examples/skill_create/data`
- 基于 PDF 文本生成的 Markdown 会写在同目录下，文件名与 PDF 保持一致，仅扩展名改为 `.md`
- `SkillCreator` 生成的结果会打印为 JSON
- 最终生成的 skill 会落在 `OUTPUT_DIR/<skill_name>/SKILL.md`

## 已验证的默认流程

一次真实 smoke run 已验证这条链路可以走通：

1. 下载公开 PDF 到 `examples/skill_create/data`
2. 用 `PDFParser` 提取文本
3. 在同目录生成同名 `.md` 辅助文件
4. 调用 `SkillCreator.generate(...)`
5. 在 `examples/skill_create/output/<skill_name>/SKILL.md` 写出最终 skill

运行日志里会同时打印：

- PDF 下载位置
- Markdown 生成位置
- `SkillCreator` 返回的 JSON 结果

注意：Java 版这里实现的是轻量 Markdown 辅助流程，内容来自 `PDFParser` 提取的文本，不追求与其他 Markdown 转换工具完全一致的版式保真。

另外，`SkillCreator` 当前也已经做了 Windows 兼容约束，优先使用文件工具，不依赖 Unix heredoc 或 shell 特性。