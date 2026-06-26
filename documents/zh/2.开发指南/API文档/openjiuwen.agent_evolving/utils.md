# openjiuwen.agent_evolving.utils

`com.openjiuwen.agent_evolving.TuneUtils` 对应 Python `openjiuwen.agent_evolving.utils`，提供自优化流程中的通用工具函数。

## SkillReferenceScore

`SkillReferenceScore` 记录技能引用命中的优先级计数：

- `skillToolHits`
- `skillsPathHits`
- `legacySkillMdHits`

`rankingKey()` 按 Python 的优先级返回三元计数，用于技能引用推断排序。

## 技能引用推断

`TuneUtils.inferSkillFromTexts(...)` 从以下来源推断最可能被引用的技能：

- `skill_tool` 调用参数中的 `skill_name`
- 文本里的 `skill_tool(skill_name=...)`
- `/skills/<skill>/...` 路径
- 旧式 `<skill>/SKILL.md` 路径

优先级与 Python 一致：显式 `skill_tool` 命中高于 skills 路径，高于旧式 `SKILL.md` 路径。

## Markdown frontmatter

`parseTopLevelFrontmatter(content)` 只解析 Markdown 顶层 scalar frontmatter 字段，跳过缩进行和列表项。

## TuneUtils

主要方法：

- `validateDigitalParameter(param, paramName, lower, upper)`：校验数值在闭区间内，越界时抛出工具链参数错误。
- `getInputStringFromCase(caseValue)`：将 Case 输入字典格式化为单行文本。
- `getOutputStringFromMessage(message)`：将消息内容转为字符串；Assistant tool calls 会序列化 name/arguments。
- `getContentStringFromTemplate(template)`：将 PromptTemplate 消息内容按换行拼接。
- `parseJsonFromLlmResponse(text)`：从 ```json 代码块中提取并解析 JSON。
- `parseListFromLlmResponse(text)`：从 ```list 代码块中提取并解析 list。
- `convertCasesToExamples(cases)`：将 Case/EvaluatedCase 序列化为 few-shot 示例文本。

这些方法保留 Python 对 JSON/list 解析失败返回 `None` 的语义；Java 中对应返回 `null`。
