package com.openjiuwen.harness.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("HarnessCli Tenant Tests")
class HarnessCliTenantTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Nested
    @DisplayName("CLIOptions tenantId field")
    class CLIOptionsTenantId {
        @Test
        @DisplayName("CLIOptions builder sets tenantId")
        void testBuilderSetsTenantId() {
            CLIOptions opts = CLIOptions.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .tenantId("cli_tenant")
                    .build();
            assertThat(opts.getTenantId()).isEqualTo("cli_tenant");
        }

        @Test
        @DisplayName("CLIOptions tenantId defaults to null")
        void testTenantIdDefaultNull() {
            CLIOptions opts = CLIOptions.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .build();
            assertThat(opts.getTenantId()).isNull();
        }
    }

    @Nested
    @DisplayName("HarnessCli TenantContext creation")
    class HarnessCliTenantContextCreation {
        @Test
        @DisplayName("buildTenantContext creates TenantContext from CLIOptions tenantId")
        void testBuildTenantContextFromOptions() {
            CLIOptions opts = CLIOptions.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .tenantId("cli_tenant_ctx")
                    .build();
            TenantContext ctx = HarnessCli.buildTenantContext(opts);
            assertThat(ctx).isNotNull();
            assertThat(ctx.getTenantId()).isEqualTo("cli_tenant_ctx");
            assertThat(ctx.isTenantAware()).isTrue();
        }

        @Test
        @DisplayName("buildTenantContext returns null when tenantId is null")
        void testBuildTenantContextNullId() {
            CLIOptions opts = CLIOptions.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .build();
            TenantContext ctx = HarnessCli.buildTenantContext(opts);
            assertThat(ctx).isNull();
        }

        @Test
        @DisplayName("buildTenantContext returns null when tenantId is empty")
        void testBuildTenantContextEmptyId() {
            CLIOptions opts = CLIOptions.builder()
                    .provider("openai")
                    .model("gpt-4")
                    .tenantId("")
                    .build();
            TenantContext ctx = HarnessCli.buildTenantContext(opts);
            assertThat(ctx).isNull();
        }

        @Test
        @DisplayName("buildTenantContext returns null when opts is null")
        void testBuildTenantContextNullOpts() {
            TenantContext ctx = HarnessCli.buildTenantContext(null);
            assertThat(ctx).isNull();
        }
    }
}
