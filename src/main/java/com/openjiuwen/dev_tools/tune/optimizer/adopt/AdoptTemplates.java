/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ADOPT optimizer prompt templates and utilities.
 *
 * <p>Mirrors Python's {@code utils} in {@code openjiuwen.dev_tools.tune.optimizer.adopt.utils}.
 */
public final class AdoptTemplates {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AdoptTemplates() {}

  // ================== Output Change Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String OUTPUT_CHANGE_SYSTEM_PROMPT =
      """
You are the dedicated feedback engine for output of a multi-stage workflow.
Your only responsibility is to analyze a single candidate response and produce a constructive, \
metric-driven feedback that, when applied, maximizes its score under a single metric.
You don't need to consider optimizing any node; you only need to focus on modifying the output.

Specifications:
1. The workflow description is provided in <WORKFLOW_DESCRIPTION>.
2. The current workflow output is provided in <CURRENT_OUTPUT>.
3. The expected correct output is provided in <GROUND_TRUTH>.
4. The evaluation function is <METRIC_FUNCTION>, which returns a real-valued score in [0, 1].
5. You should only critique the candidate, not rewrite it. Focus on actionable suggestions.
6. List 1-5 specific differences between the candidate response and the ground truth.
7. If the candidate already achieves a perfect score (1.0), reply: "No improvement needed, \
optimal score achieved".
8. Limit feedback to actionable suggestions for improving the specified metric.
9. Strictly adhere to the given optimization <CONSTRAIN>.
10. You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String OUTPUT_CHANGE_USER_PROMPT =
      """
      Here is the information for your feedback task:

      - <WORKFLOW_DESCRIPTION> {{workflow_description}} </WORKFLOW_DESCRIPTION>
      - <CURRENT_OUTPUT> {{current_output}} </CURRENT_OUTPUT>
      - <GROUND_TRUTH> {{ground_truth}} </GROUND_TRUTH>
      - <METRIC_FUNCTION> {{metric_fn}} </METRIC_FUNCTION>
      - <CURRENT_SCORE> {{current_score}} </CURRENT_SCORE>
      - <CONSTRAIN> {{constrain}} </CONSTRAIN>

      <OBJECT>
      1. Provide actionable, metric-focused feedback to increase the candidate response's score.
      2. Identify 1-5 key differences between the candidate response and the ground truth.
      </OBJECT>
      """;

  // ================== Deep Output Analysis Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String DEEP_OUTPUT_ANALYSIS_SYSTEM_PROMPT =
      """
You are the deep-dive analysis assistant for workflow outputs.
Your task is to explain **why** the workflow's actual output deviates from the expected output, \
using only the provided external knowledge.

Requirements:
1. Begin with an "Analysis:" section where you think step by step.
2. Then list reasons for each difference.
3. Each reason must quote the relevant excerpt from <EXTERNAL_KNOWLEDGE>.
4. Do **not** propose any fixes or mention the prompt-only diagnose the failure.
5. Strictly adhere to the given optimization <CONSTRAIN>.
6. You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String DEEP_OUTPUT_ANALYSIS_USER_PROMPT =
      """
      Here is the information for your analysis:

      - <WORKFLOW_DESCRIPTION> {{workflow_description}} </WORKFLOW_DESCRIPTION>
      - <INPUT> {{node_input}} </INPUT>
      - <CURRENT_OUTPUT> {{node_output}} </CURRENT_OUTPUT>
      - <GROUND_TRUTH> {{node_expected_output}} </GROUND_TRUTH>
      - <EXTERNAL_KNOWLEDGE> {{external_knowledge}} </EXTERNAL_KNOWLEDGE>
      - <CONSTRAIN> {{constrain}} </CONSTRAIN>
      - <SHALLOW_DIFFERENCE> {{shallow_difference}} </SHALLOW_DIFFERENCE>

      <OBJECT>
      Provide deep reasons why the <CURRENT_OUTPUT> fails to comply with <EXTERNAL_KNOWLEDGE>.
      </OBJECT>
      """;

  // ================== Expected Output Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String EXPECTED_OUTPUT_SYSTEM_PROMPT =
      """
You are the optimization assistant for a specific LLM node within a multi-stage workflow.

Requirements:
1. Begin with your chain of thought under the heading "Reasoning:".
2. After your reasoning, produce the final node output wrapped exactly in \
<REVISED_NODE_OUTPUT>...</REVISED_NODE_OUTPUT> tags.
3. Do not include any other text outside the "Reasoning:" section and the tagged output.
4. Do **not** write the REVISED_NODE_OUTPUT in the same way a CURRENT_WRONG_NODE_OUTPUT.
5. You need to first consider how the past outputs of this node led to the final erroneous result.
6. You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String EXPECTED_OUTPUT_USER_PROMPT =
      """
      Here is the information for your task:

      - <DEPENDENCY> {{dependency_from_this_workflow_final_output}} </DEPENDENCY>
      - <CURRENT_WORKFLOW_OUTPUT> {{workflow_output}} </CURRENT_WORKFLOW_OUTPUT>
      - <MODIFICATION> {{modification}} </MODIFICATION>
      - <NODE_IN_BLOCK> {{node_in_block}} </NODE_IN_BLOCK>
      - <REVISED_NODE_OUTPUT>...</REVISED_NODE_OUTPUT>

      <OBJECT>
      Think step by step ("Reasoning:") and then produce all the exact text this node should emit.
      </OBJECT>
      """;

  // ================== Gradient Generate Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String GRADIENT_GENERATE_SYSTEM_PROMPT =
      """
You are the optimization assistant for a specific LLM node prompt within a multi-stage workflow.
Your task is to analyze the current prompt, job of the node, input, actual output, and the \
expected output for a single case.

Requirements:
1. Begin with a chain of thought under the heading "Reasoning:".
2. After your reasoning, emit all the reasons wrapped in one <REASON>...</REASON> tag.
3. Do not include any other text.
4. The format difference between CURRENT_OUTPUT and EXPECTED_OUTPUT are not important.
5. You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String GRADIENT_GENERATE_USER_PROMPT =
      """
Here is the information for your optimization task:

- <NODE_JOB> {{node_job}} </NODE_JOB>
- <NODE_INPUT> {{node_input}} </NODE_INPUT>
- <CURRENT_OUTPUT> {{node_output}} </CURRENT_OUTPUT>
- <MODIFICATION> {{modification}} </MODIFICATION>
- <EXPECTED_OUTPUT> {{node_expected_output}} </EXPECTED_OUTPUT>
- <CURRENT_PROMPT> {{node_prompt}} </CURRENT_PROMPT>

<OBJECT>
Based on the above, modify the <CURRENT_PROMPT> so that the node will produce <EXPECTED_OUTPUT> \
instead of <CURRENT_OUTPUT>.
</OBJECT>
""";

  // ================== Gradient Reduce Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String GRADIENT_REDUCE_SYSTEM_PROMPT =
      """
You are the summarization assistant for prompt optimization across multiple cases.
Your task is to:

1. Synthesize a set of individual "Reasoning:" outputs from different cases into summary of \
failure patterns.
2. Based on that summary, propose 1~5 highly specific, actionable suggestions to modify the current prompt.
3. Do **not** output any revised prompt text, only list the suggestions.
4. Wrap your suggestion list in <SUGGESTIONS>...</SUGGESTIONS> tags.
5. Do not include any other content.
6. You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String GRADIENT_REDUCE_USER_PROMPT =
      """
      Here are the accumulated reasoning outputs from individual cases:
      <REASON>
      {{all_reasons}}
      </REASON>

      The job of this prompt is:
      <JOB>
      {{node_job}}
      </JOB>

      The current prompt requiring improvement is:
      <CURRENT_PROMPT>
      {{current_prompt}}
      </CURRENT_PROMPT>

      <OBJECT>
      1. Summarize the reasons above into overarching themes.
      2. Provide 1~5 concrete modification suggestions for <CURRENT_PROMPT> to fix those issues.
      </OBJECT>
      """;

  // ================== Prompt Update Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String PROMPT_UPDATE_SYSTEM_PROMPT =
      """
You are an expert prompt engineer.
When given a prompt that underperforms, analysis of its failures, and a concise feedback summary, \
you will generate a revised prompt that addresses those failures.
Your output must consist **only** the "Reasoning:" section and the improved prompt wrapped in \
<REVISED_PROMPT>...</REVISED_PROMPT> tags.
You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String PROMPT_UPDATE_USER_PROMPT =
      """
      I'm trying to refine a prompt for a large language model.

      My current prompt is:
      <CURRENT_PROMPT>
      {{current_prompt}}
      </CURRENT_PROMPT>

      It produces incorrect outputs on the following examples:
      <EXAMPLES>
      {{error_cases}}
      </EXAMPLES>

      Analysis of these failures indicates:
      <FEEDBACK>
      {{feedback}}
      </FEEDBACK>

      Based on the above, generate a new, improved prompt that corrects these issues.
      Think step by step ("Reasoning:") and then produce the revised prompt.
      Wrap your revised prompt exactly in <REVISED_PROMPT>...</REVISED_PROMPT> tags.
      """;

  // ================== Conclude Prompts ==================

  /** Auto-generated for codecheck compliance. */
  public static final String CONCLUDE_NODE_SYSTEM_PROMPT =
      """
## Role
You are a workflow-analysis master who excels at pinpointing how a **single LLM call** affects \
the **final workflow output**.

## Information Received
You will receive:
1. The name of the LLM node to be analyzed
2. A rough summary of the LLM workflow and its nodes
3. The prompt of the LLM node to be analyzed
4. Multiple good cases in JSON format

## Skills
1. Carefully analyze **every good case** provided and summarize commonalities.
2. Determine **what specific duty** the LLM node performs.
3. Reason about **how the LLM node's output correlates with the final output**.

## Output
The summary of node responsibility should be concise.
You always output in Chinese.
""";

  /** Auto-generated for codecheck compliance. */
  public static final String CONCLUDE_NODE_USER_PROMPT =
      """
      ### Current LLM call to be summarized:
      {{node_name}}

      ### Rough summary of the LLM workflow
      {{agent_description}}

      ### System prompt of the current LLM call:
      {{system_prompt}}

      ### User prompt of the current LLM call:
      {{user_prompt}}

      ### In various good cases, the input & output of this LLM call:
      {{good_cases}}
      """;

  // ================== Utility Methods ==================

  /**
   * Builds node input/output string for prompts.
   *
   * @param inputList the list of inputs
   * @param outputList the list of outputs
   * @return formatted string
   */
  public static String buildNodeIoString(List<?> inputList, List<?> outputList) {
    if (inputList == null || outputList == null || inputList.isEmpty()) {
      return "";
    }
    int length = inputList.size();
    if (length == 1) {
      return String.format(
          Locale.ROOT,
          "- <NODE_INPUT> %s </NODE_INPUT>\n"
              + "- <CURRENT_WRONG_NODE_OUTPUT> %s </CURRENT_WRONG_NODE_OUTPUT>",
          safeParseJson(inputList.get(0)),
          safeParseJson(outputList.get(0)));
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append(
          String.format(
              Locale.ROOT,
              "- <NODE_INPUT_%d> %s </NODE_INPUT_%d>\n"
                  + "- <CURRENT_WRONG_NODE_OUTPUT_%d> %s </CURRENT_WRONG_NODE_OUTPUT_%d>\n",
              i,
              safeParseJson(inputList.get(i)),
              i,
              i,
              safeParseJson(outputList.get(i)),
              i));
    }
    return sb.toString();
  }

  private static String safeParseJson(Object obj) {
    if (obj == null) {
      return "null";
    }
    if (obj instanceof Map || obj instanceof List) {
      try {
        return MAPPER.writeValueAsString(obj);
      } catch (JsonProcessingException e) {
        return obj.toString();
      }
    }
    return obj.toString();
  }
}
