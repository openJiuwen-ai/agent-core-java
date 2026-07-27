/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;

/**
 * Public interface McpResourceService used by the Java parity implementation.
 *
 * @since 1.0
 */
public interface McpResourceService {
    List<?> listResources(String serverId) throws Exception;

    List<?> readResource(String serverId, String uri) throws Exception;
}
