package examples.lsp;

import com.openjiuwen.harness.lsp.LspExampleSupport;

import java.nio.file.Path;

/**
 * Thin entry point for the Java LSP example baseline.
 */
public final class LspToolExample {

    private LspToolExample() {
    }

    public static void main(String[] args) {
        Path workspace = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        System.out.println(LspExampleSupport.runDefinitionDemo(workspace, "src/Main.java"));
    }
}
