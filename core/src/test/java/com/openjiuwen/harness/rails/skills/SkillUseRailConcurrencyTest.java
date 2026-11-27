/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.tools.skills.SkillDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stress test for the shared-rail concurrency defect seen in perf-soak as sporadic {@code
 * skill_tool} failures with outcome {@code business_error} ("Skill not found").
 *
 * <p>A singleton agent shares one SkillUseRail. {@link SkillUseRail#reloadSkills()} runs on every
 * beforeInvoke / beforeModelCall callback and repopulates the shared skills list via a
 * non-atomic clear-then-addAll while other sessions' skill_tool lookups copy the same list through
 * {@link SkillUseRail#getSkillsMeta()}. This test reproduces the torn read: a lookup must never
 * observe an empty or partially filled list, and the reload path must not corrupt its caches when
 * several sessions reload concurrently.
 */
class SkillUseRailConcurrencyTest {

    private static final int SKILL_COUNT = 5;
    private static final int WRITER_THREADS = 4;
    private static final int READER_THREADS = 8;
    private static final long STRESS_DURATION_NANOS = 3_000_000_000L;

    @TempDir
    private Path tempDir;

    @Test
    void concurrentReloadNeverExposesEmptyOrPartialSkillListToLookups() throws Exception {
        Set<String> expectedNames = new HashSet<>();
        for (int index = 0; index < SKILL_COUNT; index++) {
            String name = "concurrency_skill_" + index;
            writeSkill(name);
            expectedNames.add(name);
        }
        SkillUseRail rail = new SkillUseRail(tempDir.toString());
        rail.reloadSkills();
        assertThat(rail.getSkillsMeta())
                .as("fixture sanity: all written skills must be discovered")
                .hasSize(SKILL_COUNT);

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong tornReads = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        CountDownLatch finished = new CountDownLatch(WRITER_THREADS + READER_THREADS);

        for (int index = 0; index < WRITER_THREADS; index++) {
            Thread writer =
                    new Thread(
                            () -> {
                                try {
                                    while (!stop.get()) {
                                        rail.reloadSkills();
                                    }
                                } catch (RuntimeException exception) {
                                    errors.incrementAndGet();
                                } finally {
                                    finished.countDown();
                                }
                            },
                            "reload-writer-" + index);
            writer.start();
        }
        for (int index = 0; index < READER_THREADS; index++) {
            Thread reader =
                    new Thread(
                            () -> {
                                try {
                                    while (!stop.get()) {
                                        List<SkillDescriptor> snapshot = rail.getSkillsMeta();
                                        if (snapshot.size() != SKILL_COUNT) {
                                            tornReads.incrementAndGet();
                                            continue;
                                        }
                                        for (SkillDescriptor skill : snapshot) {
                                            if (skill == null
                                                    || !expectedNames.contains(skill.name())) {
                                                tornReads.incrementAndGet();
                                            }
                                        }
                                    }
                                } catch (RuntimeException exception) {
                                    errors.incrementAndGet();
                                } finally {
                                    finished.countDown();
                                }
                            },
                            "skill-lookup-reader-" + index);
            reader.start();
        }

        long deadline = System.nanoTime() + STRESS_DURATION_NANOS;
        while (System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        stop.set(true);
        finished.await();

        assertThat(errors.get())
                .as("concurrent reloads must not throw (skillCache / skillUpdatedAt corruption)")
                .isZero();
        assertThat(tornReads.get())
                .as(
                        "skill_tool lookups must never observe an empty or partial skill list"
                                + " while a reload is in flight (production symptom: Skill not"
                                + " found business_error)")
                .isZero();
    }

    private void writeSkill(String name) throws IOException {
        Path skillDir = tempDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: concurrency fixture\n---\n\nbody\n",
                StandardCharsets.UTF_8);
    }
}
