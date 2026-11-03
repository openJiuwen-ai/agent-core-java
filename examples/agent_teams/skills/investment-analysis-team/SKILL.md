---

name: investment-analysis-team
description: |
  8-role debate-pattern investment analysis team with direct peer-to-peer debate for balanced decisions.
  Use when analyzing a security for investment decision with adversarial debate.
  Do NOT use for single-perspective analysis or quick price checks.
version: "0.3"
kind: team-skill
roles:
  - id: fundamental-analyst
    purpose: "Analyze financial statements, profitability, and competitive advantages to identify undervalued investment opportunities."
    skills: [gs_stock_financial_query]
    tools: [python3, curl, jq]
  - id: technical-analyst
    purpose: "Analyze price trends, technical indicators, and market sentiment to identify trading signals and key price levels."
    skills: [gs_stock_market_query]
    tools: [python3, curl, jq]
  - id: digital-media-analyst
    purpose: "Analyze social media sentiment, hot topics, and abnormal signals to detect market sentiment and potential risks."
    skills: [content-strategy]
    tools: [python3, curl, jq]
  - id: macro-analyst
    purpose: "Analyze macroeconomic environment, monetary policy, and systemic risks to identify macro-driven investment impacts."
    skills: [gs_economy_query]
    tools: [python3, curl, jq]
  - id: optimistic-researcher
    purpose: "Integrate analyses, identify opportunities, debate pessimistic researcher directly, and output debate conclusions."
    skills: []
    tools: []
  - id: pessimistic-researcher
    purpose: "Integrate analyses, identify risks, debate optimistic researcher directly, and output debate conclusions."
    skills: []
    tools: []
  - id: portfolio-risk-controller
    purpose: "Construct investment portfolio, formulate risk control strategies, and propose final investment decisions based on debate conclusions."
    skills: []
    tools: []

---

# 投资分析团队 (Investment Analysis Team) - 直接辩论版

这是一个 **Debate pattern (B+A+C)** 模式的多角色投资分析团队，结合了并行分解、直接点对点辩论和质量门控，旨在通过对抗辩论机制解决单一视角投资分析的偏见问题。团队通过四个分析师并行工作、乐观与悲观研究员并列获取相同输入并进行直接点对点辩论（无需协调员转达）、研究员直接输出辩论结论，最终由投资组合与风险控制角色生成可执行的投资决策。

## Workflow

团队遵循以下工作流程（详见 [workflow.md](workflow.md)）：

1. **Pre-flight: check dependencies** — 读取 [dependencies.yaml](dependencies.yaml) 并验证所有依赖是否可用。报告缺失项：`required: true` = 缺失后可能失败；`required: false` = 降级但仍可运行。**用户决定**是否继续。团队可以在纯推理模式（inline-persona-only）下运行，如果所有技能缺失。
2. **任务分发** — Leader 创建所有阶段的 task（包括自己最终的汇总 task，assignee="team_leader"），每个 task 设置 assignee 和 dependencies。**不要漏掉 Leader 自己的最终汇总环节。**
3. **四分析师并行分析** — 四个分析师并行工作，各自输出结构化分析报告。质量门控：每份报告必须包含至少3个关键发现，格式符合 Output Schema。
4. **研究员观点整合（Round 1）** — 乐观研究员和悲观研究员并列获取三个分析师输入（基本面、技术、数字媒体），并行工作，彼此不可见。乐观研究员从乐观视角识别投资机会，悲观研究员从悲观视角识别潜在风险。质量门控：每份报告必须包含至少2个关键观点和辩论辩护准备。
5. **直接点对点辩论（Round 2）** — 乐观研究员和悲观研究员直接看到对方的观点，进行反驳辩论。每轮辩论必须包含反驳论据和新证据支撑。辩论固定进行2轮。研究员直接输出辩论结论（无需协调员转达）。质量门控：每份辩论报告必须包含至少2个反驳论据和辩论结论。
6. **完成辩论校验** — Leader 验证辩论是否充分、是否达成共识或明确分歧。若判断为"否"，继续下一轮辩论，最多2轮。
7. **投资组合与风险控制** — 投资组合与风险控制角色基于研究员输出的辩论结论，构建投资组合建议、制定风险控制策略、提出最终决策。质量门控：必须包含至少3个风险控制措施。
8. **Final: emit 投资分析报告** — Leader 整合所有分析结果，生成最终投资分析报告（包含分析师观点、研究员观点、多轮辩论过程、辩论结论、投资决策）。

## Roles


| id                        | Purpose                          | When dispatched           | Input                                      | Key dependencies            | Role file                                                                |
| ------------------------- | -------------------------------- | ------------------------- | ------------------------------------------ | --------------------------- | ------------------------------------------------------------------------ |
| fundamental-analyst       | 分析财务报表、盈利能力和竞争优势，识别被低估的投资机会      | 每次运行（并行）                  | 证券代码、财务数据                                  | python3                     | [roles/fundamental-analyst.md](roles/fundamental-analyst.md)             |
| technical-analyst         | 分析价格趋势、技术指标和市场情绪，识别交易信号和关键价位     | 每次运行（并行）                  | 证券代码、价格数据                                  | python3                     | [roles/technical-analyst.md](roles/technical-analyst.md)                 |
| digital-media-analyst     | 分析社交媒体舆情、热点话题和异常信号，检测市场情绪和潜在风险   | 每次运行（并行）                  | 证券代码、社交媒体数据                                | content-strategy, python3   | [roles/digital-media-analyst.md](roles/digital-media-analyst.md)         |
| macro-analyst             | 分析宏观经济环境、货币政策和系统性风险，识别宏观驱动的投资影响  | 每次运行（并行）                  | 证券代码、宏观经济数据                                | python3                     | [roles/macro-analyst.md](roles/macro-analyst.md)                         |
| optimistic-researcher     | 整合分析，识别机会，直接辩论悲观研究员（固定2轮），输出辩论结论 | 每次运行（Round 1并行，Round 2辩论） | 基本面、技术、媒体分析师报告（Round 1）；悲观研究员反驳论据（Round 2） | 无                           | [roles/optimistic-researcher.md](roles/optimistic-researcher.md)         |
| pessimistic-researcher    | 整合分析，识别风险，直接辩论乐观研究员（固定2轮），输出辩论结论 | 每次运行（Round 1并行，Round 2辩论） | 基本面、技术、媒体分析师报告（Round 1）；乐观研究员反驳论据（Round 2） | 无                           | [roles/pessimistic-researcher.md](roles/pessimistic-researcher.md)       |
| portfolio-risk-controller | 基于辩论结论构建投资组合、制定风险控制策略、提出最终投资决策   | 每次运行（串行）                  | 辩论结论、投资目标、风险承受能力                           | 无                           | [roles/portfolio-risk-controller.md](roles/portfolio-risk-controller.md) |


> Before dispatching each teammate, read the corresponding role file and extract the
> `## Inline Persona for Teammate` section — paste it directly into the dispatch prompt.
> Most adopting agents do NOT auto-load role files for teammates.

## Files


| File                                   | What it contains                                                                      | When to read                                                                  |
| -------------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| [workflow.md](workflow.md)             | Mermaid diagram, step-by-step protocol, multi-round debate rules, Final Report format | Before first dispatch — the complete playbook                                 |
| [bind.md](bind.md)                     | Resource limits, behavioral constraints, debate failure handling and degraded modes   | When hitting limits, handling debate failures, or needing degraded-mode rules |
| [roles/*.md](roles/)                   | Per-role identity, success criteria, output schema, Inline Persona for Teammate       | Before dispatching each teammate — extract Inline Persona                     |
| [dependencies.yaml](dependencies.yaml) | External skills and tools required to run                                             | **Startup** — verify deps, report missing items, user decides go/no-go        |