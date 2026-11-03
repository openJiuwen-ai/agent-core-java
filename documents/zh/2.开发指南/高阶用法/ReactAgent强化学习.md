# ReActAgent 演化训练

这里重点说明 Java 当前已经落地的 ReActAgent 演化训练链路：`ReActAgentEvolve` 配合 `com.openjiuwen.agentevolving.*` 提供运行、评估、优化、回写和 checkpoint 闭环。

当前公开并由示例直接覆盖的主线，是“基于运行时 operator 的自演化 / 指令优化”路径，而不是独立的模型权重训练平台。

## 当前能力状态

| 能力模块 | Java 当前入口 | 当前状态 |
| --- | --- | --- |
| ReActAgent 演化训练入口 | `com.openjiuwen.core.single_agent.agents.ReActAgentEvolve` / `com.openjiuwen.core.single_agent.agents.ReActAgentEvolve` | 已提供，可直接运行与演化 |
| 训练闭环编排 | `com.openjiuwen.agentevolving.trainer.Trainer` | 已提供 |
| 自动评估 | `com.openjiuwen.agentevolving.evaluator.DefaultEvaluator` | 已提供，使用 LLM-as-a-judge |
| 指令优化 | `com.openjiuwen.agentevolving.optimizer.llm_call.InstructionOptimizer` | 已提供 |
| 参数写回 | `SingleDimUpdater` + `Trainer.applyUpdates(...)` | 已提供 |
| 训练恢复 | `DefaultCheckpointManager` + `FileCheckpointStore` | 已提供，本地 JSON checkpoint |
| 模型权重级训练 | 无公开 Java 实现 | 当前未提供 |

## Java 当前训练链路

Java 这条能力线的核心不是训练模型权重，而是让 `ReActAgentEvolve` 在多轮运行中暴露出可优化的 operator，然后由 evaluator 和 optimizer 根据 bad case 迭代这些 operator 的参数。

当前默认最重要的可调对象是 `react_llm`：

- `ReActAgentEvolve.getOperators()` 会返回 evolvable operator registry。
- `LLMCallOperator` 暴露 `system_prompt` 和 `user_prompt` 两个 tunable。
- Java 示例把优化目标显式限制为 `targets = List.of("system_prompt")`，保持当前 prompt 结构稳定。
- prompt 更新后会通过 `onLlmParameterUpdated(...)` 同步回 `ReActAgentConfig`，所以训练后的 agent 可以直接继续推理。

这意味着 Java 当前这条路径更准确的理解应当是：

`ReActAgentEvolve` 运行闭环 + `agent_evolving` 评估/优化/恢复机制。

而不是：

通用 RL 基础设施 + 独立 rollout 训练平台。

## 训练 / 评估 / 优化闭环

`examples/agent_evolving` 展示的是真实主线，闭环顺序如下：

1. 创建 `ReActAgentEvolve`，配置模型、system prompt、最大迭代次数等运行时参数。
2. 构造 `CaseLoader`，再按固定 seed 切分 train / val 数据集。
3. `Trainer.predict(...)` 为每个 case 创建 `AgentSession`，调用 `agent.invoke(...)`，同时保留 session 供后续轨迹提取。
4. `DefaultEvaluator.batchEvaluate(...)` 对预测结果做模型评估，输出 `EvaluatedCase` 列表和平均分。
5. `TracerTrajectoryExtractor` 从 session tracer 中抽取 `Trajectory`，把 agent span / workflow span 转成可供 updater 使用的结构化轨迹。
6. `SingleDimUpdater` 调用 `InstructionOptimizer.backward(...)` 和 `step()`，生成新的 `Updates`。
7. `Trainer` 把这些更新回写到 operator，如果有多个候选更新，会在验证集上选择得分更高的版本。
8. 当分数提升或命中保存周期时，`DefaultCheckpointManager` 会生成 checkpoint，`FileCheckpointStore` 把它写到本地 JSON。

可以把它理解成一条“演化训练回路”：

`invoke -> evaluate -> extract trajectory -> optimize -> apply updates -> validate -> checkpoint`

## 示例入口

Java 当前建议直接从 [examples/agent_evolving/README.md](../../../../examples/agent_evolving/README.md) 进入。示例主实现位于 `AgentEvolvingExampleSupport.java`，其中最重要的一段配置如下：

```java
Trainer trainer = new Trainer.Builder()
        .updater(new SingleDimUpdater(new InstructionOptimizer(modelConfig, clientConfig)))
        .evaluator(new DefaultEvaluator(modelConfig, clientConfig))
        .numParallel(2)
        .earlyStopScore(0.95)
        .checkpointDir(CHECKPOINT_DIR.toString())
        .resumeFrom(CHECKPOINT_FILE.toString())
        .checkpointEveryNEpochs(1)
        .checkpointOnImprove(true)
        .build();

trainer.train(agent, trainCases, valCases, 3, Map.of(
        "targets", List.of("system_prompt")
));
```

这段代码体现了 Java 当前页面应当关注的三个事实：

- 训练对象是已经可以正常 `invoke(...)` 的 `ReActAgentEvolve`。
- 优化器当前走的是 `InstructionOptimizer + SingleDimUpdater` 的 instruction 路径。
- checkpoint 是 example 自己管理的 `examples/agent_evolving/.checkpoints/latest.json`，不是 core Session checkpointer。

## 训练、验证与恢复路径

按示例的默认设置，完整运行路径是：

1. 读取 `examples/apiconfig.json` 中的模型配置。
2. 创建 `ReActAgentEvolve` 并设置基础 system prompt。
3. 切分训练集和验证集。
4. 训练时把验证集分数作为 early stop 依据，阈值默认是 `0.95`。
5. 将 operator state 与进度信息保存到 `examples/agent_evolving/.checkpoints/latest.json`。
6. 再次运行时，如果该文件已存在，`Trainer` 会先调用 `resumeIfNeeded(...)` 恢复 operator state 和最佳分数，再继续后续 epoch。
7. 训练结束后，示例会打印优化后的 prompt，并立即做一次推理验证。

如果你只想看“怎么跑”，直接按 [示例 README](../../../../examples/agent_evolving/README.md) 里的命令执行即可；如果你想理解 checkpoint 内容和回写方式，再继续读 [自优化Agent](自优化Agent.md)。

## 当前实现边界

- 这里聚焦的是 operator 参数优化，不涉及模型权重训练、独立 rollout 服务或分布式训练栈。
- Java 当前优化的是 operator 参数，最常见的是 `system_prompt`，不是模型权重。
- Java checkpoint 是 `agent_evolving` 自己的本地 JSON 快照，保存的是 `operators_state`、训练进度和少量元数据，不是统一训练平台的 checkpoint 体系。
- Java 示例直接复用正常的 `agent.invoke(...)`、`AgentSession` 和 tracer，训练数据来自运行时真实输出，而不是独立 rollout 服务。

更适合把它理解成 Java 当前的演化训练入口页，而不是通用 RL 框架页。

## 相关页面

- 如果你想继续看 evaluator、optimizer、trainer、checkpoint 怎样配合，转到 [自优化Agent](自优化Agent.md)。
- 如果你想看 prompt builder 这类开发期工具，而不是训练闭环，转到 [生成和优化提示词](生成和优化提示词.md)。

## 参考入口

- [高阶用法 README](README.md)
- [示例：agent_evolving](../../../../examples/agent_evolving/README.md)
- [源码：ReActAgentEvolve.java](../../../../src/main/java/com/openjiuwen/core/singleagent/ReActAgentEvolve.java)
- [源码：agents/ReActAgentEvolve.java](../../../../src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgentEvolve.java)
- [源码：Trainer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/trainer/Trainer.java)
- [源码：DefaultEvaluator.java](../../../../src/main/java/com/openjiuwen/agent_evolving/evaluator/DefaultEvaluator.java)
- [源码：InstructionOptimizer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/optimizer/llm_call/InstructionOptimizer.java)
- [源码：SingleDimUpdater.java](../../../../src/main/java/com/openjiuwen/agent_evolving/updater/SingleDimUpdater.java)
- [源码：TracerTrajectoryExtractor.java](../../../../src/main/java/com/openjiuwen/agent_evolving/trajectory/TracerTrajectoryExtractor.java)
- [源码：LLMCallOperator.java](../../../../src/main/java/com/openjiuwen/core/operator/llm_call/LLMCallOperator.java)
