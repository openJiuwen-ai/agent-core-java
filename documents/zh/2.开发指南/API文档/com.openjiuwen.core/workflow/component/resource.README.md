# resource

`com.openjiuwen.core.workflow.component.resource` 提供知识检索型工作流组件、执行器以及输入输出模型。

## 类型

| 类型 | 种类 | 说明 |
| --- | --- | --- |
| [KnowledgeRetrievalCompConfig](./resource/KnowledgeRetrievalCompConfig.md) | `class` | 知识检索组件配置模型，声明知识库、检索、向量存储和可选模型参数。 |
| [KnowledgeRetrievalComponent](./resource/KnowledgeRetrievalComponent.md) | `class` | 知识检索组件封装类型，负责把检索执行器注册到工作流图。 |
| [KnowledgeRetrievalExecutable](./resource/KnowledgeRetrievalExecutable.md) | `class` | 知识检索执行器，按组件配置初始化知识库与模型并返回检索结果。 |
| [KnowledgeRetrievalInput](./resource/KnowledgeRetrievalInput.md) | `class` | 知识检索输入模型，承载查询文本。 |
| [KnowledgeRetrievalOutput](./resource/KnowledgeRetrievalOutput.md) | `class` | 知识检索输出模型，封装结果列表、上下文和元数据结果。 |

## 说明

- 当前包收录 5 个类型页面。
