# com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalComponent

## 类 KnowledgeRetrievalComponent

```java
public class KnowledgeRetrievalComponent implements ComponentComposable
```

知识检索组件封装类型，负责把检索执行器注册到工作流图。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public KnowledgeRetrievalComponent(KnowledgeRetrievalCompConfig config)` | 创建 `KnowledgeRetrievalComponent` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | 向工作流图中注册当前组件。 |
| `public Executable<?, ?> toExecutable()` | 构造并返回可执行节点。 |
