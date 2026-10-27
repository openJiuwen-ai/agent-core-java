---
name: devflow-clean-code
description: 在编写、修改或重构任何实现代码与测试代码时使用；TDD implementer 进入 GREEN/REFACTOR 和返回 clean_code_check 时必须作为通用基准加载。也在代码评审需要内在质量判据，或发现命名混乱、函数过长、嵌套过深、直接变异状态、类型/契约含混、错误处理散乱等代码异味时使用。语言无关的整洁代码标准；语言细则见对应的 <language>-coding-standards 技能，领域或框架规范按各自 description 叠加在本技能之上，不能替代本技能。
---

# DevFlow Clean Code（第三层）

## 总览

前两层保证代码做对的事、被证明做对；这一层保证代码本身写得好。判断标准只有一个视角：**下一个读者**（评审者、半年后的维护者、也包括下一轮迭代的 AI）。代码是写给人读的，顺便能在机器上运行。

人能持续低成本地审查 AI 的产出（human-on-the-loop 能成立），靠的就是这一层。所以"整洁"不是美学偏好，是协作姿态的前提。

本文是语言无关的共享地板，不是某个框架的详细剧本；TDD implementer 的 REFACTOR 证据与 `clean_code_check` 以本文为通用基准。语言细则在对应的 `<language>-coding-standards` 技能中，领域或框架规则由命中 description 的专项技能承载；它们叠加在本文之上而不是替代本文。示例用 C/C++ 写，原则通用；遇到特定语言、框架或工程领域时，再加载适用的扩展技能。

Robert C. Martin 的 Clean Code 在 DevFlow 中落成一个操作准则：**通过测试、消除重复、表达设计意图、保留最少必要实体**。这不是追求漂亮代码；这是让代码在正确、可读、可改、可测和可长期持有之间保持平衡。

## 适用边界

加载本技能的典型入口：

- 开始实现新模块、新函数或新测试。
- 在 GREEN 后做 REFACTOR，或行为不变地清理旧代码。
- 评审代码质量、可维护性、命名、错误处理、类型/契约表达、状态变异与性能风险。
- 设置 lint、format、type-check、静态分析规则前，需要先确定通用质量基准。
- 新成员或新 agent 接手代码，需要知道“什么样的代码可以长期维护”。

本技能负责通用质量基线：描述性命名、可读性、KISS、DRY、YAGNI、不可变默认、错误处理、类型/契约清晰、测试代码质量和代码异味识别。不要把它当作 React hooks、后端分层、API 资源建模、数据库访问、C/C++ 内存细则等专项规范的唯一来源；专项技能命中时必须叠加加载。

## 工作流

`devflow-clean-code` 是第三层叠加技能，不是独立阶段。它在四种模式下被消费：

| 模式 | 入口 | 动作 | 产出 |
|---|---|---|---|
| 实现期 REFACTOR | `devflow-tdd` GREEN 后 | 在全绿基础上检查本任务触碰范围，消除本任务引入的异味；每个小重构后跑测试 | plan.md 的 REFACTOR 证据行 + implementer `clean_code_check` |
| R3 返工 | `devflow-review` 代码评审打回 | 把 finding 映射到 `devflow-tdd` 返工队列；纯整洁问题走绿灯重构，测试弱/行为错回 RED/GREEN | Resolution 回填 + 复审 |
| 纯重构 | 用户明确要求行为不变清理 | 先建立全绿基线；源代码重构与测试代码重构分批进行，任一批次都保持行为不变 | 小批次提交 + 全量验证 |
| 评审消费 | R3 code review 或专项 clean-code review | 评审者按本文与 `references/quality-dimensions.md` 判定 finding 严重级；评审者不动手修 | `reviews/` finding + verdict |

REFACTOR 只在绿灯上进行。GREEN 帽只改变行为，REFACTOR 帽只改善表达；二者混在一个 diff 里，评审者无法判断风险。发现需要跨模块重构、接口契约变化、错误模型变化或新抽象方向变化时，停止当前编码层处理，回 `devflow-design`。

## 五维质量判据

完整速查见 `references/quality-dimensions.md`。在 `clean_code_check` 与 R3 评审中至少覆盖这五维：

| 维度 | 代码应呈现 | 阻塞信号 |
|---|---|---|
| 简洁 | 当前规格需要的最少结构；函数小、路径直、抽象有真实理由 | 为"以后可能"添加插件点、单实现接口、配置矩阵；100 行能解决的问题写成 1000 行 |
| 可靠 | 错误路径、资源释放、状态契约与主路径一样清楚 | 吞错误、漏检查可失败调用、失败后资源/状态不符合设计契约 |
| 可维护 | 名字揭示意图；职责按变化理由聚合；重复知识有单一表达；类型/接口表达契约 | 命名撒谎、上帝对象、霰弹式修改、未登记的大函数/深嵌套、`any`/裸结构绕过契约 |
| 可测试 | 逻辑可通过公共行为验证；测试快速、独立、可重复、自验证 | 为测试加生产后门、mock 内部纯逻辑、测试共享可变状态、不可控时间/随机 |
| 高性能 | 算法、资源与热路径成本匹配；性能取舍可解释 | 热路径 N+1、无界循环/读取、重复重计算、资源泄漏；无证据的微优化同样是噪音 |

高性能不是要求先优化，而是要求**不写明显低效且难以修复的结构**。没有热路径、复杂度或资源证据时，不为性能牺牲表达力；有证据时，性能问题按可靠性和可维护性同等处理。

## clean_code_check 返回契约

TDD implementer 返回 `DONE` 时必须列出已加载 `devflow-clean-code`，并给出简短但具体的 `clean_code_check`。`REFACTOR: N/A` 也要说明这些项已检查，而不是写 "looks clean"。

建议格式见 `references/clean-code-check-template.md`。最低覆盖：

```markdown
clean_code_check:
- 简洁: <无新增投机抽象 / 已删多余分支 / N/A 理由>
- 可靠: <可失败调用、错误路径、资源/状态处理结论>
- 可维护: <命名、函数大小/抽象层级、重复/死代码、数据/类型契约结论>
- 可测试: <测试代码清晰性、fixture/mock/可重复性结论；无测试变更则写 N/A 理由>
- 高性能: <热路径/资源/复杂度检查结论；无性能相关触碰则写 N/A 理由>
- 范围纪律: <仅触碰任务范围；路过问题登记去向>
```

父 controller 发现以下情况必须拒绝 `DONE` 并重派或要求补证据：

- `loaded_skills` 缺 `devflow-clean-code`，即使已加载语言规范。
- 无 REFACTOR 证据行，或 `REFACTOR: N/A` 没有具体检查结论。
- `clean_code_check` 只写总体印象，未覆盖错误路径、数据契约、范围纪律或测试代码。
- 发现结构性问题却在任务内硬改，没有回 `devflow-design` 或登记债务。

## 严重级与升级路径

R3 评审使用 `devflow-review` 的 `critical` / `important` / `minor`。Clean Code finding 的严重级按影响判定，不按修复工作量判定：

| 严重级 | 典型问题 | 去向 |
|---|---|---|
| `critical` | 吞错误；失败路径资源泄漏；状态契约被破坏；代码与 design 默默不一致；test-only 后门进入生产代码 | `devflow-tdd` 修复并复审；若契约/边界错，回 `devflow-design` |
| `important` | 命名撒谎；未登记的大函数/深嵌套掩盖多职责；设计未批准的新抽象；第三次重复同一知识 | 通常回 `devflow-tdd` 绿灯重构；跨模块变化回 `devflow-design` |
| `minor` | 局部命名可更好、注释措辞、非阻塞的局部整理 | 可修或由人接受不修，必须有 Resolution |

不要把 blocking 问题降级成风格偏好。命名撒谎、吞错误、test-only 后门、投机抽象和混合行为/重构 diff 都会让代码"能跑但不可审"，必须修复、登记债务或升级设计裁决。

## 命名

名字是最廉价的文档。规则：

| 对象 | 规则 | 反例 → 正例 |
|---|---|---|
| 函数 | 动词开头，说出做什么或返回什么 | `process()` → `discard_expired_sessions()` |
| 布尔 | is/has/can/should 开头，肯定语义 | `flag`, `not_ready` → `is_calibrated`, `has_pending_request` |
| 变量 | 名词，带足语义；作用域越大名字越长 | `d`, `tmp2` → `retry_delay_ms`, `merged_config` |
| 常量 | 说出含义而非值 | `TIMEOUT_3S` → `HANDSHAKE_TIMEOUT`（值变了名字不会说谎） |
| 带单位/语义的量 | 单位进名字或进类型 | `timeout` → `timeout_ms`；更好：`duration_ms_t timeout` |

判据：

- **名字撒谎是最高优先级的修复**：`get_config()` 里偷偷做了网络请求和缓存写入，比没有名字更糟。函数做的事超出名字 → 改名或拆函数。
- 同一概念全库一个词：别让 `fetch`/`load`/`read` 混用指同一件事。
- 名字里出现 `data`、`info`、`manager`、`util`、`process`、`handle` 而无修饰 → 几乎总能更具体。
- 需要注释解释名字含义 → 直接把解释写进名字。

命名自检用 PASS/FAIL 视角最有效：读到 `q`、`flag`、`x`、`process()`、`handle()` 时，先问“半年后的读者能不能不跳转就知道它是什么”；读到 `searchQuery`、`isAuthenticated`、`retry_delay_ms`、`calculate_similarity()` 时，读者能直接建立意图模型。

## 函数

- **一个函数一件事，一层抽象**。函数体内不应同时出现"调用其他函数表达意图"和"操作位与指针的细节"两个层次——细节下沉成命名函数。
- 经验阈值（超出即审视，不是机械红线）：函数 ≤ 50 行；参数 ≤ 4 个；嵌套 ≤ 3 层。
- 参数结伴出现（同一组 3-4 个参数在多个签名里重复）→ 提取结构体。
- 输出参数能用返回值就用返回值；布尔参数改变函数行为（`render(true)`）→ 拆成两个名字明确的函数。

**怎么拆长函数**：找出函数里的"段落"（通常已有空行或注释分隔），每个段落提取为一个以意图命名的函数。注释 `// validate input` + 十行代码 → `validate_input()`，注释删掉。

```c
/* ❌ 三个抽象层次挤在一起 */
int config_apply(const uint8_t *blob, size_t len) {
    if (blob == NULL || len < 8) return ERR_INVALID_ARG;
    uint32_t crc = 0xFFFFFFFF;                      /* 细节：CRC 计算 */
    for (size_t i = 0; i < len - 4; i++) { crc = crc32_step(crc, blob[i]); }
    if (crc != read_le32(blob + len - 4)) return ERR_CRC;
    ...30 行解析字段...
    ...20 行逐项生效与回滚...
}

/* ✅ 每层一个函数，主函数读起来就是流程本身 */
int config_apply(const uint8_t *blob, size_t len) {
    int rc = config_verify_integrity(blob, len);
    if (rc != OK) return rc;

    parsed_config_t parsed;
    rc = config_parse(blob, len, &parsed);
    if (rc != OK) return rc;

    return config_commit(&parsed);
}
```

## 控制流

让主路径（happy path）保持在最低缩进层级，异常分支尽早离开：

```c
/* ❌ 主逻辑埋在三层嵌套里 */
int session_send(session_t *s, const msg_t *m) {
    if (s != NULL) {
        if (s->state == SESSION_OPEN) {
            if (msg_is_valid(m)) {
                /* 真正的发送逻辑，缩进三层 */
            } else { return ERR_INVALID_ARG; }
        } else { return ERR_BAD_STATE; }
    } else { return ERR_INVALID_ARG; }
}

/* ✅ 卫语句：前置检查依次出场，主逻辑零缩进 */
int session_send(session_t *s, const msg_t *m) {
    if (s == NULL) return ERR_INVALID_ARG;
    if (s->state != SESSION_OPEN) return ERR_BAD_STATE;
    if (!msg_is_valid(m)) return ERR_INVALID_ARG;

    /* 真正的发送逻辑 */
}
```

- 条件复杂到需要思考 → 提取为命名的谓词函数或解释变量：`if (is_retryable(err) && attempts < MAX_RETRIES)`。
- 同一个标志变量控制后面多段逻辑的开关 → 通常应拆成两条直线路径。
- 魔法数字/字符串一律命名常量；`if (mode == 3)` 在评审中按 important 处理。

## 数据、类型与状态

Clean Code 不只看函数长短，也看数据是否让非法状态难以出现：

- **默认不可变**：能用不可变值、复制后更新、返回新集合，就不要原地改共享状态。必须变异时，让作用域足够小，并在命名或注释里说明原因（例如性能、外部 API 约束、资源生命周期）。
- **类型表达契约**：优先用具体类型、枚举/联合、值对象或带单位的类型表达业务边界；`any`、裸 `void *`、未区分单位的数字、随处传递的 map/dict，都会把设计契约推迟到运行时。
- **避免共享可变状态**：全局缓存、单例、静态变量、可变默认参数、跨测试共享 fixture 都要有明确生命周期和并发语义。
- **输入在边界校验**：外部输入（HTTP、CLI、文件、环境变量、消息队列、硬件返回值）进入系统边界时校验并转换成内部可信类型；不要让未验证的原始数据在业务逻辑里流动。
- **输出结构稳定**：公共返回值、事件、错误对象、API response 要有一致形状。调用方不应靠猜字段、解析字符串或检查内部细节来判断结果。

PASS/FAIL 判据：

```typescript
// PASS: 复制后更新，调用方能看出这是新状态
const updatedUser = { ...user, name: newName };
const nextItems = [...items, newItem];

// FAIL: 直接改共享对象，副作用边界不清
user.name = newName;
items.push(newItem);
```

不可变不是教条。局部 builder、性能热路径、底层缓冲区、资源句柄管理可以使用变异；要求是边界小、意图清楚、测试覆盖行为，且不会把副作用泄漏给不知情的调用方。

## 错误处理写法

设计层定了错误模型（`devflow-design`），编码层的纪律：

- **检查每个可失败调用**。忽略返回值必须显式且有理由：`(void)log_write(...);  /* 日志失败不影响主路径 */`
- **错误处理不喧宾夺主**：用卫语句/早返回让错误路径短促清晰，主路径保持直线。
- **失败时资源必须回收**。使用当前语言的惯用资源管理形态；例如无自动析构的语言常用集中清理出口，支持确定性析构的语言优先用 RAII / scope guard，具体写法以适用的语言规范技能为准。
- **不吞错误**：捕获/拦截了错误就必须处理（恢复、降级、上报）之一；空的 catch / 只打日志然后当没发生，按 critical 处理。
- 错误信息带上下文：报「config block 3 CRC mismatch (got 0x1A2B, want 0x3C4D)」而不是「verify failed」。
- 公共边界的错误要稳定：HTTP/API/CLI/SDK 返回一致的错误形状与状态码/错误码；内部异常、第三方错误和栈细节不要原样泄漏给用户侧契约。

错误处理本身也要单一职责。错误分支太长、try/catch 把主路径淹没、cleanup 分散在多个 return 中，都是应重构的信号。

## 注释

注释解释**为什么**，代码说明**是什么**。

```c
/* ❌ 复述代码 */
i++;  /* i 加一 */

/* ✅ 解释代码说不出的约束、取舍、外部事实 */
/* 先写数据后写索引：掉电时宁可丢这条记录，不可指向垃圾数据 */
record_write(slot, &data);
index_update(slot);
```

值得写注释的场景：非显然的不变量与前置条件、为绕过硬件/第三方缺陷的奇怪写法（带 issue 链接）、有意为之的"看似低效"、并发约束（"只能在任务上下文调用"）。

不写的：版本历史（git 的事）、注释掉的代码（删，git 里有）、段落标题式注释（提取函数代替）、TODO 不带负责人和去向（要么登记成债务，要么删）。

公共 API、导出函数、CLI 命令、SDK 方法、事件 schema 值得写“契约型文档”：参数含义、单位、默认值、返回值、错误/异常、并发或幂等保证、一个最小示例。内部小函数不需要仪式化文档，除非它表达了代码本身说不出的约束。

## 重复与死代码

- **三次法则**：第二次复制可以容忍（标记），第三次必须提取。但**只提取真正相同的知识**——两段代码长得像但服务不同业务规则、会因不同理由变化，提取反而制造耦合（错误的抽象比重复更贵）。
- 死代码零容忍：不可达分支、未使用变量/函数/参数、永远为真的条件、"以防万一"保留的旧实现——删。版本控制就是你的"以防万一"。
- 僵尸兼容层（`#if 0`、`legacy_` 前缀但无调用方、deprecated 但无下线计划）：登记并删除或给出下线计划。

Clean Code 的 DRY 是"同一知识只有一个权威表达"，不是"长得像就抽"。如果两段代码服务不同业务规则、会因不同理由变化，保留重复比制造错误抽象更整洁。

## SOLID 在编码时的落点

设计层负责判断边界，编码层负责让这些判断在 diff 里成立。看到 SOLID 问题时先找最小重构动作，不为原则本身制造新抽象：

| 原则 | 编码坏信号 | 小步动作 |
|---|---|---|
| SRP | 函数/文件同时处理解析、校验、持久化、通知等多个变化理由 | Extract Function / Move Function；必要时回 `devflow-design` 拆模块 |
| OCP | 新增一种类型要改多处条件分支 | 真实变化轴已确认时，用查表、策略或多态集中变化点 |
| LSP | 某个实现要求更强前置条件、返回不同错误语义或破坏失败状态保证 | 收紧契约或拆接口；不能替换就不要伪装成同一抽象 |
| ISP | 调用方 include 大头文件、传入无关配置、依赖内部字段 | 拆小 API / 参数结构；隐藏内部结构和私有宏 |
| DIP | 高层业务流程直接调用硬件、协议、存储或第三方库细节 | 在真实边界引入适配函数；单实现且无真实边界时保持具体依赖 |

## 测试代码也是代码

测试代码同样适用本文：名字说明行为，fixture 小而清楚，无死测试、无注释掉的旧断言、无大段复制粘贴。但测试的第一目标是**读者一眼看懂它验证什么行为**，所以不要为了 DRY 把 Given/When/Then 藏进三层 helper。

测试代码额外遵循 FIRST 思路：快速、独立、可重复、自验证、及时。具体断言强度、fixture、mock/fake 边界见 `../devflow-tdd/references/test-quality.md`。

测试结构可以用 AAA / Given-When-Then，但不要让模板压过可读性。好测试名直接说行为和条件：`returns empty list when no market matches query`；坏测试名只说实现或情绪：`works`、`test search`、`should handle stuff`。

红线：

- 为测试给生产代码加 public test-only 方法。
- mock 本模块内部纯逻辑，导致测试只验证调用形状而不验证行为结果。
- 测试依赖顺序、真实时间、随机数或共享可变全局状态。
- 测试名叫 `Test1` / `WorksCorrectly`，失败时无法告诉读者哪个行为坏了。

## 性能与资源

Clean Code 不鼓励无证据的微优化，但反对把明显低效和资源风险伪装成"先求可读"：

- 热路径中的 N+1 查询、无界循环、重复解析/分配、同步阻塞调用，应作为 important 或 critical 处理。
- 资源生命周期（内存、文件、锁、句柄、事务、订阅）必须能从代码结构看出来；释放路径不清就是可靠性问题。
- 性能优化要保留表达力：先用更合适的数据结构、缓存边界或批处理让意图更清楚；只有测量证明需要时才引入复杂技巧。
- 有意保留看似低效的写法时，注释写 why 和证据，例如数据规模上限、实时性预算或基准结果。
- 异步/并发代码要表达依赖关系：彼此独立的 I/O 并行执行；确有顺序依赖时，顺序要从变量名、注释或结构中看得出来。

```typescript
// PASS: 三个请求彼此独立，结构表达了并行
const [users, markets, stats] = await Promise.all([
  fetchUsers(),
  fetchMarkets(),
  fetchStats(),
]);

// FAIL: 无必要串行，延迟被人为叠加
const users = await fetchUsers();
const markets = await fetchMarkets();
const stats = await fetchStats();
```

## 范围与提交纪律

- **一个 diff 一个目的**：行为变更、重构、格式化分开提交。评审者无法在 500 行混合 diff 里分辨哪个变化是有意的。
- 只改任务要求改的。路过发现的问题：登记（issue / plan.md 债务登记节），不顺手修。
- **童子军规则的边界**：触碰范围内的小清理（改个错字命名、删几行死代码）值得做且随手做；超出触碰范围、或清理本身值得独立评审 → 登记。
- 不删不理解的代码、不"顺手统一"无关文件的风格。

## 常见异味与重构手法

完整目录（识别特征 + 操作步骤 + before/after）见 `references/refactoring-catalog.md`。速查：

| 异味 | 识别 | 手法 |
|---|---|---|
| 长函数 | 一屏放不下 / 多个段落注释 | Extract Function（按意图段落拆） |
| 深嵌套 | ≥3 层缩进 | 卫语句 / 提取谓词 / 反转条件 |
| 魔法数 | 裸字面量参与逻辑 | 命名常量（说含义不说值） |
| 数据泥团 | 参数组在多个签名重复 | 提取结构体 |
| 特性依恋 | 函数大量操作别的模块的数据 | Move Function 到数据所在地 |
| 霰弹式修改 | 一个行为变更要改 N 个文件 | 按变化理由重新聚合（回 `devflow-design`） |
| 开关参数 | 布尔参数改变函数行为 | 拆成两个函数 |
| 注释补丁 | 注释解释一段代码在干嘛 | 提取函数，注释变函数名 |
| 直接变异 | 共享对象/数组被原地改，调用方不知情 | 复制后更新 / 缩小变异边界 / 明确所有权 |
| 契约含混 | `any`、裸 map、字符串错误码散落 | 引入具体类型、schema、错误对象或枚举 |
| 边界未校验 | 外部输入穿透到业务逻辑 | 在入口校验并转换为内部可信类型 |
| 无必要串行 | 独立 I/O 被连续 await / 阻塞调用 | 并行、批处理或解释顺序依赖 |

重构永远在绿灯上进行、小步、每步跑测试（纪律见 `devflow-tdd` 的 REFACTOR 节）。

## 风险信号

- implementer 返回 `DONE` 但没有 `clean_code_check`，或只写 "looks clean"。
- `REFACTOR: N/A` 没有逐项说明命名、函数、控制流、错误路径、测试代码、性能/资源和范围纪律。
- 语言规范已加载，但 `devflow-clean-code` 未加载；语言技能不能替代第三层通用标准。
- 一个 diff 同时包含行为变更、大面积重命名、格式化和跨模块搬移。
- 为了测试加生产后门，或为了过测试弱化断言。
- 把热路径明显的 N+1、资源泄漏、无界读取说成"以后优化"。
- 用 `any`、裸 map/dict、字符串拼接错误对象绕过已经存在的类型或 schema。
- 外部输入没有边界校验，却在内部多处临时判断。
- 独立的异步 I/O 被无理由串行化。
- 评审者在 R3 上下文直接修代码，而不是产出 finding 让作者阶段闭环。
- 三轮返工仍出现同类 clean-code finding，说明方向问题，升级人或 `devflow-design`。

## 合理化反驳

| 话术 | 现实 |
|---|---|
| 「这个命名我自己懂」 | 代码是给下一个读者写的；"自己懂"的名字两周后你自己也不懂 |
| 「先跑起来，以后再清理」 | "以后"不存在。REFACTOR 是循环的一部分，不是可选附录 |
| 「多留个参数/分支，以后可能用」 | 死代码 + 假想需求。YAGNI；要用的时候再加，git 会帮你记住一切 |
| 「注释写多点总没错」 | 复述代码的注释会腐烂成谎言；该改的是代码的表达力 |
| 「顺手把旁边的也改了」 | 范围扩张让 diff 不可审。登记，另开任务 |
| 「这段复制一下改两行就行」 | 第三次复制时逻辑已经悄悄分叉。检查是不是同一个知识点 |
| 「性能以后再说」 | 没证据的微优化不做；但热路径 N+1、无界读取、资源泄漏不是优化，是缺陷 |
| 「测试 helper 抽出来更专业」 | 测试首先是行为说明书。helper 让读者看不懂 Given/When/Then，就是过度抽象 |
| 「先用 any/map 顶一下」 | 类型和 schema 是契约，不是装饰。绕过契约会把错误推迟到运行时和评审后 |
| 「直接改对象更方便」 | 共享变异会制造隐形调用方。除非所有权和生命周期清楚，否则复制后更新 |

## 自检清单（提交前）

- [ ] 简洁：没有投机抽象、单实现接口、未批准插件点；函数保持一件事一层抽象
- [ ] 可靠：每个可失败调用被处理或显式注明忽略理由；失败路径资源回收；错误信息带上下文
- [ ] 可维护：名字不撒谎；无未登记的 >50 行 / >4 参数 / >3 层嵌套；重复知识已处理或说明为何不抽
- [ ] 数据与契约：默认不可变；外部输入已在边界校验；类型/schema/错误对象表达公共契约；没有无理由 `any`/裸 map
- [ ] 可测试：测试名说明行为；测试快速、独立、可重复；无 test-only 生产后门；mock/fake 边界合理
- [ ] 高性能：热路径无明显 N+1、无界读取、重复重计算、无理由串行 I/O 或资源泄漏；性能取舍有证据
- [ ] 注释与死代码：注释解释 why；[SKILL.md](../automotive-development/SKILL.md)无注释掉的代码、未使用符号、复述型注释
- [ ] 范围纪律：diff 单一目的；行为变更、重构、格式化分开；路过问题有债务去向
- [ ] 适用语言的 `<language>-coding-standards` 与领域技能已叠加检查

## 支撑参考

| 文件 | 用途 |
|---|---|
| `references/refactoring-catalog.md` | 常见异味的识别特征、重构步骤与完整 before/after |
| `references/quality-dimensions.md` | 简洁、可靠、可维护、可测试、高性能五维判据与升级路径 |
| `references/clean-code-check-template.md` | implementer `clean_code_check` 返回模板与父 controller 验收规则 |
