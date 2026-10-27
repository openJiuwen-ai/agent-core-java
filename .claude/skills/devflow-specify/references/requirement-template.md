# spec.md 模板

使用说明：`devflow-specify` 生成组件根下 `features/<id>-<slug>/spec.md` 的默认模板。团队 `AGENTS.md` 声明等价路径或模板时优先遵循团队约定。微小修改可压缩本模板（见 `using-devflow` 的裁剪规则），但需求条目的可测试性要求不变。

```markdown
# <Work Item ID> <标题>

## 身份信息

| 字段 | 内容 |
|---|---|
| 类型 | AR / DTS / CHANGE |
| ID |  |
| 所属组件 |  |
| 上游追溯 | IR / SR / 缺陷单 / 输入文档锚点 |
| 状态 | draft / 已确认 |

## 背景与目标

- 背景与问题来源:
- 目标（做完后什么变得不同）:

## 范围 / 非范围

- 范围:
- 非范围:

## 需求条目

### FR-001 <标题>
- Statement: <EARS 句式>
- Acceptance:
  - Given …；When …；Then …
  - Given …；When <异常条件>；Then <保护/反馈行为>
- Priority: Must / Should / Could
- Source: <上游锚点>
- Change Type: new / modify / remove
- Existing Behavior: <modify/remove 必填旧行为基线；new 写 N/A>

### NFR-001 <标题>
- 类别: <ISO 25010 维度>
- QAS:
  - Stimulus Source / Stimulus / Environment / Response / Response Measure（含阈值）
- Acceptance: <与 QAS 一致的 Given/When/Then>
- Source / Change Type / Existing Behavior: 同上

### CON-001 / ASM-001 / EXC-001 …
（约束 / 假设 / 显式排除项，按需）

## 接口候选契约（涉及对外接口时必填）

### IFC-001 <接口/服务语义名>
- Provider / Consumer:
- Operation（触发条件与操作语义）:
- Inputs（语义级字段、单位、范围）:
- Outputs / 可观察结果:
- Error Semantics（错误码、失败语义、幂等性）:
- Sync/Async 与时序预期:
- Compatibility（兼容/版本/弃用策略）:
- Covers: <FR/IFR IDs>

## Open Questions

| ID | 问题 | 类型 | 负责人 | 阻塞什么决策 |
|---|---|---|---|---|
| OQ-001 |  | blocking / non-blocking |  |  |

## 假设与依赖

| ID | 内容 | 失效影响 |
|---|---|---|
| ASM-001 |  |  |
```
