/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing.backends;

import com.openjiuwen.agentevolving.sharing.QueryKeywords;
import com.openjiuwen.agentevolving.sharing.SharedSkillBundle;
import com.openjiuwen.agentevolving.sharing.SkillPackageMeta;
import com.openjiuwen.agentevolving.sharing.SkillSearchResult;
import com.openjiuwen.agentevolving.sharing.UploadResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class SharingBackendTest {

    @Test
    void defaultTopKValuesMirrorPythonContract() {
        DemoSharingBackend backend = new DemoSharingBackend();
        QueryKeywords query = new QueryKeywords();
        query.setKeywords(List.of("sharing"));

        List<SharedSkillBundle> bundles = backend.downloadBundles("skill-a", query).toCompletableFuture().join();
        List<SkillSearchResult> results = backend.searchSkills(query).toCompletableFuture().join();

        assertThat(backend.lastDownloadSkillId).isEqualTo("skill-a");
        assertThat(backend.lastDownloadTopK).isEqualTo(3);
        assertThat(bundles).singleElement().extracting(SharedSkillBundle::getSkillId).isEqualTo("skill-a");
        assertThat(backend.lastSearchTopK).isEqualTo(5);
        assertThat(results).singleElement().extracting(SkillSearchResult::getSkillId).isEqualTo("skill-a");
    }

    @Test
    void skillPackageOperationsRoundTripHubState() {
        DemoSharingBackend backend = new DemoSharingBackend();
        SkillPackageMeta meta = new SkillPackageMeta();
        meta.setSkillId("skill-a");
        meta.setSkillName("Demo skill");
        meta.setDescription("backend fixture");

        backend.uploadSkillPackage("skill-a", new byte[] {1, 2, 3}, meta).toCompletableFuture().join();

        assertThat(backend.hasSkillPackage("skill-a").toCompletableFuture().join()).isTrue();
        assertThat(backend.downloadSkillPackage("skill-a").toCompletableFuture().join()).containsExactly(1, 2, 3);
        assertThat(backend.getSkillPackageMeta("skill-a").toCompletableFuture().join().getSkillName())
            .isEqualTo("Demo skill");
    }

    private static final class DemoSharingBackend implements SharingBackend {
        private String lastDownloadSkillId;
        private int lastDownloadTopK;
        private int lastSearchTopK;
        private byte[] packageBytes;
        private SkillPackageMeta packageMeta;

        @Override
        public CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle) {
            UploadResult result = new UploadResult();
            result.setOk(true);
            result.setBundleId(bundle == null ? "" : bundle.getBundleId());
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK) {
            lastDownloadSkillId = skillId;
            lastDownloadTopK = topK;
            SharedSkillBundle bundle = new SharedSkillBundle();
            bundle.setSkillId(skillId);
            bundle.setKeywordsAggregate(query == null ? List.of() : query.getKeywords());
            return CompletableFuture.completedFuture(List.of(bundle));
        }

        @Override
        public CompletionStage<Boolean> hasSkillPackage(String skillId) {
            return CompletableFuture.completedFuture(packageMeta != null && skillId.equals(packageMeta.getSkillId()));
        }

        @Override
        public CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta) {
            this.packageBytes = packageBytes == null ? null : packageBytes.clone();
            this.packageMeta = meta;
            if (this.packageMeta != null && this.packageMeta.getSkillId() == null) {
                this.packageMeta.setSkillId(skillId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
            if (packageMeta == null || !skillId.equals(packageMeta.getSkillId()) || packageBytes == null) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(packageBytes.clone());
        }

        @Override
        public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
            if (packageMeta == null || !skillId.equals(packageMeta.getSkillId())) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.completedFuture(packageMeta);
        }

        @Override
        public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
            lastSearchTopK = topK;
            SkillSearchResult result = new SkillSearchResult();
            result.setSkillId("skill-a");
            result.setKeywords(query == null ? List.of() : query.getKeywords());
            result.setScore(0.9d);
            return CompletableFuture.completedFuture(List.of(result));
        }
    }
}
