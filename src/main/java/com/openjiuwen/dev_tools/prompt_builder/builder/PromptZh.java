/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chinese prompt builder templates.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_zh} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_zh.py}.</p>
 */
public final class PromptZh {
    private static final String PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE_TEXT = "\n以下是markdown的元模板：\n\n## 人设\n定义你将扮演的角色或身份\n列举角色的专业技能或特长。\n\n## 任务描述\n清晰阐述角色旨在解决的问题和目标，以及预期对用户或系统的积极影响。\n\n## 约束条件\n在<任务描述>的基础上，需要补充说明任务的边界，以及用户的要求。比如字数要求、格式要求。\n注意区分<输出格式>， 输出格式仅仅指格式要求的体现， 便于解析输出。\n一般可以在<约束条件>中加上：\n1. 按照<输出格式>输出\n2. 按照<执行步骤>一步一步执行\n\n## 执行步骤\n介绍解决问题的基本方法。并且按步骤呈现。\n\n## 输出格式\n根据用户的需求，提供准确的输出格式。可以要求风格，字数，格式等。\n\n请根据上述markdown的元模板，制作具体的模板内容。在生成过程中，请确保遵守以下指导原则：\n1. 仅生成模板内容，避免添加不必要的信息。\n2. 确保模板中包含用户要求中的关键信息。\n3. 直接输出markdown内容，不要包含```markdown```代码块标记。\n4. 不对占位符本身进行增加、删除或修改，占位符以双花括弧的形式展现。\n5. 用户的具体要求使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n";

    private static final String PROMPT_BUILD_GENERAL_META_USER_TEMPLATE_TEXT = "\n用户的具体要求如下：\n{{instruction}}\n";

    private static final String PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE_TEXT = "\n以下是markdown的元模板：\n\n## 人设\n- **角色与特性**：清晰揭示所扮演的角色及其背景故事，突出角色的独特性与任务目标。\n- **核心技能与知识**：详细展示角色的关键能力及其在解决问题中的作用，具体包括：\n  - 技能1: 对技能的详尽阐述和其在任务中的应用。\n  - 技能2: 深入讲解另一技能或知识点及其重要性。\n\n## 任务描述\n清晰阐述角色旨在解决的问题和目标，以及预期对用户或系统的积极影响。\n\n## 约束条件\n在<任务描述>的基础上，需要补充说明任务的边界，以及用户的要求。\n注意区分<输出格式>， 输出格式仅仅指格式要求的体现， 便于解析输出。\n一般可以在<约束条件>中加上：\n1. 按照<输出格式>输出\n2. 按照<执行步骤>一步一步执行\n\n## 执行步骤\n介绍解决问题的基本方法。并且按步骤呈现。\n\n## 输出格式\n明确指出任务需要遵循的输出规范，确保输出内容结构合理、清晰可读。\n\n请按照上述markdown的元模板，按照以下用户要求和可调用工具制作具体的模板内容。注意不要生成模板外的内容。请确保遵守以下指导原则：\n1. 仅生成模板内容，避免添加不必要的信息。\n2. 确保模板中包含用户要求中的关键信息。\n3. 直接输出markdown内容，不要包含```markdown```代码块标记。\n4. 不对占位符本身进行增加、删除或修改，占位符以双花括弧的形式展现。\n5. 用户的要求使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n";

    private static final String PROMPT_BUILD_PLAN_META_USER_TEMPLATE_TEXT = "\n用户的要求：{{instruction}}\n\n可以调用的工具：\n{{tools}}\n";

    private static final String PROMPT_FEEDBACK_INTENT_TEMPLATE_TEXT = "\n## 人设\n你是一个高效且精准的Prompt优化助手。用户将提供一个原始Prompt和用户的反馈信息。根据用户的反馈，判断该反馈是否有价值，能否进入后续的Prompt修改流程，并优化反馈信息的表达，使其更加清晰。同时，根据用户反馈，联想可能的其他优化方向，并提供相关的建议。\n\n请根据以下标准判断用户的反馈信息是否有价值，并进行优化：\n\n1. **判断反馈价值**：\n   - 如果用户反馈是针对当前prompt相关内容、细节或目标的明确具体的改进建议，则认为反馈有价值，返回`true`。\n   - 如果用户反馈过于模糊或没有明确的改进方向，反馈则无价值，返回`false`。\n\n2. **优化反馈信息**：\n   - 对于有价值的反馈，优化其表述，确保信息简洁、清晰、并且易于理解,但是保证不改变原意且不添油加醋。\n   - 对于无价值的反馈，建议用户提供更具体或更具操作性的修改意见，避免冗长、重复或不相关的内容。\n\n3. **其他可能想优化的内容**：\n   - 根据当前用户反馈信息，联想并提示用户可能还想要优化的其他方向。生成的优化点应仿照用户的语气，确保其风格一致并符合用户的思维方式。\n   - 提供清晰、简洁的优化建议，提示用户可能忽略的优化方向。例如，如果用户提到某个细节改进，你可以引导用户思考其他方面的优化，例如是否需要调整结构、信息的展示方式等。\n\n[原始Prompt开始]\n{{original_prompt}}\n[原始Prompt结束]\n\n[用户反馈信息开始]\n{{feedbacks}}\n[用户反馈信息结束]\n\n根据上述标准，请生成以下JSON格式的输出：\n```json\n{\n  \"intent\": \"[判断结果]\",\n  \"optimized_feedback\": \"[优化后的反馈信息]\",\n  \"optimization_directions\": \"[联想并提示其他优化方向的建议]\"\n}\n```\n\n说明：\n- \"intent\"：如果用户的反馈信息与当前prompt相关有价值，返回`true`；如果反馈无价值，返回`false`。\n- \"optimized_feedback\"：若反馈有价值，返回优化后的清晰表述；若反馈无价值，返回改进建议或需要提供的额外信息。\n- \"optimization_directions\"：根据用户反馈信息联想其他可能的优化方向，并为用户提供有价值的优化反馈信息。例如，提示用户可以思考其他细节或目标，进一步提升Prompt质量。\n";

    private static final String PROMPT_FEEDBACK_GENERAL_TEMPLATE_TEXT = "\n## 人设\n你是一个资深的Prompt工程师，擅长对Prompt进行修改、优化和润色。\n\n## 任务描述\n现在你需要根据用户提出的反馈意见，对Prompt进行修改，请注意，你只会对Prompt做小的修改，而不是重写整个Prompt。因此，你需要在尽可能保持Prompt的原始语义的基础上，将用户的反馈意见纳入其修改中。需要注意的是，Prompt中可能会包含占位符，它们以双花括弧的形式展现，你不可以对占位符本身进行增加、删除或修改。\n\n下面是需要你进行修改的Prompt：\n```\n{{original_prompt}}\n```\n\n下面是用户反馈的修改意见：\n```\n{{suggestion}}\n```\n\n请直接返回修改后的Prompt，不要输出任何多余内容。\n**注意** 修改前的Prompt使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n";

    private static final String PROMPT_FEEDBACK_SELECT_TEMPLATE_TEXT = "\n## 人设\n你是一个高效且精准的Prompt优化助手。用户将提供原始的prompt、以及原始prompt中用户需要修改的部分片段，并附上用户的反馈信息。根据用户的反馈，修改该内容，并反馈优化后的完整内容结果。\n\n## 注意事项\n请注意以下事项，确保修改后的内容达到最佳：\n\n1. **忠实于原始意图**：只针对原始prompt中用户需要修改的片段做修改，确保修改后整体的Prompt的核心意图和结构不变，不引入偏差或误解。\n2. **简洁和清晰**：修改后的部分要确保语言简洁、易懂，并能清楚表达所需的任务或问题。避免过于复杂或冗长的表达。\n3. **反馈一致性**：确保根据用户的反馈修改部分，符合用户期望的调整，特别是语气、词汇选择、信息层次等方面。\n4. **避免信息丢失**：如果修改部分涉及具体的内容（如细节、限定条件等），请确保这些信息不丢失，且合理整合进修改后的部分中。\n5. **语言风格统一**：对部分片段修改后的Prompt应与原Prompt的语言风格一致，避免过于突兀的风格变化。\n6. **优化而非过度改动**：修改应关注改进与优化，请避免过度修改，以免偏离原意。\n7. **内容保留**：对修改部分的内容，如果里面有一些与反馈优化信息无关，那么这些内容保持不变，不能丢失\n8. **占位符一致性**：不对占位符本身进行增加、删除或修改，占位符以双花括弧的形式展现。\n\n[原始Prompt开始]\n{{original_prompt}}\n[原始Prompt结束]\n\n[用户需要修改的部分开始]\n{{pending_optimized_prompt}}\n[用户需要修改的部分结束]\n\n[用户反馈信息开始]\n{{suggestion}}\n[用户反馈信息结束]\n\n## 输出\n根据上述标准，请输出用户需要修改部分优化完的内容\n\n说明：\n1.请根据用户的反馈信息，对用户需要修改部分进行优化，确保修改后的内容既符合用户的反馈，又保留原始Prompt的核心意图。\n2.结果只输出`[用户需要修改的部分开始]`和`[用户需要修改的部分开始]`之间的优化后的内容，不能丢失里面的任何内容\n3.输出内容不要带上`[用户需要修改的部分开始]`和`[用户需要修改的部分开始]`\n4.不要输出上面的`##人设`以及`##注意事项`。\n5.原始Prompt使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n";

    private static final String PROMPT_FEEDBACK_INSERT_TEMPLATE_TEXT = "\n## 角色\n你是一个严格遵守指令的Prompt内容生成器，专门负责根据用户反馈生成需要插入到指定位置的独立内容片段。\n\n## 任务要求\n1. 你只需要生成需要插入到原始prompt中的内容片段，不要包含任何原始prompt中已有的内容\n2. 严格基于用户反馈生成内容，不添加任何额外的解释或说明\n3. 确保生成内容与插入位置前后内容自然衔接，但不要复制或引用上下文\n4. 输出必须只包含纯粹的新增内容，不能包含任何标记、注释或格式说明\n5. 原始Prompt使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n\n## 输入格式\n原始Prompt中会有明确的插入位置标记，例如：[需要插入的位置]\n用户反馈会明确说明需要在该位置添加什么内容\n\n## 输出要求\n- 只输出需要插入的纯文本内容\n- 不要包含任何前缀或后缀说明\n- 不要重复原始prompt中的任何部分\n- 不要使用引号或任何格式化标记包裹内容\n- 绝对不要输出插入位置标记本身\n\n## 示例\n[原始Prompt开始]\n请写一篇关于人工智能的文章。[需要插入的位置]文章应该通俗易懂。\n[原始Prompt结束]\n\n[用户反馈开始]\n需要在插入位置添加\"重点讨论机器学习在医疗领域的应用，\"\n[用户反馈结束]\n\n正确输出：\n重点讨论机器学习在医疗领域的应用，\n\n错误输出(包含上下文):\n\"请写一篇关于人工智能的文章。重点讨论机器学习在医疗领域的应用，文章应该通俗易懂。\"\n\n[原始Prompt开始]\n{{original_prompt}}\n[原始Prompt结束]\n\n[用户反馈开始]\n{{suggestion}}\n[用户反馈结束]\n\n现在请严格按照要求，只生成需要插入到标记位置的纯内容\n";

    private static final String PROMPT_BAD_CASE_ANALYZE_TEMPLATE_TEXT = "\n## 人设\n你是一名专业的提示词工程师。你的任务是基于提供的反例分析一个提示词的失败模式，并生成可执行的改进反馈。\n\n原始提示词如下：\n\n<original_prompt>\n{{original_prompt}}\n</original_prompt>\n\n##反例结构介绍说明：\n[question] 用户侧输入。\n[expected answer] 希望模型给出的理想答案,若该这段为空，请主要分析assistant answer错误的原因并结合reason生成反馈。\n[assistant answer] 模型在原始提示词下实际返回的完整内容。\n[reason] 模型输出与期望不符的原因或用户的反馈。\n\n反例如下：\n\n<bad_cases>\n{{bad_cases}}\n</bad_cases>\n\n## 任务描述\n你的任务是：\n\n1. **分析整体反例输出intent内容**：如果反例的内容没有实际意义或者对改进原提示词完全没有帮助，那么输出`false`；如果反馈有价值，返回`true`，并将值包含在`<intent>`和`</intent>`标签内\n2. **单独分析每个反例**：针对每个反例，识别输出中存在的具体问题，并解释为什么原始提示词未能产生期望的结果。\n3. **为每个反例生成具体的反馈**：将每个反馈包含在 `<feedback>` 和 `</feedback>` 标签内。每个反馈应包含：\n    * 清晰地描述问题。\n    * 解释与提示词措辞或指令相关的可能原因。\n    * 提出针对该问题改进提示词的具体建议。\n4. **创建反馈的简洁总结**：在分析所有反例后，提供关键问题和建议改进的总结。将总结包含在 `<summary>` 和 `</summary>` 标签内。总结应将各个反馈综合成改进提示词的总体建议。重点关注方法论，而不是具体细节，并尽量保持简洁。\n5. 原始提示词使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n";

    private static final String PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE_TEXT = "\n## 人设\n你是一名专业的提示词工程师。你的任务是根据在特定案例应用后收到的反馈，改进大型语言模型的提示词。\n\n使用的原始提示词是：\n\n<original_prompt>\n{{original_prompt}}\n</original_prompt>\n\n我们使用此提示词在多个输入上进行了测试，并观察到以下问题并收到了以下反馈：\n\n<feedback>\n{{feedback}}\n</feedback>\n\n你的目标是修改原始提示词以解决反馈中提出的问题。修改后的提示词应：\n\n*   针对反馈中提出的问题进行专门的解决。\n*   保持提示词的原始意图，除非反馈明确建议更改意图。\n*   尽可能清晰、简洁且无歧义。\n*   考虑边缘情况和潜在的误解。\n*   不对占位符本身进行增加、删除或修改，占位符以双花括弧的形式展现。\n*   原始提示词使用什么语言，输出内容就使用什么语言，请严格遵守此条规则。\n\n仅返回改进后的提示词内容,不要输出其他多余的标签。\n";

    private static final String FORMAT_BAD_CASE_TEMPLATE_TEXT = "\n[question]: {{question}}\n[expected answer]: {{label}}\n[assistant answer]: {{answer}}\n[reason]: {{reason}}\n=== \n";

    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE = messageTemplate(new SystemMessage(PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_BUILD_GENERAL_META_USER_TEMPLATE = messageTemplate(new UserMessage(PROMPT_BUILD_GENERAL_META_USER_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE = messageTemplate(new SystemMessage(PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_BUILD_PLAN_META_USER_TEMPLATE = messageTemplate(new UserMessage(PROMPT_BUILD_PLAN_META_USER_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_FEEDBACK_INTENT_TEMPLATE = messageTemplate(new UserMessage(PROMPT_FEEDBACK_INTENT_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_FEEDBACK_GENERAL_TEMPLATE = messageTemplate(new UserMessage(PROMPT_FEEDBACK_GENERAL_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_FEEDBACK_SELECT_TEMPLATE = messageTemplate(new UserMessage(PROMPT_FEEDBACK_SELECT_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_FEEDBACK_INSERT_TEMPLATE = messageTemplate(new UserMessage(PROMPT_FEEDBACK_INSERT_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_BAD_CASE_ANALYZE_TEMPLATE = messageTemplate(new UserMessage(PROMPT_BAD_CASE_ANALYZE_TEMPLATE_TEXT));

    public static final PromptTemplate PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE = messageTemplate(new UserMessage(PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE_TEXT));

    public static final PromptTemplate FORMAT_BAD_CASE_TEMPLATE = PromptTemplate.builder().content(FORMAT_BAD_CASE_TEMPLATE_TEXT).build();

    private static final Map<String, PromptTemplate> TEMPLATES = buildTemplates();

    private PromptZh() {
    }

    public static Map<String, PromptTemplate> templates() {
        return TEMPLATES;
    }

    public static String templateText(String name) {
        PromptTemplate template = TEMPLATES.get(name);
        if (template == null) {
            return null;
        }
        Object content = template.getContent();
        if (content instanceof String text) {
            return text;
        }
        return template.toMessages().get(0).getContentAsString();
    }

    private static PromptTemplate messageTemplate(BaseMessage message) {
        return PromptTemplate.builder().content(List.of(message)).build();
    }

    private static Map<String, PromptTemplate> buildTemplates() {
        Map<String, PromptTemplate> templates = new LinkedHashMap<>();
        templates.put("PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE", PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE);
        templates.put("PROMPT_BUILD_GENERAL_META_USER_TEMPLATE", PROMPT_BUILD_GENERAL_META_USER_TEMPLATE);
        templates.put("PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE", PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE);
        templates.put("PROMPT_BUILD_PLAN_META_USER_TEMPLATE", PROMPT_BUILD_PLAN_META_USER_TEMPLATE);
        templates.put("PROMPT_FEEDBACK_INTENT_TEMPLATE", PROMPT_FEEDBACK_INTENT_TEMPLATE);
        templates.put("PROMPT_FEEDBACK_GENERAL_TEMPLATE", PROMPT_FEEDBACK_GENERAL_TEMPLATE);
        templates.put("PROMPT_FEEDBACK_SELECT_TEMPLATE", PROMPT_FEEDBACK_SELECT_TEMPLATE);
        templates.put("PROMPT_FEEDBACK_INSERT_TEMPLATE", PROMPT_FEEDBACK_INSERT_TEMPLATE);
        templates.put("PROMPT_BAD_CASE_ANALYZE_TEMPLATE", PROMPT_BAD_CASE_ANALYZE_TEMPLATE);
        templates.put("PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE", PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE);
        templates.put("FORMAT_BAD_CASE_TEMPLATE", FORMAT_BAD_CASE_TEMPLATE);
        return Collections.unmodifiableMap(templates);
    }
}
