// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Loads the persisted ACE playbook for the current user.
 */
public class LoadPlaybookOp extends BaseOp {

    @Override
    protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        Playbook playbook = new Playbook();
        String userId = context.getString("user_id", "default");

        Object vectorStoreService = getVectorStore();
        if (!(vectorStoreService instanceof MemoryVectorStore vectorStore)) {
            context.set("playbook", playbook);
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("workspace_id", userId);
        filter.put("type", "ace_memory");

        List<VectorNode> existingNodes = vectorStore.getAll(filter).stream()
            .sorted(Comparator.comparing(VectorNode::getId))
            .toList();

        int maxCounter = 0;
        for (VectorNode node : existingNodes) {
            ACEMemory memory = ACEMemory.fromVectorNode(node);
            playbook.loadBullet(new Playbook.Bullet(
                memory.getId(),
                memory.getSection(),
                memory.getContent(),
                memory.getHelpful(),
                memory.getHarmful(),
                memory.getNeutral(),
                memory.getCreatedAt().toString(),
                memory.getUpdatedAt().toString()
            ));
            maxCounter = Math.max(maxCounter, AceUtils.trailingCounter(memory.getId()));
        }

        playbook.setNextId(maxCounter);
        context.set("playbook", playbook);
        return CompletableFuture.completedFuture(null);
    }
}
