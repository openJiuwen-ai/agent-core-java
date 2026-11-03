/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL utility helpers.
 * <p>
 * Mirrors Python's {@code redact_url_password} in
 * {@code openjiuwen/core/common/utils/url_utils.py}.
 */
public final class UrlUtils {

    private UrlUtils() {
    }

    /**
     * Redact the password portion of a URL for safe logging.
     *
     * @param url URL that may contain credentials
     * @return original URL when no password exists or parsing fails; otherwise the password is replaced with {@code ***}
     */
    public static String redactUrlPassword(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            URI parsed = new URI(url);
            String userInfo = parsed.getUserInfo();
            if (userInfo == null || !userInfo.contains(":")) {
                return url;
            }

            int separator = userInfo.indexOf(':');
            String username = separator > 0 ? userInfo.substring(0, separator) : "";
            StringBuilder netloc = new StringBuilder();
            if (!username.isEmpty()) {
                netloc.append(username);
            }
            netloc.append(":***");

            if (parsed.getHost() != null) {
                netloc.append('@').append(parsed.getHost());
            }
            if (parsed.getPort() >= 0) {
                netloc.append(':').append(parsed.getPort());
            }

            URI redacted = new URI(
                    parsed.getScheme(),
                    netloc.toString(),
                    parsed.getPath(),
                    parsed.getQuery(),
                    parsed.getFragment()
            );
            return redacted.toString();
        } catch (URISyntaxException ex) {
            return url;
        }
    }
}
