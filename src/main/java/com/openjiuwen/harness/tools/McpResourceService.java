/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.List;

/**
 * Public interface McpResourceService used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public interface McpResourceService {
    /**
     * listResources.
     * 
     * @param serverId serverId
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    List<?> listResources(String serverId) throws Exception;

    /**
     * readResource.
     * 
     * @param serverId serverId
     * @param uri uri
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    List<?> readResource(String serverId, String uri) throws Exception;
}
