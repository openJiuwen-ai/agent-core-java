# Agent Teams Database Baseline

The Java database baseline currently exposes:

- `DatabaseType`
- `DatabaseConfig`
- `TeamDatabase`
- in-memory `team/member/message/task` DAO entry points

This first layer is a persistence-facing compatibility surface for later upgrades to a real SQL-backed implementation.
