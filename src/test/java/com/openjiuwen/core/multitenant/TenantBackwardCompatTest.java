package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class TenantBackwardCompatTest {

    @BeforeEach
    void clearAll() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    void testTenantContext_nullId_notTenantAware() {
        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        assertThat(ctx.isTenantAware()).isFalse();
    }

    @Test
    void testTenantContextHolder_defaultNull() {
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    void testTenantKVStoreKeyResolver_noTenant_originalKey() {
        assertThat(TenantKVStoreKeyResolver.resolveKey("original_key")).isEqualTo("original_key");
    }

    @Test
    void testTenantKVStoreKeyResolver_noTenant_originalPrefix() {
        assertThat(TenantKVStoreKeyResolver.resolvePrefix("original_prefix")).isEqualTo("original_prefix");
    }

    @Test
    void testCheckpointer_buildKeyWithTenant_noTenant_originalFormat() {
        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        String key = Checkpointer.buildKeyWithTenant(ctx, "session-1", "agent", "id", "blobs");
        assertThat(key).isEqualTo("session-1:agent:id:blobs");
    }

    @Test
    void testCwdContext_noTenantRoot_allowsAllPaths() {
        assertThat(CwdContext.isWithinTenantRoot(Path.of("/any/path"))).isTrue();
    }

    @Test
    void testCwdContext_defaultTenantRootIsNull() {
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    void testTenantWorkspaceResolver_noTenant_returnsBasePath() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver("/data/workspace");
        TenantContext noTenant = TenantContext.builder().tenantId(null).build();
        assertThat(resolver.resolveWorkspaceRoot(noTenant)).isEqualTo(Path.of("/data/workspace").toAbsolutePath().normalize());
    }

    @Test
    void testTenantWorkspaceResolver_noTenant_skillRootReturnsSkillsPath() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver("/data/workspace");
        TenantContext noTenant = TenantContext.builder().tenantId(null).build();
        Path skillRoot = resolver.resolveSkillRoot(noTenant);
        assertThat(skillRoot).isNotNull();
        assertThat(skillRoot.toString()).contains("skills");
    }

    @Test
    void testTenantWorkspaceResolver_noTenant_isPathWithinTenantAlwaysTrue() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver("/data/workspace");
        TenantContext noTenant = TenantContext.builder().tenantId(null).build();
        assertThat(resolver.isPathWithinTenant(Path.of("/any/path"), noTenant)).isTrue();
    }

    @Test
    void testTenantContext_safeTenantId_preservesValidChars() {
        TenantContext ctx = TenantContext.builder().tenantId("abc-123_def").build();
        assertThat(ctx.safeTenantId()).isEqualTo("abc-123_def");
    }
}
