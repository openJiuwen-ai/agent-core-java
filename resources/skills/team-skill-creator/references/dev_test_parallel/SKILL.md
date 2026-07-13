---
name: dev-test-parallel
version: "1.0"
kind: team-skill
roles:
  - id: developer
    purpose: "实现用户描述的功能代码"
    skills: []
    tools: [python3]
  - id: tester
    purpose: "为功能代码写测试用例"
    skills: []
    tools: [python3]
---

# 开发-测试并行团队

两人并行：developer 实现代码，tester 写测试，彼此不可见，最后 leader 整合两份输出。这是 **并行分解模式** 的最小示例，用作 team-skill-creator 生成新 team skill 时的参照样本。

## Workflow

团队遵循以下工作流程（详见 [workflow.md](workflow.md)）：

1. **Pre-flight** — 检查 python3 可用
2. **并行执行** — developer 写代码，tester 写测试，彼此不可见
3. **Final Report** — leader 整合两份输出

## 角色职责表

| 角色 | 职责 | 输出 |
| --- | --- | --- |
| developer | 实现功能代码 | .team/reports/T1_developer.md |
| tester | 写测试用例 | .team/reports/T2_tester.md |

## 文件清单

- `SKILL.md` — 本文件，团队元数据
- `roles/developer.md` — 开发者角色定义（含 inline persona）
- `roles/tester.md` — 测试工程师角色定义（含 inline persona）
- `workflow.md` — 完整执行剧本
- `bind.md` — 资源约束与失败处理
- `dependencies.yaml` — 外部依赖声明
