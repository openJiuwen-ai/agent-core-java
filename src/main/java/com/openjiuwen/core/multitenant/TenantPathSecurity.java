package com.openjiuwen.core.multitenant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class TenantPathSecurity {

    public static Path resolveSafePath(String relativePath, String tenantRoot) {
        Path cwd = Path.of("").toAbsolutePath();
        Path resolved = cwd.resolve(relativePath).toAbsolutePath().normalize();

        if (tenantRoot != null) {
            Path root = Path.of(tenantRoot).toAbsolutePath().normalize();
            if (!resolved.startsWith(root)) {
                throw new SecurityException(
                    "Path traversal blocked: " + relativePath +
                    " is outside tenant boundary");
            }
        }

        try {
            if (Files.exists(resolved) && Files.isSymbolicLink(resolved)) {
                Path realTarget = resolved.toRealPath();
                if (tenantRoot != null) {
                    Path root = Path.of(tenantRoot).toAbsolutePath().normalize();
                    if (!realTarget.startsWith(root)) {
                        throw new SecurityException(
                            "Symlink traversal blocked: symlink target is outside tenant boundary");
                    }
                }
            }
        } catch (IOException e) {
            throw new SecurityException("Failed to resolve symlink: " + relativePath, e);
        }

        return resolved;
    }
}
