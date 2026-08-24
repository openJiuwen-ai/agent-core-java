---
name: caveman-install
description: >
  Install and configure the caveman plugin for Claude Code on the current machine.
  Trigger when user says "install caveman", "setup caveman", "配置 caveman", "安装 caveman",
  or when caveman hooks/statusline are missing from settings.json.
  Also applies when user wants to verify, troubleshoot, or uninstall caveman.
  Not applicable to: using caveman mode (that's the built-in caveman skill),
  general Claude Code configuration unrelated to caveman.
---

# Caveman Plugin Installation Guide

Caveman is a Claude Code plugin that reduces output tokens ~65% by enforcing
terse, article-free communication. This skill covers install, verify,
configure, and uninstall on Windows.

## Quick Install

### Windows (PowerShell 5.1+)

```powershell
irm https://raw.githubusercontent.com/JuliusBrussee/caveman/main/install.ps1 | iex
```

If execution policy blocks it:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
irm https://raw.githubusercontent.com/JuliusBrussee/caveman/main/install.ps1 | iex
```

### WSL / Git Bash

```bash
curl -fsSL https://raw.githubusercontent.com/JuliusBrussee/caveman/main/install.sh | bash
```

### Claude Code Only (Manual)

```bash
claude plugin marketplace add JuliusBrussee/caveman
claude plugin install caveman@caveman
```

## What Gets Installed

1. **Plugin** — `~/.claude/plugins/marketplaces/caveman/`
2. **Hooks** — Two hooks registered in `~/.claude/settings.json`:
   - `SessionStart`: `node src/hooks/caveman-activate.js` — reads mode from
     `~/.claude/.caveman-active`, injects activation prompt
   - `UserPromptSubmit`: `node src/hooks/caveman-mode-tracker.js` — tracks
     mode changes, appends context to user prompts
3. **Hook files** — `~/.claude/hooks/caveman-activate.js`,
   `caveman-mode-tracker.js`, `caveman-stats.js`, `caveman-config.js`
4. **Statusline** — PowerShell script `caveman-statusline.ps1` for `[CAVEMAN]`
   badge in Claude Code status bar
5. **Flag file** — `~/.claude/.caveman-active` stores current mode (default: `full`)

## Verify Installation

Three checks:

1. **List detected agents:**
   ```bash
   node ~/.claude/plugins/marketplaces/caveman/bin/install.js --list
   ```

2. **Test in Claude Code:** Type `/caveman`. Response should be terse fragments.

3. **Check flag file:**
   ```bash
   cat ~/.claude/.caveman-active
   # expected: full
   ```

4. **Check settings.json hooks:** Look for `caveman-activate.js` and
   `caveman-mode-tracker.js` in `hooks` section.

## Statusline Setup

The statusline badge shows `[CAVEMAN]` (orange) in the Claude Code status bar.
After first `/caveman-stats` run it appends a savings counter like
`[CAVEMAN] ⛏ 12.4k`.

Add to `~/.claude/settings.json` (or project `.claude/settings.json`):

```json
{
  "statusLine": {
    "type": "command",
    "command": "powershell -ExecutionPolicy Bypass -File \"C:\\Users\\twc\\.claude\\plugins\\marketplaces\\caveman\\src\\hooks\\caveman-statusline.ps1\""
  }
}
```

> Adjust path if your Windows username differs. The `.ps1` script lives under
> the caveman plugin directory.

## Modes

| Command | Mode | Effect |
|---|---|---|
| `/caveman` | full (default) | Drop articles, fragments OK, short synonyms |
| `/caveman lite` | lite | Mild compression, keep most structure |
| `/caveman ultra` | ultra | Maximum compression, near-telegraphic |
| `/caveman wenyan` | wenyan-lite/full/ultra | Classical Chinese style |
| `stop caveman` / `normal mode` | off | Revert to normal output |

## Troubleshooting

### Hooks not firing

1. Check `claude` is on PATH: `which claude`
2. Check `~/.claude/settings.json` has `hooks` with `caveman-activate.js`
3. Check `~/.claude/.caveman-active` exists with content `full`
4. Restart Claude Code — SessionStart hook only fires on session start

### Windows-specific

- Use `install.ps1`, not `install.sh`, for hook wiring
- PowerShell 5.1 minimum: check `$PSVersionTable.PSVersion`
- If `irm | iex` blocks on execution policy: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`

### settings.json mangled

1. Check backup at `~/.claude/settings.json.bak`
2. Restore from backup or version control
3. Re-run installer with `--force`

### Managed environment (no hooks)

Use rule-file-only path:

```bash
node ~/.claude/plugins/marketplaces/caveman/bin/install.js --with-init --only cursor
```

Drops `.cursor/rules/caveman.mdc` etc. into repo. No hooks, no global config.

## Uninstall

```bash
npx -y github:JuliusBrussee/caveman -- --uninstall
```

Removes hooks, plugin, flag file. Does NOT remove:
- Skills installed via `npx skills add`
- Per-repo rule files from `--with-init` (delete manually)

## Useful Flags

| Flag | Purpose |
|---|---|
| `--dry-run` | Preview commands, write nothing |
| `--all` | Install everything detected |
| `--minimal` | Plugin only, no hooks |
| `--only <id>` | One agent only (e.g. `--only claude`) |
| `--force` | Re-run even if already installed |
| `--with-init` | Drop always-on rule files into current repo |
| `--with-mcp-shrink="<cmd>"` | Register caveman-shrink MCP proxy (opt-in) |
| `--list` | Print full agent matrix and exit |