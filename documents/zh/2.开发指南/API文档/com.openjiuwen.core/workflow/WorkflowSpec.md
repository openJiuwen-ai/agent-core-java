# com.openjiuwen.core.workflow.WorkflowSpec

## 类 WorkflowSpec

```java
public class WorkflowSpec
```

`WorkflowSpec` 保存工作流结构定义，包括普通边、流式边、节点配置和起始节点集合。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, List<String>> getEdges()` | 返回普通边定义。 |
| `public void setEdges(Map<String, List<String>> edges)` | 更新普通边定义。 |
| `public Map<String, List<String>> getStreamEdges()` | 返回流式边定义。 |
| `public void setStreamEdges(Map<String, List<String>> streamEdges)` | 更新流式边定义。 |
| `public Map<String, NodeConfig> getCompConfigs()` | 返回节点配置集合。 |
| `public void setCompConfigs(Map<String, NodeConfig> compConfigs)` | 更新节点配置集合。 |
| `public List<String> getStartNodes()` | 返回起始节点列表。 |
| `public void setStartNodes(List<String> startNodes)` | 更新起始节点列表。 |
