# Context Evolver Java Example

这个目录提供 Java 版 `context_evolver` 示例，覆盖 quickstart 到 HotpotQA 循环的完整链路。

示例默认会按完整 quickstart 路径执行：

1. 读取模型配置并初始化 `TaskMemoryService`
2. 按当前 `context_evolver` 算法写入一条 seed memory
3. 创建 `ContextEvolvingReActAgent`
4. 执行一次带 memory 检索增强的问答
5. 对第二次交互做 trajectory summarize，并把 memory 持久化到本地
6. 继续执行 HotpotQA + Wikipedia 的 retrieve-generate-summarize 循环

## 文件说明

- `ContextEvolverQuickstartExample.java`: 推荐入口，保持为薄包装类。
- `ContextEvolverExampleSupport.java`: 示例主实现，负责配置桥接、memory service、agent 调用、trajectory summarize 和 HotpotQA 段落。
- `../SharedExampleApiConfigLoader.java`: 统一读取 `examples/apiconfig.json` 中的模型配置。

## 配置来源

1. 运行时读取 `examples/apiconfig.json` 中的真实模型配置。
2. 示例会把 `API_KEY`、`MODEL_NAME`、`EMBEDDING_MODEL` 等值写入 `context_evolver` 的 `Config`，这样 agent 侧和 memory pipeline 侧使用同一组配置。
3. 算法和检索参数仍然复用 `src/main/java/com/openjiuwen/extensions/context_evolver/config.yaml` 中的默认值。
4. 如果 `examples/apiconfig.json` 中没有显式提供 embedding model，示例会回退到 `text-embedding-3-small`。

## 输出产物

默认输出目录是 `examples/context_evolver/output`，里面主要有两类产物：

1. `memory_files/`: `ContextEvolvingReActAgent.summarizeTrajectories(...)` 持久化出来的 memory JSON。
2. `quickstart.log`: 记录第二次交互的 trajectory，以及 HotpotQA 多轮轨迹和 summarize 结果。

可以通过以下方式覆盖输出目录：

- JVM 参数 `-Dopenjiuwen.example.contextEvolver.outputDir=...`
- 环境变量 `CONTEXT_EVOLVER_OUTPUT_DIR`

如果你想修改 HotpotQA 的并行轮数，可以通过以下方式覆盖默认值 `3`：

- JVM 参数 `-Dopenjiuwen.example.contextEvolver.mattsK=2`
- 环境变量 `CONTEXT_EVOLVER_MATTS_K`

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行下面的命令，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。
3. 默认运行会发起多次真实模型请求，并在 HotpotQA 段落里访问 Wikipedia 接口。
4. 因为示例默认完整执行，所以耗时和调用成本明显高于普通单轮 example。

## 运行方式

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/context_evolver.classpath"
javac -cp "target/classes;examples;$(Get-Content target/context_evolver.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/context_evolver/ContextEvolverExampleSupport.java examples/context_evolver/ContextEvolverQuickstartExample.java
java -Dfile.encoding=UTF-8 -cp "target/classes;examples;examples/context_evolver;$(Get-Content target/context_evolver.classpath -Raw)" ContextEvolverQuickstartExample
```

也可以在最后一条命令后追加自定义首轮查询，例如：

```powershell
java -Dfile.encoding=UTF-8 -cp "target/classes;examples;examples/context_evolver;$(Get-Content target/context_evolver.classpath -Raw)" ContextEvolverQuickstartExample What are some good practices for documenting a Java API?
```

注意：即使传了自定义查询，示例仍然会继续执行默认的 summarize 和 HotpotQA 段落。

## 输出说明

控制台会打印以下几类信息：

1. 当前 provider、model、embedding model、初始算法和输出目录。
2. `TaskMemoryService` 选中的 retrieval / summary 算法。
3. 首轮问答的结果和 `memories_used`。
4. 第二次交互总结后提取出的 memory 数量，以及持久化文件路径。
5. HotpotQA 每个问题的多轮执行状态、摘要提取结果和最终 memory 文件位置。

如果你重复运行该示例，并且 `output/memory_files` 下已经存在同一个 user id 对应的 memory 文件，`ContextEvolvingReActAgent` 会在启动时尝试把旧 memory 重新加载进当前 vector store。