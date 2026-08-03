/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.Map;

/**
 * Public class VerificationContractRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class VerificationContractRail extends DeepAgentRail {
  /** Auto-generated for codecheck compliance. */
  public static final String SECTION_NAME = "verification_contract";

  /** Auto-generated for codecheck compliance. */
  public static final int SECTION_PRIORITY = 88;

  private static final String CONTRACT_CONTENT =
      """
## Verification Gate

After any non-trivial implementation turn, you MUST spawn the verification agent before reporting completion.

Non-trivial means any of:
- 3 or more file edits in a single turn
- Backend, API, or service changes
- Infrastructure or configuration changes

Use task_tool with subagent_type="verification_agent" and pass the original request, changed files, approach, and plan file path if one was used.
On VERDICT: PASS, spot-check 2-3 reported commands before reporting completion.
On VERDICT: FAIL, fix the issue and re-invoke the same verification session until PASS.
On VERDICT: PARTIAL, report what was verified and the environmental gap.
You cannot self-assign any verdict; only the verification agent issues PASS, FAIL, or PARTIAL.
""";

  private DeepAgent owner;

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    super.init(agent);
    if (agent != null) {
      owner = agent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void uninit(DeepAgent agent) {
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeModelCall(CallbackContext ctx) {
    if (owner != null) {
      ctx.put("verification_contract_section", new PromptSection(
          SECTION_NAME,
          Map.of("en", CONTRACT_CONTENT, "cn", CONTRACT_CONTENT),
          SECTION_PRIORITY));
    }
  }

  /** Auto-generated for codecheck compliance. */
  public boolean requiresVerification(
      int fileEditCount, boolean isBackendChange, boolean isInfrastructureChange) {
    return fileEditCount >= 3 || isBackendChange || isInfrastructureChange;
  }

  /** Auto-generated for codecheck compliance. */
  public String contractContent() {
    return CONTRACT_CONTENT.trim();
  }
}
