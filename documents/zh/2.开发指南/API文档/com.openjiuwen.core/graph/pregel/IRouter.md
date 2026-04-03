# com.openjiuwen.core.graph.pregel.IRouter

## 接口 IRouter

```java
public interface IRouter
```

节点执行完成后分发消息的路由接口。

## 方法

| 签名 | 说明 |
| --- | --- |
| `List<Message> dispatch(String sourceNode)` | 根据给定来源节点名生成下一轮要发送的消息列表。 |
