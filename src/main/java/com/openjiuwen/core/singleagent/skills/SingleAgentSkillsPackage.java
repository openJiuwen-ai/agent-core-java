/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package facade for single-agent skill exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent.skills} module in
 * {@code openjiuwen/core/single_agent/skills/__init__.py}.</p>
 */
public final class SingleAgentSkillsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/single_agent/skills/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "SkillUtil",
            "SkillManager",
            "GitHubTree",
            "RemoteSkillUtil"
    );

    private static final Map<String, String> JAVA_REFERENCES = Map.of(
            "SkillUtil", SkillUtil.class.getName(),
            "SkillManager", SkillManager.class.getName(),
            "GitHubTree", GitHubTree.class.getName(),
            "RemoteSkillUtil", RemoteSkillUtil.class.getName()
    );

    private SingleAgentSkillsPackage() {
    }

    public static boolean exports(String symbol) {
        return EXPORTED_SYMBOLS.contains(symbol);
    }

    public static Optional<String> javaReference(String symbol) {
        return Optional.ofNullable(JAVA_REFERENCES.get(symbol));
    }
}
