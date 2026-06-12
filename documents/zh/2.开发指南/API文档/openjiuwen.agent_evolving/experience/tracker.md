# com.openjiuwen.agent_evolving.experience.ExperienceTracker

跟踪已展示给 Agent 的经验记录，并基于展示、使用和评估结果更新记录分数。

## class ExperienceTracker

`ExperienceTracker` 对应 Python 的 `openjiuwen.agent_evolving.experience.tracker.ExperienceTracker`。Java 实现提供：

- session 级已展示记录与评估计数隔离。
- 只跟踪 `EvolutionTarget.BODY` 类型经验记录。
- 展示记录时更新 `UsageStats.timesPresented` 与 `lastPresentedAt`。
- 到达评估间隔时消费 session 内缓存的展示记录。
- 按展示时保存的 snippet 分组调用 `ExperienceScorer`，再通过 `EvolutionStore` 写回分数和使用统计。

公开 API 使用 `EvolutionStore`、`ExperienceScorer`、`EvolutionRecord`、`UsageStats` 和 `ExperienceTracker.PresentedEntry` 等 Java 领域类型；`Object` 仅用于模拟 Python 动态 session 边界。
