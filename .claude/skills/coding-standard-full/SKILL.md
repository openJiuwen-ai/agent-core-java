---
name: coding-standard-full
description: 华为 CodeArts Check Java 编程规则集（144条规则，按分类拆分，按需加载）。在用户编写或修改 Java 代码、做 Code Review、要求检查代码规范时主动应用。覆盖命名、异常、日志、并发、格式化、安全等全部规则。涉及关键词：编程规范、代码规范、coding standard、code convention、规范检查、代码风格、Code Review、华为规则、CodeArts Check、G.FMT、G.NAM、G.ERR。不适用于：只问 Java 语法概念、非 Java 代码、调试运行错误、配置部署问题。
---

# 华为 Java 编程规则集 — 索引（按需加载）

共 144 条规则，按 20 个分类拆分到 `rules/` 子目录。本文件仅保留索引，需要查看某分类的详细规则（含示例代码）时，用 Read 工具加载对应文件。

## 严重级别

| 级别 | 标记 | 含义 |
|---|---|---|
| 0 致命 | 🔴 | 安全漏洞，必须修复 |
| 1 严重 | 🟠 | 高风险问题，优先修复 |
| 2 一般 | 🟡 | 规范问题，应当修复 |
| 3 建议 | 🟢 | 改善建议，推荐修复 |

## 快速自检（覆盖 80% 常见问题）

1. `G.NAM.03` 类/枚举/接口：PascalCase
2. `G.NAM.04` 方法名：camelCase
3. `G.NAM.05` 常量：ALL_UPPERCASE + 下划线
4. `G.ERR.01` 禁止空 catch 块
5. `G.ERR.02` 禁止直接捕获 Throwable/Exception/RuntimeException
6. `G.ERR.06` 🔴 重抛异常时必须传入原始异常作为 cause
7. `G.FMT.07` 避免空代码块；必须使用时加注释
8. `G.LOG.01` 使用 SLF4J 门面，不直接用 Log4j/Logback
9. `G.LOG.02` Logger 声明为 private static final
10. `G.OBJ.06` 覆写 equals 必须同时覆写 hashCode
11. `G.PRM.05` 禁止创建不必要的对象
12. `G.CON.07` 新线程必须指定线程名

## 场景 → 规则速查

| 场景 | 规则前缀 | 加载文件 |
|---|---|---|
| 服务/业务类 | G.OBJ, G.NAM, G.CMT | rules/G.OBJ.md, rules/G.NAM.md, rules/G.CMT.md |
| 异常处理 | G.ERR, G.CTL | rules/G.ERR.md, rules/G.CTL.md |
| 多线程代码 | G.CON, G.TYP, SEC_EXT | rules/G.CON.md, rules/G.TYP.md, rules/SEC_EXT.md |
| 日志 | G.LOG | rules/G.LOG.md |
| 集合/泛型 | G.COL | rules/G.COL.md |
| IO 操作 | G.PRM, G.FIO, G.TYP | rules/G.PRM.md, rules/G.FIO.md, rules/G.TYP.md |
| 方法签名 | G.MET, G.NAM, G.ERR | rules/G.MET.md, rules/G.NAM.md, rules/G.ERR.md |
| 常量/枚举 | G.NAM, G.DCL, G.FMT | rules/G.NAM.md, rules/G.DCL.md, rules/G.FMT.md |
| 覆写 equals | G.OBJ, G.EXP | rules/G.OBJ.md, rules/G.EXP.md |
| 可序列化类 | G.SER | rules/G.SER.md |
| 安全审查 | G.SEC, G.EDV, G.FIO, G.OTH, SEC_EXT | rules/G.SEC.md, rules/G.EDV.md, rules/G.FIO.md, rules/G.OTH.md, rules/SEC_EXT.md |

## 分类索引

| 分类 | 文件 | 规则数 |
|---|---|---|
| G.CMT 注释 | rules/G.CMT.md | 8 |
| G.COL 集合与泛型 | rules/G.COL.md | 3 |
| G.CON 并发 | rules/G.CON.md | 12 |
| G.CTL 控制流 | rules/G.CTL.md | 3 |
| G.DCL 声明 | rules/G.DCL.md | 5 |
| G.EDV XML安全 | rules/G.EDV.md | 3 |
| G.ERR 异常处理 | rules/G.ERR.md | 11 |
| G.EXP 表达式 | rules/G.EXP.md | 6 |
| G.FIO 文件IO | rules/G.FIO.md | 4 |
| G.FMT 格式化 | rules/G.FMT.md | 20 |
| G.LOG 日志 | rules/G.LOG.md | 4 |
| G.MET 方法 | rules/G.MET.md | 7 |
| G.NAM 命名 | rules/G.NAM.md | 8 |
| G.OBJ 类与对象 | rules/G.OBJ.md | 10 |
| G.OTH 其他 | rules/G.OTH.md | 5 |
| G.PRM 性能 | rules/G.PRM.md | 8 |
| G.SEC 安全 | rules/G.SEC.md | 3 |
| G.SER 序列化 | rules/G.SER.md | 5 |
| G.TYP 类型 | rules/G.TYP.md | 11 |
| SEC_EXT 扩展安全 | rules/SEC_EXT.md | 8 |

## 使用方式

1. **Code Review 时**：根据代码涉及的分类，用 Read 工具加载对应 `rules/*.md` 文件，获取详细规则+示例代码
2. **按规则 ID 查找**：规则 ID 格式 `G.<前缀>.<编号>`，根据前缀定位文件，如 `G.ERR.02` → 加载 `rules/G.ERR.md`
3. **按场景查找**：参考「场景 → 规则速查」表，确定需要加载的文件
