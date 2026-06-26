# openjiuwen.agent_teams.agent.BridgeOutboundWrap

`BridgeOutboundWrap` 对应 Python 模块 `openjiuwen/agent_teams/agent/bridge_outbound_wrap.py`，用于把团队侧 mailbox 消息格式化后转发给远端 bridge agent。

## 模式

- `PASSTHROUGH`：只添加最小发送者头部，正文原样转发。
- `REPHRASE`：添加发送者角色、persona、消息类型，并在存在任务上下文时追加任务提示。

## 行为

- `senderDisplayName` 为空时回退到内部 `sender`。
- 英文广播使用 `(broadcast)` 标记，中文广播使用 `（广播）` 标记。
- `REPHRASE` 中 `senderRole` 为空时角色值为 `unknown`。
- persona 使用 Python `repr` 风格的单引号字符串，保持和 Python 格式一致。
