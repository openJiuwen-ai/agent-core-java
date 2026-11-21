/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Remote skill target roots must follow the tenant bound at execution time instead of being cached
 * on the rail, which is shared across tenants.
 */
class SkillUseRailTenantTargetTest {

    @TempDir
    Path baseDir;

    @BeforeEach
    void resetTenant() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void cleanupTenant() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void remoteTargetRoot_isResolvedPerTenantWithoutCaching() throws Exception {
        Path workspace = Files.createDirectories(baseDir.resolve("workspace"));
        DeepAgent agent = newAgent(workspace);
        RecordingRail rail = new RecordingRail();
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("tenant_b").build();

        TenantContextHolder.setCurrentTenant(tenantA);
        rail.init(agent);
        rail.uninit(agent);

        TenantContextHolder.setCurrentTenant(tenantB);
        rail.init(agent);
        rail.uninit(agent);

        try {
            assertThat(rail.targets).hasSize(2);
            assertThat(rail.targets.get(0))
                    .isEqualTo(agent.resolveWorkspaceRootForTenant(tenantA).resolve("skills"));
            assertThat(rail.targets.get(1))
                    .isEqualTo(agent.resolveWorkspaceRootForTenant(tenantB).resolve("skills"));
            assertThat(rail.targets.get(0)).isNotEqualTo(rail.targets.get(1));
            assertThat(rail.getSkillDirs()).isEmpty();
        } finally {
            agent.close();
        }
    }

    private static DeepAgent newAgent(Path workspace) {
        AgentCard card = AgentCard.builder()
                .id("skill_tenant_target_agent")
                .name("skill_tenant_target_agent")
                .description("test")
                .build();
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(workspace.toString())
                .tenantDataRoot(workspace.toString())
                .enableTenantIsolation(true)
                .language("cn")
                .build();
        return new DeepAgent(card, config, new Workspace(workspace.toString(), "cn"));
    }

    private static final class RecordingRail extends SkillUseRail {
        private final List<Path> targets = new ArrayList<>();

        private RecordingRail() {
            super(List.of(), "all", List.of(), List.of(),
                    List.of(new RemoteSkillSource("owner", "repo", null, null, null)), true, false);
        }

        @Override
        protected void uploadRemoteSkill(DeepAgent deepAgent, RemoteSkillSource source, Path targetRoot) {
            targets.add(targetRoot);
        }
    }
}
