# 自优化Agent

Java 版这里已经有一条可运行的“自优化 Agent”主路径，核心链路是：

`ReActAgentEvolve` + `DefaultEvaluator` + `InstructionOptimizer` + `SingleDimUpdater` + `Trainer` + checkpoint

如果只看标题，容易把它理解成一个更大而全的训练框架。实际更合适的定位是：

- Java 当前已经有一条可运行的 agent 自演化闭环。
- 这条闭环围绕 `com.openjiuwen.agent_evolving.*` 构建。
- 仓库里也保留了较早的 `com.openjiuwen.dev_tools.tune.*` prompt tuning 路径，但它更多面向 legacy `BaseAgent` / `LLMCall`，不应混同为本页的当前主线。

## 当前能力状态

| 关注点 | Java 当前主路径 | 说明 |
| --- | --- | --- |
| 可优化 Agent | `ReActAgentEvolve` | 通过 `getOperators()` 暴露可调 operator |
| 评估器 | `com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator` | 用 LLM 判断预测与 label 是否一致 |
| 优化器 | `com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer` | 依据 bad case 生成 textual gradient 并改 prompt |
| 更新器 | `SingleDimUpdater` | 负责 bind targets、调用 optimizer、产出 `Updates` |
| 训练器 | `com.openjiuwen.agent_evolving.trainer.Trainer` | 编排 forward / evaluate / update / validate / checkpoint |
| checkpoint | `DefaultCheckpointManager` + `FileCheckpointStore` | 本地 JSON 保存与恢复 |
| 示例入口 | `examples/agent_evolving` | 当前最可靠的真实运行入口 |

## 组件怎么协作

这条链路的协作关系可以按“谁负责什么”来记：

### `ReActAgentEvolve`

- 负责正常的 ReAct 推理循环。
- 内部持有 `LLMCallOperator` 与 `ToolCallOperator`。
- `getOperators()` 会把当前可演化 operator 暴露出来，供 trainer / updater / checkpoint 使用。

### `DefaultEvaluator`

- 输入是 `Case` 和 Agent 预测结果。
- 内部使用一个独立 `Model` 作为 judge。
- 输出 `EvaluatedCase`，包含 `score`、`reason`、`answer` 等信息。

### `InstructionOptimizer`

- 只处理 LLM prompt 这一类 textual parameter。
- `backward(...)` 阶段会根据 bad case 生成 textual gradient。
- `step()` 阶段会生成新的 `Updates`，目标通常是 `system_prompt` 或 `user_prompt`。
- 当前实现会尽量恢复原 prompt 里的占位符，避免优化后把 `{{query}}` 之类变量弄丢。

### `SingleDimUpdater`

- 把 `targets` 绑定到真正可调的 operator tunable 上。
- 收集 `Trajectory` 和 `EvaluatedCase` 后，调用 optimizer 的 `backward(...)` 与 `step()`。
- 当前 `getState()` / `loadState(...)` 还是空实现，所以 checkpoint 主要恢复的是 operator state，而不是复杂的 optimizer 内部状态。

### `Trainer`

- 为每个 case 创建独立 `AgentSession` 并调用 `agent.invoke(...)`。
- 用 `TracerTrajectoryExtractor` 从 session tracer 抽取轨迹。
- 评估当前结果。
- 应用更新，必要时在验证集上挑选更优候选。
- 维护 early stop、epoch 进度、checkpoint 保存与恢复。

### checkpoint 组件

- `DefaultCheckpointManager` 负责决定何时保存，以及如何把 `operators_state`、最佳分数、epoch 等信息打包成 `EvolveCheckpoint`。
- `FileCheckpointStore` 负责把 checkpoint 持久化为本地 JSON，并在恢复时重新读回。

## 一次完整自优化是怎么跑起来的

下面这条流程就是 Java 当前“自优化 Agent”的标准路径：

1. 创建并配置 `ReActAgentEvolve`。
2. 准备 `CaseLoader`，划分 train / val。
3. 创建 `DefaultEvaluator`。
4. 创建 `InstructionOptimizer`。
5. 用 `SingleDimUpdater` 包住 optimizer，并指定训练目标，例如 `system_prompt`。
6. 创建 `Trainer`，配置并行度、early stop、checkpoint 目录和恢复路径。
7. 调用 `trainer.train(...)` 开始多轮优化。
8. 训练后继续用同一个 agent 做推理验证。

对应示例代码如下：

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

这段代码已经基本把本页的核心边界说完了：

- Java 当前主打的是 instruction self-evolution。
- 训练对象是 Agent 暴露出来的 operator tunable。
- 训练输出是优化后的 operator state，不是模型权重文件。

## evaluator、optimizer、trainer、checkpoint 的协作方式

如果把组件串起来，可以得到下面这条更细的执行链：

1. `Trainer.predict(...)` 调用 `agent.invoke(...)`，拿到预测结果和 session。
2. `DefaultEvaluator.batchEvaluate(...)` 根据 case label 和预测输出打分。
3. `Trainer.forward(...)` 额外把 session 交给 `TracerTrajectoryExtractor`，抽成 `Trajectory`。
4. `SingleDimUpdater.update(...)` 把 trajectory 和 bad case 交给 `InstructionOptimizer`。
5. `InstructionOptimizer` 产出 `Updates`。
6. `Trainer.applyUpdates(...)` 通过 operator 的 `setParameter(...)` / `loadState(...)` 把更新写回。
7. `Trainer.evaluate(...)` 在验证集上重新评估。
8. 如果达到保存条件，`DefaultCheckpointManager.buildCheckpoint(...)` 生成快照，`FileCheckpointStore.saveCheckpoint(...)` 落盘。

这里最关键的一点是：Java 当前不是把优化结果写到某个脱离 Agent 的外部训练对象里，而是直接把结果写回 Agent 的 operator registry。所以训练完成后，同一个 agent 实例就能立刻继续推理。

## checkpoint 与恢复

当前示例默认把 checkpoint 写到：

`examples/agent_evolving/.checkpoints/latest.json`

Java 这套 checkpoint 的内容重点是：

- `operators_state`
- `best_score`
- `epoch`
- `last_metrics`
- `updater_state`

其中最重要的是 `operators_state`，因为它保存了诸如 `system_prompt`、`user_prompt` 这类实际被调优后的 operator 参数。再次运行时：

- `Trainer.resumeIfNeeded(...)` 会读取 `resumeFrom` 指向的 checkpoint。
- `DefaultCheckpointManager.restore(...)` 把 operator state 恢复到 agent 上。
- `progress.startEpoch` 和 `progress.bestScore` 也会同步恢复。

需要注意的边界是：

- 这是 `agent_evolving` 自己的 checkpoint 机制。
- 它和 core Session / Workflow 的 checkpointer 不是同一个子系统。
- 当前 `SingleDimUpdater` 没有稳定的内部可恢复状态，因此恢复重点仍然是 operator 和训练进度。

## 它和普通 `ReActAgent` 使用方式有什么区别

普通 Agent 使用方式关注的是：

- 构建 Agent
- 配模型和工具
- 调 `invoke(...)` 或 `stream(...)`

自优化路径额外增加了：

- 训练 / 验证数据集
- 自动评估器
- 优化器与 updater
- 多 epoch 训练循环
- checkpoint / resume
- 训练后的 prompt/operator 回写

所以这条路径适合的是：

“我已经有一个能跑的 Agent，但希望它基于 case 和评估反馈自动改进。”

而不是：

“我只是想完成一次普通问答或工具调用。”

## 与旧 `dev_tools.tune` 路径的区别

仓库里还有一套较早的 prompt tuning 实现：`com.openjiuwen.dev_tools.tune.*`。为了避免概念混淆，这里把两条路径分开写：

| 维度 | 当前主路径 `agent_evolving` | 旧路径 `dev_tools.tune` |
| --- | --- | --- |
| 主要对象 | `ReActAgentEvolve` + operator registry | legacy `BaseAgent` + `LLMCall` |
| 绑定方式 | `getOperators()` / tunable targets | `getLlmCalls()` |
| 运行数据 | `AgentSession` + tracer trajectory | 主要是 prompt tuning 数据闭环 |
| checkpoint | 内置 `FileCheckpointStore` / `DefaultCheckpointManager` | 当前默认 checkpoint 方案 |
| 当前推荐理解 | Java 当前推荐理解路径 | 历史 / 相邻能力，不作为当前主线 |

与 [生成和优化提示词](生成和优化提示词.md) 的区别在于：前者讨论开发期 prompt builder 工具；`dev_tools.tune` 更接近旧的 prompt tuning 实现；这里讨论的是 `agent_evolving` 这条已经通过 Java example 落地的 agent 自演化路径。

## 当前实现边界

- Java 当前主线是 instruction optimization 和 operator writeback。
- Java 这条链路目前最可靠的入口是 `examples/agent_evolving`，不是抽象文档层面的假设能力。
- 想理解“提示词怎么生成或微调”时，回到 [生成和优化提示词](生成和优化提示词.md)；想理解“Agent 怎么在 case 驱动下自我迭代”时，再重点阅读这里。

## 参考入口

- [高阶用法 README](README.md)
- [ReActAgent演化训练](ReactAgent强化学习.md)
- [生成和优化提示词](生成和优化提示词.md)
- [示例：agent_evolving](../../../../examples/agent_evolving/README.md)
- [源码：AgentEvolvingExampleSupport.java](../../../../examples/agent_evolving/AgentEvolvingExampleSupport.java)
- [源码：Trainer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/trainer/Trainer.java)
- [源码：DefaultEvaluator.java](../../../../src/main/java/com/openjiuwen/agent_evolving/evaluator/DefaultEvaluator.java)
- [源码：InstructionOptimizer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/optimizer/llm_call/InstructionOptimizer.java)
- [源码：SingleDimUpdater.java](../../../../src/main/java/com/openjiuwen/agent_evolving/updater/SingleDimUpdater.java)
- [源码：DefaultCheckpointManager.java](../../../../src/main/java/com/openjiuwen/agent_evolving/checkpointing/DefaultCheckpointManager.java)
- [源码：FileCheckpointStore.java](../../../../src/main/java/com/openjiuwen/agent_evolving/checkpointing/FileCheckpointStore.java)
- [源码：旧 Trainer.java](../../../../src/main/java/com/openjiuwen/dev_tools/tune/trainer/Trainer.java)
