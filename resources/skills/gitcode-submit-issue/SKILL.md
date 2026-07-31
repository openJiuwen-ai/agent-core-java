---
name: gitcode-submit-issue
description: >-
  Draft, validate, authenticate, and submit standardized GitCode bug Issues intended for
  examples/gitcode_issue_evolver. Use when a user wants to prepare an Evolver-ready bug report,
  configure the submitter's personal git-identity.inc and gitcode-config.json files, verify the
  personal GitCode account, preview or create the Issue, or explicitly add the bug label that
  triggers the Evolver. Keep submitter credentials separate from the Evolver bot credentials and
  never create or update a remote Issue without explicit user approval.
---

# GitCode 合规 Issue 提交

为 `examples/gitcode_issue_evolver` 准备可执行、可审计的 Bug Issue。始终把 Issue
提交者和 Evolver Bot 当作两个独立身份。

## 信任边界

- 仅使用提交者自己的 `git-identity.inc` 和 `gitcode-config.json`。
- 不读取、复制或修改 `evolver-secrets.local.json`、Webhook Secret、模型配置或 Bot Token。
- 不把 Token 放入提示词、Issue、命令行、环境变量、日志或仓库文件。
- 把 `git-identity.inc` 视为本地 Git 作者信息，不把它误当成 GitCode API 身份。
  Issue 作者由 PAT 对应的 GitCode 账号决定。
- 只为提交 Issue 申请读取账号/仓库/标签及创建、更新本人 Issue 所需的最小权限。
  不申请代码推送、PR、Merge 或仓库管理权限。

需要配置或诊断身份文件时，读取 `references/credentials.md`。需要起草、校验或触发
Evolver 时，先读取 `references/evolver-issue-contract.md`。

## 标准流程

1. 确认目标仓库与 Evolver 配置的 `targetRepository` 完全一致，并确认仓库已有小写
   `bug` 标签。不要创建或修改仓库标签。
2. 收集可复现证据并复制 `assets/evolver-bug-issue.md` 到仓库外的私有草稿路径。
   填完所有占位符。只列出已确认存在的仓库相对文件；无法确认时使用模板规定的
   “由 Agent 根据仓库证据检索确定”，不要猜路径。
3. 运行本地校验，不联网也不修改远端：

   ```bash
   python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py validate \
     --draft "<private-draft-path>" \
     --repository-root "<repository-root>"
   ```

4. 检查两个提交者配置文件。若文件缺失，按 `references/credentials.md` 和
   `assets/*.example` 指导用户在仓库外创建；要求用户在本地填写 PAT，不要让用户把
   PAT 粘贴到对话中。

   ```bash
   python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py check-config \
     --identity-file "<workspace-root>/git-identity.inc" \
     --token-file "<workspace-root>/gitcode-config.json"
   ```

5. 在用户确认本地配置已就绪后，联网验证 PAT 对应账号、目标仓库和 `bug` 标签。
   `--expected-login` 必须是用户确认的个人 GitCode 登录名，不能是 Evolver Bot：

   ```bash
   python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py verify \
     --repo "<owner/repo>" \
     --expected-login "<personal-login>" \
     --identity-file "<workspace-root>/git-identity.inc" \
     --token-file "<workspace-root>/gitcode-config.json"
   ```

6. 向用户展示目标仓库、已验证的登录名、完整标题、完整正文、显式目标文件和校验摘要。
   明确说明提交会执行两次远端写入：先创建不带 `bug` 的 Issue，再新增 `bug` 标签以
   触发 Evolver。等待用户明确批准；模糊同意不算批准。
7. 获得批准后才运行提交命令：

   ```bash
   python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py submit \
     --repo "<owner/repo>" \
     --expected-login "<personal-login>" \
     --draft "<private-draft-path>" \
     --repository-root "<repository-root>" \
     --identity-file "<workspace-root>/git-identity.inc" \
     --token-file "<workspace-root>/gitcode-config.json" \
     --confirm-submit
   ```

8. 报告脚本返回的 Issue URL、Issue 编号和 `EVOLVER_TRIGGER` 状态。不要声称 Evolver
   已修复问题、测试已通过或 PR 已创建；这些都是后续异步结果。

## 部分失败

- 若 Issue 已创建但 `bug` 标签未确认新增，不要重跑 `submit`，避免重复 Issue。
- 先打开脚本返回的 Issue URL 核对内容与当前标签。只有在 Issue 仍为 open、内容未变且
  当前没有 `bug` 标签时，向用户说明恢复动作并再次取得明确批准，然后运行：

  ```bash
  python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py trigger \
    --repo "<owner/repo>" \
    --issue-number "<number>" \
    --expected-login "<personal-login>" \
    --repository-root "<repository-root>" \
    --identity-file "<workspace-root>/git-identity.inc" \
    --token-file "<workspace-root>/gitcode-config.json" \
    --confirm-trigger
  ```

- 若 Issue 已有 `bug` 标签，不自动移除并重加。先检查 Evolver Job/Webhook 状态，避免
  生成重复任务。
- 若创建请求结果不确定，先在 GitCode 查找相同标题和正文；不要盲目重试写请求。

## 硬性停止条件

在以下任一情况停止，不创建 Issue，也不加标签：

- 目标仓库、个人登录名或用户批准不明确。
- 标题/正文仍有占位符、包含凭据、缺少复现步骤、实际/预期结果或验收标准。
- Issue 请求修改 Java 主/测试源码之外的内容，要求新增依赖、降低安全校验或访问外部服务。
- 明确写出的目标文件不存在、不是仓库相对路径，或不在允许的 Java 源码范围内。
- PAT 对应账号不是 `--expected-login`，PAT 文件权限不安全，或 `bug` 标签不存在。
- 提交者配置与 Evolver Bot secrets 混在同一个文件中。
