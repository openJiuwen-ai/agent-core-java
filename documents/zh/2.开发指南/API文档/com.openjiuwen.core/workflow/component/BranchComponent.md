# com.openjiuwen.core.workflow.component.BranchComponent

## 类 BranchComponent

```java
public class BranchComponent extends WorkflowComponent
```

`BranchComponent` 是图中的条件路由组件，内部持有 `BranchRouter(true)` 并负责把路由器挂到条件边上。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public void addBranch(Object condition, Object target, String branchId)` | 添加分支规则。 |
| `public void addBranch(Object condition, Object target)` | 添加分支规则，自动生成分支 id。 |
| `public void add_branch(...)` | `snake_case` 兼容别名。 |
| `public BranchRouter router()` | 返回内部路由器。 |
| `public Object invoke(Object inputs, NodeSessionApi session, ModelContext context)` | 绑定 session 并返回空 `Map`；真正路由由条件边执行。 |
| `public void addComponent(Graph graph, String nodeId, boolean waitForAll)` | 把当前组件及其路由器注册到图中。 |

## 说明

- `WorkflowTest` 验证了该组件可根据表达式把流程路由到不同节点。
