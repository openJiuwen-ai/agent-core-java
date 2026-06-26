/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

/**
 * Smoke checks for the isolated T01012 candidate.
 */
public final class LongTermMemoryCandidateSmokeTest {
    private LongTermMemoryCandidateSmokeTest() {
    }

    public static void main(String[] args) {
        LongTermMemory.resetInstance();
        LongTermMemory first = LongTermMemory.getInstance();
        LongTermMemory second = LongTermMemory.getInstance();
        require(first == second, "LongTermMemory singleton should reuse instance");

        MemInfo info = MemInfo.builder()
                .memId("m1")
                .content("hello")
                .type(MemoryType.USER_PROFILE)
                .build();
        require("m1".equals(info.getMemId()), "MemInfo.memId");
        require("hello".equals(info.getContent()), "MemInfo.content");
        require(info.getType() == MemoryType.USER_PROFILE, "MemInfo.type");

        MemResult result = MemResult.builder().memInfo(info).score(0.75d).build();
        require(result.getMemInfo() == info, "MemResult.memInfo");
        require(Double.compare(result.getScore(), 0.75d) == 0, "MemResult.score");

        AddMemResult addResult = AddMemResult.builder().build();
        require(addResult.getVariables().isEmpty(), "AddMemResult.variables");
        require(addResult.getUserProfile().isEmpty(), "AddMemResult.userProfile");
        require(addResult.getSemanticMemory().isEmpty(), "AddMemResult.semanticMemory");
        require(addResult.getEpisodicMemory().isEmpty(), "AddMemResult.episodicMemory");
        require(addResult.getSummary().isEmpty(), "AddMemResult.summary");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
