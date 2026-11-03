---
name: devflow-review
description: 在规格、设计、测试或代码需要独立评审时使用：阶段产物完成后的把关、人要求 review、或对既有产物做专项检查时。评审必须由作者之外的独立上下文执行，产出 findings 与 verdict。
---

# DevFlow 评审

## 总览

评审是 human-on-the-loop 的支点：AI 生产，独立评审暴露问题，人做最终把关。它是工作流的**必经节点**：specify、design、tdd 每个阶段产物完成后都经评审（R1/R2/R3，见 `using-devflow` 工作流），通过前不进入下一阶段。三条不变量：

1. **作者不自审。** 写产物的会话/agent 不能给自己出 verdict。评审由独立 subagent 或新会话执行——它没有作者的写作记忆，只能依赖产物本身，这正是"可冷读"的检验方式。
2. **评审者不动手修。** 评审产出 findings 和 verdict，修改由作者根据 findings 执行。裁判不下场。
3. **没有记录的评审等于没有评审。** 每轮评审必须在 `reviews/` 落盘一份记录；findings 的修复过程必须回写同一份记录（resolution 闭环）。口头说"评审过了"而 `reviews/` 里没有对应文件与闭环记录，按未评审处理。

评审不是流程仪式。一次好的评审 = 带着「这东西哪里会骗我」的怀疑去读：规格会在哪里被两种人读出两种意思？测试会放过哪种错误实现？代码哪里在对读者撒谎？

## 工作流

### 1. 确定目标与 rubric

| 评审目标 | Rubric | 关注核心 |
|---|---|---|
| spec.md | `references/spec-review-rubric.md` | 可测试性、变更风险显式、无走私的实现细节 |
| design.md（及 component-design-draft.md，如适用） | `references/design-review-rubric.md` | 契约完整、复杂度有理由、测试设计覆盖、追溯一致 |
| 测试 | `references/test-review-rubric.md` | 断言强度、覆盖映射、mock 边界、RED 证据 |
| 代码 | `references/code-review-rubric.md` + `devflow-clean-code` | 正确性、与设计一致、整洁标准、语言/领域规则 |

### 2. 以独立上下文执行

派发 `devflow-reviewer` subagent（agent name: `devflow-reviewer`，角色定义见 `agents/devflow-reviewer.md`；OpenCode 通过 `task` 工具传入 agent name，task prompt 为评审输入）执行评审，输入只给：被评审产物、它的上游工件（评审设计给 spec，评审代码给 design + diff）、对应 rubric、代码评审时的 `devflow-clean-code`、适用的 coding-standards / 领域技能。**不给**作者的推理过程和聊天历史。

### 3. 产出 findings 与 verdict

每条 finding：`位置 + 问题 + 为什么是问题 + 严重级 + 分类 + 建议返工阶段`。

| 严重级 | 含义 | 例 |
|---|---|---|
| `critical` | 不修不能继续：会导致做错事、留 bug 或不可审 | 验收标准不可测试；测试断言放过 mutation；错误路径资源泄漏 |
| `important` | 完成前应修 | 边界用例缺失；函数职责混杂；命名误导 |
| `minor` | 建议改进 | 措辞、风格微调 |

| 分类 | 含义 | 处理 |
|---|---|---|
| `LLM-FIXABLE` | 信息已足够，作者可按 finding 定向修复 | 不问人，回对应作者阶段修复并复审 |
| `USER-INPUT` | 缺业务事实、优先级、验收阈值、外部来源确认 | 只问 finding 指向的最小问题，拿到回答后再修 |
| `TEAM-EXPERT` | 需要模块架构师、资深工程师或团队规则裁决 | 把问题封装成 1-2 个具体决策点上抛，不在评审或作者阶段擅自决定 |

verdict 三选一：

- `通过`：无 critical/important，或仅剩已被人接受的 minor
- `需修改`：findings 可定向修复，修复后复审
- `重新设计`：问题出在上游（规格漏洞、设计方向错误），打回对应阶段

建议返工阶段按问题本质填写：

| 问题本质 | 返工阶段 |
|---|---|
| 规格不可测试、缺业务事实、Change Type / Existing Behavior 错 | `devflow-specify` |
| 设计契约、错误模型、测试设计、组件边界错误 | `devflow-design` |
| R3 中的测试断言、RED 证据、实现 bug、代码整洁问题 | `devflow-tdd` |

R3 的 `需修改` 默认回 `devflow-tdd`：测试弱就先补强或重写会失败的测试，代码问题就用 RED/GREEN/REFACTOR 或纯 REFACTOR 修复。只有 finding 明确证明规格或设计工件本身错误，才回 `devflow-specify` / `devflow-design`。

### 4. 落盘评审记录（必做，与评审同时发生）

记录写入同一组件根/工件根下 `features/<id>/reviews/<目标>-review-<日期>.md`（或团队覆盖路径），同一目标的复审追加轮次后缀（`-r2`、`-r3`）。每份记录包含：评审对象（含版本/commit）、findings 表（**含 Resolution 列**、分类、建议返工阶段）、verdict、抽查记录（如做了 mutation 自检，写明改了哪行、哪个测试红了）。格式见 `agents/devflow-reviewer.md` 的输出模板。

### 5. Findings 闭环（作者侧职责）

verdict 为 `需修改`/`重新设计` 时，作者按 findings 返工，并**逐条回写**原评审记录的 Resolution 列：

- 修复了：怎么改的 + commit 锚点
- 人接受不修：理由 + 谁接受的
- 升级为债务：登记去向（plan.md 债务节 / 新工作项）

返工顺序：

1. 先收集 `USER-INPUT` 与 `TEAM-EXPERT` 的答案；同一决策面合并成最少问题，不把整份评审记录丢给人。
2. 再修 `LLM-FIXABLE` findings；只改 finding 指向的行、章节、测试或代码，不借机重写无关内容。
3. 回填每条 finding 的 Resolution 后发起复审（新轮次记录）。

全部 critical/important 有 resolution 后才能复审。**Resolution 列有空着的 critical/important，门禁不算通过**——`devflow-ship` 的 DoD 会核验这一点。复审必须核对上一轮 Resolution 与实际 diff 一致；问题不能在新记录里“凭空消失”。

同一 R 节点最多自动返工复审 3 轮。第 3 轮仍有未闭环 critical/important，或持续出现新的同级问题，停止自动循环，把剩余问题、已修证据和需要人裁决的具体问题呈给人。

### 6. 人工确认（按运行模式）

- `attended`（默认）：把评审记录与 verdict 呈给人，**人同意后才进入下一阶段**；人的否决/接受意见记入评审文件。
- `unattended`：不停顿，但本技能的其余动作一项不少——独立评审、落盘记录、critical 阻塞返工与复审照常执行；人工确认列记 `N/A(unattended)`，供人事后统一审计 `reviews/`。

在 plan.md 门禁表更新本轮门禁状态与记录路径：`pending` 表示等待评审，`passed` 表示评审通过，`rework` 表示必须先回作者阶段修复。R3 评审为 `rework` 时，下一步是 `devflow-tdd`，不是再次直接评审，也不是进入 `devflow-ship`。

## 评审者纪律

- 按 rubric 逐项过，不凭整体印象打分；rubric 之外发现的问题照样列出
- 每条 critical/important finding 给出**具体位置**和**可执行的修复方向**，不写"质量有待提高"
- 抽查重于通读：测试评审必做 2-3 个关键用例的 mutation 自检；代码评审优先读错误路径与资源路径——那是问题密度最高的地方
- 不确定的判断标注"待人裁决"，不假装确定
- 发现产物间漂移（代码与 design 不符、测试与 spec 不符）→ 一律 critical：要么改产物，要么改工件，不允许默默不一致

## 风险信号

- 作者会话自己宣布"评审通过"
- 声称评审完成但 `reviews/` 没有对应记录文件（= 未评审）
- findings 修复后没有回写 Resolution，复审记录里问题"凭空消失"
- findings 全是 minor 措辞建议，对错误路径、断言强度、契约完整性只字不提（评审走过场）
- verdict 为"需修改"但 findings 没有一条具体到位置
- 评审者直接动手改了代码
- attended 模式下未经人确认就进入下一阶段；或以"unattended"为由省掉评审/记录本身
- 同一产物三轮评审仍在打回 → 停止循环，升级人裁决方向问题
- R3 `需修改` 后停在评审上下文里自修，或直接复审而没有作者阶段的 Resolution 与证据

## 支撑参考

| 文件 | 用途 |
|---|---|
| `references/spec-review-rubric.md` | 规格评审检查项 |
| `references/design-review-rubric.md` | 设计评审检查项 |
| `references/test-review-rubric.md` | 测试评审检查项 |
| `references/code-review-rubric.md` | 代码评审检查项 |
