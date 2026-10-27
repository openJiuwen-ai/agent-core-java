# Definition of Done

> 配套 `devflow-ship`。工作项关闭前的完成定义，人在最后把关时的固定核验清单。每项核验写结论：满足 / 缺口 + 返工去向。

## 第一层 SDD：意图正确

1. `spec.md` 存在且 R1 门禁通过；本轮实现范围与 spec 范围一致（没有 spec 外的功能混进来）
2. spec 评审记录存在（`reviews/`），critical/important findings 的 **Resolution 列已逐条回填**（修复+commit / 人接受+理由 / 债务+去向）
3. blocking Open Questions 全部闭合（答案已写回 spec），没有被悄悄遗忘的待决项

## 第二层 TDD：功能正确

4. `plan.md` 全部任务为 done；每个任务带 RED/GREEN 证据行（命令、关键输出、commit 锚点），证据是本轮真实产生的；门禁状态表与 reviews/ 实际记录一致
5. spec 每条验收标准（含 NFR 的 Response Measure）有对应**通过**的测试；`modify` 条目有回归测试、`remove` 条目有删除后语义测试的通过记录
6. 完整测试套件在最终代码上全绿；构建无新增警告
7. R1/R2/R3 全部评审记录存在于 `reviews/`，最终 verdict 为通过，findings 全部有 Resolution；attended 模式下各门禁均有人工确认记录，unattended 模式下已把 `reviews/` 全量呈给人做事后审计

## 第三层 Clean Code：内在质量

8. 实现与 `design.md`（及组件设计，如适用）一致；所有偏离已回写工件并重新确认
9. 适用约束审计逐项标注状态（`clean` / `documented-debt` / `critical-open` / `N/A`+理由），并给出证据引用（任务 REFACTOR 证据、implementer `clean_code_check`、R3 finding/resolution、静态分析输出、review 摘要、commit、或债务登记）。只写 `clean` 没有证据，按缺口处理：

| 约束项 | 来源 |
|---|---|
| 整洁代码 | `devflow-clean-code` |
| 语言编码规范 | 适用的 `<language>-coding-standards`，工作项涉及的每种语言一行 |
| 领域约束 | 命中 description 的领域开发技能各一行；未命中的相邻领域不用列，除非评审要求说明 N/A |

   任一 `critical-open` → 阻塞关闭；`documented-debt` 必须有可定位的登记与去向；`N/A` 必须说明为什么该约束不适用
10. 静态分析新增项已修复或带理由抑制；无未解释的 critical 项

## 一致性与追溯

11. `traceability.md` 链路闭合：每条需求 → 设计章节 → 测试用例 → 代码/测试文件 → 证据；`N/A` 有理由
12. 触及组件边界的工作项：组件设计修订已经模块架构师确认，promotion 已规划

## 裁剪说明

- 微小修改（按 `using-devflow` 裁剪规则省略了 spec/design）：第 1-3、8 项替换为「plan.md 中的验收标准已全部有通过测试」；其余各项**不降低**。
- 缺陷工作项（DTS）：第 1-3 项替换为「fix.md 的复现/根因/修复边界完整，复现测试先失败后通过」；其余各项不降低。
- 紧急不等于绕过：任何情况下证据要求（4-7、10）与一致性要求（8、11）不可裁剪。
