# openjiuwen.agent_teams.monitor.stream_logger

Java output:

- `com.openjiuwen.agent_teams.monitor.TeamStreamLogger`

Mirrors Python's `openjiuwen/agent_teams/monitor/stream_logger.py`.

The Java translation aggregates team-tagged stream chunks per
`(source_member, role)`, writes readable timestamped records, preserves full
LLM text, caps bulky tool payloads, skips untagged infrastructure chunks, and
keeps diagnostic logging best-effort so stream processing is not interrupted.
