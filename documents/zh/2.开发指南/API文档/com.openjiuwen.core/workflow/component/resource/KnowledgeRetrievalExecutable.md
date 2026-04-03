# com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalExecutable

## 类 KnowledgeRetrievalExecutable

```java
public class KnowledgeRetrievalExecutable extends ComponentExecutable
```

知识检索执行器，按组件配置初始化知识库与模型并返回检索结果。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public KnowledgeRetrievalExecutable(KnowledgeRetrievalCompConfig config)` | 创建 `KnowledgeRetrievalExecutable` 实例。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 执行当前组件的运行逻辑。 |
