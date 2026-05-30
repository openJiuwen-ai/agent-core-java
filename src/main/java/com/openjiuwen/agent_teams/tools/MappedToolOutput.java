/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

/**
 * Tool output whose string form is the LLM-facing mapped content.
 *
 * <p>Mirrors Python's {@code MappedToolOutput} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.</p>
 */
public class MappedToolOutput extends ToolOutput {

    private final String mappedContent;

    public MappedToolOutput(boolean success, Object data, String error, String mappedContent) {
        super(success, data, error);
        this.mappedContent = mappedContent;
    }

    public static MappedToolOutput fromOutput(ToolOutput output, String mappedContent) {
        return new MappedToolOutput(output.isSuccess(), output.getData(), output.getError(), mappedContent);
    }

    public String getMappedContent() {
        return mappedContent;
    }

    @Override
    public String toString() {
        return mappedContent;
    }
}
