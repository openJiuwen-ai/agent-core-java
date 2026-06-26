# openjiuwen.agent_teams.interaction.router

对应 Python 文件：`openjiuwen/agent_teams/interaction/router.py`

`InteractionRouter` 是 `interact(str, ...)` 用户输入的顶层解析器。它把字符串中的通道、收件人和正文解析为 `InteractPayload`，后续 inbox 和 dispatch 逻辑只消费已经类型化的 payload，不再重复解析正文。

Java 对应类型：

- `com.openjiuwen.agent_teams.interaction.InteractionRouter`
- `com.openjiuwen.agent_teams.interaction.InteractionRouter.Mention`
- `com.openjiuwen.agent_teams.interaction.InteractionRouter.MemberExistsCheck`
- `com.openjiuwen.agent_teams.interaction.InteractionRouter.MessageManagerView`

主要行为：

- `parseMention("@target body")` 返回单个 mention 的目标和正文。
- `isReservedName(name)` 判断 `user`、`team_leader`、`human_agent` 等运行时保留成员名。
- `parseInteractStr(body)` 支持默认 god-view、`# ` 通道、`$name` human-agent 通道、单播、多播和广播。
- `resolveTargets(payloads, memberExists)` 使用实时 roster 匹配收件人；未知 mention 会折回为默认通道正文，保留原始 `@name body` 文本。
- `deliverDirect(...)` 在发送点对点消息前校验目标成员，返回稳定失败 token：`unknown_member:<target>` 或 `send_failed:<target>`。

实现说明：

- `#hashtag` 和 `$alice` 这类没有满足语法分隔的输入会保留为普通正文。
- `$alice@dev-1 body` 与 `$alice @dev-1 body` 一样，会把 `alice` 作为 sender、`dev-1` 作为 recipient。
- `@all` 和 `@*` 是广播目标；广播会覆盖同一输入中的其它收件人。
