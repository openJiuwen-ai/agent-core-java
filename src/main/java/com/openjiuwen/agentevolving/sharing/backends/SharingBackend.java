/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing.backends;

import com.openjiuwen.agentevolving.sharing.QueryKeywords;
import com.openjiuwen.agentevolving.sharing.SharedSkillBundle;
import com.openjiuwen.agentevolving.sharing.SkillPackageMeta;
import com.openjiuwen.agentevolving.sharing.SkillSearchResult;
import com.openjiuwen.agentevolving.sharing.UploadResult;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code SharingBackend} in
 * {@code openjiuwen/agent_evolving/sharing/backends/base.py}.
 */
public interface SharingBackend {

    CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle);

    default CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query) {
        return downloadBundles(skillId, query, 3);
    }

    CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK);

    CompletionStage<Boolean> hasSkillPackage(String skillId);

    CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta);

    CompletionStage<byte[]> downloadSkillPackage(String skillId);

    CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId);

    default CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query) {
        return searchSkills(query, 5);
    }

    CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK);
}
