/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.coordinator;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts rollout sequences into padded token IDs, attention masks, rewards,
 * and metadata components required for RL batch construction.
 * <p>
 * Mirrors Python's {@code RLBatchBuilder} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.coordinator.batch_builder}.
 */
public class RLBatchBuilder {

    private int maxPromptLength;
    private int padTokenId;
    private int maxResponseLength;

    public RLBatchBuilder(int maxPromptLength, int padTokenId, int maxResponseLength) {
        this.maxPromptLength = maxPromptLength;
        this.padTokenId = padTokenId;
        this.maxResponseLength = maxResponseLength;
    }

    /**
     * Left-pads the input ID sequence to a fixed length.
     * 
     * @param ids Input ID sequence
     * @param maxLength Maximum length
     * @param padTokenId Padding token ID
     * @return Array of [padded_ids, attention_mask]
     */
    public static List<Integer>[] getLeftPaddedIdsAndAttentionMask(
            List<Integer> ids, int maxLength, int padTokenId) {
        
        int seqLen = ids.size();
        
        if (seqLen >= maxLength) {
            List<Integer> trimmed = ids.subList(seqLen - maxLength, seqLen);
            List<Integer> attentionMask = new ArrayList<>();
            for (int i = 0; i < maxLength; i++) attentionMask.add(1);
            return new List[] { trimmed, attentionMask };
        }
        
        int padLen = maxLength - seqLen;
        List<Integer> paddedIds = new ArrayList<>();
        for (int i = 0; i < padLen; i++) paddedIds.add(padTokenId);
        paddedIds.addAll(ids);
        
        List<Integer> attentionMask = new ArrayList<>();
        for (int i = 0; i < padLen; i++) attentionMask.add(0);
        for (int i = 0; i < seqLen; i++) attentionMask.add(1);
        
        return new List[] { paddedIds, attentionMask };
    }

    /**
     * Right-pads the input ID sequence to a fixed length.
     * 
     * @param ids Input ID sequence
     * @param maxLength Maximum length
     * @param padTokenId Padding token ID
     * @return Array of [padded_ids, attention_mask]
     */
    public static List<Integer>[] getRightPaddedIdsAndAttentionMask(
            List<Integer> ids, int maxLength, int padTokenId) {
        
        int seqLen = ids.size();
        
        if (seqLen >= maxLength) {
            List<Integer> trimmed = ids.subList(0, maxLength);
            List<Integer> attentionMask = new ArrayList<>();
            for (int i = 0; i < maxLength; i++) attentionMask.add(1);
            return new List[] { trimmed, attentionMask };
        }
        
        int padLen = maxLength - seqLen;
        List<Integer> paddedIds = new ArrayList<>(ids);
        for (int i = 0; i < padLen; i++) paddedIds.add(padTokenId);
        
        List<Integer> attentionMask = new ArrayList<>();
        for (int i = 0; i < seqLen; i++) attentionMask.add(1);
        for (int i = 0; i < padLen; i++) attentionMask.add(0);
        
        return new List[] { paddedIds, attentionMask };
    }

    public int getMaxPromptLength() { return maxPromptLength; }
    public void setMaxPromptLength(int maxPromptLength) { this.maxPromptLength = maxPromptLength; }
    public int getPadTokenId() { return padTokenId; }
    public void setPadTokenId(int padTokenId) { this.padTokenId = padTokenId; }
    public int getMaxResponseLength() { return maxResponseLength; }
    public void setMaxResponseLength(int maxResponseLength) { this.maxResponseLength = maxResponseLength; }
}