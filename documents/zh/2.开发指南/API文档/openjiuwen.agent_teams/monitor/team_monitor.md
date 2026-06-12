# openjiuwen.agent_teams.monitor.team_monitor

`TeamMonitor` 观察 leader `TeamAgent`，提供团队、成员、任务、消息查询，并把内部团队事件转换为监控事件流。

## Java 对应

- `com.openjiuwen.agent_teams.monitor.TeamMonitor`
- `TeamMonitor.createMonitor(TeamAgent teamAgent)`
- `TeamMonitor.createMonitor(TeamAgent teamAgent, boolean hideDm)`

## 行为

- `start()` 注册事件监听器，重复调用保持幂等。
- `stop()` 取消事件监听器，并向事件流写入结束信号，重复调用保持幂等。
- 查询方法会临时绑定构造时记录的 session id，避免动态表查询落到空 session。
- `hideDm=true` 时，按 Python 行为过滤直接消息查询和 `message` 类型实时事件，但保留广播消息。
- 未识别的内部事件类型静默丢弃。

## 查询方法

- `getTeamInfo()`
- `getMembers(String status)`
- `getMember(String memberName)`
- `getTasks(String status)`
- `getMessages(String toMemberName, String fromMemberName)`

所有查询返回 `CompletionStage`，以匹配 Python async API。
