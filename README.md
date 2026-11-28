# openJiuwen Core Java

[中文版](README.zh.md) | [English Version](README.md)

## Introduction

**openJiuwen Core Java** is an independent Java implementation for the openJiuwen framework, a Java software development toolkit designed for large language model applications. It provides a high-performance runtime for agents running on the **openJiuwen** framework. This development toolkit not only encapsulates multi-layered, easy-to-use external interfaces for Agent creation, workflow orchestration, large language model invocation, and tool calling; it also includes a high-performance runtime with support for asynchronous I/O and stream processing, enabling agent state persistence and interruption resumption; furthermore, it is equipped with a complete suite of debugging and optimization tools for full-link observability. The **openJiuwen Core Java** development toolkit balances flexibility and stability, helping developers efficiently build robust large language model applications.

## Why Choose openJiuwen Core Java?

- **Ready-to-Use Components**: Provides a rich set of pre-built components, including intent recognition, questioners, LLM invocation, tool components, etc., significantly lowering the development barrier.

- **Efficient and Precise Task Execution**: Built-in high-performance execution engine with support for asynchronous parallel graph execution, component concurrency, stream processing, and other capabilities, ensuring efficiency and precision in Agent task execution.

- **Flexible and Controllable Multi-Workflow Transitions**: Supports Agent management of multiple workflows within a single session, allows users to freely switch between different workflows, with the framework guaranteeing breakpoint continuation for interrupted workflows. Solves the need for users to switch between different task scenarios in the same conversation, providing flexible multi-task management capabilities.

- **Rich Storage Support**: Built-in support for multiple storage backends, including vector databases (Milvus, Chroma, PostgreSQL/pgvector), key-value stores, graph databases, etc., meeting various data persistence requirements.

## Quick Start

### Environment Requirements

- **Operating System**: Compatible with Windows, Linux, macOS.
- **Java Version**: Java 17 or higher.
- **Build Tool**: Maven 3.9+.

### Installation

**Build from Source**

```bash
git clone <repository-url>
cd agent-core-java
mvn clean install -DskipTests
```

Import the published Core POM in your application's `dependencyManagement` and
declare the Core JAR as a dependency. Use the same Core version in both entries.
The published POM expands the managed versions, including versions from upstream BOMs.
The import aligns shared library versions such as Jackson, OpenTelemetry,
Bouncy Castle, and Protobuf with Core's build.

```xml
<properties>
    <agent-core.version>0.1.18</agent-core.version>
</properties>
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.openjiuwen</groupId>
            <artifactId>agent-core-java</artifactId>
            <version>${agent-core.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>com.openjiuwen</groupId>
        <artifactId>agent-core-java</artifactId>
        <version>${agent-core.version}</version>
    </dependency>
</dependencies>
```

When using Runtime, import the matching Runtime POM for its application and
service constraints. For a Spring Boot application that imports Boot's BOM,
put the OpenJiuwen imports before it. If you inherit a Boot parent, check
`mvn help:effective-pom` and `mvn dependency:tree -Dscope=runtime` for
inherited management and any explicit application overrides.

### Optional storage backends

Applications using the Core Milvus graph store or Context Evolver's Milvus
persistence must add `io.milvus:milvus-sdk-java:2.6.22` alongside Core.
Context Evolver falls back to JSON in auto mode when the SDK is absent.
Core retains the AWS SDK dependency for the public `AioBotoClient` API. Core's Retrieval module
continues to supply its own vector store and document parser dependencies.


### Examples

Runnable samples live in the `examples/` directory: [examples/agent_builder](examples/agent_builder/)
is the smallest agent sample, [examples/agent_teams](examples/agent_teams/) shows a team sample.
Each sample directory documents how to build and run it.

For a complete `WorkflowAgent` walk-through — workflow card, component registration,
`QuestionerComponent` interrupt and `InteractiveInput` resume — see
[执行器Runner.md](documents/zh/2.开发指南/高阶用法/执行器Runner.md) (Chinese).

The financial `WorkflowAgent` demo that used to live in `examples/workflow_agent/` is not part of
the current tree, so the snippet previously shown here has been removed.

## Architecture Design

As the core engine of the openJiuwen architecture, **openJiuwen Core Java** has the following core capabilities:

* **SDK Interface Layer**: Focuses on the development needs of large language model applications, providing Java SDK interfaces for developers. Interface capabilities cover Agent instance creation, workflow design and orchestration, LLM invocation and output parsing, prompt template construction and dynamic filling, and support for local tool invocation of external services.

* **Agent Engine**: Targeting the two major scenarios of ReAct intelligent interaction and workflow automatic transitions, supports complex task planning, tool selection and invocation, and workflow task switching through the Agent controller. Built-in ready-to-use standardized components lower the barrier for Agent development. Provides Agent runtime environment, along with supporting capabilities such as conversation history context management and basic tool sets.

* **High-Performance Graph Execution Engine**: Based on the Pregel model's asynchronous parallel graph executor, supporting component concurrent execution, state interruption and resumption, and stream data transmission.

## Features

### **Agent Orchestration**

**openJiuwen Core Java** includes two types of pre-built agents: **ReActAgent** and **WorkflowAgent**, with rich functionality and flexible development, meeting intelligent needs in different scenarios.

- **ReActAgent**: Follows the ReAct (Reasoning + Action) planning paradigm, completing tasks through iterative cycles of **Thinking → Acting → Observing**. With powerful multi-round reasoning and self-correction capabilities, it possesses dynamic decision-making and environmental adaptation characteristics, suitable for diverse scenarios requiring complex reasoning and strategy adjustment.

- **WorkflowAgent**: Focuses on process automation for multi-step task-oriented scenarios, strictly executing complex tasks according to user predefined workflows, while also flexibly switching tasks as user intent changes. It emphasizes standardized and efficient task execution based on preset processes, suitable for scenarios where tasks have clear structures and can be decomposed into multiple steps.

### **High-Performance Execution Engine**

**openJiuwen Core Java** provides a high-performance execution engine that supports distributed deployment and low-cost operation, effectively solving the pain points of low execution efficiency and high operational costs for massive agents, providing solid support for large-scale agent cluster operation and industry-level production application deployment.

- **Asynchronous Parallel Graph Executor**: Equipped with component concurrent execution, asynchronous I/O processing, and structured context management capabilities, supports efficient parallel processing of multi-workflow tasks, enabling flexible invocation of heterogeneous components.

- **Component Basic Capabilities**: Supports stream-batch integrated value passing between components, dynamic transitions, state interruption and resumption, while providing component dynamic configuration and multi-instance management functions.

- **Data Storage and Stream Processing**: Provides data management capabilities such as stream output and inter-component stream transmission, can interface with external storage systems to externalize agent context data, facilitating elastic scaling in distributed scenarios.

### **Storage Support**

Supports multiple storage backends to meet different data persistence requirements:

- **Vector Storage**: Milvus, Chroma, PostgreSQL/pgvector, in-memory vector storage
- **Graph Storage**: In-memory graph data storage
- **Key-Value Storage**: Database-backed KV storage, in-memory KV storage
- **Object Storage**: Local object storage client

## Project Structure

```
agent-core-java/
├── src/main/java/com/openjiuwen/core/
│   ├── application/          # Agent application layer
│   │   ├── llm/             # LLM Agent
│   │   ├── workflow/        # Workflow Agent
│   │   └── schema/          # Configuration Schema
│   ├── controller/           # Agent controller
│   ├── foundation/           # Infrastructure layer
│   │   ├── llm/             # LLM clients
│   │   ├── tool/            # Tool system
│   │   ├── store/           # Storage system
│   │   └── prompt/          # Prompt templates
│   ├── graph/               # Graph execution engine
│   ├── memory/              # Memory system
│   ├── context/             # Context management
│   ├── runner/              # Runner
│   └── session/             # Session management
├── src/test/                # Test code
├── examples/                # Example code
└── documents/               # Documentation
    └── zh/                  # Chinese documentation
        └── SUMMARY.md       # API documentation index
```

## Full Documentation

For detailed API documentation, please refer to [documents/zh/SUMMARY.md](documents/zh/SUMMARY.md).

Main documents include:

- [Development Guide](documents/zh/2.开发指南/)
  - [API Documentation](documents/zh/2.开发指南/API文档/)
    - [Application Layer API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/application.README.md)
    - [Controller API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/controller.README.md)
    - [Foundation Layer API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/foundation.README.md)
    - [Graph Engine API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/graph.README.md)
    - [Memory System API](documents/zh/2.开发指南/API文档/com.openjiuwen.core/memory.README.md)

## Contributing

We welcome all forms of contributions, including but not limited to:
- Submitting issues and feature suggestions
- Improving documentation
- Submitting code
- Sharing usage experiences

## License

This project is licensed under the Apache-2.0 License.

This product serves solely as a workflow orchestration tool and does not embed any AI model capabilities. When users integrate AI models for specific business scenarios, they shall bear full responsibility for compliance obligations under the EU AI Act and other relevant regulatory frameworks.
