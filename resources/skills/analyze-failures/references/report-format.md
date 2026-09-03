# 输入与输出合同

## 标准报告目录

标准输入是一个不可变的 PR 采集目录：

```text
<report-root>/pr-<PR号>-<yyyyMMddHHmmss>/
├── pr_info.txt
├── failed_tests_list.txt
└── failed_tests_detail.log
```

同一个 PR 有多次采集时，使用时间戳最新的目录。不要跨采集目录拼接三种文件，否则 PR
版本、失败清单和失败详情可能不一致。

## `pr_info.txt`

至少提取：

- PR/MR ID；
- 仓库坐标或规范 URL；
- 源分支和目标分支；
- PR head、合入 commit 或其他可验证 revision；
- 采集时间；
- 变更文件列表。

不要假设“合入 commit 的任意父提交”都是 PR 基线。对于 merge commit，通常使用第一
父提交表示目标分支合入前状态，并验证 diff 与报告中的变更文件一致；对于 squash、
rebase 或 fast-forward，依据 PR API 返回的 base/head SHA 或本地可验证历史确定边界。

## `failed_tests_list.txt`

每个条目应包含：

```text
[failure|error] <fully-qualified-test-class>#<method>
```

把 `<fqcn>#<method>` 作为稳定 nodeid。列表数量必须与详情日志中的用例段数量一致；不
一致时继续分析可识别条目，但在报告中记录采集不完整。

`failure` 通常表示断言失败，`error` 通常表示测试执行异常，但最终归因仍以详情证据为
准，不能只依赖该标签。

## `failed_tests_detail.log`

优先提取每个 nodeid 对应的：

- 断言消息；
- expected/actual 或 expected/but-was 差异；
- 异常类型、消息和 cause 链；
- 第一个相关项目代码帧；
- 测试方法代码帧；
- 超时、环境、容器、依赖或测试发现信息。

日志很大时，先搜索 nodeid、`Caused by`、`expected`、`but was`、`Tests run:`、
`Failures:`、`Errors:` 等锚点，再读取对应区段。保留首个根因异常和最相关项目帧，
不要用大量框架反射栈淹没报告。

## 等价结构化输入

可信 Controller 可以直接提供以下等价字段：

- PR/repository/base/head/merge revision；
- changed files 和 patch；
- 失败 nodeid 列表；
- 每个用例的断言、异常和堆栈证据；
- 当前测试源码与相关产品源码；
- 证据截断、脱敏和缺失说明。

该模式下不要再次联网获取已有证据，也不要把 Controller 提供的运行权限转交给分析
Agent。

## 降级规则

| 缺失项 | 仍可做什么 | 必须声明的限制 |
| --- | --- | --- |
| PR diff | 分析异常和测试契约 | 无法可靠判断是否由该 PR 引入 |
| 基线源码 | 使用 patch 的删除/新增行 | 无法检查 hunk 外行为变化 |
| 测试源码 | 使用断言与堆栈 | 无法确认测试意图和夹具语义 |
| 完整堆栈 | 使用断言差异 | 无法证明调用链 |
| 断言差异 | 使用异常和源码 | 值变化类结论置信度降低 |
| 全部关键证据 | 仅列缺口 | 归因为 `证据不足`，不提供猜测补丁 |

## `analysis.md` 结构

```markdown
# PR #<id> 测试失败根因分析

## 元信息与证据完整性
<仓库、revision、采集时间、输入文件、缺失或截断项>

## 结论摘要
| 失败用例 | 分类 | 置信度 | 根因摘要 | 共同根因组 |

## 失败用例分析

### <fqcn>#<method>

#### 失败原因分类
<开发问题 / 测试代码问题 / 环境问题 / 证据不足>

#### 具体失败原因
<2-4 句证据化结论>

#### 证据来源
- 测试输出：...
- 测试代码：<path:line>
- PR 变更：<path 与 revision/hunk>
- 相关实现：<path:line>

#### 因果链
<PR 行为变化 → 可达调用路径 → 运行时结果 → 断言或异常>

#### 修复建议
<最小、可执行但尚未执行的修复或取证步骤>

#### 具体修复代码
<有充分证据时给 before/after；否则写“证据不足，不提供猜测性补丁”>

## 跨用例观察
<共享根因、级联失败、重复模式和未受影响边界>

## 分析限制
<所有缺失、降级、无法确认和未执行事项>
```

置信度只使用 `高`、`中`、`低`：

- `高`：失败现象、测试契约、PR 行为变化和可达调用链均有直接证据；
- `中`：因果链基本闭合，但缺少基线、完整源码或一项关键运行证据；
- `低`：只有相关性或部分证据，结论需要进一步验证。

报告只生成 Markdown。不要读取或生成旧版 `analysis.html`。
