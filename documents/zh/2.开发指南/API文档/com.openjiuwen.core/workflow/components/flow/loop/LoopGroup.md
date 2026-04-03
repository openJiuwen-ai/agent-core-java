# com.openjiuwen.core.workflow.components.flow.loop.LoopGroup

## 类 LoopGroup

```java
public class LoopGroup extends com.openjiuwen.core.workflow.component.loop.LoopGroup
```

旧版兼容循环体容器，提供与历史包路径一致的 `addWorkflowComp` 重载。

## 方法

| 签名 | 说明 |
| --- | --- |
| `public LoopGroup addWorkflowComp(String id,ComponentComposable c,Object in,Object sin,Boolean w,List ab)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String id,Object c,Object in,Object sin,Boolean w,List ab)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String id,ComponentComposable c,Object in)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String id,Object c,Object in)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String id,ComponentComposable c)` | 向循环体中追加工作流节点。 |
| `public LoopGroup addWorkflowComp(String id,Object c)` | 向循环体中追加工作流节点。 |

## 说明

- 该类型位于旧版兼容包路径，用于兼容历史导入方式。
