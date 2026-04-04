# openJiuwen Core Java

[中文版](README.zh.md) | [English Version](README.md)

## 简介

**openJiuwen Core Java**本项目是 OpenJiuwen Core Python 版本的 Java 移植，是一款面向大模型应用的Java软件开发工具包，为运行在**openJiuwen**框架上的智能体提供高性能运行时。这款开发工具包不仅封装了Agent创建、工作流编排、大模型与工具调用等多层次、易上手的对外接口；还内置了支持异步IO、流式处理的高性能运行时，实现智能体的状态保存和中断接续；更配备了全链路观测等一系列智能体调试调优工具。**openJiuwen Core Java**开发工具包兼顾灵活性与稳定性，助力开发者高效构建稳定的大模型应用。

## 为什么选择openJiuwen Core Java?

- **开箱即用的组件**：提供丰富的预置组件，包括意图识别、提问器、大模型调用、工具组件等，大幅降低开发门槛。

- **高效精准的任务执行**：内置高性能执行引擎，支持异步并行图执行、组件并发、流式处理等能力，确保Agent在执行任务时的高效性与精准性。

- **灵活可控的多工作流跳转能力**：支持Agent在同一会话中管理多个工作流，支持用户在不同工作流间自由切换，由框架保障被打断工作流的断点接续。解决了用户在同一对话中切换不同任务场景的需求，提供了灵活的多任务管理能力。

- **丰富的存储支持**：内置多种存储后端支持，包括向量数据库（Milvus、Chroma、PostgreSQL/pgvector）、键值存储、图数据库等，满足不同的数据持久化需求。

## 快速开始

### 环境要求

- **操作系统**：兼容Windows、Linux、macOS。
- **Java 版本**：Java 21或更高版本。
- **构建工具**：Maven 3.9+。

### 安装



**从源码构建**

```bash
git clone <repository-url>
cd agent-core-java
mvn clean install -DskipTests
```

然后将agent-core-java加入你的maven项目依赖
```xml
<dependency>
    <groupId>com.openjiuwen</groupId>
    <artifactId>agent-core-java</artifactId>
    <version>0.1.7</version>
</dependency>
```

### 样例

让我们创建一个简单的WorkflowAgent，调用工作流处理金融场景业务：
将examples文件夹拷贝至你的maven工程的src/main目录，将examples内的apiconfig.json,apiconfig_example.json复制到工程的src/resources,即可运行样例代码

```java
// 示例代码位于 examples/workflow_agent/WorkflowAgentExample.java
public class WorkflowAgentExample {
    public static void main(String[] args) throws Exception {
        WorkflowAgentExampleSupport.run(args);
    }
}
```

**预期输出**
```
assistant> 请补充转账金额，必须是数字或带货币单位的金额描述。
// 用户回复: 2000元
assistant> 转账服务完成，记录的转账金额为 2000元。
```

更多完整示例请查看[examples/workflow_agent](examples/workflow_agent/)目录。

## 架构设计

**openJiuwen Core Java**作为openJiuwen架构的核心引擎，核心能力包括：

* **SDK接口层**：聚焦大模型应用的开发需求，为开发者提供Java SDK接口。接口能力覆盖Agent实例创建、工作流设计与编排、大模型调用及输出结果解析、提示词模板构建与动态填充，并支持本地工具调用外部服务。

* **Agent引擎**：针对ReAct智能交互与工作流自动跳转两大场景，通过构建Agent控制器，支撑复杂任务规划、工具选择与调用、工作流任务切换。内置开箱即用的标准化组件，降低Agent的开发门槛。提供Agent运行时环境，同时配套对话历史上下文管理、基础工具集等底层能力。

* **高性能图执行引擎**：基于Pregel模型的异步并行图执行器，支持组件并发执行、状态中断与恢复、流式数据传输。

## 功能特性

### **Agent编排**

**openJiuwen Core Java**内置了**ReActAgent**和**WorkflowAgent**两类预置智能体，功能丰富、开发灵活，可满足不同场景下的智能需求。

- **ReActAgent**：遵循ReAct（Reasoning + Action）规划范式，以**思考→行动→观察**的循环迭代完成任务。凭借强大的多轮推理与自我修正能力，具备动态决策和环境适应特性，适用于需复杂推理、策略调整的多样化场景。

- **WorkflowAgent**：专注多步骤任务导向的流程自动化，严格按照用户预定义流程高效执行复杂任务，也能够随着用户意图的变更灵活切换任务。其侧重于基于预设流程实现任务的规范化与高效化执行，适用于任务结构清晰、可拆解为多步骤的场景。

### **高性能执行引擎**

**openJiuwen Core Java**提供高性能执行引擎，支持分布式部署与低成本运行，可有效解决海量智能体执行效率低、运维成本高的痛点，为大规模智能体集群运转及行业级生产应用落地提供坚实支撑。

- **异步并行图执行器**：具备组件并发执行、异步IO处理、结构化上下文管理能力，支持多工作流任务高效并行处理，实现异构组件的灵活调用。

- **组件基础能力**：支持组件间流批一体传值、动态跳转、状态中断与恢复，同时提供组件动态配置与多实例管理功能。

- **数据存储与流式处理**：提供流式输出、组件间流式传输等数据管控能力，可对接外部存储系统实现智能体上下文数据外置，助力分布式场景下的弹性扩展。

### **存储支持**

支持多种存储后端，满足不同的数据持久化需求：

- **向量存储**：Milvus、Chroma、PostgreSQL/pgvector、内存向量存储
- **图存储**：基于内存的图数据存储
- **键值存储**：数据库-backed KV存储、内存KV存储
- **对象存储**：本地对象存储客户端

## 项目结构

```
agent-core-java/
├── src/main/java/com/openjiuwen/core/
│   ├── application/          # Agent应用层
│   │   ├── llm/             # LLM Agent
│   │   ├── workflow/        # Workflow Agent
│   │   └── schema/          # 配置Schema
│   ├── controller/           # Agent控制器
│   ├── foundation/           # 基础设施层
│   │   ├── llm/             # 大模型客户端
│   │   ├── tool/            # 工具系统
│   │   ├── store/           # 存储系统
│   │   └── prompt/          # 提示词模板
│   ├── graph/               # 图执行引擎
│   ├── memory/              # 记忆系统
│   ├── context/             # 上下文管理
│   ├── runner/              # 运行器
│   └── session/             # 会话管理
├── src/test/                # 测试代码
├── examples/                # 示例代码
└── documents/               # 文档
    └── zh/                  # 中文文档
        └── SUMMARY.md       # API文档索引
```

## 完整文档

详细API文档请参考[documents/zh/SUMMARY.md](documents/zh/SUMMARY.md)。

主要文档包括：

- [开发指南](documents/zh/2.开发指南/)
  - [API文档](documents/zh/2.开发指南/API文档/)
    - [应用层API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/application.README.md)
    - [控制器API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/controller.README.md)
    - [基础层API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/foundation.README.md)
    - [图引擎API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/graph.README.md)
    - [记忆系统API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/memory.README.md)

## 参与贡献

我们欢迎所有形式的贡献，包括但不限于:
- 提交问题和功能建议
- 改进文档
- 提交代码
- 分享使用经验

## 开源许可证

本项目依据Apache-2.0许可证授权。
