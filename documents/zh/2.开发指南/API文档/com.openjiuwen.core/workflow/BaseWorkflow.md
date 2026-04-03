# com.openjiuwen.core.workflow.BaseWorkflow

## 类 BaseWorkflow

```java
public class BaseWorkflow implements HasDrawable
```

`BaseWorkflow` 是工作流内部图构建器，负责注册节点、维护普通边和流式边、管理 `WorkflowSpec`、执行编译，并在需要时输出 Mermaid 可视化结果。

## 构造方法

| 签名 | 说明 |
| --- | --- |
| `public BaseWorkflow()` | 创建默认工作流基类，内部使用默认 `WorkflowConfig` 和 `PregelGraph`。 |
| `public BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph)` | 使用指定配置和图实现创建实例；参数为空时会回退到默认配置或默认图。 |

## 主要方法

| 签名 | 说明 |
| --- | --- |
| `public WorkflowConfig getConfig()` | 返回当前工作流配置。 |
| `public Graph getGraph()` | 返回底层图对象。 |
| `public StreamGraph getStreamActor()` | 返回流式边使用的 `StreamGraph`。 |
| `public BaseWorkflow addWorkflowComp(...)` | 注册组件节点、节点能力以及普通/流式输入输出 schema。 |
| `public BaseWorkflow startComp(String startCompId)` | 标记起始节点。 |
| `public BaseWorkflow endComp(String endCompId)` | 标记结束节点。 |
| `public BaseWorkflow addConnection(Object srcCompId, String targetCompId)` | 添加普通数据边，`srcCompId` 支持单节点或节点列表。 |
| `public BaseWorkflow addStreamConnection(String srcCompId, String targetCompId)` | 添加流式边，并把目标节点登记到 `streamActor`。 |
| `public BaseWorkflow addConditionalConnection(String srcCompId, Object router)` | 添加条件边，`router` 只接受 `BranchRouter` 或 `Function`。 |
| `public ExecutableGraph<?, ?> compile(BaseSession sessionArg, Object context)` | 绑定 session 与上下文后编译底层图。 |
| `public String toMermaid(...)` | 输出 Mermaid 文本。 |
| `public byte[] toMermaidPng(...)` | 输出 Mermaid PNG 字节。 |
| `public byte[] toMermaidSvg(...)` | 输出 Mermaid SVG 字节。 |
| `public Drawable getDrawable()` | 返回内部 `Drawable`；未开启绘图时可能为 `null`。 |
| `public void autoCompleteAbilities()` | 根据边拓扑自动补齐节点能力。 |
| `public void reset()` | 重置图执行状态，便于重新执行。 |

## 说明

- 默认配置会生成随机工作流 id，并自动创建新的 `WorkflowSpec`。
- `addConditionalConnection(...)` 传入空源节点、空路由器或不支持的路由类型时，会抛出 `WORKFLOW_CONDITION_EDGE_INVALID`。
- `compile(...)` 遇到子工作流嵌套层级超过 `WorkflowConfig.workflowMaxNestingDepth` 时，会抛出 `WORKFLOW_COMPILE_ERROR`。
- 若未启用 drawable，`toMermaid()` 返回空字符串，`toMermaidPng()` 和 `toMermaidSvg()` 返回空字节数组。
