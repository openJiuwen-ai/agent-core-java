# com.openjiuwen.core.graph.visualization.DrawableNode

## 类 DrawableNode

```java
public class DrawableNode
```

表示可绘制图中的基础节点数据模型。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `id` | `String` | `-` | 节点唯一标识。 |
| `name` | `String` | `null` | 可选展示名称。 |
| `metadata` | `Map<String, Object>` | `null` | 节点扩展元数据。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableNode(String id)` | 仅以节点 ID 创建基础节点。 |
| `public DrawableNode(String id, String name, Map<String, Object> metadata)` | 同时设置展示名称与元数据。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getId()` | 返回节点 ID。 |
| `public String getName()` | 返回当前展示名称。 |
| `public void setName(String name)` | 更新展示名称。 |
| `public Map<String, Object> getMetadata()` | 返回当前元数据映射。 |
| `public void setMetadata(Map<String, Object> metadata)` | 更新元数据映射。 |
