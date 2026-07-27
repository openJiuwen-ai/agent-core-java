/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the sharing package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.sharing} in
 * {@code openjiuwen/agent_evolving/sharing/__init__.py}.</p>
 */
class SharingPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/sharing/__init__.py", SharingPackage.PYTHON_MODULE);
        assertEquals("Experience sharing module.", SharingPackage.DESCRIPTION);
        assertEquals(List.of(
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
        ), SharingPackage.EXPORTED_SYMBOLS);
    }
}
