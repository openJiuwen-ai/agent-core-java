# Groups Java Examples

这个目录对齐 Python 版 `examples/groups`，用于展示 Java 框架里的多 agent 分组协作示例。

当前已提供：

1. `hierarchical_group/`: 对齐 Python `examples/groups/hierarchical_group` 的 leader-worker 示例。

## 设计说明

Java 框架已经具备 `ControllerGroup` 和 `BaseGroupController` 这类分组编排能力，但当前 examples 层没有一个直接可运行的 groups 示例。

这批示例刻意把实现限制在 `examples/` 目录内：

1. 不改动 `src/main` 的公共 API。
2. 复用现有 `ControllerGroup`、`BaseGroupController` 和 `WorkflowAgent`。
3. 在示例内部补一层最小 bridge，把 leader 的路由逻辑和 worker 的 workflow 执行串起来。

这样做的目标是先提供一个能跑、能读、能对照 Python 的 Java 示例，而不是把 hierarchical group 直接提升为正式框架能力。

## 运行方式

每个子目录都提供独立 README。当前请直接参考：

1. `hierarchical_group/README.md`

## 后续方向

如果后续要把 hierarchical group 上升为正式框架能力，优先应该处理 Java 当前 group/controller 在 event 与 session 适配上的公共接口问题，再把示例里的 bridge 收敛进 `src/main`。