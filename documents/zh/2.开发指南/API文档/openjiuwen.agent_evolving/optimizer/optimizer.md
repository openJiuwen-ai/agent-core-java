# com.openjiuwen.agent_evolving.optimizer

`com.openjiuwen.agent_evolving.optimizer` 提供优化器包级导出契约，固定 Java 翻译中对应 Python `openjiuwen.agent_evolving.optimizer` 的公开符号列表。

## class OptimizerPackage

`OptimizerPackage` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/__init__.py`。它保留：

- `PYTHON_MODULE`: `openjiuwen/agent_evolving/optimizer/__init__.py`
- `EXPORTED_SYMBOLS`: 与 Python `__all__` 顺序一致的导出符号列表。

当前导出符号包括：

- `BaseOptimizer`
- `TextualParameter`
- `LLMCallOptimizerBase`
- `ToolOptimizerBase`
- `MemoryOptimizerBase`
- `InstructionOptimizer`
- `TeamSkillExperienceOptimizer`

## class ToolOptimizerBase

`ToolOptimizerBase` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/base.py`，是 tool-call 维度优化器的基础类。

核心行为：

- 固定 `domain` 为 `tool`，默认优化目标为 `tool_description`。
- 构造时读取 `max_turns`、`llm_api_key`、`config_eg`、`config_desc`、`path_save_dir` 和 `tool_name`，并按 Python 逻辑写入 `examples`、`descriptions` 和负样本 JSON 路径。
- `optimizeTool` 保留当前 Python `result_descs[-1][-1][0]` 与 `result_descs[-1][-1][-1]` 的索引语义：下一轮描述使用上一轮描述候选中的首项，最终 reviewer 使用最后一轮最后候选。
- Java 实现通过受保护 hook/运行时连接点调用后续 `customized_pipeline` 与 `ToolDescriptionReviewer` 依赖，方便同批后续 utils 任务补齐真实 pipeline 后直接接入。

## class BaseMethod

`BaseMethod` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/base_method.py`，封装 tool-call 优化方法的通用 LLM 应答生成逻辑。

核心行为：

- 提供 `parseJson`、`formatPromptLlama` 和 `printBold` 模块级兼容入口。
- 构造时保存 `config` 并按 Python truthy 规则解析 `verbose`。
- `produceAnswerFromApiCall` 生成与 Python 一致的自然语言回答 prompt，调用 RITS 响应接口，并验证输出必须是包含 `answer` 且不包含 `error` 的 JSON 对象。
- Java 实现通过受保护 hook/运行时连接点调用后续 `RitsUtils` 依赖，方便同批 `rits.py` 任务补齐真实实现后直接接入。

## class SimpleEval

`SimpleEval` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_eval.py`，用于评估工具函数调用准确率和输出有效性。

核心行为：

- 构造时保存 `api_wrapper`、`config` 和两项权重，并校验 `fn_call_weight + output_effectiveness_weight == 1.0`。
- `evaluate`/`call` 按 runs 循环评估示例，聚合 `score_avg`、`score_std`、`fn_call_accuracy`、`output_effectiveness` 和 `results`。
- 单例评估保留 Python 的错误结构：生成调用、函数调用准确率、API wrapper 执行、输出有效性评分、加权分数和 `errors` 列表。
- 参数打分保留函数名 30%、参数 70% 的权重，支持 JSON 字符串参数解析、数值容差和大小写不敏感字符串比较。
- 输出有效性优先通过 RITS 评分；RITS 不可用时使用 Python fallback 的字符串包含关系评分。

## class CustomizedPipeline

`CustomizedPipeline` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_pipline.py`，是 tool-call 优化 pipeline 的模块级入口。
核心行为：
- `customizedPipeline` 保留 Python `customized_pipeline` 的 `stage`、`tool`、`config` 和 `tool_callable` 输入结构；`fn_call_path` 配置路径分支按 Python 行为抛出暂不支持的异常。
- `tool_callable` 存在时使用 `SimpleApiWrapperFromCallable`，再构造 `SimpleEval`；缺少 callable 时抛出和 Python 一致的参数错误。
- `example` 阶段通过受保护的运行时工厂接入同批后续 `APICallToExampleMethod`，`description` 阶段接入 `ToolDescriptionMethod`，不扩大当前 Python 文件翻译范围。
- `BeamSearch` 参数保留 Python 配置键名，固定传入 `early_stop=true`、`check_valid=true`、`max_score=3.0` 和 `top_k`。
- 结果保存到 `config["save_dir"] / f"{tool["name"]}.json"`；已有结果时按 Python 顺序执行 `old + new` 合并，写回 JSON 后返回合并后的结果。

## class ToolDescriptionReviewer

`ToolDescriptionReviewer` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/customized_reviewer.py`，用于把工具描述转换为目标 JSON、清理冗余描述、交叉校验原始描述，并在英文占比较高时翻译为中文。
核心行为：
- 构造器保存 `eval_model_id`、`llm_api_key`，并初始化与 Python 一致的 `processors` 列表。
- `format` 构造当前 Python 生效的中文 JSON 转换 prompt，固定使用 `gpt-5.2`，并保留 `verify_output` 关键字边界。
- `cleanAndDeduplicate`、`crossCheck` 和 `translateToChinese` 分别对应 Python 的 `clean_and_deduplicate`、`cross_check` 与 `translate_to_chinese`。
- `_is_mostly_english` 先移除空白，再按 `[a-zA-Z]` 字符比例大于 `0.7` 判定是否主要为英文。
- `process` 保留 Python 顺序语义，其中 `cross_check` 使用原始 `data` 参数，而不是上一轮 `result`。
- RITS 调用通过受保护运行时 hook 接入同批后续 `rits.py` 对应实现，单元测试可替换该边界以避免真实网络调用。

## class ToolDescriptionMethod

`ToolDescriptionMethod` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/description_example_method.py`，是 `BeamSearch` 使用的工具描述优化方法。
核心行为：
- `step` 在第 0 轮使用原始工具描述并写入 `iteration=0`；后续轮次加载负例、调用 `generate` 生成新描述，再通过 `eval_fn` 评估并返回 `(description, score_avg, output)` 对应的 Java `BeamSearch.StepResult`。
- `critiqueDescriptions` 保留 Python 最终生效的同名方法：按 `score_avg >= 60.0` 拆分正例和负例，拼接成功/失败模式分析 prompt。
- `critiqueNegativeExamples` 和 `critiqueAllDescriptions` 生成针对失败样例、正负样例对比的分析 prompt，并通过 RITS hook 返回 `analysis`。
- `generateDescriptionFromDocumentation` 综合正例分析、负例分析和历史描述，生成保持 schema 结构的描述优化 prompt，并用 `FormatUtils.parseJson(..., "description")` 校验响应。
- `loadExamples` 从 `<examples_dir>/<function_name>.json` 倒序扫描节点历史，选择 `score >= 3.0` 且 instruction/answer 为字符串的样例。
- `getNegativeExamples` 优先读取 `neg_ex_input_path`，不存在时回退到 `examples_dir`，选择 `1.0 <= score < 3.0` 或缺少 `scores` 的样例。
- RITS 调用和 `eval_fn` 通过运行时 hook/反射连接，保持同批 `rits.py` 和评估函数边界可替换、可测试。

## class RitsUtils

`RitsUtils` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/rits.py`，为 tool-call 优化流程提供 RITS/OpenAI 兼容 LLM 调用入口。
核心行为：
- `getRitsResponse` 对应 Python `get_rits_response`：调用内部 `ritsResponse`，异常时返回包含 `error` 的动态结果。
- `ritsResponse` 构造 `ModelRequestConfig(model=model_id, temperature=1)` 和 `ModelClientConfig(client_provider="OpenAI", api_base="https://api.openai.com/v1", api_key=llm_api_key, verify_ssl=false)`。
- 调用消息使用 `role="developer"` 和输入 prompt，保留 Python `client.invoke(messages=[...])` 的消息形状。
- 当 `verifyFn` 存在时，对模型输出执行验证函数；否则返回原始文本。
- Java 实现保留两次重试语义，并提供可注入响应边界，便于单元测试避免真实网络调用。

## class APICallToExampleMethod

`APICallToExampleMethod` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/tool_call/utils/toolcall_example_method.py`，用于在 beam search 中生成和评分 API 调用示例。
核心行为：
- 构造器保存 API 调用函数、评估函数、API key 模板和非优化参数列表。
- `step` 先基于工具描述生成候选 `fn_call`，执行工具调用，再用 `critiqueApiCall` 过滤无效 API 调用；随后生成 instruction、answer、instruction critique 和 batch reflection。
- `generateApiCallFromDescription` 生成带 `name` 与 `arguments` 的 JSON API 调用，并校验函数名必须与目标工具一致。
- `critiqueApiCall` 返回 `analysis` 和 `err_code`，`err_code=-1` 时把当前输出写入历史并继续重试。
- `generateInstructionFromApiCall`、`critiqueInstruction` 和 `batchReflectionWithScores` 保留 Python 的 RITS prompt/校验边界。
- 当 `score_eval_weight > 0` 时，使用注入的 `eval_fn` 对最终示例再评分；最终分数按 Python 公式合成。
- `getOriginalDescription` 保留 ToolBench 描述前缀裁剪逻辑。

## class SkillCallOptimizerPackage

`SkillCallOptimizerPackage` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/skill_call/__init__.py`。它保留 skill-call 优化器子包的导出契约：

- `PYTHON_MODULE`: `openjiuwen/agent_evolving/optimizer/skill_call/__init__.py`
- `EXPORTED_SYMBOLS`: `SkillExperienceOptimizer`、`TeamSkillExperienceOptimizer`

## class SkillExperienceOptimizer

`SkillExperienceOptimizer` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/skill_call/experience_optimizer.py`，用于在线 skill experience 生成。
核心行为：
- 默认优化目标为 `experiences`，`backward` 按 `skill_experience_` operator id 选择同名 skill 信号，并要求 `online_contexts` 提供 `EvolutionContext`。
- `generateRecords` 构造 skill 内容摘要、信号 JSON、对话片段和已有经验摘要，再通过 `LlmResilience.invokeTextWithRetryAndPrompt` 调用 LLM。
- LLM 返回会先做容错 JSON 提取，再委托 `ExperienceDraftParser` 解析草稿；解析失败时按 Python 逻辑执行 JSON 修复、严格修复或截断重生成。
- 生成记录时保留 Python 的数量限制：文本经验最多 2 条，script 经验最多 1 条；`skip` 和空内容不会产生 `EvolutionRecord`。

## class TeamSkillExperienceOptimizer

`TeamSkillExperienceOptimizer` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py`，用于从 team trajectory、user intent 和团队协作信号中生成 team-skill experience。

核心行为：

- 默认优化目标为 `experiences`，`backward` 按 `skill_experience_` operator id 聚合同名 skill 信号，并要求 `online_contexts` 提供 `EvolutionContext`。
- `generateRecords` 汇总 team trajectory、signals JSON、当前 skill 内容和已有 desc/body/script 经验，再通过 `LlmResilience.invokeTextWithRetryAndPrompt` 生成候选草稿。
- 为保持 Python `signal.team` helper 语义，Java 实现内置 team trajectory summary、team signal skill content、trajectory issues 和 team model JSON 解析辅助函数。
- LLM 返回先按 Python 顺序解析 fenced/direct JSON、嵌入数组和嵌入对象；解析失败时使用 team-specific JSON fix prompt 或严格修复 prompt 重试。
- 生成记录时保留 Python 数量限制：文本经验最多 2 条，script 经验最多 1 条；`skip` 和空内容不会产生 `EvolutionRecord`。

## class InstructionOptimizer

`InstructionOptimizer` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/llm_call/instruction_optimizer.py`。它继承 `LLMCallOptimizerBase`，默认优化 `system_prompt` 和 `user_prompt` 两个目标。

核心行为：

- `selectSignals` 只消费失败驱动信号：`execution_failure`、`low_score`、`user_correction`、`collaboration_failure`，或 `context.score == 0` 的信号。
- `backward` 阶段先清理上一轮 `system_prompt_optimized` / `user_prompt_optimized` 缓存，再基于选中的失败信号生成文本梯度并预计算优化后的提示词。
- `step` 阶段只返回 `backward` 预计算出的提示词更新，不再调用 LLM。
- 优化后的提示词会校验并恢复原始 `{{placeholder}}` 占位符；如果恢复模型仍遗漏占位符，会把缺失占位符追加到结果末尾以保持 Python 行为。

Java 构造器使用具体 DTO 类型 `ModelRequestConfig` 与 `ModelClientConfig`；测试可注入 `Model` 调用边界以避免真实 LLM 网络依赖。

## class LlmResilience

`LlmResilience` 对应 Python 的 `openjiuwen/agent_evolving/optimizer/llm_resilience.py`，为演进流程中的 LLM 调用提供轻量重试与总预算控制。

核心行为：

- `LLMInvokePolicy` 定义单次调用超时、总预算、最大尝试次数、退避基数，以及是否重试空响应。
- `invokeTextWithRetry` 返回最终文本；`invokeTextWithRetryAndPrompt` 同时返回最终文本和实际使用的 prompt。
- 当一次调用出现 timeout-like 异常且提供 `retryPrompt` 时，后续尝试使用较短 prompt。
- 空响应或 `isResultUsable` 判定不可用时，会在最大尝试次数内重试。
- 超过总预算、调用失败、空响应和不可用响应会抛出框架统一错误，并保留 `reason`、`attempts`、`last_response` 和 `last_error` 细节。

`OptimizerPackage` 只表达包级导出契约；具体优化器类和行为由各自 Python 文件对应的 Java 类型实现。
