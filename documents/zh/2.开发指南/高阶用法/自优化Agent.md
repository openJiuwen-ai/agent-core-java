当前在大模型应用上积累的badcase主要用于直接微调大模型进行干预，但该方法实现成本高，且case修复的周期取决于大模型微调的版本，无法做到即时干预，因此对Agent中提示词的自动调优需求十分急迫。

openJiuwen提供的对Agent提示词的自动调优算法，采用基于指令和示例的联合优化机制，在用户对已有应用场景中出现的错误案例进行标注后，同时优化指令和示例，提供自动调优后的提示词，从而有效修复错误案例，快速提升特定场景下的提示词效果。

以下将介绍自动调优Agent的整个流程，包括优化前准备、Agent性能评估、Agent优化。另外openJiuwen框架对训练过程进行了整合，用户可以直接通过Trainer类来统一执行优化任务。

注意：当前Java版本支持`ReActAgentEvolve`类型的Agent调优。

# 自优化Agent流程

以下将介绍自动调优Agent的完整流程，包括：

- **优化前准备**：准备好待优化的Agent和标注好的数据集。
- **评估优化前的Agent**：创建评估器，评估Agent在数据集上的表现。
- **优化Agent**：创建优化器优化Agent，评估优化后Agent的表现。

## 优化前准备

### 构建待调优的Agent

假设任务是问答场景，首先需要构建一个`ReActAgentEvolve`，Agent配置如下：

```java
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.schema.AgentCard;

// 大模型配置（从环境变量或配置文件读取）
String apiBase = "your api base";
String apiKey = "your api key";
String modelName = "your model name";
String modelProvider = "your model provider";
boolean sslVerify = false;

// Agent系统提示词
String SYSTEM_PROMPT = "你是一个 helpful 的 AI 助手。"
        + "请直接回答用户的问题，如果需要可以使用工具来辅助回答。";

// 创建AgentCard
AgentCard agentCard = AgentCard.builder()
        .id("qa_agent")
        .name("问答助手")
        .description("问答场景的自优化Agent")
        .build();

// 创建ReActAgentEvolve
ReActAgentEvolve agent = new ReActAgentEvolve(agentCard);

// 配置Agent
ReActAgentConfig config = ReActAgentConfig.builder()
        .maxIterations(3)
        .build()
        .configureModelClient(
                modelProvider,
                apiKey,
                apiBase,
                modelName,
                sslVerify
        )
        .configurePromptTemplate(List.of(SystemMessage.builder().content(SYSTEM_PROMPT).build()));

// 设置模型参数
if (config.getModelConfigObj() != null) {
    config.getModelConfigObj().setTemperature(0.3);
    config.getModelConfigObj().setTopP(0.9);
    config.getModelConfigObj().setMaxTokens(1000);
}

agent.configure(config);
```

Agent的输入输出可以根据通用ReActAgent的输入输出类型明确为：

- **输入(Map格式)**：`{"query": "用户提问内容"}`
- **输出(Map格式)**：`{"output": "Agent回答", "result_type": "answer"}`

### 构造数据集

首先，需要准备调优使用的Case（案例），其概念类似机器学习中的数据集，有inputs(输入)和标注好的参考答案label（标签）。下面准备一个在问答场景下的几条样例，作为任务的Case数据集。

```java
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;

CaseLoader caseLoader = new CaseLoader(List.of(
        new Case(
                Map.of("query", "什么是机器学习？"),
                Map.of("answer", "机器学习是人工智能的一个分支，通过算法从数据中学习规律。"),
                "qa_case_1"
        ),
        new Case(
                Map.of("query", "Python 如何读取文件？"),
                Map.of("answer", "使用 open() 函数，例如：with open('file.txt', 'r') as f: content = f.read()"),
                "qa_case_2"
        ),
        new Case(
                Map.of("query", "水的化学式是什么？"),
                Map.of("answer", "水的化学式是 H2O，由两个氢原子和一个氧原子组成。"),
                "qa_case_3"
        ),
        new Case(
                Map.of("query", "光速大约是多少？"),
                Map.of("answer", "光速在真空中约为每秒 30 万公里，即 3x10^8 米/秒。"),
                "qa_case_4"
        ),
        new Case(
                Map.of("query", "地球的直径是多少？"),
                Map.of("answer", "地球的平均直径约为 12,742 公里。"),
                "qa_case_5"
        )
));
```

## 评估优化前的Agent

### 评估原始Agent的表现

准备好数据集和Agent后，可以先评估一下当前Agent的表现。例如，创建一个基于模型打分的评估器`DefaultEvaluator`，来对数据集中每个Case的表现进行打分。

```java
import com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.session.internal.AgentSession;
import java.util.UUID;
import java.util.List;

// 创建评估器使用的模型配置
ModelRequestConfig modelConfig = ModelRequestConfig.builder()
        .modelName(modelName)
        .temperature(0.3)
        .topP(0.9)
        .maxTokens(1000)
        .build();

ModelClientConfig clientConfig = ModelClientConfig.builder()
        .clientProvider(modelProvider)
        .apiBase(apiBase)
        .apiKey(apiKey)
        .verifySsl(sslVerify)
        .timeout(120.0)
        .build();

// 创建评估器
DefaultEvaluator evaluator = new DefaultEvaluator(modelConfig, clientConfig);

// 运行Agent获取预测结果
List<Map<String, Object>> predicts = new ArrayList<>();
for (Case case : caseLoader.getCases()) {
    Map<String, Object> result = (Map<String, Object>) agent.invoke(
            case.getInputs(),
            new AgentSession(UUID.randomUUID().toString())
    );
    predicts.add(result);
}

// 评估Agent的表现
List<EvaluatedCase> results = evaluator.batchEvaluate(caseLoader.getCases(), predicts);
for (EvaluatedCase evalResult : results) {
    System.out.printf("score: %.1f, reason: %s, answer: %s, label: %s%n",
            evalResult.getScore(),
            evalResult.getReason(),
            evalResult.getAnswer(),
            evalResult.getCase().getLabel());
}
```

**样例输出**

```text
score: 1.0, reason: 回答内容与标准答案一致，准确解释了机器学习的概念。, answer: {output=机器学习是人工智能的一个分支...}, label: {answer=机器学习是人工智能的一个分支...}
score: 0.5, reason: 回答不够完整，缺少示例代码。, answer: {output=Python读取文件可以用open函数}, label: {answer=使用 open() 函数，例如：with open('file.txt', 'r') as f: content = f.read()}
score: 1.0, reason: 回答准确，化学式正确。, answer: {output=水的化学式是 H2O}, label: {answer=水的化学式是 H2O，由两个氢原子和一个氧原子组成。}
score: 1.0, reason: 回答准确，数值正确。, answer: {output=光速约为每秒 30 万公里}, label: {answer=光速在真空中约为每秒 30 万公里，即 3x10^8 米/秒。}
score: 0.5, reason: 回答不够精确，缺少具体数值。, answer: {output=地球直径约 12000 公里}, label: {answer=地球的平均直径约为 12,742 公里。}
```

## 优化Agent

### 构建优化器

做好前置准备后，可以使用openJiuwen优化器，通过运行、评估、优化的流程，来优化和修正Agent中的提示词。目前openJiuwen框架Java版本提供了以下优化器：

- **InstructionOptimizer**：提示词指令优化器，基于数据集评估结果反馈修正提示词内容。

这里以指令优化器来对Agent进行优化为例，InstructionOptimizer的创建、运行需要以下步骤：

1. **创建优化器**：配置优化模型信息，优化器使用的模型配置，推荐使用比较强大的模型，获取更好优化效果。
2. **执行优化器**：通过Trainer来编排运行、评估、更新流程。

```java
import com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agent_evolving.updater.SingleDimUpdater;
import com.openjiuwen.agent_evolving.trainer.Trainer;

// 划分训练集和验证集
CaseLoader[] split = caseLoader.split(0.6, 7);
CaseLoader trainCases = split[0];
CaseLoader valCases = split[1].isEmpty() ? split[0] : split[1];

// 创建优化器
InstructionOptimizer optimizer = new InstructionOptimizer(modelConfig, clientConfig);

// 创建更新器
SingleDimUpdater updater = new SingleDimUpdater(optimizer);

// 创建训练器
Trainer trainer = new Trainer.Builder()
        .updater(updater)
        .evaluator(evaluator)
        .numParallel(2)
        .earlyStopScore(0.95)
        .checkpointDir(".checkpoints")
        .checkpointEveryNEpochs(1)
        .checkpointOnImprove(true)
        .build();

// 执行训练
trainer.train(agent, trainCases, valCases, 3, Map.of(
        "targets", List.of("system_prompt")
));
```

训练日志：

```text
[train] starting instruction optimization...
[train iteration: (baseline), score: 0.7
[train iteration: 1, score: 0.85
[train iteration: 2, score: 0.95
[train] finished.
```

### 评估优化后Agent的表现

优化完成后，优化器会自动修改Agent中相关的提示词内容。使用优化后的Agent来看看效果：

```java
// 运行优化后的Agent获取预测结果
List<Map<String, Object>> predictsAfter = new ArrayList<>();
for (Case case : caseLoader.getCases()) {
    Map<String, Object> result = (Map<String, Object>) agent.invoke(
            case.getInputs(),
            new AgentSession(UUID.randomUUID().toString())
    );
    predictsAfter.add(result);
}

// 评估Agent的表现
List<EvaluatedCase> resultsAfter = evaluator.batchEvaluate(caseLoader.getCases(), predictsAfter);
for (EvaluatedCase evalResult : resultsAfter) {
    System.out.printf("score: %.1f, reason: %s, answer: %s, label: %s%n",
            evalResult.getScore(),
            evalResult.getReason(),
            evalResult.getAnswer(),
            evalResult.getCase().getLabel());
}
```

**样例输出**

```text
score: 1.0, reason: 回答完整准确，与标准答案一致。, answer: {output=机器学习是人工智能的一个分支，通过算法从数据中学习规律。}, label: {answer=机器学习是人工智能的一个分支...}
score: 1.0, reason: 回答包含完整示例代码，与标准答案一致。, answer: {output=使用 open() 函数，例如：with open('file.txt', 'r') as f: content = f.read()}, label: {answer=使用 open() 函数...}
score: 1.0, reason: 回答准确完整。, answer: {output=水的化学式是 H2O，由两个氢原子和一个氧原子组成。}, label: {answer=水的化学式是 H2O...}
score: 1.0, reason: 回答准确，数值正确。, answer: {output=光速在真空中约为每秒 30 万公里，即 3x10^8 米/秒。}, label: {answer=光速在真空中约为每秒 30 万公里...}
score: 1.0, reason: 回答精确，数值正确。, answer: {output=地球的平均直径约为 12,742 公里。}, label: {answer=地球的平均直径约为 12,742 公里。}
```

可以看到，优化后的Agent在该数据集上表现提升了！

# 通过训练器优化Agent

openJiuwen框架对运行、评估、优化过程进行了整合，提供训练类Trainer。用户可以通过Trainer类来一键式完成Agent训练任务的创建。

## 训练前准备

与上述自优化Agent流程一致，首先需要构建好待调优的Agent与数据集。

```java
import com.openjiuwen.agent_evolving.dataset.CaseLoader;

// 创建Agent
AgentCard agentCard = AgentCard.builder()
        .id("qa_agent")
        .name("问答助手")
        .description("问答场景的自优化Agent")
        .build();

ReActAgentEvolve agent = new ReActAgentEvolve(agentCard);
ReActAgentConfig config = ReActAgentConfig.builder()
        .maxIterations(3)
        .build()
        .configureModelClient(modelProvider, apiKey, apiBase, modelName, sslVerify)
        .configurePromptTemplate(List.of(SystemMessage.builder().content(SYSTEM_PROMPT).build()));
agent.configure(config);

// 创建数据集
CaseLoader caseLoader = new CaseLoader(List.of(
        new Case(Map.of("query", "什么是机器学习？"), Map.of("answer", "机器学习是人工智能的一个分支..."), "qa_case_1"),
        new Case(Map.of("query", "Python 如何读取文件？"), Map.of("answer", "使用 open() 函数..."), "qa_case_2"),
        // 更多案例...
));

// 划分训练集和验证集
CaseLoader[] split = caseLoader.split(0.6, 7);
CaseLoader trainCases = split[0];
CaseLoader valCases = split[1].isEmpty() ? split[0] : split[1];
```

## 创建训练器

训练器需要绑定评估器和优化器。训练器的创建步骤如下：

1. 创建优化器使用的模型配置。
2. 创建评估器。
3. 创建优化器和更新器。
4. 创建训练器，绑定优化器和评估器，同时可以配置并行数、早停参数、checkpoint配置。

```java
import com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agent_evolving.updater.SingleDimUpdater;
import com.openjiuwen.agent_evolving.trainer.Trainer;

// 创建模型配置
ModelRequestConfig modelConfig = ModelRequestConfig.builder()
        .modelName(modelName)
        .temperature(0.3)
        .topP(0.9)
        .maxTokens(1000)
        .build();

ModelClientConfig clientConfig = ModelClientConfig.builder()
        .clientProvider(modelProvider)
        .apiBase(apiBase)
        .apiKey(apiKey)
        .verifySsl(sslVerify)
        .timeout(120.0)
        .build();

// 创建评估器
DefaultEvaluator evaluator = new DefaultEvaluator(modelConfig, clientConfig);

// 创建优化器和更新器
InstructionOptimizer optimizer = new InstructionOptimizer(modelConfig, clientConfig);
SingleDimUpdater updater = new SingleDimUpdater(optimizer);

// 创建训练器
Trainer trainer = new Trainer.Builder()
        .updater(updater)
        .evaluator(evaluator)
        .numParallel(2)
        .earlyStopScore(0.95)
        .checkpointDir(".checkpoints")
        .resumeFrom(".checkpoints/latest.json")
        .checkpointEveryNEpochs(1)
        .checkpointOnImprove(true)
        .build();
```

## 训练/优化

创建好训练器之后，只需要调用`train`接口。接口包含如下参数：

- **agent**：待优化的Agent。
- **trainCases**：训练使用的数据集。
- **valCases**：验证使用的数据集。
- **numIterations**：训练的迭代轮次。
- **options**：可选训练参数，如优化目标（targets）。

调用训练器调优Agent，会基于每轮最好的结果持续迭代，并应用最优的提示词结果到Agent中。

```java
// 执行训练
trainer.train(agent, trainCases, valCases, 3, Map.of(
        "targets", List.of("system_prompt")
));
```

训练日志：

```text
[train] starting instruction optimization...
[train iteration: (baseline), score: 0.7
[train iteration: 1, score: 0.85
[train iteration: 2, score: 0.95
[train] finished.
```

## 评估Agent的表现

Trainer集成了Agent批量运行、批量评估的功能，用户可以使用如下接口：

批量预测：

```java
List<Map<String, Object>> predicts = trainer.predict(agent, trainCases);
```

样例输出：

```text
[{output=机器学习是人工智能的一个分支..., result_type=answer}
 {output=使用 open() 函数..., result_type=answer}
 {output=水的化学式是 H2O..., result_type=answer}
 ...]
```

批量评估：

```java
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;

double avgScore = trainer.evaluate(agent, valCases);
List<EvaluatedCase> evaluatedCases = trainer.getEvaluatedCases();

// 打印评估细节
for (EvaluatedCase evalResult : evaluatedCases) {
    System.out.printf("score: %.1f, reason: %s%n",
            evalResult.getScore(),
            evalResult.getReason());
}
```

样例输出：

```text
score: 1.0, reason: 回答准确完整，与标准答案一致。
score: 1.0, reason: 回答包含完整示例代码，符合预期。
score: 1.0, reason: 回答精确，数值正确。
```

# Checkpoint与恢复

训练过程中，Trainer会自动保存checkpoint到配置的目录。Checkpoint包含：

- `operators_state`：优化后的operator状态（如system_prompt）
- `best_score`：最佳分数
- `epoch`：当前轮次
- `last_metrics`：最近指标

如果训练中断，可以通过`resumeFrom`参数从checkpoint恢复：

```java
Trainer trainer = new Trainer.Builder()
        .updater(updater)
        .evaluator(evaluator)
        .checkpointDir(".checkpoints")
        .resumeFrom(".checkpoints/latest.json")  // 从checkpoint恢复
        .build();
```

恢复时，`Trainer`会：

1. 读取checkpoint文件
2. 把operator state恢复到agent上
3. 同步训练进度（epoch、bestScore）

# 组件协作方式

这条链路的协作关系可以按"谁负责什么"来记：

### `ReActAgentEvolve`

- 负责正常的ReAct推理循环
- 内部持有`LLMCallOperator`与`ToolCallOperator`
- `getOperators()`会把当前可演化operator暴露出来，供trainer/updater使用

### `DefaultEvaluator`

- 输入是`Case`和Agent预测结果
- 内部使用一个独立`Model`作为judge
- 输出`EvaluatedCase`，包含`score`、`reason`、`answer`等信息

### `InstructionOptimizer`

- 只处理LLM prompt这一类textual parameter
- 根据bad case生成textual gradient
- 生成新的`Updates`，目标通常是`system_prompt`

### `SingleDimUpdater`

- 把`targets`绑定到真正可调的operator tunable上
- 收集`Trajectory`和`EvaluatedCase`后，调用optimizer

### `Trainer`

- 为每个case创建独立`AgentSession`并调用`agent.invoke(...)`
- 评估当前结果
- 应用更新，必要时在验证集上挑选更优候选
- 维护early stop、epoch进度、checkpoint保存与恢复

# 参考入口

- [示例：agent_evolving](../../../../examples/agent_evolving/README.md)
- [源码：AgentEvolvingExampleSupport.java](../../../../examples/agent_evolving/AgentEvolvingExampleSupport.java)
- [源码：Trainer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/trainer/Trainer.java)
- [源码：DefaultEvaluator.java](../../../../src/main/java/com/openjiuwen/agent_evolving/evaluator/DefaultEvaluator.java)
- [源码：InstructionOptimizer.java](../../../../src/main/java/com/openjiuwen/agent_evolving/optimizer/llm_call/InstructionOptimizer.java)