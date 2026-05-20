# AGENTS.md

## 项目目标

本仓库 `agent-core-java` 当前处于 Phase 1 迁移阶段，目标是按模块迁移并对齐 Python 版 `agent-core`。

当前阶段只做迁移和功能对齐，不做 Java 风格重构、结构优化、线程安全优化、长方法拆分或自定义替代设计。

## 基础执行要求

- 运行 shell 命令必须遵循 `/home/gallon/.codex/RTK.md`，统一使用 `rtk` 前缀，例如 `rtk mvn -q test`。
- 搜索文本优先用 `rg`，搜索文件优先用 `rg --files`。
- 修改文件必须使用 `apply_patch`。
- 仓库可能存在他人或历史未提交改动，不得回滚、覆盖或清理与当前任务无关的改动。
- 不使用破坏性 git 命令，例如 `git reset --hard`、`git checkout --`，除非用户明确要求。

## 必读迁移文档

开始任何模块迁移前必须先阅读：

- `documents/zh/3.迁移报告/README.md`
- `documents/zh/3.迁移报告/源码对齐审计总表.md`
- 当前模块对应的迁移报告，例如 `documents/zh/3.迁移报告/core.memory.graph.store.md`

这些文档是当前迁移计划、状态口径和模块差异的权威来源。若报告与源码现状不一致，以源码复核为准，并在收尾时刷新报告和总表。

## 迁移前强制检查

每次开始一个模块或明确切片之前，必须先形成并保留以下对照结果：

- Python 基线文件清单
- Java 对位文件清单
- Python 测试文件清单
- Java 测试文件清单
- 当前模块允许迁入的边界说明
- 当前模块是否存在废弃、过时或不再推荐路径

若以上任一项缺失、未确认或存在歧义，禁止直接写实现，必须先补齐源码对照或在报告中明确记为“暂不迁入”。

## 硬性迁移约束

- 直接按 Python 原模块结构、类型命名、职责边界和行为推进迁移。
- 不引入兼容层。
- 不引入本地替代实现。
- 不引入 fallback 方案来掩盖未迁移能力。
- 不使用双字段、旁路字段或旧新语义并存来兼容现有 Java 行为。
- 不把 Python 的多个 first-party 类型压平成单一 Java 类型。
- 不因为 Java 现有 adapter、测试或上层调用依赖旧语义，就在当前模块里保留旧语义；应补齐底层能力或直接改调用方。
- 如果底层依赖能力缺失，必须先补齐底层能力，再继续当前模块迁移。
- 三方库如果没有官方 Java SDK 或可确认 Maven artifact，不硬迁、不自造替代实现；只保留可验证调用边界，并在迁移报告的 `第三方阻断` 中记录。
- 对 Python 语言/运行时专属机制，不要求 Java 字节级复刻。例如 `pickle`、Python coroutine 调度、dataclass/pydantic 内省等，可以使用 Java/JVM 常规方案实现等价的公开行为和持久化能力。
- 使用 Java 常规方案替代 Python 专属机制时，不得伪装成同一底层协议。例如 Java 原生序列化不能标记为 Python `pickle` payload；报告必须明确说明替代边界、可观察行为一致性和不能上调为源码级协议对齐的部分。
- 命名、大小写、字段形态、序列化字面值和外部边界默认按目标语言与成熟生态方案处理，不为 Python 字面形态强行 1:1。例如 Java API 可用 camelCase，Python API 可用 snake_case；Java enum 可按 Java/Jackson 常规形态输出，Python enum 可按 Python 常规 value 输出。
- 迁移对齐的是对外功能、公开行为、状态语义、错误语义、生命周期语义和数据含义；不得仅因为大小写、驼峰/下划线、enum 名称展示、payload 字段名或协议承载格式不同就判定未对齐。
- 外部协议、工具名、资源 id、服务参数或跨语言持久化格式如已有 Java 生态成熟方案或当前 Java 公开接口风格，应优先采用该方案；只要能保证对外功能一致，不要求和 Python 字面值逐字一致。迁移报告需记录采用的 Java 方案、与 Python 的语义映射和可观察差异。
- 这类替代不是兼容层；前提是公开 API、状态语义、错误语义、生命周期语义和测试覆盖已按 Python 基线迁入，且没有用 fallback 或双语义掩盖缺失能力。
- 如果 Python 基线明确标记某接口、参数、工具或迁移路径已废弃、过时或不再推荐，则 Java 侧可以不迁入该路径；老版本兼容、迁移辅助工具、升级脚本不作为当前阶段必须项，但必须在迁移报告中记录跳过依据与不纳入范围的原因。
- 如果 Python 基线明确标记某接口、参数、工具或迁移路径已废弃、过时或不再推荐，则 Java 侧可以跳过该路径；老版本兼容、迁移辅助工具、升级脚本不作为当前阶段必须项，但必须在迁移报告中记录跳过依据与不纳入范围的原因。
- 任何“跳过”都必须写明 Python 版本、路径或注释依据，不允许口头默认。

## 状态口径

- `迁移中`：模块已开始迁移，但主链路、资源、测试或关键行为仍未收口。
- `已迁入`：代码、资源、测试、文档已落地，Java 侧实现可运行，但尚未完成逐项 Python 源码级对齐审计。
- `已对齐`：已完成 Python 源码级差异审计，报告中列出审阅过的 Python/Java 源码与测试路径，并确认无剩余 first-party 阻断差异，或差异已逐条记录并接受。

不能仅因为 focused/integration 测试通过就标记为 `已对齐`。缺少明确源码对位证据或测试对位证据时，结论只能是 `已迁入` 或 `迁移中`。

## 当前模块优先级

优先完成 `core/*`，`agent_evolving/*` 放到最后。当前 memory 迁移优先队列：

1. `core.memory.long_term_memory`
2. `core.memory.graph.store`
3. `core.memory.migration`
4. `core.memory.compaction`
5. `core.memory.team`
6. `core.memory.message-window`

随后继续处理总表中的 core retrieval 模块：

1. `core.retrieval.document-parser`
2. `core.retrieval.indexing-processor`
3. `core.retrieval.embedding`

再处理 `auto_harness.*`、`agent_teams.*`、`agent_evolving/agent_rl/` 等后续模块。

## 单模块工作流程

1. 读取模块迁移报告，明确 Python 基线范围、Java 范围、剩余差异和测试缺口。
2. 读取对应 Python 源码和测试，不凭报告或记忆直接改代码。
3. 读取对应 Java 源码和测试，确认当前仓库真实状态。
4. 按 Python first-party 语义补齐 Java 代码、资源、示例和测试。
5. 先做源码对位检查，再决定是否可以写实现。
6. 运行 focused tests；必要时补 integration tests。
7. 刷新模块迁移报告。
8. 更新 `documents/zh/3.迁移报告/源码对齐审计总表.md` 状态。
9. 在报告中记录仍未完成差异、第三方阻断、验证命令和后续遗留项。

## 防止自行设计的强制流程

- 每个明确切片开始实现前，必须先形成 Python 生产源码、Python 测试、Java 生产源码、Java 测试四方对照；缺任一项时不得写实现。
- 生产 API、构造参数、runtime 字段、adapter、fallback、hook、helper 对象只能来自 Python 生产源码行为或 Java 现有运行时必要承载，不能为了 Java 单测便利新增。
- 测试 seam 必须优先跟随 Python 测试方式。Python 用 patch factory/function，Java 应采用等价的测试专用 seam，例如 static mock、package-private helper 或不改变生产契约的 factory wrapper；不得把 fake agent、fake service、测试注入字段做成生产公开契约。
- 如果 Java 因语言或运行时限制必须采用不同测试承载方式，必须先在迁移报告中写明“仅测试承载差异”、Python 测试依据、Java 生产面未新增语义，再实现。
- 每次实现前必须用源码证据确认两个问题：Python 生产代码是否有该能力或扩展点；Python 测试是否采用该 mock/seam 策略。任一问题无法确认时，禁止继续设计方案，必须回到源码对照。
- 不得因为已有 Java 切片、既有测试便利、个人习惯或 Java 风格推断后续模块做法；每个切片都重新以 Python 源码和测试为准。
- 项目内迁移 skill 位于 `.codex/skills/jiuwen-java-parity-migration/SKILL.md`；执行迁移切片时应按该 skill 的流程做源码/测试对照和收尾。

## 模块完成定义

模块标记为 `已迁入` 至少需要满足：

- 对应代码已迁入或已补齐等价能力。
- 对应资源文件已迁入。
- 对应示例已补齐或明确不适用。
- 对应测试已补齐并可执行。
- 对应迁移报告已生成或刷新。
- `源码对齐审计总表.md` 状态已同步更新。
- 仍未完成差异已在模块迁移报告中记录。
- 已完成一轮完成度审计，且审计结论可由源码、测试、报告三者交叉验证。

模块标记为 `已对齐` 还需要满足：

- 已完成按 Python 源码逐项差异审计。
- 报告列出审阅过的 Python 源码路径、Python 测试路径、Java 源码路径、Java 测试路径。
- 默认值、参数、返回结构、异常语义、状态和生命周期语义无未记录差异。
- 不存在仅靠测试通过但缺少源码/测试证据支撑的结论。
- 不存在未写入报告的跳过项或隐含兼容项。

## 测试与日志判断

- 判断测试是否通过，以命令退出码和 Maven/Surefire 的 `failures` / `errors` 汇总为准。
- 测试中故意触发的业务 `ERROR` 日志不等于测试失败。
- 如果负向用例会打印 `ERROR` 日志，应在测试命名、断言或最终说明中明确这是预期路径。
- 回答用户测试状态时，必须区分“业务日志级别是 ERROR”和“测试框架 errors 计数”。

## 文档收尾要求

每完成一个模块或明确切片，必须同步更新：

- 当前模块迁移报告
- `documents/zh/3.迁移报告/源码对齐审计总表.md`

迁移报告至少包含：

- 迁移范围
- 对齐目标
- `Python基线范围`
- `Java范围`
- 代码/资源/示例/测试/文档结果
- `代码对齐`
- `测试对齐`
- 验证方式
- 与 Python 版仍存在的差异
- `第三方阻断`
- 后续遗留项
