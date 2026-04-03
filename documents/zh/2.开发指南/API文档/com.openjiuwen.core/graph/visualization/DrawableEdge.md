# com.openjiuwen.core.graph.visualization.DrawableEdge

## 类 DrawableEdge

```java
public class DrawableEdge
```

表示可绘制图中的一条边，支持条件边标签与流式边样式标记。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `source` | `String` | `-` | 边的源节点 ID。 |
| `target` | `String` | `-` | 边的目标节点 ID。 |
| `data` | `Object` | `null` | 条件边的标签或附加数据。 |
| `conditional` | `boolean` | `false` | 是否按虚线条件边渲染。 |
| `streaming` | `boolean` | `false` | 是否按流式粗线渲染。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public DrawableEdge(String source, String target)` | 创建仅包含源节点和目标节点的普通边。 |
| `public DrawableEdge(String source, String target, Object data, boolean conditional, boolean streaming)` | 一次性设置边标签、条件标记与流式标记。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getSource()` | 返回源节点 ID。 |
| `public String getTarget()` | 返回目标节点 ID。 |
| `public Object getData()` | 返回当前边附带的数据或标签。 |
| `public void setData(Object data)` | 更新边附带的数据或标签。 |
| `public boolean isConditional()` | 返回当前边是否为条件边。 |
| `public void setConditional(boolean conditional)` | 更新条件边标记。 |
| `public boolean isStreaming()` | 返回当前边是否为流式边。 |
| `public void setStreaming(boolean streaming)` | 更新流式边标记。 |
