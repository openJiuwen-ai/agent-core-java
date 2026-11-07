/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Host port for designer sessions.
 *
 * @since 2026-11-06
 */
public interface DesignerSessionPort {

    /**
     * Create a design session.
     *
     * @param request creation request
     * @return created session
     */
    DesignerSessionInfo createSession(DesignerSessionCreate request);

    /**
     * Find one session.
     *
     * @param sessionId session id
     * @return session when present
     */
    Optional<DesignerSessionInfo> findSession(String sessionId);

    /**
     * List sessions in a project.
     *
     * @param projectId project id
     * @return sessions, never null
     */
    List<DesignerSessionInfo> listSessions(String projectId);

    /**
     * Update mutable session fields.
     *
     * @param sessionId session id
     * @param patch fields to update
     */
    void updateSession(String sessionId, Map<String, Object> patch);

    /**
     * Delete a session and its runtime data.
     *
     * @param sessionId session id
     */
    void deleteSession(String sessionId);
}
