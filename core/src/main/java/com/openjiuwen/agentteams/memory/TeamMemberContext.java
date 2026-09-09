/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

/**
 * Stable team member identity passed to an optional team memory integration.
 *
 * @param memberName local member name
 * @param teamName team identifier
 * @param lifecycle configured team lifecycle
 * @param language configured team language
 * @since 0.1.7
 */
public record TeamMemberContext(String memberName, String teamName, String lifecycle, String language) {
}
