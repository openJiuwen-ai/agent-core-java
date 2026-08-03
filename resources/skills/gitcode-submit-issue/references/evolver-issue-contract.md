# GitCode Issue Evolver 提单契约

## 触发契约

`gitcode_issue_evolver` 只接收同时满足以下条件的事件：

1. Webhook 仓库路径与服务配置的 `targetRepository` 完全一致。
2. Issue 处于 open/opened 状态。
3. Webhook action 是 `update`。
4. 标签变更明确表现为：变更前没有小写 `bug`，变更后新增小写 `bug`。

因此不要在创建 Issue 的请求中直接携带 `bug`。先创建内容完整且不带 `bug` 的 Issue，
再通过独立标签更新新增 `bug`。创建时已带 `bug` 通常不会形成 Evolver 需要的 update
事件。

## Evolver 实际读取的内容

Worker 获取 Issue 的标题、正文和最新评论，并把它们当作不可信问题数据。正文超过
12,000 个字符会被截断；每条评论最多向 Agent 提供 2,000 个字符。把解决问题所需的
关键信息放在不超过 12,000 字符的正文中，不依赖标签添加后的补充评论。

标准正文按以下顺序提供信息：

1. `问题摘要`：一句话描述可观察故障和触发条件。
2. `复现环境`：目标分支/版本、JDK、操作系统和相关模块。
3. `复现步骤`：最少两个确定性步骤，并给出最小输入。
4. `实际结果`：当前行为、异常类型或失败断言。
5. `预期结果`：修复后的外部可观察行为。
6. `证据`：脱敏后的最小日志、堆栈或测试失败；没有时明确写“暂无”。
7. `代码定位`：已确认存在的目标文件、相关符号和定位依据；不知道时允许 Agent 搜索。
8. `验收标准`：可判断的行为结果和回归要求。
9. `变更边界`：限定为最小 Java 主源码/测试源码修改。

## 路径和变更范围

Evolver 的 sparse Worktree 与写工具只允许：

- `src/main/java/**`
- `src/test/java/**`

Issue 标题、正文或评论中明确出现的目标文件会在 Agent 启动前校验。只有已确认存在的
仓库相对文件才能写进 Issue；任何缺失文件都会导致 `TARGET_PATH_NOT_FOUND`。如果期望
新增测试文件，不要猜测一个尚不存在的完整路径；描述要覆盖的类、行为和测试场景，让
Agent 根据仓库证据选择位置。

请求修改文档、Example、Skill、构建配置、CI、生成物或运行时文件会被拒绝。不要要求
安装软件、访问网络、执行 Shell、读取凭据、Push、创建 PR 或 Merge。

## 验证能力边界

服务的确定性 Gate 是：

```text
mvn -B -ntp -DskipTests test-compile
```

它编译主源码和测试源码，但不执行测试。Issue 可以要求补充回归测试源码，但不得预先
声称测试已运行或通过。最终 PR 仍需人工 Review 和 Merge。

## 提交前检查

- 标题以 `[BUG] ` 开头，单行、具体，不超过 200 字符。
- 正文不超过 12,000 字符，所有模板占位符已清除。
- 复现步骤、实际结果、预期结果和至少两个验收项完整。
- 日志、命令和样例数据均已脱敏。
- 显式目标文件真实存在且位于允许范围；否则使用模板规定的搜索占位语义。
- 目标仓库已有精确小写 `bug` 标签。
- 完整内容在加标签前已经写入 Issue。

## 对应实现

- 触发判断：`examples/gitcode_issue_evolver/src/main/java/examples/gitcode_issue_evolver/webhook/GitCodeIssueEvent.java`
- 标签差异解析：`examples/gitcode_issue_evolver/src/main/java/examples/gitcode_issue_evolver/webhook/GitCodeWebhookParser.java`
- Agent 输入和截断：`examples/gitcode_issue_evolver/src/main/java/examples/gitcode_issue_evolver/agent/IssueWorkerAgent.java`
- 显式路径预检：`examples/gitcode_issue_evolver/src/main/java/examples/gitcode_issue_evolver/worker/IssueTargetPathPreflight.java`
- sparse 范围策略：`examples/gitcode_issue_evolver/src/main/java/examples/gitcode_issue_evolver/worker/SparseCheckoutIssuePolicy.java`

## GitCode 官方接口

- Issue 创建与读取：
  https://docs.gitcode.com/v1-docs/docs/openapi/repos/issues/
- Issue 标签更新：
  https://docs.gitcode.com/v1-docs/docs/openapi/repos/labels/
- Issue 模板：
  https://docs.gitcode.com/docs/help/home/org_project/issue/issue_template/
