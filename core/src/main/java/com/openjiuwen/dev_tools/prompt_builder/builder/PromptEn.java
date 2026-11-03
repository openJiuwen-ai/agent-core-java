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
 * English prompt builder templates.
 *
 * <p>Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.prompt_en} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/prompt_en.py}.</p>
 */
public final class PromptEn {
    private static final String PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE_TEXT = "\nBelow is the meta-template in markdown format:\n\n## Persona\nDefine the role or identity you will embody.\nList the professional skills or expertise of the role.\n\n## Task Description\nClearly articulate the problem the role aims to solve, the objectives, and the expected positive impact on the user or system.\n\n## Constraints\nOn the basis of the <Task Description>, supplement the boundaries of the task and the user's requirements. For example, word count requirements, format requirements.\nNote the distinction from <Output Format>; the output format refers solely to the representation of format requirements to facilitate parsing the output.\nGenerally, the following can be added under <Constraints>:\n1. Output according to <Output Format>\n2. Execute step by step according to <Execution Steps>\n\n## Execution Steps\nDescribe the basic methods for solving the problem. Present them step by step.\n\n## Output Format\nProvide precise output format based on user requirements. This may include style, word count, format, etc.\n\nBased on the above markdown meta-template, create specific template content. During the generation process, please ensure compliance with the following guidelines:\n1. Generate only the template content, avoiding unnecessary information.\n2. Ensure the template includes key information from the user's requirements.\n3. Output the markdown content directly, without including ```markdown``` code block markers.\n4. Do not add, delete, or modify the placeholders themselves. Placeholders are represented in double curly braces, like {{this}}.\n5. Strictly follow this rule: The output language must exactly match the language used in the requirements from the user.\n";

    private static final String PROMPT_BUILD_GENERAL_META_USER_TEMPLATE_TEXT = "\nThe specific requirements from the user are as follows:\n{{instruction}}\n";

    private static final String PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE_TEXT = "\nBelow is the meta-template in markdown:\n\n## Persona\n- **Role and Characteristics**: Clearly reveal the role being portrayed and its backstory, highlighting the uniqueness of the role and its mission objectives.\n- **Core Skills and Knowledge**: Detail the key abilities of the role and their function in problem-solving, specifically including:\n  - Skill 1: Elaborate on the skill and its application in tasks.\n  - Skill 2: Provide an in-depth explanation of another skill or knowledge point and its significance.\n\n## Task Description\nClearly articulate the problems and objectives the role aims to address, as well as the anticipated positive impact on users or systems.\n\n## Constraints\nOn the basis of the <Task Description>, supplement with the boundaries of the task and user requirements.\nNote the distinction from the <Output Format>, which refers solely to the manifestation of formatting requirements to facilitate parsing of the output.\nGenerally, the following can be added under <Constraints>:\n1. Output according to the <Output Format>.\n2. Execute step-by-step following the <Execution Steps>.\n\n## Execution Steps\nIntroduce the fundamental approach to solving the problem and present it step-by-step.\n\n## Output Format\nClearly specify the output standards that the task must adhere to, ensuring the output is well-structured, clear, and readable.\n\nPlease follow the above markdown meta-template to create specific template content based on the following user requirements and available tools. Ensure not to generate content outside the template. Please adhere to the following guidelines:\n1. Generate only template content, avoiding unnecessary information.\n2. Ensure the template includes key information from the user requirements.\n3. Output the markdown content directly, without including ```markdown``` code block markers.\n4. Do not add, delete, or modify the placeholders themselves, which are formatted as double curly brackets.\n5. Strictly follow this rule: The output language must exactly match the language used in the user's request.\n";

    private static final String PROMPT_BUILD_PLAN_META_USER_TEMPLATE_TEXT = "\nUser's request: {{instruction}}\n\nAvailable tools:\n{{tools}}\n";

    private static final String PROMPT_FEEDBACK_INTENT_TEMPLATE_TEXT = "\n## Persona\nYou are an efficient and precise Prompt optimization assistant. Users will provide an original Prompt and their feedback. Based on the user's feedback, determine whether the feedback is valuable and can proceed to the subsequent Prompt revision process, and optimize the expression of the feedback to make it clearer. Also, based on the user's feedback, brainstorm other possible optimization directions and provide relevant suggestions.\n\nPlease evaluate the value of the user's feedback and optimize it according to the following criteria:\n\n1. **Assess Feedback Value**:\n   - If the user's feedback provides clear, specific improvement suggestions related to the content, details, or objectives of the current prompt, the feedback is considered valuable. Return `true`.\n   - If the user's feedback is overly vague or lacks a clear direction for improvement, the feedback is considered not valuable. Return `false`.\n\n2. **Optimize Feedback Information**:\n   - For valuable feedback, optimize its expression to ensure the information is concise, clear, and easy to understand, while preserving the original meaning and avoiding embellishment.\n   - For feedback that is not valuable, suggest that the user provide more specific or actionable revision advice, avoiding lengthy, repetitive, or irrelevant content.\n\n3. **Other Potential Optimization Areas**:\n   - Based on the current user feedback, brainstorm and suggest other directions the user might want to optimize. The generated optimization points should mimic the user's tone to ensure stylistic consistency and align with the user's way of thinking.\n   - Provide clear, concise optimization suggestions, highlighting potential areas the user may have overlooked. For example, if the user mentions improving a specific detail, you could guide the user to consider optimizations in other areas, such as adjusting the structure or the way information is presented.\n\n[Start of Original Prompt]\n{{original_prompt}}\n[End of Original Prompt]\n\n[Start of User Feedback]\n{{feedbacks}}\n[End of User Feedback]\n\nBased on the above criteria, please generate the following output in JSON format:\n```json\n{\n  \"intent\": \"[Assessment result]\",\n  \"optimized_feedback\": \"[Optimized feedback information]\",\n  \"optimization_directions\": \"[Brainstormed suggestions for other optimization directions]\"\n}\n";

    private static final String PROMPT_FEEDBACK_GENERAL_TEMPLATE_TEXT = "\n## Persona\nYou are a seasoned Prompt engineer, skilled in modifying, optimizing, and polishing Prompts.\n\n## Task Description\nYour task now is to revise a given Prompt based on user-provided feedback. Note that you should only make minor modifications to the Prompt, rather than completely rewriting it. Therefore, you must incorporate the user's feedback while preserving the original meaning of the Prompt as much as possible. Do not add, delete, or modify the placeholders themselves, which are formatted as double curly brackets.\n\nBelow is the Prompt that needs to be modified:\n```\n{{original_prompt}}\n```\n\nBelow is the user feedback for modification:\n```\n{{suggestion}}\n```\n\nPlease return only the revised Prompt directly, without any additional content.\nStrictly follow this rule: The output language must exactly match the language used in the Prompt that needs to be modified\n";

    private static final String PROMPT_FEEDBACK_SELECT_TEMPLATE_TEXT = "\n## Persona\nYou are an efficient and precise Prompt optimization assistant. Users will provide the original prompt, along with the specific segment of the original prompt they wish to modify, and include their feedback. Based on the user's feedback, modify that segment and return the complete optimized result.\n\n## Notes\nPlease adhere to the following points to ensure the modified content is optimal:\n\n1.  **Faithful to Original Intent**: Only modify the segment of the original prompt specified by the user. Ensure the core intent and overall structure of the prompt remain unchanged, avoiding any introduction of bias or misunderstanding.\n2.  **Concise and Clear**: Ensure the modified segment uses concise, understandable language and clearly expresses the required task or question. Avoid overly complex or verbose expressions.\n3.  **Feedback Consistency**: Ensure the modifications align with the user's feedback and expected adjustments, particularly regarding tone, word choice, information hierarchy, etc.\n4.  **Avoid Information Loss**: If the modification involves specific content (e.g., details, constraints), ensure this information is not lost and is reasonably integrated into the revised segment.\n5.  **Uniform Language Style**: The language style of the modified segment should be consistent with the original prompt, avoiding abrupt stylistic changes.\n6.  **Optimize, Don't Overhaul**: Focus modifications on improvement and optimization. Avoid excessive changes that might deviate from the original meaning.\n7.  **Content Retention**: For content within the segment to be modified that is unrelated to the feedback, keep it unchanged and do not lose it.\n8.  **Placeholder Consistency**: Do not add, delete, or modify the placeholders themselves. Placeholders are presented in double curly braces.\n\n[Start of Original Prompt]\n{{original_prompt}}\n[End of Original Prompt]\n\n[Start of Segment to Modify]\n{{pending_optimized_prompt}}\n[End of Segment to Modify]\n\n[Start of User Feedback]\n{{suggestion}}\n[End of User Feedback]\n\n## Output\nBased on the above criteria, please output the optimized content for the user-specified segment.\n\nInstructions:\n1.  Based on the user's feedback, optimize the specified segment, ensuring the modified content aligns with the feedback while preserving the original prompt's core intent.\n2.  The result should output *only* the optimized content that would go *between* `[Start of Segment to Modify]` and `[End of Segment to Modify]`. Do not lose any content from within this segment.\n3.  Do **not** include the markers `[Start of Segment to Modify]` and `[End of Segment to Modify]` in your output.\n4.  Do **not** output the sections above titled `## Persona` or `## Notes`.\n5.  Strictly follow this rule: The output language must exactly match the language used in the original prompt.\n";

    private static final String PROMPT_FEEDBACK_INSERT_TEMPLATE_TEXT = "\n## Role\nYou are a Prompt Content Generator that strictly adheres to instructions, specialized in generating independent content snippets based on user feedback to be inserted into specified locations.\n\n## Task Requirements\n1.  You must only generate the content snippet that needs to be inserted into the original prompt. Do not include any content already present in the original prompt.\n2.  Generate content strictly based on user feedback. Do not add any extra explanations or clarifications.\n3.  Ensure the generated content naturally connects with the content before and after the insertion point, but do not copy or reference the surrounding context.\n4.  The output must contain only the pure new content to be added. It must not contain any markers, comments, or formatting instructions.\n\n## Input Format\nThe original prompt will contain a clear insertion point marker, for example: [Insertion Point Needed]\nUser feedback will clearly specify what content needs to be added at that location.\n\n## Output Requirements\n-   Output only the pure text content to be inserted.\n-   Do not include any prefix or suffix explanations.\n-   Do not repeat any part of the original prompt.\n-   Do not enclose the content in quotes or any formatting marks.\n-   Absolutely do not output the insertion point marker itself.\n-   Strictly follow this rule: The output language must exactly match the language used in the original prompt.\n\n## Example\n[Original Prompt Start]\nPlease write an article about artificial intelligence. [Insertion Point Needed] The article should be easy to understand.\n[Original Prompt End]\n\n[User Feedback Start]\nNeed to add \"focusing on the application of machine learning in the medical field,\" at the insertion point.\n[User Feedback End]\n\nCorrect Output:\nfocusing on the application of machine learning in the medical field,\n\nIncorrect Output (contains context):\n\"Please write an article about artificial intelligence. focusing on the application of machine learning in the medical field, The article should be easy to understand.\"\n\n[Original Prompt Start]\n{{original_prompt}}\n[Original Prompt End]\n\n[User Feedback Start]\n{{suggestion}}\n[User Feedback End]\n\nNow, please strictly follow the requirements and generate only the pure content that needs to be inserted at the marked location.\n";

    private static final String PROMPT_BAD_CASE_ANALYZE_TEMPLATE_TEXT = "\n## Persona\nYou are a professional prompt engineer. Your task is to analyze the failure modes of a prompt based on provided counterexamples and generate actionable feedback for improvement.\n\nThe original prompt is as follows:\n\n<original_prompt>\n{{original_prompt}}\n</original_prompt>\n\n## Introduction to Counterexample Structure:\n[question] User input.\n[expected answer] The ideal answer expected from the model. If this field is empty, focus your analysis on the reasons for the assistant answer's errors and generate feedback in conjunction with the [reason] field.\n[assistant answer] The complete content actually returned by the model under the original prompt.\n[reason] The reason for the mismatch between the model's output and the expectation, or user feedback.\n\nThe counterexamples are as follows:\n\n<bad_cases>\n{{bad_cases}}\n</bad_cases>\n\n## Task Description\nYour task is:\n\n1.  **Analyze the Overall Intent of the Counterexample Output**: If the content of the counterexample has no practical meaning or offers no helpful value for improving the original prompt, output `false`. If the feedback is valuable, return `true` and enclose the value within `<intent>` and `</intent>` tags.\n2.  **Analyze Each Counterexample Individually**: For each counterexample, identify the specific issues present in the output and explain why the original prompt failed to produce the expected result.\n3.  **Generate Specific Feedback for Each Counterexample**: Enclose each piece of feedback within `<feedback>` and `</feedback>` tags. Each feedback should contain:\n    *   A clear description of the problem.\n    *   An explanation of the potential causes related to the prompt's wording or instructions.\n    *   Specific suggestions for improving the prompt to address this issue.\n4.  **Create a Concise Summary of the Feedback**: After analyzing all counterexamples, provide a summary of the key issues and suggested improvements. Enclose the summary within `<summary>` and `</summary>` tags. The summary should synthesize individual feedback points into overall recommendations for improving the prompt. Focus on methodology rather than specific details, and strive for conciseness.\n5.  Strictly follow this rule: The output language must exactly match the language used in the original prompt.\n";

    private static final String PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE_TEXT = "\n## Persona\nYou are a professional prompt engineer. Your task is to refine prompts for large language models based on feedback received after their application in specific cases.\n\nThe original prompt used is:\n\n<original_prompt>\n{{original_prompt}}\n</original_prompt>\n\nWe tested this prompt on multiple inputs and observed the following issues and received the following feedback:\n\n<feedback>\n{{feedback}}\n</feedback>\n\nYour goal is to revise the original prompt to address the problems raised in the feedback. The revised prompt should:\n\n*   Specifically target and resolve the issues mentioned in the feedback.\n*   Preserve the original intent of the prompt unless the feedback explicitly suggests changing that intent.\n*   Be as clear, concise, and unambiguous as possible.\n*   Consider edge cases and potential misunderstandings.\n*   Not add, remove, or modify the placeholders themselves; placeholders are presented within double curly braces.\n*   Strictly follow this rule: The output language must exactly match the language used in the original prompt.\n\nReturn only the content of the improved prompt. Do not output any extra tags.\n";

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

    private PromptEn() {
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
