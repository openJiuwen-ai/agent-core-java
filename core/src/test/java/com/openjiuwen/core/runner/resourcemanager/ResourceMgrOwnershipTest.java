/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * shared-registration ownership semantics on the
 * process-global ResourceMgr (equivalent reuse with owner-claim joining,
 * visible definition conflicts, per-owner release).
 *
 * <p>The red-subset tests restrict themselves to
 * baseline-compatible APIs (two-arg addTool/addSysOperation) and reach
 * the new dimensions (the idToOwners ownership table, the per-instance
 * ownerToken) through reflection, so the same source both documents and
 * detects the baseline defects. The remaining cases
 * exercise the new owner-aware overloads and ship with the
 * implementation.</p>
 *
 * <p>The harness-dependent faces of the same contract (DeepAgent /
 * HarnessFactory landing points) live in the harness
 * test tree: {@code com.openjiuwen.harness.deep_agent
 * .DeepAgentRegistrationOwnershipTest}.</p>
 *
 * @since 0.1.16
 */
public class ResourceMgrOwnershipTest {
    private final List<String> trackedToolIds = new ArrayList<>();
    private final List<String> trackedSysOpIds = new ArrayList<>();

    /**
     * Force-removes every entry the test registered. The public remove
     * overloads are the declared explicit-management escape hatch and
     * ignore ownership by design, which keeps tests isolated regardless
     * of how many owners an entry accumulated.
     */
    @AfterEach
    void cleanupGlobalEntries() {
        for (String toolId : trackedToolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
        for (String sysOpId : trackedSysOpIds) {
            Runner.resourceMgr().removeSysOperation(sysOpId, null, TagMatchStrategy.ALL, true);
        }
    }

    /**
     * an equivalent re-registration from another owner is
     * reused (the existing entry and its first registrant's instance
     * stay) and both owners are recorded on the shared entry.
     */
    @Test
    void equivalentRegistrationIsReusedWithDualOwnership() {
        String toolId = uniqueId("ut09-tool");
        Tool first = tool(toolCard(toolId, "ut09", "shared ability"));
        Tool second = tool(toolCard(toolId, "ut09", "shared ability"));
        trackedToolIds.add(toolId);

        Result<ToolCard> firstResult = Runner.resourceMgr().addTool(first, "ut09-owner-a");
        Result<ToolCard> secondResult = Runner.resourceMgr().addTool(second, "ut09-owner-b");

        assertThat(firstResult.isOk()).as("first registration succeeds").isTrue();
        assertThat(secondResult.isOk()).as("equivalent re-registration is reused").isTrue();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("entry stays resolvable").isNotNull();
        assertThat(registeredToolInstance(toolId)).as("first registrant's instance is kept").isSameAs(first);
        assertThat(ownersOf(toolId)).as("both owners claimed the shared entry")
                .containsExactlyInAnyOrder("ut09-owner-a", "ut09-owner-b");
    }

    /**
     * a same-id registration with a different definition fails
     * visibly (RESOURCE_ADD_ERROR distinguishing a definition conflict),
     * the first entry stays intact, and the conflicting owner is not
     * recorded.
     */
    @Test
    void conflictingDefinitionFailsVisiblyAndKeepsFirstEntry() {
        String toolId = uniqueId("ut10-tool");
        Tool first = tool(toolCard(toolId, "ut10", "original ability"));
        Tool conflicting = tool(toolCard(toolId, "ut10", "conflicting ability"));
        trackedToolIds.add(toolId);

        Runner.resourceMgr().addTool(first, "ut10-owner-a");
        Result<ToolCard> conflictResult = Runner.resourceMgr().addTool(conflicting, "ut10-owner-b");

        assertThat(conflictResult.isError()).as("conflicting registration fails").isTrue();
        assertThat(conflictResult.getError()).as("failure distinguishes a definition conflict")
                .hasMessageContaining("definition conflict");
        assertThat(Runner.resourceMgr().getTool(toolId)).as("first entry survives the conflict").isNotNull();
        assertThat(registeredToolInstance(toolId)).as("first registrant's instance is untouched").isSameAs(first);
        assertThat(ownersOf(toolId)).as("conflicting owner is not registered").containsExactly("ut10-owner-a");
    }

    /**
     * re-registering the same id with an equivalent definition
     * under the same owner is an idempotent no-op: success without error,
     * unchanged registry instance, unchanged owner set.
     */
    @Test
    void sameOwnerReRegistrationIsIdempotent() {
        String toolId = uniqueId("ut13-tool");
        Tool first = tool(toolCard(toolId, "ut13", "stable ability"));
        Tool again = tool(toolCard(toolId, "ut13", "stable ability"));
        trackedToolIds.add(toolId);

        Result<ToolCard> firstResult = Runner.resourceMgr().addTool(first, "ut13-owner");
        Result<ToolCard> repeatResult = Runner.resourceMgr().addTool(again, "ut13-owner");

        assertThat(firstResult.isOk()).isTrue();
        assertThat(repeatResult.isOk()).as("same-owner re-registration succeeds idempotently").isTrue();
        assertThat(registeredToolInstance(toolId)).as("registry instance unchanged").isSameAs(first);
        assertThat(ownersOf(toolId)).as("owner set unchanged").containsExactly("ut13-owner");
    }

    /**
     * the ToolCard value fields (name, description,
     * inputParams, properties; id keys the entry) each drive the
     * equivalence decision, while the SysOperationCard resource-bound
     * workDir deliberately does not — a different workDir still reuses
     * the sys_operation entry under the declared transitional semantics
     * (the first registrant's binding stays effective).
     */
    @Test
    void toolCardFieldsDriveEquivalenceAndSysOpWorkConfigIsExcluded() {
        String toolId = uniqueId("ut16-tool");
        Tool original = tool(toolCard(toolId, "ut16", "original"));
        assertThat(Runner.resourceMgr().addTool(original, "ut16-owner-a").isOk()).isTrue();
        trackedToolIds.add(toolId);

        ToolCard renamed = toolCard(toolId, "ut16-renamed", "original");
        assertRegistrationConflicts(renamed, "name");
        ToolCard reDescribed = toolCard(toolId, "ut16", "changed description");
        assertRegistrationConflicts(reDescribed, "description");
        ToolCard changedParams = toolCard(toolId, "ut16", "original");
        changedParams.getInputParams().put("extra", "value");
        assertRegistrationConflicts(changedParams, "inputParams");
        ToolCard changedProperties = toolCard(toolId, "ut16", "original");
        changedProperties.getProperties().put("extra", "value");
        assertRegistrationConflicts(changedProperties, "properties");

        String otherId = uniqueId("ut16-tool-other");
        trackedToolIds.add(otherId);
        Result<ToolCard> independent = Runner.resourceMgr().addTool(tool(toolCard(otherId, "ut16", "original")),
                "ut16-owner-a");
        assertThat(independent.isOk()).as("a different id registers independently").isTrue();

        String sysOpId = uniqueId("ut16-sysop");
        trackedSysOpIds.add(sysOpId);
        Result<SysOperationCard> firstSysOp =
                Runner.resourceMgr().addSysOperation(sysOpCard(sysOpId, "first-workdir"), "ut16-owner-a");
        Result<SysOperationCard> secondSysOp =
                Runner.resourceMgr().addSysOperation(sysOpCard(sysOpId, "second-workdir"), "ut16-owner-b");
        assertThat(firstSysOp.isOk()).isTrue();
        assertThat(secondSysOp.isOk()).as("workDir difference still reuses the sys_operation").isTrue();
        assertThat(ownersOf(sysOpId)).as("both owners claimed the shared sys_operation")
                .containsExactlyInAnyOrder("ut16-owner-a", "ut16-owner-b");
    }

    /**
     * releasing one owner's claim keeps the shared entry alive
     * for the remaining owner — resolution and the first registrant's
     * instance stay intact, and the entry's owner set shrinks to the
     * survivor.
     */
    @Test
    void singleOwnerReleaseKeepsEntryForSurvivingOwners() {
        String toolId = uniqueId("ut11-tool");
        Tool first = tool(toolCard(toolId, "ut11", "shared ability"));
        Tool second = tool(toolCard(toolId, "ut11", "shared ability"));
        trackedToolIds.add(toolId);

        assertThat(Runner.resourceMgr().addTool(first, "ut11-tag", "ut11-owner-a").isOk()).isTrue();
        assertThat(Runner.resourceMgr().addTool(second, "ut11-tag", "ut11-owner-b").isOk()).isTrue();

        boolean isEntryRemoved = Runner.resourceMgr().removeToolOwnedBy(toolId, "ut11-owner-a");

        assertThat(isEntryRemoved).as("entry survives while another owner holds a claim").isFalse();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("surviving owner still resolves").isNotNull();
        assertThat(registeredToolInstance(toolId)).as("first registrant's instance kept").isSameAs(first);
        assertThat(ownersOf(toolId)).as("only the surviving owner remains").containsExactly("ut11-owner-b");
    }

    /**
     * releasing the last owner removes the entry completely and
     * the freed id re-registers normally (no dead entry blocking it).
     */
    @Test
    void lastOwnerReleaseRemovesEntryAndAllowsReRegistration() {
        String toolId = uniqueId("ut12-tool");
        Tool first = tool(toolCard(toolId, "ut12", "shared ability"));
        Tool second = tool(toolCard(toolId, "ut12", "shared ability"));
        trackedToolIds.add(toolId);

        assertThat(Runner.resourceMgr().addTool(first, "ut12-tag", "ut12-owner-a").isOk()).isTrue();
        assertThat(Runner.resourceMgr().addTool(second, "ut12-tag", "ut12-owner-b").isOk()).isTrue();

        boolean isEntryRemovedByFirstOwner = Runner.resourceMgr().removeToolOwnedBy(toolId, "ut12-owner-a");
        boolean isEntryRemovedByLastOwner = Runner.resourceMgr().removeToolOwnedBy(toolId, "ut12-owner-b");

        assertThat(isEntryRemovedByFirstOwner).as("non-final release keeps the entry").isFalse();
        assertThat(isEntryRemovedByLastOwner).as("last owner release removes the entry").isTrue();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("entry fully gone").isNull();
        assertThat(ownersOf(toolId)).as("no ownership residue").isEmpty();

        Tool replacement = tool(toolCard(toolId, "ut12", "shared ability"));
        Result<ToolCard> reRegistered = Runner.resourceMgr().addTool(replacement, "ut12-tag", "ut12-owner-c");
        assertThat(reRegistered.isOk()).as("the freed id re-registers without a dead entry").isTrue();
        assertThat(registeredToolInstance(toolId)).as("re-registration installs the new instance")
                .isSameAs(replacement);
    }

    /**
     * 16 threads × 2000 rounds of interleaved equivalent
     * registration, resolution, and per-owner release against one shared
     * entry held by two anchor owners (the owner count cycles as the four
     * worker tokens join and exit). Acceptance: zero exceptions (no
     * CME), zero resolution loss while the anchors hold, and the stress
     * tail — after the final release the entry is fully gone (no
     * dead-tag residue) and the freed id re-registers normally.
     *
     * @throws Exception if any stress worker fails or exceeds its wait bound
     */
    @Test
    void concurrentClaimReleaseStressKeepsSharedEntryConsistent() throws Exception {
        String toolId = uniqueId("ut15-tool");
        ToolCard sharedCard = toolCard(toolId, "ut15", "shared ability");
        trackedToolIds.add(toolId);
        assertThat(Runner.resourceMgr().addTool(tool(sharedCard), "ut15-tag", "ut15-anchor-a").isOk()).isTrue();
        assertThat(Runner.resourceMgr().addTool(tool(sharedCard), "ut15-tag", "ut15-anchor-b").isOk()).isTrue();

        int threadCount = 16;
        int rounds = 2000;
        String[] workerTokens = {"ut15-owner-0", "ut15-owner-1", "ut15-owner-2", "ut15-owner-3"};
        AtomicInteger failures = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = newPool(threadCount, "ut15-stress");
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int index = 0; index < threadCount; index++) {
                workers.add(pool.submit(() -> {
                    stressWorker(rounds, workerTokens, sharedCard, barrier, failures);
                    return null;
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(120L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures.get()).as("stress produced zero failures and zero resolution losses").isZero();
        assertThat(ownersOf(toolId)).as("only the anchors remain after the stress")
                .containsExactlyInAnyOrder("ut15-anchor-a", "ut15-anchor-b");

        boolean isEntryRemovedByFirstAnchor = Runner.resourceMgr().removeToolOwnedBy(toolId, "ut15-anchor-a");
        boolean isEntryRemovedByLastAnchor = Runner.resourceMgr().removeToolOwnedBy(toolId, "ut15-anchor-b");
        assertThat(isEntryRemovedByFirstAnchor).isFalse();
        assertThat(isEntryRemovedByLastAnchor).as("final release removes the entry").isTrue();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("entry fully gone after the last release").isNull();
        assertThat(ownersOf(toolId)).as("no ownership residue").isEmpty();

        Tool replacement = tool(toolCard(toolId, "ut15", "shared ability"));
        Result<ToolCard> reRegistered = Runner.resourceMgr().addTool(replacement, "ut15-tag", "ut15-fresh");
        assertThat(reRegistered.isOk()).as("freed id re-registers without dead-tag blocking").isTrue();
        assertThat(registeredToolInstance(toolId)).isSameAs(replacement);
    }

    /**
     * the hypothetical dead-tag residue error face. The
     * structure write lock removes the interleaving that could produce a
     * tagged-but-unregistered id through public APIs, so the residue is
     * constructed by reflection injection; the documented behavior is
     * that a same-id registration against the residue fails visibly
     * instead of silently binding to a phantom entry. The post-fix
     * acceptance (no residue at all, freed ids re-register normally) is
     * carried by the stress tail.
     */
    @Test
    void deadTagResidueFailsVisiblyInsteadOfSilentBinding() {
        String toolId = uniqueId("ut17-tool");
        trackedToolIds.add(toolId);

        injectDeadTagEntry(toolId, "ut17-dead-tag");

        Result<ToolCard> result =
                Runner.resourceMgr().addTool(tool(toolCard(toolId, "ut17", "ability")), "ut17-owner");

        assertThat(result.isError()).as("registration against a dead-tag residue fails visibly").isTrue();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("no silent binding to a phantom entry").isNull();
    }

    /**
     * Companion: the zero-ownership crossing the anchored stress
     * cannot reach. Threads repeatedly claim and release one shared id
     * with no permanent anchor, so the owner set genuinely empties and
     * re-populates while other threads are mid-flight. A release that
     * observes an emptied set may only remove the entry when no claim
     * landed in between: every successful claim must stay resolvable until
     * its own release. Before the release-path locking fix, a destroying
     * thread's stale empty-set decision wiped entries freshly claimed by
     * a creating thread (observed by the S2 transition stress).
     *
     * @throws Exception if any crossing worker fails or exceeds its wait bound
     */
    @Test
    void claimReleaseCrossingOnEmptiedEntryStaysResolvable() throws Exception {
        String toolId = uniqueId("ut15b-tool");
        ToolCard sharedCard = toolCard(toolId, "ut15b", "shared ability");
        trackedToolIds.add(toolId);

        int threadCount = 8;
        int rounds = 20000;
        AtomicInteger losses = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = newPool(threadCount, "ut15b-cross");
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int index = 0; index < threadCount; index++) {
                String ownerToken = "ut15b-owner-" + index;
                workers.add(pool.submit(() -> {
                    barrier.await();
                    for (int round = 0; round < rounds; round++) {
                        if (!Runner.resourceMgr().addTool(tool(sharedCard), "ut15b-tag", ownerToken).isOk()) {
                            losses.incrementAndGet();
                            return null;
                        }
                        if (Runner.resourceMgr().getTool(toolId) == null) {
                            losses.incrementAndGet();
                            return null;
                        }
                        Runner.resourceMgr().removeToolOwnedBy(toolId, ownerToken);
                    }
                    return null;
                }));
            }
            for (Future<?> worker : workers) {
                worker.get(120L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(losses.get()).as("every claim stays resolvable until its own release").isZero();
        assertThat(Runner.resourceMgr().getTool(toolId)).as("entry fully gone after the last release").isNull();
        assertThat(ownersOf(toolId)).as("no ownership residue").isEmpty();

        Result<ToolCard> reRegistered =
                Runner.resourceMgr().addTool(tool(toolCard(toolId, "ut15b", "shared ability")), "ut15b-fresh");
        assertThat(reRegistered.isOk()).as("freed id re-registers without dead-tag blocking").isTrue();
    }

    /**
     * Batch face of the ownership contract: the owner-aware addTools
     * overload claims every entry under the same owner token, an
     * equivalent batch re-registration from another owner reuses the
     * entries, and the two-arg form degrades the owner to the tag. Pins
     * the addTools-family mirror of the equivalent-re-registration contract.
     */
    @Test
    void batchRegistrationWithOwnerClaimsSharedEntries() {
        String firstId = uniqueId("utb-first");
        String secondId = uniqueId("utb-second");
        String thirdId = uniqueId("utb-third");
        Tool first = tool(toolCard(firstId, "utb", "first ability"));
        Tool second = tool(toolCard(secondId, "utb", "second ability"));
        Tool secondEquivalent = tool(toolCard(secondId, "utb", "second ability"));
        Tool third = tool(toolCard(thirdId, "utb", "third ability"));
        trackedToolIds.add(firstId);
        trackedToolIds.add(secondId);
        trackedToolIds.add(thirdId);

        List<Result<ToolCard>> ownerBatch =
                Runner.resourceMgr().addTools(List.of(first, second), "utb-tag", "utb-owner-a");
        List<Result<ToolCard>> reuseBatch =
                Runner.resourceMgr().addTools(List.of(secondEquivalent, third), "utb-tag", "utb-owner-b");
        assertThat(ownerBatch).extracting(Result::isOk).containsOnly(true);
        assertThat(reuseBatch).extracting(Result::isOk).containsOnly(true);
        assertThat(registeredToolInstance(secondId)).as("first registrant's instance kept").isSameAs(second);
        assertThat(ownersOf(firstId)).containsExactly("utb-owner-a");
        assertThat(ownersOf(secondId)).containsExactlyInAnyOrder("utb-owner-a", "utb-owner-b");

        List<Result<ToolCard>> tagBatch = Runner.resourceMgr()
                .addTools(List.of(tool(toolCard(firstId, "utb", "first ability"))), "utb-tag-c");
        assertThat(tagBatch).extracting(Result::isOk).containsOnly(true);
        assertThat(ownersOf(firstId)).as("two-arg batch degrades the owner to the tag")
                .containsExactlyInAnyOrder("utb-owner-a", "utb-tag-c");
    }

    /**
     * Sys_operation face of the release contract (the semantics through removeSysOperationOwnedBy): releasing one owner
     * keeps the entry and its bound tools for the survivor, the last
     * release removes the entry together with its bound tools in one
     * locked sequence, and releasing an unclaimed id (or a null owner)
     * is a safe no-op. The destroy-chain wiring of this API lands with the harness lifecycle tests.
     */
    @Test
    void sysOperationOwnerReleaseRemovesBoundToolsWithEntry() {
        String sysOpId = uniqueId("uts-sysop");
        trackedSysOpIds.add(sysOpId);

        assertThat(Runner.resourceMgr()
                .addSysOperation(sysOpCard(sysOpId, "first-workdir"), "uts-tag", "uts-owner-a").isOk()).isTrue();
        assertThat(Runner.resourceMgr()
                .addSysOperation(sysOpCard(sysOpId, "second-workdir"), "uts-tag", "uts-owner-b").isOk()).isTrue();
        List<String> boundToolIds = sysOperationToolIds(sysOpId);
        assertThat(boundToolIds).as("sys_operation registered its bound tools").isNotEmpty();

        boolean isEntryRemovedBySurvivorOwner =
                Runner.resourceMgr().removeSysOperationOwnedBy(sysOpId, "uts-owner-a");
        assertThat(isEntryRemovedBySurvivorOwner).as("entry survives while the second owner holds a claim").isFalse();
        assertThat(Runner.resourceMgr().getSysOperation(sysOpId, null, TagMatchStrategy.ALL))
                .as("surviving owner still resolves the sys_operation").isNotNull();
        assertThat(sysOperationToolIds(sysOpId)).as("bound tools kept for the survivor").isEqualTo(boundToolIds);

        boolean isEntryRemovedByLastOwner = Runner.resourceMgr().removeSysOperationOwnedBy(sysOpId, "uts-owner-b");
        assertThat(isEntryRemovedByLastOwner).as("last release removes the sys_operation").isTrue();
        assertThat(Runner.resourceMgr().getSysOperation(sysOpId, null, TagMatchStrategy.ALL))
                .as("entry fully gone").isNull();
        for (String boundToolId : boundToolIds) {
            assertThat(Runner.resourceMgr().getTool(boundToolId)).as("bound tool removed with the entry").isNull();
        }

        boolean isEntryRemovedByUnclaimedOwner =
                Runner.resourceMgr().removeSysOperationOwnedBy(sysOpId, "uts-owner-a");
        assertThat(isEntryRemovedByUnclaimedOwner).as("releasing an unclaimed id is a no-op").isFalse();
        assertThat(Runner.resourceMgr().removeToolOwnedBy(uniqueId("uts-unclaimed"), null))
                .as("null owner release is a no-op").isFalse();
    }

    /**
     * Companion of the bound-tools cascade test: on equivalent sysop reuse
     * the joining owner must also claim the existing bound tools, and the
     * first owner's release must drop its claims everywhere without
     * breaking the survivor's bound tools. Before the fix the bound tools
     * carried only the first registrant's claim, so the first owner's
     * destroy left ghost ownership behind (observed by the S5 cross
     * destroy system-level test).
     */
    @Test
    void sharedSysOpBoundToolsAreClaimedByEveryOwnerAndReleasedFully() {
        String sysOpId = uniqueId("uts2-sysop");
        trackedSysOpIds.add(sysOpId);

        assertThat(Runner.resourceMgr()
                .addSysOperation(sysOpCard(sysOpId, "first-workdir"), "uts2-tag", "uts2-owner-a").isOk()).isTrue();
        assertThat(Runner.resourceMgr()
                .addSysOperation(sysOpCard(sysOpId, "second-workdir"), "uts2-tag", "uts2-owner-b").isOk()).isTrue();
        List<String> boundToolIds = sysOperationToolIds(sysOpId);
        assertThat(boundToolIds).as("sys_operation registered its bound tools").isNotEmpty();
        for (String boundToolId : boundToolIds) {
            assertThat(ownersOf(boundToolId)).as("reusing owner claims the bound tool %s", boundToolId)
                    .containsExactlyInAnyOrder("uts2-owner-a", "uts2-owner-b");
        }

        boolean isEntryRemovedBySurvivorOwner =
                Runner.resourceMgr().removeSysOperationOwnedBy(sysOpId, "uts2-owner-a");
        assertThat(isEntryRemovedBySurvivorOwner).as("entry survives while the second owner holds a claim").isFalse();
        GlobalRegistrySnapshot afterFirstRelease = GlobalRegistrySnapshot.take();
        assertThat(afterFirstRelease.hasOwner("uts2-owner-a"))
                .as("first owner's claims released everywhere (no ghost ownership on bound tools)").isFalse();
        assertThat(afterFirstRelease.hasOwner("uts2-owner-b"))
                .as("surviving owner keeps its claims").isTrue();
        for (String boundToolId : boundToolIds) {
            assertThat(Runner.resourceMgr().getTool(boundToolId))
                    .as("bound tool %s survives the first owner's release", boundToolId).isNotNull();
            assertThat(ownersOf(boundToolId)).containsExactly("uts2-owner-b");
        }

        boolean isEntryRemovedByLastOwner = Runner.resourceMgr().removeSysOperationOwnedBy(sysOpId, "uts2-owner-b");
        assertThat(isEntryRemovedByLastOwner).as("last release removes the sys_operation").isTrue();
        for (String boundToolId : boundToolIds) {
            assertThat(Runner.resourceMgr().getTool(boundToolId))
                    .as("bound tool %s removed once the last owner releases", boundToolId).isNull();
        }
    }

    /**
     * Review finding (refresh vs shared ownership): the refresh variant
     * replaces the resource instance under the id but must not silently
     * wipe the shared entry's owner set — every other owner's claim is
     * re-seeded, so a live owner never loses the entry to a foreign
     * refresh. Baseline red channel: the refresh leaves only the
     * refreshing tag as owner and the joining owner's later release can
     * never see the entry again.
     */
    @Test
    void refreshToolKeepsOtherOwnersClaims() {
        String toolId = uniqueId("ut41-refresh-tool");
        trackedToolIds.add(toolId);
        assertThat(Runner.resourceMgr()
                .addTool(tool(toolCard(toolId, "ut41", "shared")), "ut41-holder").isOk()).isTrue();
        assertThat(Runner.resourceMgr()
                .addTool(tool(toolCard(toolId, "ut41", "shared")), "ut41-joiner-tag", "ut41-joiner").isOk())
                .isTrue();
        assertThat(ownersOf(toolId)).containsExactlyInAnyOrder("ut41-holder", "ut41-joiner");

        Runner.resourceMgr().addTool(tool(toolCard(toolId, "ut41", "refreshed")), "ut41-holder", true);

        assertThat(ownersOf(toolId)).as("refresh preserves the other owners' claims")
                .containsExactlyInAnyOrder("ut41-holder", "ut41-joiner");
        assertThat(Runner.resourceMgr().getTool(toolId)).as("refreshed entry stays resolvable").isNotNull();
    }

    /**
     * Review finding (sys_operation registration window): concurrent
     * equivalent registrations must both claim every bound tool. The
     * entry registration and the bound-tool registration/claim run in
     * one structureLock domain, so a reusing registration can never
     * observe the tool association still empty, claim nothing, and
     * later lose the bound tools when the first registrant releases.
     * Baseline red channel: some rounds leave bound tools claimed by
     * the first owner only.
     *
     * @throws Exception if a racing registration fails or exceeds its wait bound
     */
    @Test
    void concurrentSysOperationRegistrations_bothClaimEveryBoundTool() throws Exception {
        int rounds = 100;
        ExecutorService pool = newPool(2, "ut42-race");
        try {
            for (int round = 0; round < rounds; round++) {
                String sysOpId = uniqueId("ut42-sysop");
                trackedSysOpIds.add(sysOpId);
                SysOperationCard cardA = sysOpCard(sysOpId, "./target/ut42-workdir-a");
                SysOperationCard cardB = sysOpCard(sysOpId, "./target/ut42-workdir-b");
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<?>> futures = List.of(
                        pool.submit(() -> {
                            awaitBarrierThenRegister(barrier, cardA, "ut42-owner-a");
                            return null;
                        }),
                        pool.submit(() -> {
                            awaitBarrierThenRegister(barrier, cardB, "ut42-owner-b");
                            return null;
                        }));
                for (Future<?> future : futures) {
                    future.get(10L, TimeUnit.SECONDS);
                }
                List<String> boundToolIds = sysOperationToolIds(sysOpId);
                assertThat(boundToolIds).as("round %d registered bound tools", round).isNotEmpty();
                for (String boundToolId : boundToolIds) {
                    assertThat(ownersOf(boundToolId))
                            .as("round %d bound tool %s claimed by both racing owners", round, boundToolId)
                            .containsExactlyInAnyOrder("ut42-owner-a", "ut42-owner-b");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void awaitBarrierThenRegister(CyclicBarrier barrier, SysOperationCard card, String owner)
            throws Exception {
        barrier.await(5L, TimeUnit.SECONDS);
        assertThat(Runner.resourceMgr().addSysOperation(card, "ut42-tag", owner).isOk()).isTrue();
    }

    private void stressWorker(int rounds, String[] tokens, ToolCard sharedCard, CyclicBarrier barrier,
            AtomicInteger failures) throws Exception {
        barrier.await();
        for (int round = 0; round < rounds; round++) {
            String token = tokens[round % tokens.length];
            Result<ToolCard> claimed =
                    Runner.resourceMgr().addTool(tool(sharedCard), "ut15-tag", token);
            if (!claimed.isOk()) {
                failures.incrementAndGet();
                return;
            }
            if (Runner.resourceMgr().getTool(sharedCard.getId()) == null) {
                failures.incrementAndGet();
                return;
            }
            Runner.resourceMgr().removeToolOwnedBy(sharedCard.getId(), token);
        }
    }

    private static ExecutorService newPool(int threads, String poolName) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads * 2), runnable -> {
                    Thread thread = new Thread(runnable, poolName + "-" + seq.incrementAndGet());
                    thread.setUncaughtExceptionHandler((failedThread, error) ->
                        Loggers.COMMON.error("Uncaught exception in {}: {}",
                            failedThread.getName(), error.getMessage()));
                    return thread;
                });
    }

    @SuppressWarnings("unchecked")
    private static void injectDeadTagEntry(String resourceId, String tag) {
        TagMgr tagMgr = fieldValue(Runner.resourceMgr(), ResourceMgr.class, "tagMgr");
        ReentrantLock tagLock = fieldValue(tagMgr, TagMgr.class, "lock");
        tagLock.lock();
        try {
            Map<String, Set<String>> resourceTags = fieldValue(tagMgr, TagMgr.class, "resourceTags");
            resourceTags.put(resourceId, new HashSet<>(Set.of(tag)));
            Map<String, Set<String>> tagToResource = fieldValue(tagMgr, TagMgr.class, "tagToResource");
            tagToResource.computeIfAbsent(tag, key -> new HashSet<>()).add(resourceId);
        } finally {
            tagLock.unlock();
        }
    }

    private void assertRegistrationConflicts(ToolCard conflictingCard, String field) {
        Result<ToolCard> result = Runner.resourceMgr().addTool(tool(conflictingCard), "ut16-owner-b");
        assertThat(result.isError()).as("varying %s conflicts", field).isTrue();
        assertThat(result.getError()).as("varying %s fails visibly", field).hasMessageContaining("definition conflict");
    }

    private static Tool tool(ToolCard card) {
        return new ResourceMgrTest.SimpleTool(card);
    }

    private static ToolCard toolCard(String id, String name, String description) {
        return ToolCard.builder().id(id).name(name).description(description).build();
    }

    private static SysOperationCard sysOpCard(String id, String workDir) {
        return SysOperationCard.builder().id(id).name(id).mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workDir).build()).build();
    }

    private static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static Set<String> ownersOf(String resourceId) {
        Map<String, Set<String>> idToOwners = fieldValue(Runner.resourceMgr(), ResourceMgr.class, "idToOwners");
        Set<String> owners = idToOwners.get(resourceId);
        return owners != null ? owners : Set.of();
    }

    @SuppressWarnings("unchecked")
    private static Tool registeredToolInstance(String toolId) {
        Map<String, Tool> tools = fieldValue(toolMgr(), ToolMgr.class, "tools");
        return tools.get(toolId);
    }

    private static ToolMgr toolMgr() {
        ResourceRegistry registry = fieldValue(Runner.resourceMgr(), ResourceMgr.class, "resourceRegistry");
        return fieldValue(registry, ResourceRegistry.class, "toolMgr");
    }

    private static List<String> sysOperationToolIds(String sysOpId) {
        return toolMgr().getSysOperationToolIds(sysOpId);
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldValue(Object target, Class<?> declaringType, String name) {
        try {
            Field field = declaringType.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "ownership reflection path missing: " + declaringType.getSimpleName() + "." + name, e);
        }
    }
}
