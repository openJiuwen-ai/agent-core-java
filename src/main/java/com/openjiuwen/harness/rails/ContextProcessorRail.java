/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Legacy flat-package facade; prefer {@code com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail}.
 *
 * @since 1.0
 * @deprecated Use {@code com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail} instead.
 */
@Deprecated
public class ContextProcessorRail extends com.openjiuwen.harness.rails.context_engineer.ContextProcessorRail {

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail() {
    super();
  }

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail(
      com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec processor) {
    super(processor);
  }

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail(
      java.util.List<com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec> processors) {
    super(processors);
  }

  /** Auto-generated for codecheck compliance. */
  public ContextProcessorRail(
      java.util.List<com.openjiuwen.core.context_engine.ContextEngine.ProcessorSpec> processors,
      boolean preset,
      com.openjiuwen.core.context_engine.context.SessionMemoryConfig sessionMemory) {
    super(processors, preset, sessionMemory);
  }
}
