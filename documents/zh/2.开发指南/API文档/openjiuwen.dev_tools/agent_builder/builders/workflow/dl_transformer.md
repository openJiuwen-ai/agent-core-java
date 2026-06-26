# com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer

`DLTransformer` 是工作流 DL 转换入口，负责将 DL JSON 数组转换为 Mermaid 流程图文本或平台工作流 DSL JSON。

## DLTransformer

Java 类型：

```java
com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.DLTransformer
```

对应 Python：

```text
openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/dl_transformer.py
```

### getDslConverterRegistry

返回 DL 节点类型到转换器类的映射副本。支持的 DL 类型包括 `Start`、`End`、`LLM`、`IntentDetection`、`Questioner`、`Code`、`Plugin`、`Output` 和 `Branch`。

### collectPlugin

根据工具 ID 列表、插件字典和工具到插件的映射收集插件元数据。输出字段包括插件 ID、插件名、插件版本、工具 ID、工具名、输入和输出；当工具定义中存在有效的 `language`、`code`、`path` 或 `method` 时也会保留。

### transformToMermaid

从文本中提取 JSON，要求顶层是 DL 节点数组，然后委托 `SimpleirToMermaid` 生成 Mermaid `graph TD` 文本。

### transformToDsl

将 DL 节点数组转换为平台工作流 DSL JSON。传入资源时，会先把 `resource.plugins` 原地替换为 `collectPlugin` 收集到的插件元数据，然后按节点类型实例化对应转换器，生成 `Workflow` 中的节点和边。

动态 DL JSON 和 resource 插件结构保留为 `Map<String, Object>` 边界；缺少 Python 源码中通过 `dict` 索引访问的必需键时，Java 会快速失败并报告缺失键。
