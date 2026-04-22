# 智能体

本章节介绍openJiuwen Java版本提供的智能体能力，包括ReAct智能体、Workflow智能体、自定义智能体、上下文管理和指令自演进。

## 目录

| 文档 | 说明 |
|------|------|
| [概述.md](概述.md) | 智能体概念介绍，ReActAgent与WorkflowAgent的区别 |
| [构建ReActAgent.md](构建ReActAgent.md) | 构建ReAct智能体，推理+行动循环实现 |
| [构建WorkflowAgent.md](构建WorkflowAgent.md) | 构建Workflow智能体，多组件工作流实现 |
| [构建自定义智能体.md](构建自定义智能体.md) | 继承BaseAgent创建自定义智能体 |
| [构建ContextEvolvingAgent.md](构建ContextEvolvingAgent.md) | Context Evolver扩展，ACE/RB/ReMe记忆算法实现 |
| [构建ContextEvolvingReActAgent.md](构建ContextEvolvingReActAgent.md) | 使用ContextEvolvingReActAgent实现记忆增强智能体 |
| [构建自定义组件.md](构建自定义组件.md) | 继承WorkflowComponent创建自定义工作流组件 |
| [构建工具自演进.md](构建工具自演进.md) | Agent指令自演进训练（Java实现差异说明） |

## 推荐阅读顺序

### 入门路径

1. **概述** - 了解智能体基本概念和两种范式
2. **构建ReActAgent** - 学习最常用的ReAct智能体构建
3. **构建WorkflowAgent** - 学习工作流智能体构建

### 进阶路径

4. **构建自定义智能体** - 自定义智能体行为
5. **构建自定义组件** - 扩展工作流组件
6. **构建ContextEvolvingAgent** - 上下文管理

### 高级路径

7. **构建工具自演进** - Agent指令优化训练

## 快速开始

### ReActAgent示例

```java
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

AgentCard card = AgentCard.builder()
        .id("weather_agent")
        .name("天气查询Agent")
        .build();

ReActAgent agent = new ReActAgent(card);
agent.configure(ReActAgentConfig.builder()
        .maxIterations(5)
        .build()
        .configureModelClient("openai", apiKey, apiBase, modelName, true));
```

### WorkflowAgent示例

```java
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.WorkflowConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.llm.LLMComponent;

WorkflowConfig config = WorkflowConfig.builder()
        .id("finance_workflow")
        .name("金融业务工作流")
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
// 添加组件并连接...
```

## 相关资源

- [基础功能](../基础功能/README.md) - LLM配置、提示词模板、工具注册
- [工作流](../工作流/README.md) - 工作流详细配置和组件使用
- [高阶用法](../高阶用法/README.md) - Session、Memory、Runner等高级能力
- [API文档](../API文档/README.md) - 详细API参考