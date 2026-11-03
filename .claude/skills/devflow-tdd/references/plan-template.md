# plan.md 模板

使用说明：

- `plan.md` 是工作项的**执行计划与中断恢复的单一入口**：任何新会话（上下文完全丢失）只读 spec.md → design.md → plan.md 三个文件，就能从断点继续执行，不需要任何聊天记忆。这决定了它的写法标准：**每个任务自包含**——组件根、工件根、精确文件路径、测试用例锚点、验证命令、完成判据、REFACTOR 检视标准全部内联，不写「同上」「见聊天记录」。
- 生命周期：`devflow-specify` 在工作流启动时建骨架（组件根 + 工件根 + 运行模式 + 门禁表 + 边界）；`devflow-design` 评审通过后由 `devflow-tdd` 细化任务拆解；TDD 执行期逐任务更新状态与证据行，并在任务完成后直接选择下一个唯一可执行任务继续；各 R 评审节点更新门禁表。门禁表也是 todo / 计划投影的来源：`pending` 表示下一步去评审；`rework` 表示评审已打回，下一步先回对应作者阶段修 findings，修完再复审；`passed` 表示评审 verdict 已通过，attended 下还要看人工确认列是否为 yes。任务级 `done` 表示本任务 RED/GREEN/REFACTOR 证据齐全、traceability 已更新、提交完成，不表示等待人工确认。
- plan 是 design 测试设计的**执行索引层**，不是测试设计本身：不得新增 design.md 中没有的用例或业务事实；发现缺用例 → 回 `devflow-design`。

````markdown
# <Work Item ID> 执行计划

## 运行模式与门禁状态

- 运行模式: attended / unattended（工作流启动时向用户确认一次，此后沿用）
- 组件根: `<absolute-or-repo-relative-component-root>`
- 工件根: `<component-root>/features/<id>-<slug>`（或 `AGENTS.md` 覆盖后的等价路径）
- 长期文档根: `<component-root>/docs`（或 `AGENTS.md` 覆盖后的等价路径）
- 来源工件: spec.md@<commit> / design.md@<commit>

| 门禁 | 状态 | 轮次 | 评审记录 | 返工目标 | 人工确认（attended） |
|---|---|---:|---|---|---|
| R1 spec 评审 | pending / passed / rework | 0 | reviews/spec-review-<日期>.md | devflow-specify / N/A | yes / no / N/A(unattended) |
| R2 design 评审 | pending / passed / rework | 0 | reviews/design-review-<日期>.md | devflow-design / N/A | … |
| R3 test+code 评审 | pending / passed / rework | 0 | reviews/test-review-…、code-review-… | devflow-tdd / devflow-design / devflow-specify / N/A | … |
| ship DoD | pending / passed | 0 | closeout.md | N/A | … |

## 恢复指引（保留此节原文）

上下文丢失后从本文件恢复：

1. 先读取本文件头部的组件根、工件根、长期文档根，后续所有相对路径都以这些根解析；
2. 读 spec.md、design.md（必要时 component-design-draft.md）取得契约与测试设计；
3. 看上方门禁表确定所处阶段：有 `pending` 门禁 → 先去 `devflow-review`；例如 spec 已写完但 R1 pending 时，下一步是评审，不是人工确认或 `devflow-design`；
4. 有 `rework` 门禁 → 先去“返工目标”阶段修 findings，Resolution 全部闭环后再回 `devflow-review` 复审；R3 rework 默认返工目标是 `devflow-tdd`；
5. attended 模式下，门禁为 `passed` 但人工确认列为 no → 先呈人确认评审记录，不重复评审；
6. 门禁全通过且有未完成任务 → 从下方第一个唯一可执行的非 done 任务继续，按其「步骤」执行；任务间不询问是否继续；
7. in-progress 任务以其「步骤」勾选与证据行判断断点：有 RED 证据无 GREEN 证据 = 从实现继续；
8. 若存在多个 in-progress 任务、多个同等 next-ready 候选、依赖冲突或阻塞项未闭合，先停止并修正 plan 状态，不猜测选择；
9. 运行模式以本文件头部为准，不重新询问；attended / unattended 只影响 R 门禁后的人工确认，不影响 TDD 任务间续跑。

## 计划边界

- 范围内 / 范围外:
- 假设:
- 阻塞项:

## 任务拆解

<!-- 任务粒度 = 一组内聚的测试设计用例（薄垂直切片：完成后可构建、全绿、可独立提交）。
     每个任务自包含；新增任务必须能回指 design.md 测试设计表。
     任务状态必须支持唯一 next task 判定：同一时间最多一个 in-progress；依赖满足且未阻塞的第一个 pending 任务即为下一任务。
     细化完成后核对：所有任务覆盖的 Case ID 集合 = design.md 测试设计表全集。 -->

### T1: <标题>  [pending / in-progress / done]

- 覆盖测试设计用例: TC-001（<Given/When/Then 一行摘要>）、TC-002（<摘要>）
- 覆盖需求: FR-001（Change Type: modify，回归基线见 spec）
- 文件:
  - 测试: `test/<精确路径>`
  - 实现: `src/<精确路径>`（允许触碰的范围；范围外文件列入「范围外」）
- 步骤:
  - [ ] RED: 按 TC-001 写失败测试 `<测试名>`；运行 `<测试命令>` 确认因行为缺失而失败
  - [ ] GREEN: 最小实现；运行 `<完整套件命令>` 确认全绿、无新增警告
  - [ ] REFACTOR: 对照 `devflow-clean-code` 检视任务触碰范围；清理命名/函数/控制流/错误路径/重复/死代码；或记录 `N/A` + 无异味理由；每步跑测试
  - [ ] 记证据行、更新 traceability.md 对应行、提交 `<提交信息建议>`
- 完成定义: <可判定条件，如"TC-001/002 通过；mode 非法输入路径覆盖；套件 47/47">
- 依赖: <前置任务或无>
- 就绪条件: <依赖已 done、所需工件存在、无阻塞项；用于唯一选择下一任务>
- 阻塞原因: <无 / 具体阻塞项；非“无”时不得自动选择为下一任务>
- 执行模式: implementer-subagent / controller-direct（仅 runtime 无 subagent 能力时允许 controller-direct，并写明原因）
- 派发记录: <subagent 标识或派发摘要；controller-direct 时写明退化原因>
- 证据:
  - RED:   <命令> → <关键失败输出摘要> @ <commit>
  - GREEN: <命令> → <通过摘要> @ <commit>
  - REFACTOR: <改动摘要 + 测试命令摘要> @ <commit> / N/A（已对照 clean-code 自检，无任务内异味：<理由>）

### T2: …（同结构）

## 评审返工队列

<!-- R1/R2/R3 被评审打回时维护。pending 门禁不写返工队列；rework 门禁必须有对应 open finding。
     R3 finding 命中已 done 任务时，保留原任务证据，创建 Tn-rework 或在原任务下追加返工条目。 -->

| Finding | 来源评审 | 严重级 | 分类 | 返工目标 | 关联任务/文件 | 状态 | Resolution / 复审 |
|---|---|---|---|---|---|---|---|
| F-001 | reviews/<目标>-review-<日期>.md#1 | critical / important / minor | LLM-FIXABLE / USER-INPUT / TEAM-EXPERT | devflow-tdd / devflow-design / devflow-specify | T1 / `src/...` / `test/...` | open / fixed / accepted / debt | <修复摘要 + commit + 验证命令；复审记录路径> |

## 风险与待确认

| ID | 风险 / 待确认项 | 是否阻塞 | 处理方式 |
|---|---|---|---|

## 债务登记

<!-- 路过发现不顺手修的问题登记于此，ship 时核对去向 -->

| 项 | 发现于 | 去向（新工作项 / issue） |
|---|---|---|
````
