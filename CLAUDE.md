@AGENTS.md

# Claude Code Notes

- Keep shared project rules in `AGENTS.md` so all coding agents use the
  same architecture guidance.
- Use this file only for Claude-specific imports or workflow notes.
- Detailed rules by topic: see `.claude/rules/` (coding-standard,
  performance-tuning, agent-team-guide, workflow-guide).
- Skills for deep operational guides: see `.claude/skills/`
  (jvm-troubleshoot, refactor-guide, skill-creator, team-skill-creator,
  agent-team-guide, workflow-guide, performance-tuning).
- Permissions, env vars, and model defaults: see `.claude/settings.json`.
- Run `/memory` to manage auto memory.
- Run `/context` to see which files are loaded in the current session.
