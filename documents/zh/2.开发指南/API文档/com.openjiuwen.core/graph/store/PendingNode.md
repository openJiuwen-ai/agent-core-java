# com.openjiuwen.core.graph.store.PendingNode

## 类 PendingNode

```java
public class PendingNode
```

记录待恢复节点的名称、状态和异常列表，用于持久化失败或中断现场。

## 字段

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `nodeName` | `String` | `-` | 待恢复节点名称。 |
| `status` | `String` | `-` | 由调用方定义的节点状态字符串。 |
| `exceptions` | `List<Exception>` | `null` | 可选异常列表，用于附带失败原因。 |

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public PendingNode(String nodeName, String status)` | 创建不带异常列表的待恢复节点记录。 |
| `public PendingNode(String nodeName, String status, List<Exception> exceptions)` | 创建带异常列表的待恢复节点记录。 |

## 方法

| 签名 | 说明 |
| --- | --- |
| `public String getNodeName()` | 返回节点名称。 |
| `public String getStatus()` | 返回状态字符串。 |
| `public List<Exception> getExceptions()` | 返回异常列表；未提供时可能为 `null`。 |

## 相关测试

- `GraphStoreTest`
