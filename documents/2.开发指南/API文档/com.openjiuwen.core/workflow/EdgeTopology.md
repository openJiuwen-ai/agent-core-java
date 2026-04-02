# com.openjiuwen.core.workflow.EdgeTopology

## 类 EdgeTopology

```java
public class EdgeTopology
```

`EdgeTopology` 保存工作流普通边与流式边的拓扑快照，供 `BaseWorkflow.autoCompleteAbilities()` 推断节点能力。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public Map<String, List<String>> getSourceMap()` | 返回普通边 source 映射。 |
| `public Map<String, List<String>> getTargetMap()` | 返回普通边 target 映射。 |
| `public Map<String, List<String>> getSourceStreamMap()` | 返回流式边 source 映射。 |
| `public Map<String, List<String>> getTargetStreamMap()` | 返回流式边 target 映射。 |
| `public Set<String> allEdgeNodes()` | 汇总所有普通边与流式边中出现过的节点。 |
