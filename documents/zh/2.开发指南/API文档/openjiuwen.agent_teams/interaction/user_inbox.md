# openjiuwen.agent_teams.interaction.user_inbox

对应 Python 文件：`openjiuwen/agent_teams/interaction/user_inbox.py`

`UserInbox` 是外部调用者 `"user"` 进入团队运行时的入站通道。它只负责把已经解析好的 payload 落到 leader DeepAgent 或团队消息总线，不做成员名解析。

Java 对应类型：

- `com.openjiuwen.agent_teams.interaction.UserInbox`
- `com.openjiuwen.agent_teams.interaction.UserInbox.MessageManagerView`
- `com.openjiuwen.agent_teams.interaction.UserInbox.LeaderInput`

主要行为：

- `direct(target, body)` 以 `from_member_name="user"` 写点对点消息。
- `broadcast(body)` 以 `from_member_name="user"` 写团队广播。
- `deliverToLeader(deliverInput, body)` 把原始正文交给 leader DeepAgent。
- 总线拒收点对点消息时返回 `DeliverResult.failure("send_failed:<target>")`。
- 总线拒收广播时返回 `DeliverResult.failure("broadcast_failed")`。
- leader 投递异常会被转换为 `DeliverResult.failure("deliver_to_leader_failed:<message>")`。

实现说明：

- Java 使用小接口 `MessageManagerView` 和 `LeaderInput` 表达 Python 中 `TYPE_CHECKING` 的运行时边界，避免引入 TeamAgent 与 message-manager 的循环依赖。
- `UserInbox` 不校验目标成员是否存在；目标解析和未知 mention 折回由上游 router/runtime 负责。
