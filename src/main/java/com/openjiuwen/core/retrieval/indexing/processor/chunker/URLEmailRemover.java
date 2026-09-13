/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Removes URLs and email addresses from text.
 * 
 * @since 0.1.7
 */
public class URLEmailRemover implements TextPreprocessor {
    private final boolean isRemoveUrls;
    private final boolean isRemoveEmails;
    private final String replacement;

    /**
     * URLEmailRemover.
     * 
     * @since 0.1.7
     */
    public URLEmailRemover() {
        this(true, true, "");
    }

    /**
     * URLEmailRemover.
     * 
     * @param isRemoveUrls isRemoveUrls
     * @param isRemoveEmails isRemoveEmails
     * @param replacement replacement
     * @since 0.1.7
     */
    public URLEmailRemover(boolean isRemoveUrls, boolean isRemoveEmails, String replacement) {
        this.isRemoveUrls = isRemoveUrls;
        this.isRemoveEmails = isRemoveEmails;
        this.replacement = replacement != null ? replacement : "";
    }

    /**
     * process.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String process(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        if (isRemoveUrls) {
            result = result.replaceAll("https?://\\S+|www\\.\\S+", replacement).replaceAll(
                    "(?<![\\w.%+-]@)(?:https?://|www\\.)?\\b\\S+?\\.(?:com|net|org|cn)(?:[/?#]\\S*)?\\b", replacement);
        }
        if (isRemoveEmails) {
            result = result.replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", replacement);
        }
        return result;
    }

    /**
     * isRemoveUrls.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRemoveUrls() {
        return isRemoveUrls;
    }

    /**
     * isRemoveEmails.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRemoveEmails() {
        return isRemoveEmails;
    }

    /**
     * getReplacement.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReplacement() {
        return replacement;
    }
}
