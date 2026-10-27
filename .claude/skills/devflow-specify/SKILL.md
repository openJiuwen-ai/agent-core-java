---
name: devflow-specify
description: 在开始一个新的开发工作项（功能、变更、需要正式规格的缺陷）时使用，把一句话需求或散乱输入澄清成可测试的规格；也在规格评审被打回需要修订时使用。不用于产品方向决策或实现设计。
---

# DevFlow 规格（第一层 SDD）

## 总览

规格是模型与人之间的第一份契约。它存在的唯一目的：**让"要做什么"清晰到不需要猜**。判断一份规格好不好只有一个标准——**每条需求都可测试**：能直接落成一个失败的测试用例（RED），并且两个不同的人读完会写出相同的测试。

写规格时你在对抗的失败模式：需求含糊 → 模型靠猜补全 → 做出来的不是用户要的东西。所以规格阶段的纪律是：**澄清而不臆造**。你可以追问、可以提出方案让人选择，但不能替人决定业务规则、优先级和验收阈值。

产出：组件根下的 `features/<id>-<slug>/spec.md`（模板见 `references/requirement-template.md`；组件仓库根 `AGENTS.md` 覆盖路径时使用覆盖后的工件根）。

## 工作流

### 1. 收集上下文

读取：用户的原始请求、上游单据（如有）、相关的长期文档（组件设计、接口文档）、组件仓库根 `AGENTS.md`。先按 `using-devflow` 的路径解析纪律确定组件根与工件根；没有确定组件根前不创建 `features/` 或 `docs/`。判断工作项类型：新功能（AR）、变更（CHANGE）、缺陷（DTS，通常先经 `devflow-fix` 产出根因再回到这里）。

### 2. 澄清（Capture → Challenge → Clarify）

按以下顺序提问，已清晰的跳过，不重复追问：

1. 目标与成功标准：做完后什么变得不同？怎么验证做到了？
2. 核心行为与触发条件
3. 边界、异常路径、失败时的预期行为
4. 既有行为基线：这是新增、修改还是删除既有行为？修改/删除时旧行为是什么？
5. 接口与兼容性：谁调用、谁被调用、错误语义、对既有调用方的影响
6. 非功能约束：实时性、内存、并发、安全（不适用就不强加）

每轮结束总结「已锁定」与「待确认」。只剩 1-2 个问题时合并问。**业务方向、优先级、验收阈值答不上来时，列入 Open Questions 交回提出人，不自己编。**

### 3. 写需求条目

每条核心需求是一个结构化条目，不是一段散文。

**分类前缀**：

| 前缀 | 含义 |
|---|---|
| `FR-` | 功能需求：可观察的系统行为 |
| `NFR-` | 质量需求：实时性、内存、并发、安全等（必须有阈值） |
| `CON-` | 硬性约束：目标平台、编译条件、ABI 兼容等 |
| `IFR-` | 接口需求：对外服务契约、协议、错误码 |
| `ASM-` | 假设：失效会改变规格的事实 |
| `EXC-` | 显式排除项：本轮不做的事 |

**条目字段**：`ID`、`Statement`（EARS 句式）、`Acceptance`（Given/When/Then）、`Priority`、`Source`（上游单据/文档锚点，不接受"口头要求"）、`Change Type`（new/modify/remove）、`Existing Behavior`（modify/remove 必填）。

**Statement 用 EARS 句式**，让条目可冷读判断：

| 模式 | 句式 |
|---|---|
| 常驻行为 | `<主体> 必须 <持续成立的能力或约束>` |
| 事件触发 | `当 <触发条件> 时，<主体> 必须 <可观察结果>` |
| 状态约束 | `在 <状态/前置条件> 下，<主体> 必须 <行为结果>` |
| 异常路径 | `如果 <异常条件>，<主体> 必须 <保护/反馈/恢复行为>` |
| 可选配置 | `在启用 <配置> 时，<主体> 必须 <行为结果>` |

主体写「本组件/该模块」，不写无主体的「系统应该」。Statement 里不出现实现细节（函数签名、数据结构、库名、并发原语）——那些属于设计。

**Acceptance 用 Given/When/Then**，规则：

- 每条 FR 至少一个正向验收 + 关键失败路径各一条
- 必须能形成明确的通过/不通过判断；禁止「体验良好」「足够快」
- 一条验收只验证一个行为
- 验收要能直接落成 RED 测试用例——这是规格与 TDD 的接口

**Change Type 按对既有可观察行为的影响分类**（不是按"是不是新工作项"）：

| 类型 | 判定 | 要求 |
|---|---|---|
| `new` | 新增能力且不改变任何既有接口语义、错误码、状态机、阈值 | 基线写 `N/A` |
| `modify` | 改变既有行为、错误语义、默认值、阈值、兼容承诺 | 必须写旧行为基线；Acceptance 覆盖保留的行为与批准的破坏 |
| `remove` | 移除/禁用/废弃既有能力或兼容承诺 | 必须写被移除行为、已知消费者、删除后的可观察语义 |

一条需求同时含 new 与 modify 信号 → 拆条；不能拆 → 按更高风险归类。碰了旧路径却标 `new` 是规格阶段最危险的伪装，它会让回归风险消失在评审视野里。

### 4. NFR 写成 QAS

每条核心 NFR 必须能写成 Quality Attribute Scenario 五要素：**Stimulus Source / Stimulus / Environment / Response / Response Measure**。Response Measure 必须有阈值或可判定准则。写不出来 = 还不够具体 → 回澄清或列 Open Question。完整格式与六个领域示例见 `references/nfr-quality-attribute-scenarios.md`。

### 5. 粒度检查

单条 FR 出现以下信号必须拆分：多角色打包、CRUD 打包成"管理功能"、需要 ≥4 个独立场景才能说清、混写多个状态下的不同规则、即时结果和延时/异步结果绑在一条。拆分后每条子需求重写自己的 Acceptance，不允许写「同父需求」。详见 `references/granularity-and-split.md`。

### 6. 初始化追溯矩阵与执行计划骨架

- 按 `references/traceability-template.md` 初始化 `<component-root>/features/<id>/traceability.md`（或组件仓库覆盖后的等价路径）：每条核心 FR/NFR/IFR/可测 CON 一行，填入需求条目、Change Type、上游锚点列；设计/实现列留给后续阶段。ASM/EXC 放入备注或范围说明，不伪装成实现追溯行。追溯矩阵是 spec-design-code 一致性的显式约束，`devflow-review` 抽查、`devflow-ship` 终验。
- 按 `devflow-tdd/references/plan-template.md` 建立 `<component-root>/features/<id>/plan.md`（或组件仓库覆盖后的等价路径）骨架：写入组件根、工件根、运行模式（工作流启动时向用户确认的 attended/unattended）、门禁状态表、计划边界；任务拆解留给 `devflow-tdd` 在设计评审通过后细化。

### 7. 自检并交评审

自检清单见文末。通过后只表示作者侧规格产物就绪，下一步必须进入 R1 门禁：派发 `devflow-review` 按 spec rubric 做**独立评审**并落盘记录（这是必经节点，不是可选预审）；评审 verdict 通过后，attended 模式再把评审记录与 verdict 呈人确认，并更新 plan.md 门禁表。**R1 门禁未通过（含 attended 下未确认）前不进入设计。**

## 正反例

反例——这些都不是规格：

```text
❌ FR-001: 系统应该处理用户请求            （无主体、无触发、无结果，不可测试）
❌ NFR-001: 性能要好                       （无阈值，落不成测试）
❌ FR-002: 增加一个环形缓冲区处理协议解析    （混入实现决策；环形缓冲区是设计的事）
❌ FR-003: 无效 mode 返回 ERR_UNSUPPORTED   （实际修改了既有错误码语义却未标 modify、未写旧行为）
```

正例——可冷读、可测试、变更风险显式：

```markdown
### FR-001 模式切换
- Statement: 当组件 X 收到 SetMode 请求且 mode ∈ {NORMAL, SAFE} 时，
  必须在下一控制周期内将运行模式更新为请求值，并发出 ModeChanged 事件。
- Acceptance:
  - Given 当前 mode=SAFE；When 调用 SetMode(NORMAL)；
    Then 下一控制周期内 ModeChanged.event=NORMAL，返回 OK。
  - Given 当前 mode=SAFE；When 调用 SetMode(INVALID)；
    Then 返回 ERR_INVALID_ARG，mode 仍为 SAFE，不发出 ModeChanged。
- Priority: Must
- Source: SR-1234 §3.2
- Change Type: modify
- Existing Behavior: 旧实现对非法 mode 返回 ERR_UNSUPPORTED_MODE；
  本条将错误码改为 ERR_INVALID_ARG（已获需求负责人批准）。
```

## 接口候选契约

当需求涉及对外接口（IFR 条目存在或接口语义被修改）时，spec 必须给出**语义级**接口候选契约：provider / consumer / 操作语义 / 输入输出（含单位与范围）/ 错误语义 / 同步异步与时序预期 / 兼容策略。**不写**语言级函数签名、私有数据结构、重试次数、线程模型——那些是设计决策。说不清 provider 或错误语义时写 Open Question，不猜。

## Open Questions

每个开放问题标注：`blocking`（阻塞规格确认）或 `non-blocking`、负责人、它阻塞了什么决策。blocking 问题闭合前规格不能确认。把待决问题藏在正文里而不列出来，等于把猜测走私进规格。

## 风险信号

- 把用户原文逐句改写成条目就当规格写完了（原文 ≠ 规格，必须经过澄清与结构化）
- 验收标准只是把 Statement 换个说法重复一遍，没有新增判定口径
- NFR 写「尽快」「合理」并打算"实现时再定"
- 碰了既有接口/状态机/错误码却全部标 `new`
- 自己猜了 Open Question 的答案并补进规格
- 在 Statement 或 Acceptance 里指定数据结构、函数名、库选择

## 自检清单

- [ ] 每条 FR/IFR：EARS 句式 Statement + 可落成 RED 用例的 Acceptance + Source
- [ ] 每条核心 NFR：QAS 五要素 + 含阈值的 Response Measure
- [ ] 每条 FR/NFR/IFR/CON 有 Change Type；modify/remove 有旧行为基线与回归验收
- [ ] 范围与非范围显式；本轮不做的事在 EXC 或新工作项里，不埋在正文
- [ ] 涉及接口时有语义级接口候选契约
- [ ] Open Questions 已分类；blocking 项已闭合或显式交回负责人
- [ ] 通篇没有实现细节（签名、数据结构、库、并发原语）
- [ ] traceability.md 已初始化，每条核心需求有行，需求/Change Type/上游锚点列已填
- [ ] plan.md 骨架已建立：组件根、工件根、运行模式（已向用户确认）、门禁状态表、计划边界

## 支撑参考

| 文件 | 用途 |
|---|---|
| `references/requirement-template.md` | spec.md 模板 |
| `references/nfr-quality-attribute-scenarios.md` | QAS 五要素格式 + 实时性/内存/并发/资源/错误/安全六个完整示例 |
| `references/granularity-and-split.md` | 过大条目的检测信号与拆分规则 |
| `references/traceability-template.md` | 追溯矩阵模板（spec-design-code 一致性约束） |
