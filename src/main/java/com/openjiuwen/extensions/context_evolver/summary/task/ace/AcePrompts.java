// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

/**
 * ACE prompt templates for reflection and curation.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.summary.task.ace.prompt}.
 */
public final class AcePrompts {

    public static final String ACE_REFLECTOR_PROMPT = """
You are an expert reflection agent and educator. Your job is to diagnose the current trajectory: identify what went wrong (or could be better), grounded in execution feedback, API usage, unit test report, and ground truth when applicable.

Instructions:
- Carefully analyze the model's reasoning trace to identify where it went wrong
- Take the environment feedback into account, comparing the predicted answer with the ground truth to understand the gap
- Identify specific conceptual errors, calculation mistakes, or misapplied strategies
- Provide actionable insights that could help the model avoid this mistake in the future
- Identify root causes: wrong source of truth, bad filters (timeframe/direction/identity), formatting issues, or missing authentication and how to correct them
- Provide concrete, step-by-step corrections the model should take in this task
- Be specific about what the model should have done differently
- You will receive bulletpoints that are part of playbook that's used by the generator to answer the question
- You need to analyze these bulletpoints, and give the tag for each bulletpoint, tag can be ['helpful', 'harmful', 'neutral']
- Explicitly curate from the environment feedback the output format/schema of APIs used when unclear or mismatched with expectations (e.g., apis.blah.show_contents() returns a list of content_ids (strings), not content objects)

Inputs:
- Ground Truth Code (reference, known-correct):
GROUND_TRUTH_CODE_START
{ground_truth}
GROUND_TRUTH_CODE_END

- Test Report (unit tests result for the task after the generated code was run):
TEST_REPORT_START
{feedback}
TEST_REPORT_END

- ACE Playbook (playbook that's used by model for code generation):
PLAYBOOK_START
{playbook}
PLAYBOOK_END

Outputs: Your output should be a json object, which contains the following fields
- reasoning: your chain of thought / reasoning / thinking process, detailed analysis and calculations
- error_identification: what specifically went wrong in the reasoning?
- root_cause_analysis: why did this error occur? What concept was misunderstood?
- correct_approach: what should the model have done instead?
- key_insight: what strategy, formula, or principle should be remembered to avoid this error?
Answer in this exact JSON format:
{
  "reasoning": "[Your chain of thought / reasoning / thinking process, detailed analysis and calculations]",
  "error_identification": "[What specifically went wrong in the reasoning?]",
  "root_cause_analysis": "[Why did this error occur? What concept was misunderstood?]",
  "correct_approach": "[What should the model have done instead?]",
  "key_insight": "[What strategy, formula, or principle should be remembered to avoid this error?]"
}

{trajectory}
""";

    public static final String ACE_REFLECTOR_NOGT_PROMPT = """
You are an expert reflection agent and educator. Your job is to diagnose the current trajectory: identify what went wrong (or could be better), grounded in execution feedback and API usage.

Instructions:
- Carefully analyze the model's reasoning trace to identify where it went wrong
- Identify specific conceptual errors, calculation mistakes, or misapplied strategies
- Provide actionable insights that could help the model avoid this mistake in the future
- Identify root causes: wrong source of truth, bad filters (timeframe/direction/identity), formatting issues, or missing authentication and how to correct them
- Provide concrete, step-by-step corrections the model should take in this task
- Be specific about what the model should have done differently
- You will receive bulletpoints that are part of playbook that's used by the generator to answer the question
- You need to analyze these bulletpoints, and give the tag for each bulletpoint, tag can be ['helpful', 'harmful', 'neutral']
- Explicitly curate from the environment feedback the output format/schema of APIs used when unclear or mismatched with expectations (e.g., apis.blah.show_contents() returns a list of content_ids (strings), not content objects)

Inputs:
- ACE Playbook (playbook that's used by model for code generation):
PLAYBOOK_START
{playbook}
PLAYBOOK_END

Outputs: Your output should be a json object, which contains the following fields
- reasoning: your chain of thought / reasoning / thinking process, detailed analysis and calculations
- error_identification: what specifically went wrong in the reasoning?
- root_cause_analysis: why did this error occur? What concept was misunderstood?
- correct_approach: what should the model have done instead?
- key_insight: what strategy, formula, or principle should be remembered to avoid this error?
Answer in this exact JSON format:
{
  "reasoning": "[Your chain of thought / reasoning / thinking process, detailed analysis and calculations]",
  "error_identification": "[What specifically went wrong in the reasoning?]",
  "root_cause_analysis": "[Why did this error occur? What concept was misunderstood?]",
  "correct_approach": "[What should the model have done instead?]",
  "key_insight": "[What strategy, formula, or principle should be remembered to avoid this error?]"
}

{trajectory}
""";

    public static final String ACE_CURATOR_PROMPT = """
You are a master curator of knowledge. Your job is to identify what new insights should be added to an existing playbook based on a reflection from a previous attempt.

Context:
- The playbook you created will be used to help answering similar questions.
- The reflection is generated using ground truth answers that will NOT be available when the playbook is being used. So you need to come up with content that can aid the playbook user to create predictions that likely align with ground truth.

Instructions:
- Review the existing playbook and the reflection from the previous attempt
- ADD ONLY the NEW insights, strategies, or mistakes that are MISSING from the current playbook
- Avoid redundancy, if similar advice already exists. Only add new content that is a perfect complement to the existing playbook
- The number of MAXIMUM insight in the playbook is 50. If the playbook is full consider to REMOVE or UPDATE exisiting insight
- Do NOT regenerate the entire playbook, only provide the additions needed or update existing insight with refined one
- Focus on quality over quantity, a focused well-organized playbook is better than an exhaustive one. You can REMOVE redundant or unnecessary insight
- Format your response as a PURE JSON object with specific sections
- For any operation if no new content to ADD/UPDATE/TAG/REMOVE, return an empty list for the operations field
- Be concise and specific, each operation should be actionable
- For coding tasks, explicitly curate from the reflections the output format/schema of APIs used when unclear or mismatched with expectations (e.g., apis.blah.show_contents() returns a list of content_ids (strings), not content objects)

Task Context (the actual task instruction):
{question_context}

Current Playbook:
{playbook}

Current Generated Attempt (latest attempt, with reasoning and planning):
{trajectory}

Current Reflections (principles and strategies that helped to achieve current task):
{reflection}

Your Task: Output ONLY a valid JSON object with these exact fields:
- reasoning: your chain of thought / reasoning / thinking process, detailed analysis and calculations
- operations: a list of operations to be performed on the playbook
- type: the type of operation to be performed
- section: the section to add the bullet to
- content: the new content of the bullet
Available Operations:
1. ADD: Create new bullet points with fresh IDs
2. UPDATE: Modify the content or metadata of an existing bullet
3. TAG: Add or increment a numerical metadata tag on an existing bullet
4. REMOVE: Delete an existing bullet point

RESPONSE FORMAT - Output ONLY this JSON structure (no markdown, no code blocks):
{
  "reasoning": "<how you decided on the updates>",
  "operations": [
    {
      "type": "ADD|UPDATE|TAG|REMOVE",
      "section": "<section name>",
      "content": "<insight/strategy/mistake>",
      "bullet_id": "<optional existing id>",
      "metadata": {"helpful": 1, "harmful": 0}
    }
  ]
}
If no updates are required, return an empty list for "operations".
""";

    public static final String ACE_REFLECTOR_SCALING_PROMPT = """
You are an expert reflection agent and educator. Your job is to diagnose the current trajectories: compare and contrast them to identify the most useful and generalizable strategies as memory items, grounded in execution feedback, API usage, unit test report, and ground truth when applicable.

Guidelines:
- Identify patterns and strategies that consistently led to success
- Identify mistakes or inefficiencies from failed trajectories and formulate preventative strategies
- Explicitly curate the output format/schema of APIs used when unclear or mismatched with expectations

Inputs:
- Ground Truth Code (reference, known-correct):
GROUND_TRUTH_CODE_START
{ground_truth}
GROUND_TRUTH_CODE_END

- ACE Playbook (playbook that's used by model for code generation):
PLAYBOOK_START
{playbook}
PLAYBOOK_END

Outputs: Your output should be a json object with the fields reasoning, error_identification, root_cause_analysis, correct_approach, and key_insight.

{trajectories}
""";

    public static final String ACE_REFLECTOR_SCALING_NOGT_PROMPT = """
You are an expert reflection agent and educator. You will be given a user query and multiple trajectories showing how an agent attempted the task. Some trajectories may be successful, and others may have failed.

Guidelines:
- Identify patterns and strategies that consistently led to success
- Identify mistakes or inefficiencies from failed trajectories and formulate preventative strategies
- Explicitly curate the output format/schema of APIs used when unclear or mismatched with expectations

Inputs:
- ACE Playbook (playbook that's used by model for code generation):
PLAYBOOK_START
{playbook}
PLAYBOOK_END

Outputs: Your output should be a json object with the fields reasoning, error_identification, root_cause_analysis, correct_approach, and key_insight.

{trajectories}
""";

    public static final String ACE_CURATOR_SCALING_PROMPT = """
You are a master curator of knowledge. Your job is to identify what new insights should be added to an existing playbook based on a reflection from previous attempts.

Context:
- The playbook you create will be used to help answer similar questions
- The reflection is generated using ground truth answers that will NOT be available when the playbook is being used

Instructions:
- Review the existing playbook and the reflection from the previous attempts
- ADD ONLY the NEW insights, strategies, or mistakes that are MISSING from the current playbook
- Avoid redundancy. Only add new content that is a perfect complement to the existing playbook
- The maximum playbook size is 50 bullets; if the playbook is full, consider REMOVE or UPDATE operations
- For any operation if no new content to ADD/UPDATE/TAG/REMOVE, return an empty list for the operations field

Task Context (the actual task instruction):
{question_context}

Current Playbook:
{playbook}

Current Generated Attempts:
{trajectories}

Current Reflections:
{reflection}

RESPONSE FORMAT - Output ONLY this JSON structure (no markdown, no code blocks):
{
  "reasoning": "<how you decided on the updates>",
  "operations": [
    {
      "type": "ADD|UPDATE|TAG|REMOVE",
      "section": "<section name>",
      "content": "<insight/strategy/mistake>",
      "bullet_id": "<optional existing id>",
      "metadata": {"helpful": 1, "harmful": 0}
    }
  ]
}
If no updates are required, return an empty list for "operations".
""";

    private AcePrompts() {
    }
}
