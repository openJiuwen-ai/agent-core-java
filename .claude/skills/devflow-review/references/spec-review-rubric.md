# 规格评审 Rubric

> 评审对象：组件根下 `features/<id>/spec.md`（或团队覆盖路径）。上游输入：用户原始请求/上游单据。作者侧标准见 `devflow-specify`。
>
> 核心怀疑：**两个不同的人读这份规格，会做出同一个东西吗？**

## 检查项

### 可测试性（不过 = critical）

- [ ] 每条 FR/IFR 的 Acceptance 是 Given/When/Then 且能直接落成一个失败测试
- [ ] 验收不是 Statement 的同义复述，有新增的判定口径
- [ ] 每条核心 NFR 有 QAS 五要素；Response Measure 有阈值或可判定准则；与 Acceptance 一致
- [ ] 无「足够快」「合理」「必要时」「体验良好」等不可判定词；无 TBD/占位值留在核心条目

### 行为与边界

- [ ] Statement 是 EARS 句式、有主体；关键失败路径/边界输入/异常状态有对应条目或验收
- [ ] 单条需求未打包多个独立行为（粒度信号 G1-G8，见 `devflow-specify` references）
- [ ] 需求间无冲突、无重复

### 变更风险显式（不过 = critical）

- [ ] 每条 FR/NFR/IFR/CON 有 Change Type；触及既有接口/错误码/状态机/阈值/运行时语义的条目没有被伪装成 `new`
- [ ] `modify`/`remove` 有 Existing Behavior 基线；Acceptance 覆盖保留行为、批准的破坏、删除后语义

### 边界纪律

- [ ] Statement/Acceptance 无实现细节走私（函数签名、数据结构、库选择、并发原语）
- [ ] 涉及对外接口时有语义级接口候选契约（provider/consumer/操作/输入输出/错误语义/兼容），且未越界写内部签名
- [ ] 范围/非范围闭合；"以后再做"在 EXC 或新工作项，不埋正文

### 闭环

- [ ] Open Questions 分类 blocking/non-blocking；blocking 已闭合或显式交回负责人（带 owner）
- [ ] 每条核心需求有 Source 锚点（不接受"口头要求"）；假设显式且失效影响可读
- [ ] traceability.md 已初始化：每条核心 FR/NFR/IFR/可测 CON 有一行，需求条目 / Change Type / 上游锚点列已填；ASM/EXC 只进备注或范围说明
- [ ] plan.md 骨架已建立：运行模式、门禁状态表、计划边界存在；任务拆解未在 R1 阶段伪造 design 之后的事实

## Verdict 指引

- 可测试性或变更风险显式有 critical → `需修改`（可定向修复）或 `重新设计`（范围/方向问题需人裁决）
- traceability.md 或 plan.md 骨架缺失 → `需修改`；没有磁盘工件的规格不能通过 R1
- 仅边界纪律/闭环类 important → `需修改`
- 缺业务事实或阈值的 finding 标「待人裁决」，不替业务拍板
