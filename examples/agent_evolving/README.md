# ReAct Agent Evolving Java Example

这个目录提供 ReActAgent 演化训练的 Java 示例。

示例演示的是一个完整的自进化训练闭环：

1. 创建 `ReActAgentEvolve`
2. 构造训练/验证数据集并按固定 seed 切分
3. 配置 `DefaultEvaluator` 和 `InstructionOptimizer`
4. 通过 `Trainer` 执行多轮 instruction optimization
5. 保存/恢复 checkpoint，并在训练后做推理验证

## 文件说明

- `ReActAgentEvolvingExample.java`: 推荐入口，保持为一个薄包装类。
- `AgentEvolvingExampleSupport.java`: 示例主实现，负责 agent 创建、数据集、训练、checkpoint 和推理输出。
- `../SharedExampleApiConfigLoader.java`: 读取 `examples/apiconfig.json` 中的大模型配置。

## 配置

1. 运行时直接读取 `examples/apiconfig.json` 中的真实模型配置。
2. `examples/apiconfig_example.json` 只是脱敏模板，不会被运行时代码自动读取。
3. 当前示例复用 instruction 路径，也就是 `InstructionOptimizer + SingleDimUpdater`，并显式把优化目标限定为 `system_prompt`，以保持 Java 侧 prompt 结构稳定可运行。
4. checkpoint 默认写入 `examples/agent_evolving/.checkpoints/latest.json`。
5. 如果你想从头重新训练，可以先删除 `examples/agent_evolving/.checkpoints` 目录。

## 运行前提

1. 在 `examples/apiconfig.json` 中填入真实模型配置。
2. 从当前 Java 仓库根目录运行下面的命令，也就是包含 `pom.xml`、`examples` 和 `src` 的目录。
3. 示例会发起真实远程模型调用，训练耗时和最终输出会随模型响应变化而变化。
4. 相比普通单轮对话，这个示例会额外执行训练集推理、验证集评估和 optimizer 调用，因此需要模型账户具备足够的余额或调用额度。

## 运行方式

建议先在仓库根目录执行一次编译：

```powershell
mvn -DskipTests compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/agent_evolving.classpath"
javac -cp "target/classes;$(Get-Content target/agent_evolving.classpath -Raw)" examples/SharedExampleApiConfigLoader.java examples/agent_evolving/AgentEvolvingExampleSupport.java examples/agent_evolving/ReActAgentEvolvingExample.java
java -Dfile.encoding=UTF-8 -cp "target/classes;examples;examples/agent_evolving;$(Get-Content target/agent_evolving.classpath -Raw)" ReActAgentEvolvingExample
```

也可以在最后一条命令后追加一个训练后的测试问题，例如：

```powershell
java -Dfile.encoding=UTF-8 -cp "target/classes;examples;examples/agent_evolving;$(Get-Content target/agent_evolving.classpath -Raw)" ReActAgentEvolvingExample 请解释一下什么是机器学习
```

## 输出说明

示例会打印以下几类信息：

1. 当前使用的 provider、model、apiBase、SSL 配置
2. 训练集/验证集切分结果
3. checkpoint 保存或恢复位置
4. 优化后的 prompt template
5. 训练后推理结果对象，通常包含：
   - `output`: Agent 最终回答
   - `result_type`: 一般为 `answer`

如果你重复运行该示例，并且 `.checkpoints/latest.json` 已存在，`Trainer` 会尝试从上一次结果继续恢复。训练阶段是否会提前停止，取决于验证分数是否达到 `0.95`。