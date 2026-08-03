/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code CryptUtils} in
 * {@code openjiuwen/core/common/security/crypt_utils.py}.
 */
public final class CryptUtils {

    public static final int NONCE_LENGTH = AesGcmCrypt.NONCE_LENGTH;

    public static final int AES_KEY_LENGTH = AesGcmCrypt.AES_KEY_LENGTH;

    public static final int TAG_LENGTH = AesGcmCrypt.TAG_LENGTH;

    public static final String AES_GCM_CRYPT_NAME = "aes_gcm";

    private static final Map<String, BaseCrypt> CRYPT_REGISTRY = new LinkedHashMap<>();

    private static final Object REGISTRY_LOCK = new Object();

    static {
        registerCrypt(AES_GCM_CRYPT_NAME, AesGcmCrypt.getInstance());
    }

    private CryptUtils() {
    }

    public static void registerCrypt(String name, Object crypt) {
        if (!(crypt instanceof BaseCrypt baseCrypt)) {
            String typeName = crypt == null ? "null" : crypt.getClass().toString();
            throw ErrorHelper.buildError(
                    StatusCode.COMMON_ENCRYPTION_ERROR,
                    null,
                    null,
                    null,
                    Map.of("error_msg", "crypt must be a BaseCrypt instance, got " + typeName)
            );
        }
        synchronized (REGISTRY_LOCK) {
            CRYPT_REGISTRY.put(name, baseCrypt);
        }
    }

    public static void unregisterCrypt(String name) {
        synchronized (REGISTRY_LOCK) {
            CRYPT_REGISTRY.remove(name);
        }
    }

    public static BaseCrypt getCrypt(String name) {
        synchronized (REGISTRY_LOCK) {
            return CRYPT_REGISTRY.get(name);
        }
    }
}
