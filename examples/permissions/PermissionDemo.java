package examples.permissions;

import com.openjiuwen.harness.security.PermissionExampleSupport;

import java.nio.file.Path;

/**
 * Thin entry point for the Java permissions example baseline.
 */
public final class PermissionDemo {

    private PermissionDemo() {
    }

    public static void main(String[] args) {
        Path workspace = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var engine = PermissionExampleSupport.buildEngine(workspace);
        var result = engine.checkPermission("read_file", java.util.Map.of("path", workspace.resolve("notes.txt").toString()));
        System.out.println("permission=" + result.getPermission() + ", matchedRule=" + result.getMatchedRule());
    }
}
