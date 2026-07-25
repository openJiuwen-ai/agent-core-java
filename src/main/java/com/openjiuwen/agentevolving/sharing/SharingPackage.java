/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import java.util.List;

/**
 * Public sharing package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.sharing} in
 * {@code openjiuwen/agent_evolving/sharing/__init__.py}.</p>
 */
public final class SharingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/sharing/__init__.py";
    public static final String DESCRIPTION = "Experience sharing module.";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SharingBackend",
            "LocalFileBackend",
            "KeywordExtractor",
            "QUERY_KEYWORDS_LLM_POLICY",
            "ShareStager",
            "ExperienceSharer",
            "SkillSharingContextProvider",
            "ensure_skill_id_in_content",
            "pack_skill_directory",
            "read_skill_id_from_content",
            "unpack_skill_package",
            "StagingResult",
            "QueryKeywords",
            "SharedExperience",
            "SharedSkillBundle",
            "SharingMeta",
            "SkillPackageMeta",
            "SkillSearchResult",
            "UploadResult"
    );

    private SharingPackage() {
    }
}
