package examples.permissions;

import com.openjiuwen.harness.security.PermissionCheckResult;
import com.openjiuwen.harness.security.PermissionEngine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates the tool permission guardrail dual pipeline.
 *
 * <p>A single {@code permissions} config exercises both pipelines together:
 * <ul>
 *   <li>Pipeline A (tiered command rules): {@code cat * -> allow}, {@code curl * -> deny};</li>
 *   <li>Pipeline B (file-guard path rules): {@code /etc/hosts} {@code read=allow},
 *       {@code write=deny}.</li>
 * </ul>
 * The engine merges them with {@code strictest}, so {@code cat /etc/hosts} is approved,
 * {@code curl http://x} is denied, reading {@code /etc/hosts} is approved, and writing
 * {@code /etc/hosts} is denied. Mirrors {@code examples/permissions/permission_demo.py}.
 */
public final class PermissionDemo {

    private PermissionDemo() {
    }

    public static void main(String[] args) {
        Path workspace = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        PermissionEngine engine = new PermissionEngine(dualPipelinePermissions(), workspace);

        System.out.println("[Demo] dual-pipeline permissions (cat/curl rules + /etc/hosts file_guard):");
        check(engine, "bash", Map.of("command", "cat /etc/hosts"));
        check(engine, "bash", Map.of("command", "curl http://x"));
        check(engine, "read_file", Map.of("file_path", "/etc/hosts"));
        check(engine, "write_file", Map.of("file_path", "/etc/hosts"));
    }

    private static void check(PermissionEngine engine, String tool, Map<String, Object> toolArgs) {
        PermissionCheckResult result = engine.checkPermission(tool, toolArgs);
        System.out.println("  " + tool + " " + toolArgs + " -> " + result.getPermission()
                + " | matchedRule=" + result.getMatchedRule()
                + " | needsApproval=" + result.isNeedsApproval());
    }

    static Map<String, Object> dualPipelinePermissions() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", true);
        cfg.put("schema", "tiered_policy");
        cfg.put("permission_mode", "normal");
        cfg.put("tools", Map.of("bash", "ask"));
        cfg.put("defaults", Map.of("*", "allow"));
        cfg.put("rules", List.of(
                Map.of("id", "cat", "tools", List.of("bash"),
                        "pattern", "cat *", "action", "allow"),
                Map.of("id", "curl", "tools", List.of("bash"),
                        "pattern", "curl *", "action", "deny")));
        cfg.put("approval_overrides", List.of());
        Map<String, Object> fileGuard = new LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", "/etc/hosts",
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        cfg.put("file_guard", fileGuard);
        return cfg;
    }
}
