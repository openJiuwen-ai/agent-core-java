# com.openjiuwen.core.graph.visualization.DrawableBranchRouter

## 类 DrawableBranchRouter

```java
public class DrawableBranchRouter
```

保存条件分支的目标节点列表与每条边的展示标签。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `targets` | `List<String>` | `-` | 条件分支可能跳转到的目标节点名列表。 |
| `datas` | `List<String>` | `-` | 与 `targets` 对齐的展示文案或分支标签。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableBranchRouter(List<String> targets, List<String> datas)` | 基于目标节点列表与标签列表创建分支路由描述。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public List<String> getTargets()` | 返回当前分支目标节点列表。 |
| `public List<String> getDatas()` | 返回与目标节点对应的展示标签列表。 |
