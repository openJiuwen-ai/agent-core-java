---
name: <team-skill-name>          # 团队 skill 名，kebab-case，与目录名一致
version: "1.0"                   # 版本号
kind: team-skill                 # 固定值，标识是 team skill
roles:                           # 角色总览，leader 在 ReAct 里读这里做编排
  - id: <role-1>                 # 角色 id，kebab-case，与 roles/<role-1>.md 文件名一致
    purpose: "<职责一句话>"       # 这个角色干什么
    skills: [<skill-name>]       # 该角色依赖的外部 skill，空数组表示纯推理
    tools: [<tool-name>]         # 该角色需要的工具，如 python3/curl/jq
  - id: <role-2>
    purpose: "<职责一句话>"
    skills: []
    tools: []
---

# <团队中文名>

<2-3 句话描述团队做什么、用什么协作模式、解决什么问题。>

## Workflow

团队遵循以下工作流程（详见 [workflow.md](workflow.md)）：

1. **<Step 名>** — <一句话>
2. **<Step 名>** — <一句话>
...

## 角色职责表

| 角色 | 职责 | 输出 |
| --- | --- | --- |
| <role-1> | <职责> | <输出文件或内容> |
| <role-2> | <职责> | <输出文件或内容> |

## 文件清单

- `SKILL.md` — 本文件，团队元数据
- `roles/*.md` — 各角色定义（含 inline persona）
- `workflow.md` — 完整执行剧本
- `bind.md` — 资源约束与失败处理
- `dependencies.yaml` — 外部依赖声明
