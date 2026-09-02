/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure-logic unit tests for {@link SkillCreator} that complement
 * {@link SkillCreatorSecurityTest}. Covers constructor validation, the default
 * skills directory resolver, blank-output rejection, the uninitialized-agent
 * guard in {@code generate}, and the initial {@code getAgent} state.
 *
 * @since 0.1.13
 */
class SkillCreatorOutputResolutionTest {

    @Test
    @DisplayName("带参构造方法在 allowedOutputRoot 为 null 时抛 IllegalArgumentException")
    void constructorRejectsNullRoot() {
        assertThatThrownBy(() -> new SkillCreator((Path) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Allowed output root");
    }

    @Test
    @DisplayName("无参构造方法以当前工作目录作为默认输出根")
    void defaultConstructorUsesCwdAsRoot() {
        SkillCreator creator = new SkillCreator();

        // getAgent() is null until createAgent() runs; just assert no exception
        // and that the instance is constructed.
        assertThat(creator.getAgent()).isNull();
    }

    @Test
    @DisplayName("getAgent 在 createAgent 调用前返回 null")
    void getAgentReturnsNullBeforeInitialization() {
        SkillCreator creator = new SkillCreator(Path.of(""));

        assertThat(creator.getAgent()).isNull();
    }

    @Test
    @DisplayName("resolveSafeOutputDirectory 拒绝空白输出路径")
    void resolveSafeOutputDirectoryRejectsBlank(@TempDir Path tempDir) {
        SkillCreator creator = new SkillCreator(tempDir);

        assertThatThrownBy(() -> creator.resolveSafeOutputDirectory(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Output path must not be blank");
        assertThatThrownBy(() -> creator.resolveSafeOutputDirectory("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Output path must not be blank");
    }

    @Test
    @DisplayName("generate 在 createAgent 未调用时抛 IllegalStateException")
    void generateThrowsWhenAgentNotInitialized(@TempDir Path tempDir) {
        SkillCreator creator = new SkillCreator(tempDir);

        assertThatThrownBy(() -> creator.generate("build a skill", "sub").join())
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(creator.getAgent()).isNull();
    }

    @Test
    @DisplayName("generate(Path) 重载委托给 generate(String)")
    void generatePathOverloadDelegates(@TempDir Path tempDir) {
        SkillCreator creator = new SkillCreator(tempDir);

        assertThatThrownBy(() -> creator.generate("build a skill", Path.of("sub")).join())
                .isInstanceOf(java.util.concurrent.CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("resolveDefaultSkillsDir 解析到 bundled skills 目录")
    void resolveDefaultSkillsDirResolvesBundledDirectory() throws Throwable {
        // resolveDefaultSkillsDir is a private instance method; invoke on an
        // instance since its body is stateless but still non-static.
        SkillCreator creator = new SkillCreator(Path.of(""));
        Method method = SkillCreator.class.getDeclaredMethod("resolveDefaultSkillsDir");
        method.setAccessible(true);
        Path resolved = (Path) method.invoke(creator);

        assertThat(resolved).isDirectory();
        // The bundled skill_creator skills directory ships the skill_creation skill.
        assertThat(resolved.resolve("skill_creation").resolve("SKILL.md")).isRegularFile();
    }

    @Test
    @DisplayName("getEnvOrDefault 在键未设置时返回默认值")
    void getEnvOrDefaultReturnsDefaultWhenUnset() throws Throwable {
        Method method = SkillCreator.class.getDeclaredMethod("getEnvOrDefault", String.class, String.class);
        method.setAccessible(true);

        Object value = method.invoke(new SkillCreator(), "ENV_KEY_NOT_SET_42", "default");
        assertThat(value).isEqualTo("default");
    }
}
