# openjiuwen.agent_evolving.updater

`com.openjiuwen.agent_evolving.updater` 对应 Python 模块 `openjiuwen.agent_evolving.updater`，是 openJiuwen 自优化 Agent 框架中的更新生成模块入口。

## Java 包门面

`UpdaterPackage` 记录 Python `__init__.py` 的公开导出面：

```java
UpdaterPackage.PYTHON_MODULE
UpdaterPackage.EXPORTED_SYMBOLS
```

`EXPORTED_SYMBOLS` 顺序与 Python `__all__` 保持一致：

```text
Updater
execute_updates
apply_updates
summarize_apply_results
SingleDimUpdater
MultiDimUpdater
```

## Updater 协议

`Updater` 接口对应 Python `openjiuwen.agent_evolving.updater.protocol.Updater`，统一单维优化器直接写回与多维归因分配的入口。

主要方法：

- `bind(operators, targets, config)`：绑定算子并筛选可优化项，返回绑定数量。
- `requiresForwardData()`：声明是否需要框架在训练用例上执行前向。
- `update(trajectories, evaluatedCases, config)`：根据轨迹与评估结果生成更新。
- `process(trajectories, signals, config)`：根据轨迹与演进信号生成更新。
- `getState()` / `loadState(state)`：用于检查点保存与恢复。

## 包级更新辅助函数

Python 0.1.14 的 updater 包新增导出：

- `execute_updates`
- `apply_updates`
- `summarize_apply_results`

Java 侧由 `UpdaterPackage` 提供同名语义的驼峰方法，并委托给 `UpdateExecution`：

```java
UpdaterPackage.executeUpdates(operators, updates);
UpdaterPackage.applyUpdates(operators, updates);
UpdaterPackage.summarizeApplyResults(results);
```

这些方法保留 Python 行为：先规范化非空更新、缺失算子返回失败结果、空更新值返回失败结果，并统计应用结果数量。

## 具体更新器

`MultiDimUpdater` 已翻译为 Java 抽象基类，对应 Python `openjiuwen.agent_evolving.updater.multi_dim.MultiDimUpdater`。

构造时可传入 `Map<String, BaseOptimizer>` 形式的 `domainOptimizers`。默认行为：

- `requiresForwardData()`：只要任一域优化器需要前向数据即返回 `true`。
- `update(trajectories, evaluatedCases, config)`：读取 `score_threshold`，将 `EvaluatedCase` 转为 `EvolutionSignal`，过滤达标样本，再委托 `process(...)`。
- `bind(...)`、`process(...)`、`getState()`、`loadState(...)`：保持抽象，由具体多维更新器实现。

`SingleDimUpdater` 已翻译为 Java 具体类，对应 Python `openjiuwen.agent_evolving.updater.single_dim.SingleDimUpdater`。

构造时传入一个 `BaseOptimizer`。默认行为：

- `bind(operators, targets, config)`：当 `targets` 为空时使用 `config["targets"]`，再委托 `BaseOptimizer.bind(...)`。
- `requiresForwardData()`：委托内部 optimizer。
- `process(trajectories, signals, config)`：按顺序写入轨迹，执行 `backward(signals)`，再返回 `step()` 产出的更新。
- `update(trajectories, evaluatedCases, config)`：读取 `score_threshold`，将 `EvaluatedCase` 转为 `EvolutionSignal`，再复用 `process(...)`。
- `getState()` / `loadState(...)`：当前与 Python 一致，为空状态和 no-op 恢复。
