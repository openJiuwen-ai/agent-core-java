# Bind: 投资分析团队约束与失败处理（直接辩论版）

## Resource Constraints

| 约束项 | 限制值 | 说明 |
|---|---|---|
| `max_parallel_teammates` | 4 | 最多4个分析师并行工作 |
| `total_wall_clock_budget` | 30分钟 | 整个流程最长执行时间 |
| `total_token_budget` | 100,000 tokens | 整个流程最大token消耗 |
| `per_role_token_limit` | 15,000 tokens | 每个角色最大token消耗 |
| `max_retry_per_step` | 2 | 每个步骤最多重试2次 |
| `debate_rounds_limit` | 2 | 辩论固定进行2轮 |
| `min_debate_rounds` | 2 | 辩论必须进行2轮 |

### Per-role asymmetric limits

| 角色 | Token限制 | 时间限制 | 特殊约束 |
|---|---|---|---|
| 基本面分析师 | 15,000 | 5分钟 | 必须分析至少3年财务数据 |
| 技术分析师 | 15,000 | 5分钟 | 必须分析至少6个月价格走势 |
| 数字媒体分析师 | 15,000 | 5分钟 | 必须分析至少3个社交媒体平台 |
| 宏观分析师 | 15,000 | 5分钟 | 必须分析至少3个宏观经济指标 |
| 乐观研究员 | 20,000 | 8分钟 | 必须参与2轮辩论，每轮包含反驳论据和辩论结论 |
| 悲观研究员 | 20,000 | 8分钟 | 必须参与2轮辩论，每轮包含反驳论据和辩论结论 |
| 投资组合与风险控制 | 10,000 | 3分钟 | 必须制定至少3个风险控制措施 |

## Behavioral Constraints

### Team-level rules

1. **Leader 不生成内容也不写文件**: Leader 只负责任务分发和质量门控，不进行任何分析工作，不落盘任何报告文件。最终产物 T9 由 portfolio-risk-controller 直接产出。
2. **分析师之间不可见**: 四个分析师并行工作，彼此看不到对方的输出
3. **研究员 Round 1 不可见**: 乐观研究员和悲观研究员在 Round 1 并行工作，彼此看不到对方的输出
4. **研究员 Round 2 直接可见**: 乐观研究员和悲观研究员在 Round 2 直接看到对方的观点，进行反驳辩论（无需协调员转达）
5. **Leader 不解决矛盾**: Leader 在辩论阶段不调解分歧，只记录分歧点
6. **研究员不妥协**: 乐观研究员和悲观研究员在辩论中必须坚持各自立场，不得妥协或放弃
7. **研究员直接输出辩论结论**: 乐观研究员和悲观研究员在辩论结束后直接输出辩论结论（分歧点、共识点、论据强度评估）
8. **投资组合与风险控制不偏离辩论结论**: 必须基于研究员输出的辩论结论，不得引入新的分析观点
9. **辩论必须进行2轮**: 辩论固定进行2轮，不允许提前终止

### Phase-scoped visibility rules (Debate pattern)

| 阶段 | 可见性 | 说明 |
|---|---|---|
| Step 2 (分析师并行) | 完全隔离 | 四个分析师彼此看不到输出 |
| Step 3 (研究员 Round 1) | 完全隔离 | 乐观研究员和悲观研究员彼此看不到输出 |
| Step 4 (研究员 Round 2) | 直接双方可见 | 乐观研究员和悲观研究员直接看到对方的观点，进行反驳辩论（无需协调员转达） |
| Step 5 (完成辩论校验) | Leader可见 | Leader 可以看到所有2轮的辩论报告和辩论结论 |
| Step 6 (投资组合与风险控制) | 辩论结论可见 | 投资组合与风险控制可以看到研究员输出的辩论结论 |

### Inter-member communication preference

**强制要求**:
- **辩论阶段（Round 2）必须使用直接点对点交换** - 研究员之间直接交换观点，无需协调员转达信息
- 这是 Debate pattern 的核心机制，框架必须支持直接通信
- **辩论必须进行2轮** - 不允许提前终止，必须完成2轮辩论

**优先级顺序**:
1. **直接点对点交换** (强制用于辩论) - 研究员之间直接交换观点（辩论阶段 Round 2）
2. **共享黑板** (次优先级) - 所有角色将输出写入共享黑板，其他角色可读取
3. **Leader转发** (最低优先级，仅作为fallback) - Leader 作为中介转发角色输出

**辩论轮次规则**:
- Round 1: 研究员并行工作，彼此不可见（初始观点）
- Round 2: 研究员直接看到对方 Round 1 观点，进行反驳辩论，输出辩论结论（必须完成）

## Output Persistence

### On-disk layout

所有运行时产物写入团队当前工作目录（CWD）下的 `.team/reports/`，**不写入 skill 目录**。skill 目录只存放静态定义文件（SKILL.md / workflow.md / bind.md / roles/*.md / dependencies.yaml）。

```
<CWD>/
└── .team/
    └── reports/
        ├── T1_fundamental_analysis.md
        ├── T2_technical_analysis.md
        ├── T3_digital_media_analysis.md
        ├── T4_macro_analysis.md
        ├── T5_optimistic_round1.md
        ├── T6_pessimistic_round1.md
        ├── T7_debate_optimistic.md
        ├── T8_debate_pessimistic.md
        └── T9_portfolio_risk.md   ← FINAL (含所有中间报告原文引用)
```

### Persistence rules

1. **每个 teammate 落盘自己的产物** — 通过 `file_io(action="write", path=".team/reports/T<n>_<name>.md")` 写入，路径相对于团队 CWD。Leader 不代写 teammate 的中间报告。
2. **`send_message` 只发摘要 + 路径** — teammate 完成后向 leader 发"完成摘要 + 文件路径"，不发完整内容，避免消息体过大。
3. **T9 是最终产物，由 portfolio-risk-controller 落盘** — 含所有 T1-T8 中间报告原文引用 + 投资决策。Leader 不写任何文件。
4. **Round 2 辩论落双份** — `optimistic-researcher` 写 T7_debate_optimistic.md，`pessimistic-researcher` 写 T8_debate_pessimistic.md，各自独立落盘。
5. **降级模式标注** — 任一角色启用降级（见 Input-overscale degradation）时，在自身落盘文件开头加 `> [DEGRADED] 原因: ...`，并在 T9 最终报告元数据中汇总。

### File naming convention

- `T<n>` 为阶段编号，与 workflow.md Step 编号对齐（跳号留位给未来扩展）。
- `<name>` 用 kebab-case，与 role id 对齐。
- 固定 `.md` 扩展名，UTF-8 编码。
- 不要在文件名中塞 run_id / 时间戳 — 团队每次运行覆盖同名文件，历史版本由 `TeamDatabase`（sqlite）的事件流保留，不在文件系统层做版本化。

### Hard constraints (MANDATORY)

1. **T9 is FINAL** — T9 必须包含所有 T1-T8 中间报告原文引用 + 投资决策。T9 完成即团队完成，框架合法判 `isTeamCompleted()==true` 收尾。
2. **Exact path table** — teammate 落盘时 `file_io(action="write", path=...)` 的 `path` 必须**逐字符**匹配下表，禁止 LLM 自由命名：

   | Task | 精确路径 |
   | --- | --- |
   | T1 | `.team/reports/T1_fundamental_analysis.md` |
   | T2 | `.team/reports/T2_technical_analysis.md` |
   | T3 | `.team/reports/T3_digital_media_analysis.md` |
   | T4 | `.team/reports/T4_macro_analysis.md` |
   | T5 | `.team/reports/T5_optimistic_round1.md` |
   | T6 | `.team/reports/T6_pessimistic_round1.md` |
   | T7 | `.team/reports/T7_debate_optimistic.md` |
   | T8 | `.team/reports/T8_debate_pessimistic.md` |
   | T9 (FINAL) | `.team/reports/T9_portfolio_risk.md`（portfolio-risk-controller，含原文引用） |

3. **No aliasing** — 禁止起别名（如 `T8_pessimistic_round2.md` 代替 `T8_debate_pessimistic.md`）。若 teammate prompt 倾向自由命名，role 文件必须重复硬编码精确路径。

### Visibility vs persistence

可见性（bind.md § Phase-scoped visibility rules）约束的是"谁能在消息层看到谁的输出"。落盘是独立的：所有 teammate 产物都落盘到同一 `.team/reports/` 目录，但 Round 1 阶段两个研究员**不读对方文件**，即使文件已存在。框架通过 `member_results_delivery` 事件在 Round 2 才把对方观点投递过来。

## Failure Handling

### (a) Teammate failure

| 失败类型 | 检测方式 | 处理策略 | 报告呈现 |
|---|---|---|---|
| **超时** | 超过时间限制 | 1. 重试1次（相同输入）<br/>2. 重试2次（简化输入）<br/>3. 标记为缺失，继续流程 | 在报告中标注"[角色名]超时，输出缺失" |
| **格式错误** | 不符合Output Schema | 1. 重试1次（提示格式要求）<br/>2. 重试2次（提供模板）<br/>3. 标记为格式错误，继续流程 | 在报告中标注"[角色名]格式错误，原始输出: ..." |
| **内容不足** | 未达到Success Criteria | 1. 重试1次（提示最低要求）<br/>2. 重试2次（降低要求）<br/>3. 标记为内容不足，继续流程 | 在报告中标注"[角色名]内容不足，缺失: ..." |
| **逻辑矛盾** | 内部逻辑不一致 | 1. 重试1次（提示矛盾点）<br/>2. 重试2次（要求重新推导）<br/>3. 标记为逻辑矛盾，继续流程 | 在报告中标注"[角色名]逻辑矛盾: ..." |
| **辩论辩护不足** | 未包含反驳论据 | 1. 重试1次（提示反驳要求）<br/>2. 重试2次（提供反驳模板）<br/>3. 标记为辩护不足，继续流程 | 在报告中标注"[研究员]辩论辩护不足，缺失反驳论据" |

**重试策略**:
- 每个角色最多重试2次
- 重试时使用相同的输入，但增加提示信息
- 第2次重试可以简化输入或降低要求
- 重试失败后标记为缺失，继续流程（不阻塞整个流程）

### (b) Input-overscale degradation

| 输入规模 | 降级策略 | 说明 |
|---|---|---|
| **财务数据 > 10年** | 仅分析最近3年 | 基本面分析师降级为3年分析 |
| **价格数据 > 2年** | 仅分析最近6个月 | 技术分析师降级为6个月分析 |
| **社交媒体数据 > 5个平台** | 仅分析主要3个平台 | 数字媒体分析师降级为3个平台分析 |
| **宏观经济数据 > 10个指标** | 仅分析关键3个指标 | 宏观分析师降级为3个指标分析 |
| **总输入token > 50,000** | 启用摘要模式 | 所有分析师使用摘要输入而非完整数据 |

**降级触发条件**:
- 输入数据超过角色处理能力
- 输入token超过角色token限制
- 输入时间超过角色时间限制

**降级报告**:
- 在报告中明确标注"[角色名]启用降级模式，原因: ..."
- 说明降级后的分析范围和限制

### (c) Quality gate failure

| 门控失败 | 处理策略 | 说明 |
|---|---|---|
| **辩论辩护不足** | 继续下一轮辩论 | 研究员在下一轮补充反驳论据 |
| **完成辩论校验失败** | 继续下一轮辩论 | 重新进行辩论，必须完成2轮 |
| **分析师报告质量不足** | 回流至分析师重新分析 | 重新分发任务给该分析师 |

**回流策略**:
- 回流时保留原始输入，增加质量门控失败原因
- 回流最多进行2轮
- 回流失败后标记为质量不足，继续流程（不阻塞整个流程）

### (d) Debate failure handling

| 辩论失败场景 | 处理策略 | 说明 |
|---|---|---|
| **研究员无法反驳** | 标记为辩护不足，继续流程 | 在报告中标注辩护不足，研究员输出现有论据的辩论结论 |
| **辩论无法达成共识** | 明确分歧点，继续流程 | 研究员明确分歧点，输出辩论结论 |
| **辩论逻辑混乱** | 重试1轮辩论 | 重新进行辩论，最多重试1次 |
| **辩论未完成2轮** | 强制继续辩论 | 必须完成2轮辩论，不允许提前终止 |

**辩论失败报告**:
- 在报告中明确标注辩论失败原因
- 说明研究员如何输出辩论结论
- 提出基于现有论据的决策建议

### (e) Complete team failure

| 失败场景 | 处理策略 | 用户通知 |
|---|---|---|
| **所有分析师失败** | 停止流程，生成错误报告 | "所有分析师分析失败，无法生成投资建议" |
| **所有研究员失败** | 停止流程，生成错误报告 | "研究员观点整合失败，无法进行辩论" |
| **研究员无法输出辩论结论** | 停止流程，生成错误报告 | "辩论结论输出失败，无法生成决策建议" |
| **投资组合与风险控制失败** | 停止流程，生成错误报告 | "投资组合构建失败，无法生成最终决策" |

**错误报告格式**:
```markdown
# 投资分析失败报告

## 失败原因
- [失败角色] 失败类型: [超时/格式错误/内容不足/逻辑矛盾/辩论辩护不足]
- [失败步骤] 失败原因: [具体描述]

## 已完成部分
- [已完成角色] 输出摘要: [简要描述]
- [辩论轮次] 辩论摘要: [简要描述]

## 建议
- [建议用户采取的行动]
- [建议重新尝试的条件]
```