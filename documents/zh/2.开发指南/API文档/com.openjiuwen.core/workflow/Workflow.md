# com.openjiuwen.core.workflow.Workflow

## 类 Workflow

```java
public class Workflow
```

`Workflow` 是面向用户的主工作流类，封装构图、执行、流式输出、子工作流调用与兼容性重载。

## 主要方法

| 签名 | 说明 |
| --- | --- |
| `public Workflow setStartComp(...)` | 设置起始节点，并登记输入/输出 schema。 |
| `public Workflow addWorkflowComp(...)` | 添加普通节点；支持显式能力、流式 schema 与 `waitForAll`。 |
| `public Workflow setEndComp(...)` | 设置结束节点；可配置普通模式或 `streaming` 响应模式。 |
| `public Workflow addConnection(Object srcCompId, String targetCompId)` | 添加普通边。 |
| `public Workflow addStreamConnection(String srcCompId, String targetCompId)` | 添加流式边。 |
| `public Workflow addConditionalConnection(String srcCompId, Object router)` | 添加条件路由边。 |
| `public WorkflowOutput invoke(...)` | 同步执行工作流并返回 `WorkflowOutput`。 |
| `public Iterator<WorkflowChunk> stream(...)` | 以增量方式返回输出块。 |
| `public Object invokeSubWorkflow(...)` | 作为子工作流执行。 |
| `public Iterator<WorkflowChunk> streamSubWorkflow(...)` | 作为子工作流流式执行。 |
| `public String draw(...)` / `public byte[] drawBytes(...)` | 输出 Mermaid 文本或图像字节。 |

## 说明

- 未传 `WorkflowCard` 时会自动生成随机工作流 id。
- `WorkflowTest` 覆盖了串行执行、并行分支、条件路由、循环、子工作流、streaming end、collect 和 transform 等关键行为。
- 本类包含多组兼容性重载，用于兼容旧式 POJO 组件、不同参数顺序和省略 schema 的调用方式。
