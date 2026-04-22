# Context Evolver

Context Evolver 是 openJiuwen Java版本的记忆管理扩展，为智能体提供从过去交互中学习并检索相关知识的能力。它实现了三种先进的记忆算法：**ACE**（Agentic Context Engineering）、**RB**（Reasoning Bank）和 **ReMe**（Remember Me, Refine Me）。

## 概述

此扩展使智能体能够从过去的交互中学习，并检索相关知识以增强未来的响应。它实现了三种记忆算法：

- **ACE (Agentic Context Engineering)**：使用 `content` 和 `section` 字段进行结构化记忆存储，基于 Playbook 组织
- **RB (Reasoning Bank)**：使用 `title`、`description` 和 `content` 字段进行面向知识的记忆存储，支持丰富的描述
- **ReMe (Remember Me, Refine Me)**：使用 `whenToUse` 和 `content` 字段，结合向量检索与基于 LLM 的重排序和重写，实现智能记忆管理

## 快速开始

### 前置条件

1. 在配置文件中设置API凭证
2. 配置算法设置参数

## 配置

### 1. API配置（凭证）

```java
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

ModelClientConfig clientConfig = ModelClientConfig.builder()
        .baseUrl("https://api.openai.com/v1")
        .apiKey("your-api-key")
        .build();
```

### 2. 算法配置

通过config.yaml配置文件设置算法参数：

```yaml
# 算法选择: ACE/RB/REME
RETRIEVAL_ALGO: "REME"
SUMMARY_ALGO: "REME"
MANAGEMENT_ALGO: "REME"

USE_GOLDLABEL: true

# MATTS配置（仅适用于RB算法）
MATTS_DEFAULT_K: 3
MATTS_DEFAULT_TEMPERATURE: 0.9
MATTS_DEFAULT_MODE: "parallel"  # none/parallel/sequential/combined

# ACE配置
USE_GROUNDTRUTH: true
MAX_PLAYBOOK_SIZE: 50

# RB配置
TOPK_QUERY: 1

# ReMe配置
TOPK_RETRIEVAL: 10
LLM_RERANK: true
TOPK_RERANK: 5
LLM_REWRITE: true
MEMORY_VALIDATION: true
EXTRACT_BEST_TRAJ: true
EXTRACT_WORST_TRAJ: true
EXTRACT_COMPARATIVE_TRAJ: true
MEMORY_DEDUPLICATION: true
MEMORY_UPDATE: true
DELETE_USAGE_THRESHOLD: 5
DELETE_UTILITY_THRESHOLD: 0.5
```

配置文件默认路径：`src/main/java/com/openjiuwen/extensions/context_evolver/config.yaml`

## 主要特性

- **多算法支持**：根据用例选择 ACE、ReasoningBank 和 ReMe 算法
- **语义记忆检索**：基于语义相似性和基于 LLM 的重排序检索相关记忆
- **轨迹总结**：从智能体交互中提取学习内容并自动存储为新记忆
- **MATTS 扩展**：用于多跳查询并行/顺序处理的记忆感知测试时扩展
- **按用户记忆管理**：每个用户隔离的记忆集合，支持添加/清除/检索操作
- **自动记忆注入**：无需代码更改即可用检索到的记忆增强智能体提示
- **向量存储集成**：内置语义相似性搜索的向量存储
- **文件持久化**：将记忆保存到 JSON 文件或从 JSON 文件加载，实现持久存储

## 架构

```
com.openjiuwen.extensions.context_evolver/
├── ContextEvolvingReActAgent.java     # 记忆增强型 ReActAgent 子类
├── MemoryAgentConfigInput.java        # 配置输入参数类
├── config.yaml                        # 默认配置文件
├── core/
│   ├── config/Config.java             # 配置加载器
│   ├── context/RuntimeContext.java    # 运行时上下文
│   ├── context/ServiceContext.java    # 服务上下文
│   ├── file_connector/JSONFileConnector.java  # JSON 文件连接器
│   ├── op/BaseOp.java                 # 基础操作类
│   ├── op/ParallelOp.java             # 并行操作类
│   ├── op/SequentialOp.java           # 顺序操作类
│   ├── schema/                        # 核心数据模型
│   └── vector_store/MemoryVectorStore.java  # 向量存储
├── service/
│   ├── TaskMemoryService.java         # 核心记忆服务（检索/摘要）
│   ├── AddMemoryRequest.java          # 添加记忆请求类
│   ├── ReMeSummarizeMemoryOp.java     # ReMe 摘要操作
│   └── ReasoningBankSummarizeMemoryOp.java  # RB 摘要操作
├── retrieve/task/                     # 检索算法
│   ├── ace/RecallMemoryOp.java        # ACE 检索
│   ├── reasoning_bank/RecallMemoryOp.java  # RB 检索
│   └── reme/RecallMemoryOp.java       # ReMe 检索
├── summary/task/                      # 摘要算法
│   ├── ace/                           # ACE 摘要组件
│   ├── reasoning_bank/                # RB 摘要组件
│   └── reme/                          # ReMe 摘要组件
├── schema/                            # 数据模型
│   ├── ACEMemory.java                 # ACE 记忆模型
│   ├── ReMeMemory.java                # ReMe 记忆模型
│   ├── ReasoningBankMemory.java       # RB 记忆模型
│   └── ...                            # 其他模型类
└── tool/
    └── WikipediaTool.java             # Wikipedia 工具
```

## 组件

### ContextEvolvingReActAgent

一个 `ReActAgent` 子类，在处理查询前自动检索相关记忆：

```java
import com.openjiuwen.extensions.context_evolver.ContextEvolvingReActAgent;
import com.openjiuwen.extensions.context_evolver.MemoryAgentConfigInput;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;

// 步骤 1：创建记忆服务（共享实例）
TaskMemoryService memoryService = new TaskMemoryService(
        "gpt-4",                           // llmModel
        "text-embedding-3-small",          // embeddingModel
        "your-api-key",                    // apiKey
        "ReMe",                            // retrievalAlgo
        "ReMe"                             // summaryAlgo
);

// 步骤 2：添加记忆（使用算法特定参数）
AddMemoryRequest request = AddMemoryRequest.builder()
        .whenToUse("当被问及数据库优化时")
        .content("始终在频繁查询的列上使用索引。对于高流量应用考虑使用连接池。")
        .build();

memoryService.addMemory("test_user", request);

// 步骤 3：创建智能体卡片
AgentCard agentCard = AgentCard.builder()
        .id("memory-react-agent")
        .name("memory-react-agent")
        .description("具有自动记忆注入的 ReActAgent")
        .build();

// 步骤 4：创建 ContextEvolvingReActAgent 实例
ContextEvolvingReActAgent agent = new ContextEvolvingReActAgent(
        agentCard,
        "test_user",                       // userId
        memoryService,
        true                               // injectMemoriesInContext
);

// 步骤 5：配置智能体
MemoryAgentConfigInput configInput = new MemoryAgentConfigInput(
        "OpenAI",          // modelProvider
        "your-api-key",    // apiKey
        "https://api.openai.com/v1",  // apiBase
        "gpt-4",           // modelName
        "你是一个有帮助的软件工程助手。使用提供的记忆上下文来增强你的回答。",  // systemPrompt
        5                  // maxIterations
);

ReActAgentConfig agentConfig = ContextEvolvingReActAgent.createMemoryAgentConfig(configInput);
agent.configure(agentConfig);

// 步骤 6：使用自动记忆检索进行调用
Map<String, Object> result = agent.invoke(Map.of(
        "query", "如何优化我的数据库查询？"
));

String output = (String) result.getOrDefault("output", "无输出");
int memoriesUsed = (int) result.getOrDefault("memories_used", 0);

System.out.println("查询：'如何优化我的数据库查询？'");
System.out.println("使用的记忆数：" + memoriesUsed);
System.out.println("收到的响应：" + output);
```

#### 关键方法

| 方法 | 描述 |
|--------|-------------|
| `invoke(inputs, session)` | 使用自动记忆检索和注入调用智能体 |

### TaskMemoryService

处理记忆操作的核心服务：

```java
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;

// 使用默认配置
TaskMemoryService service = new TaskMemoryService(
        "gpt-4",                           // llmModel
        "text-embedding-3-small",          // embeddingModel
        "your-api-key",                    // apiKey
        "ReMe",                            // retrievalAlgo
        "ReMe"                             // summaryAlgo
);

// 使用自定义配置文件路径
TaskMemoryService service = new TaskMemoryService(
        "gpt-4",
        "text-embedding-3-small",
        "your-api-key",
        "ReMe",
        "ReMe",
        "/path/to/custom/config.properties"  // 可选：自定义配置文件
);

// 检索记忆
List<Memory> memories = service.retrieve(userId, query);

// 摘要轨迹
Memory summary = service.summarize(userId, matts, query, trajectories, label);

// 添加记忆
AddMemoryRequest request = AddMemoryRequest.builder()
        .content("记忆内容")
        // 算法特定字段：
        // - ReMe: whenToUse
        // - ReasoningBank: title, description
        // - ACE: section
        .build();

service.addMemory(userId, request);
```

## 记忆算法

### ACE (Agentic Context Engineering)
- 使用 `content` 和 `section` 字段存储记忆
- 最适合：具有明确使用条件的行动导向型记忆
- 基于 Playbook 的组织方式

### RB (Reasoning Bank)
- 使用 `title`、`description` 和 `content` 字段存储记忆
- 最适合：具有丰富描述的知识导向型记忆
- 支持来源归属

### ReMe (Remember Me, Refine Me)
- 结合向量检索与基于 LLM 的重排序和重写
- 支持多阶段检索管道
- 最适合：需要语义理解的复杂查询

## MATTS扩展（Memory-aware Test-Time Scaling）

MATTS是记忆感知的测试时扩展功能，用于多跳查询的并行/顺序处理。

在config.yaml中配置MATTS参数：

```yaml
RETRIEVAL_ALGO: "RB"
MATTS_DEFAULT_K: 3                  # 默认扩展因子
MATTS_DEFAULT_TEMPERATURE: 0.9      # 并行扩展的默认温度
MATTS_DEFAULT_MODE: "parallel"      # none/parallel/sequential/combined
```

**注意**：MATTS仅在使用RB（ReasoningBank）算法时适用。

## 文件持久化

使用JSONFileConnector实现记忆数据的持久化：

```java
import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;

// 创建文件连接器
JSONFileConnector connector = new JSONFileConnector("/path/to/memory/storage");

// 保存记忆
connector.saveMemories(userId, memories);

// 加载记忆
List<Memory> loadedMemories = connector.loadMemories(userId);
```

## 与openJiuwen集成

此扩展与openJiuwen agent-core框架集成：

1. **作为智能体子类**：使用 `ContextEvolvingReActAgent` 进行自动记忆注入
2. **作为服务**：在自定义智能体中直接使用 `TaskMemoryService`
3. **文件持久化**：使用 `JSONFileConnector` 实现记忆存储

## 许可证

版权所有 (c) 华为技术有限公司 2025。保留所有权利。