# 提交者身份与凭据

## 两个独立的身份域

| 身份 | 文件 | 用途 | 禁止赋予的额外能力 |
| --- | --- | --- | --- |
| Issue 提交者 | `<workspace-root>/git-identity.inc`、`<workspace-root>/gitcode-config.json` | 本地 Git 作者信息；验证个人账号；创建/更新 Bug Issue | Push、PR、Merge、仓库管理 |
| Evolver Bot | `examples/gitcode_issue_evolver/config/evolver-secrets.local.json` 等服务配置 | 服务读取 Issue、推送修复分支、创建 PR、评论 Issue | 提交者 Skill 不得读取或复用 |

不要把两类 Token 写在同一个 JSON 文件中。提交脚本要求 Token 文件只有
`gitCodeToken` 一个键，并拒绝含 `webhookSecret` 等 Bot 字段的文件。

## 配置文件

在目标仓库之外准备两个文件。可复制：

- `assets/git-identity.inc.example`
- `assets/gitcode-config.json.example`

个人 Git 身份文件：

```ini
[user]
    name = YOUR_PERSONAL_NAME
    email = YOUR_PERSONAL_VERIFIED_EMAIL
```

个人 GitCode PAT 文件：

```json
{
  "gitCodeToken": "REPLACE_WITH_PERSONAL_ISSUE_SUBMITTER_TOKEN"
}
```

`user.name` 和 `user.email` 不是 API 凭据，也不会决定 Issue 作者。Issue 作者是
`gitCodeToken` 对应账号。不要自动执行 `git config --global`；只有用户明确要求把该
身份应用到某个本地仓库时，才使用仓库级配置。

## PAT 最小权限

在 GitCode 个人设置中创建专用于 Issue 提交的个人 PAT。只授予完成以下操作所需的
最小权限：

- 读取自己的账号资料；
- 读取目标仓库和标签；
- 创建 Issue；
- 更新本人 Issue 的标签。

不要授予代码 Push、分支管理、PR、Merge、Webhook 或仓库管理权限。若 GitCode 当前
权限 UI 不能只授予上述细粒度能力，向用户说明差异并让用户决定；不要静默扩大权限。

GitCode API 支持在 `Authorization: Bearer <PAT>` 请求头中认证。辅助脚本只从 JSON
文件读取 PAT 并在进程内构造该请求头，不把 PAT 放进 URL 或命令行。

官方资料：

- REST API 认证：https://docs.gitcode.com/en/docs/guide/
- 授权用户资料：https://docs.gitcode.com/docs/apis/get-api-v-5-user/
- PAT 安全管理：https://docs.gitcode.com/docs/help/home/user_center/security_management/

## 文件安全

- 把两个文件保存在仓库之外，不要提交。
- 在 POSIX 系统将 PAT 文件权限设置为仅当前用户可读写：

  ```bash
  chmod 600 "<workspace-root>/gitcode-config.json"
  ```

- `git-identity.inc` 不含秘密，但仍应只保存必要的姓名和邮箱。
- 在 Windows 上使用文件属性/ACL 限制 PAT 文件仅当前用户可读；辅助脚本无法可靠审计
  Windows ACL，会明确报告未检查。
- 不使用符号链接作为身份或 PAT 文件。

## 本地检查与远端验证

先执行离线检查：

```bash
python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py check-config \
  --identity-file "<workspace-root>/git-identity.inc" \
  --token-file "<workspace-root>/gitcode-config.json"
```

脚本只输出结构、权限和占位符检查结果，Token 始终显示为 `<redacted>`。

用户确认后再执行联网验证：

```bash
python3 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py verify \
  --repo "<owner/repo>" \
  --expected-login "<personal-login>" \
  --identity-file "<workspace-root>/git-identity.inc" \
  --token-file "<workspace-root>/gitcode-config.json"
```

远端验证必须满足：

- PAT 返回的 GitCode `login` 与用户确认的个人登录名一致；
- 目标仓库可访问且启用了 Issue；
- 仓库存在精确小写 `bug` 标签。

邮箱仅用于本地 Git 贡献归属。远端账号隐藏邮箱或与本地提交邮箱不同不阻塞 Issue
提交，但脚本会报告无法确认匹配；不要打印任一邮箱值。
