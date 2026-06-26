# openjiuwen.agent_teams.rails.tool_approval_rail

Java 对应类型：`com.openjiuwen.agent_teams.rails.TeamToolApprovalRail`

Python 源文件：`openjiuwen/agent_teams/rails/tool_approval_rail.py`

## 职责

`TeamToolApprovalRail` 在 teammate 调用需要 leader 审批的工具时生成审批消息，并返回中断请求等待 leader 响应。恢复执行时，它解析 leader 的确认结果，批准或拒绝当前工具调用。

## 行为对应

- `priority = 90` 对应 `PRIORITY`
- `tool_names` 注册逻辑对应 `addTool`、`addTools`、`addPolicy` 和 `getTools`
- 首次调用时先检查 `auto_confirm_config`
- 未自动批准时向 leader 发送包含成员、工具名、调用 ID 和参数的审批消息
- 消息发送失败时拒绝工具调用
- 消息发送成功时返回 `InterruptRequest`
- 恢复调用时接受 `ConfirmPayload` 或 dict 风格 payload
- `approved=true` 返回批准结果
- `approved=false` 返回拒绝结果，缺省反馈为 `Tool call rejected by leader`

## 依赖边界

当前任务只翻译 rail 本身。Python 的 `TeamMessageManager` 在本任务范围外，因此 Java 使用 `ApprovalMessageManager` 窄接口表达当前 rail 唯一需要的能力：`sendMessage(content, toMemberName)`。后续翻译 message manager 时可通过适配器接入，不需要改变 rail 逻辑。
