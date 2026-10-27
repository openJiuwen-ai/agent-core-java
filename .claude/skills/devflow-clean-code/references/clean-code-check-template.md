# clean_code_check 模板

> 配套 `devflow-clean-code`。用于 TDD implementer 返回 `DONE`，以及父 controller 验收任务是否真的完成了 REFACTOR。模板不是要求长篇报告，而是防止 "looks clean" 这种不可审查的声明。

## implementer 返回格式

```markdown
loaded_skills:
- skills/devflow-tdd/SKILL.md
- skills/devflow-clean-code/SKILL.md
- skills/<language>-coding-standards/SKILL.md  # 如适用
- skills/<domain-skill>/SKILL.md               # 如适用

evidence:
- RED: <命令> -> <关键失败摘要> @ <commit 或 working tree 标识>
- GREEN: <命令> -> <通过摘要> @ <commit 或 working tree 标识>
- REFACTOR: <做了什么 + 验证命令> @ <commit 或 working tree 标识>
  # 或：REFACTOR: N/A - <具体无异味理由>

clean_code_check:
- 简洁: <当前实现没有投机抽象 / 已拆 flag 参数 / N/A 理由>
- 可靠: <可失败调用、错误路径、资源释放、失败状态检查结论>
- 可维护: <命名、函数大小、抽象层级、重复/死代码检查结论>
- 可测试: <测试命名、断言、fixture/mock、可重复性检查结论；无测试变更写 N/A 理由>
- 高性能: <热路径、无界操作、重复计算、资源成本检查结论；无性能相关触碰写 N/A 理由>
- 范围纪律: <只触碰任务范围；路过问题登记到哪里；行为变更/重构是否分离>
```

每项 1 句即可。重点是能被父 controller 和 R3 评审者冷读核验。

## REFACTOR: N/A 可接受示例

```markdown
REFACTOR: N/A - 本任务只新增 2 个错误码映射测试和 1 行查表项；生产代码无新函数/分支。已检查命名、错误路径、重复、测试 fixture 和范围纪律，无任务内异味。

clean_code_check:
- 简洁: 未新增抽象或配置；查表项是设计既有扩展点。
- 可靠: 无新增可失败调用；错误码映射不改变资源路径。
- 可维护: 新常量名与既有 DTC 命名一致；无重复逻辑。
- 可测试: 新测试名说明具体映射行为，断言具体返回值；fixture 未扩张。
- 高性能: 查表规模固定，无热路径额外循环或分配。
- 范围纪律: 仅触碰本任务对应表项和测试。
```

## 不可接受示例

```markdown
REFACTOR: N/A - looks clean
clean_code_check: all good
```

不可接受原因：

- 没有说明检查了哪些维度。
- 无法判断错误路径、测试代码或范围纪律是否被看过。
- 不能支持 R3 评审和 ship DoD 的证据审计。

## 父 controller 验收规则

父 controller 消费 implementer 返回时按下面顺序检查：

1. `loaded_skills` 必须包含 `devflow-clean-code`。只加载 `<language>-coding-standards` 不够。
2. RED/GREEN/REFACTOR 证据齐全；纯重构任务可无 RED，但必须有全绿基线和重构后验证。
3. `REFACTOR: N/A` 必须有具体理由，且理由覆盖本任务触碰范围。
4. `clean_code_check` 至少覆盖简洁、可靠、可维护、可测试、高性能、范围纪律。
5. 若返回中提到结构性问题、跨模块重构或设计契约不清，不能接受为 `DONE`；应记录 `BLOCKED` 并回 `devflow-design` 或登记债务。
6. 若 R3 返工任务来自 finding，返回必须列出已解决的 finding 编号；父 controller 再回填原评审记录 Resolution。

缺任一项时，拒绝 `DONE`：补 Context Pack 后重派，或要求 implementer 补真实证据。不要由父 controller 自己补写一个看起来完整的 `clean_code_check`。

## 纯重构任务格式

纯重构没有新增行为，不需要伪造 RED。证据应写：

```markdown
evidence:
- BASELINE: <全量测试/构建命令> -> <通过摘要> @ <commit>
- REFACTOR-1: <小批次重构摘要>；<验证命令> -> <通过摘要> @ <commit>
- REFACTOR-2: <下一小批次>；<验证命令> -> <通过摘要> @ <commit>
```

如果同时重构生产代码和测试代码，分批进行：生产代码批次由既有测试保护，测试代码批次不得修改生产代码。这样每一边都能验证另一边没有被同步改错。
