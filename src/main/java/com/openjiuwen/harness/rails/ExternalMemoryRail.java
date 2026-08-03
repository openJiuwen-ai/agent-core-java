/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Legacy flat-package facade; prefer {@code com.openjiuwen.harness.rails.memory.ExternalMemoryRail}.
 *
 * @since 1.0
 * @deprecated Use {@code com.openjiuwen.harness.rails.memory.ExternalMemoryRail} instead.
 */
@Deprecated
public class ExternalMemoryRail extends com.openjiuwen.harness.rails.memory.ExternalMemoryRail {

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail() {
    super((com.openjiuwen.core.memory.external.MemoryProvider) null);
  }

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail(com.openjiuwen.core.memory.external.MemoryProvider provider) {
    super(provider);
  }

  /** Auto-generated for codecheck compliance. */
  public ExternalMemoryRail(
      com.openjiuwen.core.memory.external.MemoryProvider provider,
      String userId, String scopeId, String sessionId) {
    super(provider, userId, scopeId, sessionId);
  }
}
