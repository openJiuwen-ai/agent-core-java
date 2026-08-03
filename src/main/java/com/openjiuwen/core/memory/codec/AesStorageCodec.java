/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.codec;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.security.BaseCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.foundation.store.StorageCodec;

import java.util.Arrays;

/**
 * AES-backed storage codec used by memory stores.
 *
 * <p>Mirrors Python's {@code AesStorageCodec} in
 * {@code openjiuwen/core/memory/codec/aes_storage_codec.py}.</p>
 */
public final class AesStorageCodec implements StorageCodec {

    private final byte[] key;

    public AesStorageCodec(byte[] key) {
        this.key = key == null ? new byte[0] : Arrays.copyOf(key, key.length);
    }

    @Override
    public String encode(String text) {
        if (key.length == 0 || text == null || text.isEmpty()) {
            return text;
        }

        BaseCrypt crypt = CryptUtils.getCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        if (crypt == null) {
            return text;
        }

        try {
            return crypt.encrypt(key, text);
        } catch (Exception exception) {
            Loggers.MEMORY.warning(
                    "Encrypt error via crypt: {} (event_type={}, exception={})",
                    exception.getMessage(),
                    LogEventType.MEMORY_PROCESS.getValue(),
                    exception.getClass().getSimpleName()
            );
            return text;
        }
    }

    @Override
    public String decode(String data) {
        if (key.length == 0 || data == null || data.isEmpty()) {
            return data;
        }

        BaseCrypt crypt = CryptUtils.getCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
        if (crypt == null) {
            return data;
        }

        try {
            return crypt.decrypt(key, data);
        } catch (Exception exception) {
            Loggers.MEMORY.warning(
                    "Decrypt error via crypt: {} (event_type={}, exception={})",
                    exception.getMessage(),
                    LogEventType.MEMORY_PROCESS.getValue(),
                    exception.getClass().getSimpleName()
            );
            return data;
        }
    }
}
