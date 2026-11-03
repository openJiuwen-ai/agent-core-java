---
name: devflow-tdd
description: 在实现任何功能或修复任何缺陷、即将编写实现代码时使用；设计确认后的整个实现期都适用。强制测试先行的 RED→GREEN→REFACTOR 循环。不用于规格编写、设计决策或纯文档修改。
---

# DevFlow TDD（第二层）

## 总览

TDD 把"正确"从主观判断变成可执行、可复现的事实。核心原则一句话：

> **没有先看着它失败的测试，就没有实现代码。**

为什么顺序不可妥协：先写实现再补测试，测试一写出来就通过——通过证明不了任何东西。你没见过它失败，就不知道它是否真的在验证目标行为，还是在验证你的实现碰巧做的事。**测试先行回答「代码应该做什么」；测试后补只能回答「代码现在做了什么」。**

写在测试之前的实现代码：删掉，重来。不要"留着当参考"——你会照着它写测试，那就是测试后补。

例外（需向人确认）：一次性探索原型（探索完丢弃，正式实现仍走 TDD）、生成代码、纯配置。想着"就这一次跳过 TDD"？停。那是合理化。

## 计划与任务组织

实现的输入是 design.md 的**测试设计表**；执行的载体是组件根下 `features/<id>/plan.md`（或团队覆盖路径；模板见 `references/plan-template.md`）。

**进入实现前先细化 plan.md**（specify 阶段已建骨架：组件根 + 工件根 + 运行模式 + 门禁表）：把测试设计表的用例组织成任务，每个任务**自包含**——用例锚点（含 Given/When/Then 摘要）、精确文件路径、RED/GREEN/REFACTOR 步骤与验证命令、完成定义全部内联。细化完成后先核对：plan 覆盖的 Case ID 集合必须等于 design.md 测试设计表的 Case ID 全集；缺失或新增都说明工件漂移，先回 `devflow-design` 修正。标准只有一个：**一个全新会话只读 spec.md + design.md + plan.md 就能从任意断点继续执行**。"同上""见前文"式的任务描述使中断恢复失效，按违规处理。

每个任务完成时在 plan.md 附上 RED/GREEN/REFACTOR 证据行（命令 + 关键输出摘要 + commit 锚点；REFACTOR 可为有理由的 `N/A`）——这是评审者和人核验"测试真的失败过、真的在最终代码上跑过、代码已经过 clean-code 检视"的最低限度证据，不接受只有叙述没有输出的"证据"：

```markdown
- 证据:
  - RED:   `ctest -R ModeServiceTest` → FAIL: SetModeRejectsInvalid…
           (expected ERR_INVALID_ARG, got OK) @ a1b2c3d
  - GREEN: `ctest` → 47/47 passed, 0 warnings @ d4e5f6a
  - REFACTOR: 提取 `is_valid_mode()`，替换裸值；`ctest` → 47/47 passed @ e7f8a9b
```

规则：

- **一次只有一个 in-progress 任务**。每个任务是一个薄垂直切片：完成后可构建、全部测试通过、可独立提交。
- 任务循环：取 plan.md 第一个唯一可执行的非 done 任务 → RED → GREEN → REFACTOR → 补证据行与 traceability → 更新任务状态 → 继续下一个。**任务完成不是人工确认点**；只要 plan.md 中还能唯一选出下一任务，就在同一 TDD 阶段继续执行。**REFACTOR 是默认步骤，不是可选收尾**；如果 GREEN 后已无任务内异味，只能记录 `REFACTOR: N/A` 并写明已对照 `devflow-clean-code` 自检的理由。**每步勾选实时更新到 plan.md**，断点信息只存在于磁盘，不存在于会话记忆。
- 任务完成时更新同一组件根/工件根下 `features/<id>/traceability.md`（或团队覆盖路径）对应行的任务 ID、代码文件、测试代码文件、验证证据列。
- plan 是测试设计的执行索引：不得新增 design.md 中没有的用例或业务事实；发现缺用例 → 回 `devflow-design`。
- 实现中发现设计错误或规格漏洞：**停下任务**，在 plan.md 记录阻塞原因，回 `devflow-design` / `devflow-specify` 修正工件并重新评审，不在代码里悄悄绕过。
- 中断恢复：按 plan.md 的「恢复指引」节执行——先看门禁表，再找第一个非 done 任务，以步骤勾选与证据行判断断点。

## R3 返工模式

`devflow-tdd` 不只从 R2 通过后进入；R3 测试/代码评审打回时也从这里恢复。进入返工模式时先读 plan.md 门禁表和最新 `reviews/` 记录，找出未闭环的 critical/important findings：

1. **确定返工目标**：测试断言、RED 证据、实现 bug、代码整洁问题默认在本阶段修；如果 finding 证明 design.md 或 spec.md 本身错误，停止本阶段并回对应上游。
2. **建立返工队列**：在 plan.md 的“评审返工队列”中为每条 finding 记录评审文件、finding 编号、目标任务/文件、测试命令、当前状态。已 `done` 的任务被命中时，不覆盖原任务证据；创建 `Tn-rework` 或在原任务下追加“R3 返工”条目。
3. **按 TDD 修复**：测试弱或缺失 → 先写/加强会失败的测试并记录 RED；实现错误 → 先用失败测试复现；纯整洁问题且不改变行为 → 在全绿上 REFACTOR，并记录测试保持全绿。不得为了过评审弱化断言。
4. **回填 Resolution**：每修完一条 finding，在原评审记录 Resolution 列写明修复摘要、commit 锚点、验证命令；同步更新 plan.md 返工队列状态。
5. **复审而非 ship**：全部 open findings 闭环后，下一步必须回 R3 `devflow-review` 复审；复审通过前不能进入 `devflow-ship`。

同一 R3 门禁最多自动返工复审 3 轮。第 3 轮仍有 critical/important，或复审持续发现同类新问题，停止自动循环，把剩余 findings、已做证据和需要人裁决的问题呈给人。

## 执行模式：必须派发 implementer subagent

runtime 支持 subagent 时，**每个任务必须派发一个全新上下文的 implementer subagent**（agent name: `devflow-implementer`，角色定义见 `agents/devflow-implementer.md`）执行；父会话不得在主上下文里直接写测试或实现。新上下文只依赖打包的输入工作，天然防止长会话的上下文漂移，也强制设计工件可冷读。

> **Runtime dispatch**：不同 runtime 用不同机制派发 subagent。OpenCode 通过 `task` 工具，传入 agent name `devflow-implementer`，task prompt 即 Context Pack。其他 runtime 按各自等价机制执行。无法按 agent name 定向派发的 runtime（例如只能内联 system prompt 而无法注册具名 subagent），按下文退化为 controller-direct。

主会话只做 controller：解析工件、选择唯一任务、组装 Context Pack、调用 subagent、消费返回、更新 plan.md / traceability.md、提交、再选择下一任务。只有 runtime 明确没有 subagent 能力时，才允许退化为 controller-direct；退化时必须在 plan.md 当前任务下记录 `执行模式: controller-direct` 与原因（例如“当前 runtime 无 subagent 工具”）。任务很小、单文件、赶时间、上下文已经足够，都不是跳过 subagent 的理由。

派发时给 subagent 的 **Context Pack**（不传聊天历史）：

- 任务 ID 与对应测试设计用例（Case ID、场景、预期结果）
- design.md 相关章节（接口契约、错误模型摘录）与允许触碰的文件范围
- 测试/构建命令
- 派发的 subagent：`devflow-implementer`（OpenCode `task` 工具的 agent name；角色定义见 `agents/devflow-implementer.md`）
- **Quality Stack**：`required_skill_files` 列出必须读取的 skill 文件路径，至少包含 `skills/devflow-tdd/SKILL.md`、`skills/devflow-clean-code/SKILL.md`，以及按触碰文件发现到的适用 `<language>-coding-standards` 与领域技能；同时写明每个技能在本任务中的用途（循环纪律、通用 clean-code 自检、语言/领域约束）。只传路径与用途，不复制技能正文。
- 返回契约：`DONE`（附 `loaded_skills`、RED/GREEN/REFACTOR 证据行与按 `devflow-clean-code` 五维契约填写的 `clean_code_check`）/ `NEEDS_CONTEXT`（缺关键输入或 Quality Stack，回来重新打包）/ `BLOCKED`（越界或设计问题，附原因）

R3 返工派发时，Context Pack 还必须包含 finding 摘录（评审文件路径、finding 编号、严重级、分类、修复方向）、关联任务或 `Tn-rework` 标识、需要回填的 Resolution 位置。subagent 返回时必须列出已解决的 finding 编号；父会话负责核对并写回评审记录。

父会话职责：逐任务派发、校验返回的证据行、更新 plan.md 与 traceability、串联提交。subagent 返回 `BLOCKED` 提示设计问题时，父会话回 `devflow-design`，不催 subagent 硬做。

runtime 无 subagent 时退化为当前会话直接执行循环，纪律不变。

### Controller 连续执行协议

父会话是 TDD 阶段 controller；implementer subagent 只做一个任务，不能决定整个阶段是否暂停。每次消费 subagent 返回后按下面协议处理：

| 返回 / 状态 | 父会话动作 |
|---|---|
| `DONE` | 校验 `loaded_skills` 覆盖 Quality Stack、证据行和 `clean_code_check`；缺 `devflow-clean-code`、适用语言/领域技能或五维自检结论（简洁/可靠/可维护/可测试/高性能/范围纪律）时拒绝 DONE 并重派 → 更新 plan.md 任务状态、步骤勾选、证据行与 traceability.md → 提交 → 重新读取 plan.md 并选择下一个唯一可执行的非 done 任务继续派发新的 implementer subagent |
| `NEEDS_CONTEXT` | 先用 spec.md / design.md / plan.md / reviews/ 中已有工件补齐更收敛的 Context Pack（特别是 Quality Stack 的 `required_skill_files`）并重派；不得把完整聊天历史倾倒给 subagent |
| `BLOCKED` | 在 plan.md 记录阻塞原因；若是规格/设计/范围问题，回对应上游阶段并重新经过受影响门禁；若只是 Context Pack 打包不完整，收敛后重派 |
| 无剩余任务 | 把 R3 门禁置为 `pending`（或确认已有 pending 记录），进入 `devflow-review` 做测试与代码独立评审 |

停止条件只有以下几类：缺业务事实或专家决策；规格/设计与实现证据冲突；无法由工件补齐的 `NEEDS_CONTEXT`；`BLOCKED` 指向范围、依赖、测试设计或架构问题；plan.md 中存在多个 in-progress 任务、多个同等 next-ready 候选、依赖冲突或状态无法唯一判定；测试/构建环境无法产生可信结果；R3 自动返工复审达到 3 轮上限。

`attended` / `unattended` 只影响 R1/R2/R3 verdict 后是否呈人确认，以及 ship 关闭确认；不影响 TDD 阶段内部的任务间续跑。不要在一个任务 `DONE` 后询问“是否进入下一个任务”，除非命中上述停止条件。

## 循环

### RED：写一个失败的测试

把当前用例的预期结果落成可执行断言。一个测试只验证一个行为，名字直接说出这个行为。

```cpp
// ✅ 名字说明行为；驱动真实代码；断言覆盖返回值、状态、副作用
TEST_F(ModeServiceTest, SetModeRejectsInvalidModeWithoutStateChange) {
  ASSERT_EQ(OK, mode_set(MODE_SAFE));          // Given：处于 SAFE

  EXPECT_EQ(ERR_INVALID_ARG, mode_set((mode_t)42));  // When：非法输入

  EXPECT_EQ(MODE_SAFE, mode_get());            // Then：状态不变
  EXPECT_EQ(0u, fake_event_queue_count());     // Then：没有发出事件
}
```

```cpp
// ❌ 名字空洞；只验证了 mock 被调用，没验证任何真实行为
TEST(ModeTest, Test1) {
  MockQueue q;
  EXPECT_CALL(q, push(_)).Times(0);
  mode_set_with_queue(42, &q);
}
```

**验证 RED（必做，不可跳过）**：运行测试，确认——

- 它**失败**而不是报错（编译错误/段错误要先修到"干净地失败"）
- 失败原因是**目标行为缺失**，不是拼写错误或测试环境问题
- 测试一写就通过？说明它没有验证新行为：要么行为已存在（确认后跳过该用例），要么测试写错了。

把命令与关键失败输出记为 plan.md 的 RED 证据行（含 commit 锚点）。

### GREEN：最小实现

只写让当前 RED 转绿的最少代码。不实现测试没有要求的功能，不引入设计没有批准的抽象，不顺手清理。

```c
/* ✅ 刚好让测试通过 */
int mode_set(mode_t mode) {
    if (mode != MODE_NORMAL && mode != MODE_SAFE) {
        return ERR_INVALID_ARG;
    }
    g_mode = mode;
    event_queue_push(make_mode_changed_event(mode));
    return OK;
}
```

```c
/* ❌ 测试只要求两个模式，却"顺便"做了模式注册表 + 钩子机制 */
int mode_set(mode_t mode) {
    const mode_descriptor_t *desc = mode_registry_lookup(mode);
    if (desc == NULL) return ERR_INVALID_ARG;
    if (desc->pre_hook && desc->pre_hook(mode) != OK) { ... }
    ...
}
```

**验证 GREEN（必做）**：当前测试通过；**完整测试套件**通过（无回归）；构建输出干净（无新增警告）。其他测试挂了 → 现在就修，不带病推进。把命令与通过摘要记为 plan.md 的 GREEN 证据行（含 commit 锚点）。

### REFACTOR：在绿灯上清理

只在全绿后进行。两顶帽子严格分开：GREEN 帽只加行为，REFACTOR 帽只改结构——**重构不改变任何可观察行为，期间不新增任何测试预期**。

做什么：对照 `devflow-clean-code` 的五维判据审视本任务触碰范围，消除本任务引入的重复、改善命名、提取函数、用常量替换魔法数、收紧错误处理表达，检查测试代码、热路径和资源路径。每做一步跑一次测试，保持全绿。

不能静默跳过：即使没有代码改动，也要在 plan.md 记录 `REFACTOR: N/A`，说明已检查简洁、可靠、可维护、可测试、高性能和范围纪律，且无任务内异味。没有 REFACTOR 记录的任务不是 done。

边界：清理限于当前任务触碰的范围。发现需要跨模块的结构性重构、或想引入设计未声明的新抽象 → 登记为债务或回 `devflow-design`，不在任务内顺手做。REFACTOR 中发现还缺行为 → 摘下帽子，回 RED。

### 提交

每个任务完成（全绿 + 清理完）即提交一次，提交信息说明覆盖了哪些用例。小步提交让失败可定位、可回滚。

## 测试质量内建

评审时测试会被独立检查（`devflow-review`），但质量在编写时就要内建。最常见的三类弱测试：

**弱断言**——测试跑过了但什么都没证明：

```cpp
EXPECT_NE(nullptr, result);            // ❌ 只证明非空
EXPECT_EQ(OK, mode_set(MODE_NORMAL));  // ❌ 只查返回码，不查副作用

// ✅ 断言到具体值和全部可观察结果
EXPECT_EQ(MODE_NORMAL, mode_get());
ASSERT_EQ(1u, fake_event_queue_count());
EXPECT_EQ(MODE_NORMAL, fake_event_queue_last().payload.mode);
```

自检方法（mutation 思维）：**如果把实现里的关键一行改错，这个测试会失败吗？**不会 → 断言不够强。

**Mock 越界**——mock 了不该 mock 的东西：只 mock 真实边界（硬件、外部组件、慢速依赖、时钟）；不 mock 模块内部纯逻辑、不为测试给生产类加 test-only 方法、不验证"mock 被调用了"来代替验证行为结果。

**测试间耦合**——用例依赖执行顺序、共享可变全局状态、依赖真实时间。每个测试独立可重复：自带 setup/teardown，受控时钟。

完整的断言/命名/fixture/mock 判据见 `references/test-quality.md`。

## 合理化反驳

| 话术 | 现实 |
|---|---|
| 「这段太简单不用测」 | 简单代码也会坏。测试 30 秒，调试 30 分钟 |
| 「先写完实现再补测试，效果一样」 | 测试后补一写就过，证明不了任何东西；你失去了"看它失败"这唯一的证据 |
| 「我已经手动验证过了」 | 没有记录、不可复现、下次改动不会自动重跑 |
| 「写了几小时的代码删了可惜」 | 沉没成本。留着没有测试证明的代码才是负债 |
| 「测试太难写」 | 测试难写 = 设计难用。回设计简化接口，而不是绕过测试 |
| 「GREEN 时顺手重构更快」 | 行为变更和结构变更混在一个 diff 里，评审者无法分辨哪些变化是有意的 |
| 「先把后面几个用例的实现一起写了」 | 大切片失败时无法定位；一次一个用例 |

## 风险信号

- 测试一写出来就是绿的，而你说不出为什么
- evidence 里只有"测试通过"，没有它曾经失败的记录
- 一个任务的 diff 同时含行为变更和大量重命名/搬移
- 多个任务同时 in-progress
- 跳过完整套件，只跑新测试就宣布完成
- 为了让测试过而改弱断言，而不是修实现
- R3 打回后直接再次评审，未先按 findings 创建返工任务、补证据并回填 Resolution
- R3 修复完成后直接进入 ship，跳过复审

## 验证清单

任务完成前逐项确认：

- [ ] 每个新行为都有先失败后通过的测试；失败原因当时已确认是行为缺失
- [ ] plan.md 中本任务的 RED/GREEN 证据行齐全（命令 + 关键输出 + commit 锚点），证据来自真实运行
- [ ] 完整测试套件通过；构建无新增警告
- [ ] 断言经得起 mutation 自检（改错实现关键行，测试会红）
- [ ] mock 只用于真实边界；没有 test-only 后门
- [ ] REFACTOR 没有改变行为；清理留在任务范围内；若为 `N/A`，plan.md 写明已对照 `devflow-clean-code` 五维判据自检的理由
- [ ] plan.md 任务状态与 traceability.md 对应行已更新；本任务已提交
- [ ] implementer 返回的 `loaded_skills` 覆盖 Quality Stack；`devflow-clean-code`、适用的 `<language>-coding-standards` 与命中 description 的领域开发技能已在实现中遵循
- [ ] 若来自 R3 返工：每条 open finding 已映射到返工队列，Resolution 已回填，修复证据已记录，下一步是 R3 复审而不是 ship

## 支撑参考

| 文件 | 用途 |
|---|---|
| `references/plan-template.md` | plan.md 模板：运行模式与门禁表、自包含任务结构、恢复指引、证据行 |
| `references/test-quality.md` | 断言强度、测试命名、fixture 设计、mock 边界的详细判据与正反例 |
