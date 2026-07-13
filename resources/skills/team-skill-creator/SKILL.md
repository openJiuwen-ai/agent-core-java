---
name: team-skill-creator
description: Team Skill 生成器。把一种多智能体协作模式固化为可复用的 team skill 文件集（SKILL.md / roles/*.md / workflow.md / bind.md / dependencies.yaml）。在用户要把协作模式固化成模板、做 team skill、写 team skill 的某个文件、参考 investment-analysis-team 写新 team skill 时主动应用。涉及关键词：team skill creator、团队技能创建、固化协作、team skill 模板、roles/workflow/bind/dependencies。不适用于：单 agent 问题、Java 代码装配（用 agent-team-guide）、与 team skill 文件无关的讨论。
---

# Team Skill 生成器

本 skill 指导 Claude 为用户生成一个完整的 team skill 文件集。team skill 是 `com.openjiuwen.agentteams` 框架的可复用协作模板，由 5 类文件组成，放在团队工作空间 `skills/` 或 `~/.openjiuwen/workspace/skills/` 下被框架自动发现。

## 生成流程（必须按此执行）

1. **收集需求**：问用户四个问题（如果用户没说全）：
   - 团队做什么？（一句话目标）
   - 有几个角色？每个角色叫什么、干什么？
   - 角色间怎么协作？（并行 / 辩论 / 流水线 / 质量门控 / 混合）
   - 各角色依赖什么外部 skill / 工具？
2. **选协作模式**：根据用户描述从"协作模式分类"选 1~3 种模式组合。
3. **建目录**：`<team-skill-name>/`，下含 `roles/` 子目录。
4. **按模板生成 5 类文件**：套用下方模板填空。
5. **重点检查 roles/*.md**：每个角色文件必须含 `## Inline Persona for Teammate` 段（框架不自动加载，leader 要 read 后粘进 dispatch prompt）。
6. **自检**：按"生成后自检清单"逐条核对。
7. **告知放置路径**：告诉用户把生成好的目录放到 `~/.openjiuwen/workspace/skills/` 或团队工作空间 `skills/` 下，框架才能发现。

## 五个文件模板（带字段注释）

生成 team skill 时，用 `read_file` 读取对应模板文件，按占位符填空后 `write_file` 到目标目录。模板文件含完整字段注释，不在本 SKILL.md 内联以节省上下文。

| 要生成的文件 | 读取模板 | 说明 |
| --- | --- | --- |
| `<team-skill-name>/SKILL.md` | `templates/SKILL.md` | 团队元数据：name/version/kind: team-skill/roles 总览 |
| `<team-skill-name>/roles/<role>.md` | `templates/role.md` | 角色定义：Identity/Success Criteria/Boundary/Output Schema/Inline Persona |
| `<team-skill-name>/workflow.md` | `templates/workflow.md` | 执行剧本：流程图 + Step 0~N + Final Report |
| `<team-skill-name>/bind.md` | `templates/bind.md` | 约束：Resource Constraints/Behavioral Constraints/Failure Handling |
| `<team-skill-name>/dependencies.yaml` | `templates/dependencies.yaml` | 依赖：skills/tools/global_dependencies |

**关键提醒**：`roles/*.md` 的 `## Inline Persona for Teammate` 段是 leader 在 `spawn_member` 前要 `read_file` 提取并粘进 dispatch prompt 的——框架不会自动加载，必须由 leader 显式读取。模板里这一段用 ` ``` ` 包裹（不是 ` ```markdown `），避免 leader 提取时嵌套代码块出错。

## 协作模式分类（选 1~3 种组合）

按用户描述选模式，决定 workflow 结构和 bind 约束：

| 模式 | 特征 | workflow 结构 | bind 关键约束 |
|---|---|---|---|
| **并行分解** | 多角色同时分析不同维度，彼此不可见 | 并行 spawn → 各自输出报告 → leader 汇总 | `max_parallel_teammates` / 分析师彼此不可见 |
| **对抗辩论** | 两个对立角色直接点对点交换观点 | Round 1 独立 → Round 2 直接辩论 → 输出结论 | `debate_rounds_limit` / `min_debate_rounds` / 直接点对点 > leader 转发 |
| **流水线** | 上游输出自动投递给下游（靠 dependencies） | T1 → T2 → T3 带依赖 | 用 `create_task(dependencies=[...])` / `member_results_delivery` 自动投递 |
| **质量门控** | 上游完成后 leader 校验质量才放下游开始 | 上游 → 质量检查 → 下游 | 质量门控失败回流 / `max_retry_per_step` |
| **混合** | 以上几种组合（如 investment-analysis-team = 并行 + 辩论 + 流水线） | 多 Step 组合 | 多重约束，按阶段写 visibility rules |

**选模式提示**：
- 用户说"几个角色同时分析不同方面" → 并行分解
- 用户说"正反方/乐观悲观/支持反对" → 对抗辩论
- 用户说"上游做完下游接着做" → 流水线
- 用户说"做完要检查质量才继续" → 质量门控
- 用户说"既有并行又有辩论" → 混合

## 极简完整示例（2 角色并行）

用户说"我要一个团队：一个写代码、一个写测试，两人并行工作"。生成的最小 team skill：

### `<team-skill-name>/SKILL.md`

```yaml
---
name: dev-test-parallel
version: "1.0"
kind: team-skill
roles:
  - id: developer
    purpose: "实现用户描述的功能代码"
    skills: []
    tools: [python3]
  - id: tester
    purpose: "为功能代码写测试用例"
    skills: []
    tools: [python3]
---

# 开发-测试并行团队

两人并行：developer 实现代码，tester 写测试，彼此不可见，最后 leader 整合。

## Workflow

1. **Pre-flight** — 检查 python3 可用
2. **并行执行** — developer 写代码，tester 写测试
3. **Final Report** — leader 整合两份输出

## 角色职责表

| 角色 | 职责 | 输出 |
| --- | --- | --- |
| developer | 实现功能代码 | .team/reports/T1_developer.md |
| tester | 写测试用例 | .team/reports/T2_tester.md |
```

### `<team-skill-name>/roles/developer.md`

```markdown
# Role: 开发者 (Developer)

## Identity

> *"我是功能实现者，按用户描述写出可运行代码。"*

负责把用户需求翻译成代码实现。

## Success Criteria

- 代码符合用户描述的所有功能点
- 代码可运行无语法错误

## Boundary

**Forbidden**:
- Do NOT 写测试（这是 tester 的职责）

**Mandatory**:
- You MUST 输出完整可运行代码

## Output Schema

\`\`\`markdown
## Role: 开发者

### 功能实现
<代码块>

### 实现说明
<关键决策点>
\`\`\`

## Inline Persona for Teammate

\`\`\`
ROLE: 开发者 in a Teamskill.

你是功能实现者，按用户描述写出可运行代码。

You MUST 输出完整可运行代码。
You MUST NOT 写测试。

INPUTS YOU WILL RECEIVE:
- 功能描述: {FEATURE_DESC_PLACEHOLDER}

PROCESSING INSTRUCTIONS:
1. 分析功能描述
2. 实现代码
3. 写入 .team/reports/T1_developer.md
4. send_message 给 leader，附文件路径和摘要

OUTPUT:
- 写入文件: .team/reports/T1_developer.md
\`\`\`
```

### `<team-skill-name>/roles/tester.md`

完整版见 `references/dev_test_parallel/roles/tester.md`（人设改为"我是测试工程师，为功能代码写测试用例"，禁止"实现功能代码"）。

### `<team-skill-name>/workflow.md`

```markdown
# Workflow: 开发-测试并行团队

## 流程图

\`\`\`mermaid
graph TD
    S0[Step 0 Pre-flight] --> S1[Step 1 并行执行]
    S1 --> Final[Final Report]
\`\`\`

## Step 0: Pre-flight

- **Executor**: leader
- **Action**: 检查 python3 可用
- **Quality Gate**: python3 可用

## Step 1: 并行执行

- **Executor**: developer + tester（并行）
- **Input**: 用户功能描述
- **Output**: .team/reports/T1_developer.md + .team/reports/T2_tester.md
- **Quality Gate**: 两份文件都存在

## Final Report

leader 整合到 .team/reports/T3_final_report.md，原文引用两份输出。
```

### `<team-skill-name>/bind.md`

```markdown
# Bind: 开发-测试并行团队约束

## Resource Constraints

| 约束项 | 限制值 | 说明 |
|---|---|---|
| `max_parallel_teammates` | 2 | 两人并行 |
| `total_wall_clock_budget` | 10分钟 | 最长执行时间 |
| `per_role_token_limit` | 8000 tokens | 每人 token 上限 |

## Behavioral Constraints

1. **Leader 不生成内容**: Leader 只负责任务分发和报告整合
2. **developer 和 tester 彼此不可见**: 并行工作时看不到对方输出

## Failure Handling

| 失败场景 | 处理 |
|---|---|
| 一方失败 | 重试 2 次，仍失败则 leader 报告失败点 |
```

### `<team-skill-name>/dependencies.yaml`

```yaml
# Dependencies: 开发-测试并行团队

skills: []

tools:
  - name: python3
    source: local
    required: true
    purpose: "运行代码和测试"
    roles:
      - developer
      - tester

global_dependencies:
  description: "只需 python3"
  fallback_mode: "无 python3 则无法运行"
```

## 生成后自检清单（必查）

生成完 5 类文件后，逐条核对：

- [ ] `SKILL.md` frontmatter 含 `kind: team-skill` + `roles` 总览
- [ ] `roles/` 下每个 `.md` 文件名与 `SKILL.md` 里 `id` 一致
- [ ] 每个 `roles/*.md` 含 `## Identity` / `## Success Criteria` / `## Boundary` / `## Output Schema` / `## Inline Persona for Teammate` 五段
- [ ] `## Inline Persona for Teammate` 段用 ` ``` ` 包裹（不是 markdown 代码块嵌套），含 `ROLE:` / `You MUST` / `INPUTS YOU WILL RECEIVE` / `PROCESSING INSTRUCTIONS` / `OUTPUT` 子段
- [ ] `workflow.md` 有 Step 编号 + 每步的 `Executor` / `Input` / `Output` / `Quality Gate`
- [ ] `bind.md` 含 `Resource Constraints` / `Behavioral Constraints` / `Failure Handling` 三段
- [ ] `dependencies.yaml` 的 `required: false` 项缺失时能降级运行
- [ ] 输出文件路径统一用 `.team/reports/T<序号>_<role>_<output>.md` 格式
- [ ] 角色间可见性规则与协作模式匹配（并行=彼此不可见，辩论 Round 2=直接可见）
- [ ] 告知用户放置路径：`~/.openjiuwen/workspace/skills/` 或团队工作空间 `skills/`

## 使用方式

1. **收集需求**：用户没说全 4 个问题时主动问。
2. **选模式**：从"协作模式分类"选 1~3 种组合。
3. **套模板**：5 类文件模板都在本 skill 里，按注释填空。
4. **参考极简示例**：2 角色并行的完整示例在上方，复杂场景照此扩展。
5. **自检**：按"生成后自检清单"逐条核对，特别是 `## Inline Persona for Teammate` 段。
6. **告知放置**：生成完告诉用户把目录放到 `~/.openjiuwen/workspace/skills/` 或团队工作空间 `skills/`。

## 参考入口

- 完整 team skill 示例：`examples/agent_teams/skills/investment-analysis-team/`（8 角色混合模式）
- **极简完整参照样本**：`references/dev_test_parallel/`（2 角色并行，5 文件全完整版，生成新 team skill 时 Read 此目录对照）
- 框架使用指南：`resources/skills/agent-team-guide/SKILL.md`
- 完整文档：`documents/zh/2.开发指南/多智能体/AgentTeams.md` 的"Team Skill"小节
