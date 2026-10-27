# 测试评审 Rubric

> 评审对象：测试代码 + plan.md + RED/GREEN 证据行。上游输入：design.md 的测试设计表、spec.md 验收标准。作者侧标准见 `devflow-tdd` 与其 `references/test-quality.md`。
>
> 核心怀疑：**这些测试会放过哪种错误实现？**

## 检查项

### 覆盖映射（不过 = critical）

- [ ] 测试设计表的每个用例有对应测试；spec 每条验收标准可指到具体测试
- [ ] plan.md 任务覆盖的 Case ID 集合等于 design.md canonical 测试设计表全集；没有孤儿任务、漏测 Case ID 或 spec 外新增用例
- [ ] traceability.md 每条需求行的测试设计用例、任务、测试代码、验证证据列非空，或有明确 `N/A` 理由；无理由空列 = critical
- [ ] `modify` 需求有回归测试（保留的旧行为）；`remove` 有删除后语义测试
- [ ] 每条 NFR 的 Response Measure 有可量化验证（latency 数据、size 输出、leak 报告），不是"跑了没崩"
- [ ] 边界输入（最大/最小/空/越界）与错误路径（非法参数、资源失败、依赖失败）有用例

### 断言强度（不过 = critical）

- [ ] 抽查 2-3 个关键测试做 mutation 自检：把实现关键行改错（边界条件反转、删错误返回、删事件投递），测试必须变红——记录抽查结果
- [ ] 断言覆盖返回值、状态变化、对外输出三类可观察结果；spec Then 提到的每项（含负向"不发生"）有对应断言
- [ ] 无弱断言充数：非空检查、只查返回码、`Times(1)` 代替行为断言、恒真断言
- [ ] 无无断言/永远成功/靠 printf 人工观察的测试

### TDD 证据

- [ ] plan.md 每个 done 任务有 RED/GREEN/REFACTOR 证据行：命令、关键输出摘要、commit 锚点；只有叙述没有输出的"证据"不接受
- [ ] RED 证据的失败原因是行为缺失（不是环境/拼写错误）；GREEN 证据来自最终代码（锚点可核）
- [ ] REFACTOR 证据来自全绿后的 clean-code 检视；`N/A` 必须写明已对照 `devflow-clean-code` 自检且无任务内异味
- [ ] 一写就绿的测试有解释（行为已存在的确认）

### Mock 纪律

- [ ] mock/fake 只在真实边界：硬件、外部组件、时钟、慢速 IO
- [ ] 未 mock 模块内部纯逻辑；无 test-only 生产方法
- [ ] mock 行为与真实依赖的契约一致（错误码集、边界行为）

### 稳定与可维护

- [ ] 每个测试独立可重复：无顺序依赖、无共享可变状态、无未受控的时间/随机
- [ ] 测试命名说出场景与预期；一个测试一个行为；无 flaky 信号（sleep、重试）
- [ ] 测试代码本身整洁（重复 setup 已提取、无注释掉的死测试）

## Verdict 指引

- 覆盖映射或断言强度 critical → `需修改`
- 测试难写暴露设计问题（必须 mock 一切、setup 巨大）→ finding 指向 `devflow-design`，verdict `重新设计`
