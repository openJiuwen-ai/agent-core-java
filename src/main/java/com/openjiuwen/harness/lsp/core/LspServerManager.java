/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.CustomServerConfig;
import com.openjiuwen.harness.lsp.LspInitializeOptions;
import com.openjiuwen.harness.lsp.LspInitializeResult;
import com.openjiuwen.harness.lsp.LspStatus;
import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.core.utils.GitIgnoreFilter;
import com.openjiuwen.harness.lsp.core.utils.LspConstants;
import com.openjiuwen.harness.lsp.query.LspDiagnostic;
import com.openjiuwen.harness.lsp.query.LspDiagnosticFile;
import com.openjiuwen.harness.lsp.query.LspCallHierarchyItem;
import com.openjiuwen.harness.lsp.query.LspIncomingCall;
import com.openjiuwen.harness.lsp.query.LspLocation;
import com.openjiuwen.harness.lsp.query.LspOutgoingCall;
import com.openjiuwen.harness.lsp.query.LspRange;
import com.openjiuwen.harness.lsp.query.LspSymbol;
import com.openjiuwen.harness.lsp.servers.LspServerDefinition;
import com.openjiuwen.harness.lsp.servers.LspServerRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimal Java harness LSP server manager.
 *
 * <p>Mirrors Python's {@code LSPServerManager} in
 * {@code openjiuwen.harness.lsp.core.manager}.
 */
public class LspServerManager {

    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            ".git", "node_modules", "target", "build", "dist", "out", "__pycache__", ".idea", ".vscode"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "py", "js", "jsx", "ts", "tsx", "go", "rs", "kt", "kts", "json", "yaml", "yml",
            "xml", "md", "txt", "properties", "gradle", "sql", "html", "css", "scss", "vue", "c", "cpp",
            "h", "hpp", "cs", "php", "rb", "swift"
    );
    private static final List<PatternRule> SYMBOL_PATTERNS = List.of(
            new PatternRule(Pattern.compile("^\\s*(?:public|protected|private|abstract|final|sealed|static\\s+)*(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"), 2, "type"),
            new PatternRule(Pattern.compile("^\\s*def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("), 1, "function"),
            new PatternRule(Pattern.compile("^\\s*(?:async\\s+)?function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("), 1, "function"),
            new PatternRule(Pattern.compile("^\\s*(?:public|protected|private|static|final|synchronized|native|default|abstract|override|async\\s+)*(?:[A-Za-z_][A-Za-z0-9_<>, ?\\[\\].]+\\s+)+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("), 1, "function"),
            new PatternRule(Pattern.compile("^\\s*(?:const|let|var|val|String|int|long|boolean|double|float|char)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"), 1, "variable")
    );

    private static LspServerManager instance;

    private final Map<String, ScopedLspServerConfig> configs = new LinkedHashMap<>();
    private final Map<String, LspServerState> states = new LinkedHashMap<>();
    private final Map<LspServerInstanceKey, LspServerInstance> runtimeInstances = new LinkedHashMap<>();
    private final Set<String> registeredDiagnosticHandlers = new java.util.LinkedHashSet<>();
    private final Set<LspServerInstance> registeredDiagnosticHandlerInstances = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> documentVersions = new LinkedHashMap<>();
    private final LspNotificationBridge notificationBridge = new LspNotificationBridge();
    private boolean initialized;
    private String workspaceRoot = ".";

    public static synchronized LspServerManager initialize() {
        if (instance == null) {
            instance = new LspServerManager();
        }
        instance.initialized = true;
        return instance;
    }

    public static synchronized LspInitializeResult initialize(LspInitializeOptions options) {
        long start = System.currentTimeMillis();
        LspServerManager manager = initialize();
        manager.configure(options);
        int serversLoaded = manager.getStatus().size();
        return new LspInitializeResult(true, serversLoaded, System.currentTimeMillis() - start);
    }

    public static synchronized LspServerManager getInstance() {
        return initialize();
    }

    public synchronized void register(ScopedLspServerConfig config) {
        if (config == null || config.getServerId() == null || config.getServerId().isBlank()) {
            return;
        }
        configs.put(config.getServerId(), config);
        states.put(config.getServerId(), LspServerState.STOPPED);
    }

    private synchronized void configure(LspInitializeOptions options) {
        String cwd = options != null ? options.getCwd() : null;
        workspaceRoot = (cwd == null || cwd.isBlank()) ? Paths.get(".").toAbsolutePath().normalize().toString()
                : Paths.get(cwd).toAbsolutePath().normalize().toString();

        if (configs.isEmpty()) {
            for (ScopedLspServerConfig config : LspServerRegistry.bootstrapDefaults(workspaceRoot).values()) {
                register(config);
            }
        }

        if (options != null && options.getCustomServers() != null) {
            for (Map.Entry<String, CustomServerConfig> entry : options.getCustomServers().entrySet()) {
                ScopedLspServerConfig config = toCustomConfig(entry.getKey(), entry.getValue());
                if (config != null) {
                    register(config);
                }
            }
        }
    }

    public synchronized void start(String serverId) {
        if (!configs.containsKey(serverId)) {
            return;
        }
        states.put(serverId, LspServerState.RUNNING);
    }

    public synchronized void stop(String serverId) {
        if (!configs.containsKey(serverId)) {
            return;
        }
        states.put(serverId, LspServerState.STOPPED);
    }

    public synchronized void stopAll() {
        for (String serverId : configs.keySet()) {
            states.put(serverId, LspServerState.STOPPED);
        }
        for (LspServerInstance instance : runtimeInstances.values()) {
            instance.stop();
        }
        runtimeInstances.clear();
    }

    public synchronized List<LspServerStatus> getStatus() {
        return configs.values().stream().map(config -> new LspServerStatus(
                config.getServerId(),
                config.getServerId(),
                runtimeInstances.values().stream().anyMatch(instance -> config.getServerId().equals(instance.getConfig().getServerId()) && instance.isRunning()),
                config.getWorkspaceFolder(),
                runtimeInstances.values().stream()
                        .filter(instance -> config.getServerId().equals(instance.getConfig().getServerId()))
                        .map(LspServerInstance::getState)
                        .findFirst()
                        .orElse(states.getOrDefault(config.getServerId(), LspServerState.STOPPED))
        )).toList();
    }

    public synchronized LspServerInstance getOrStartServer(String filePath) {
        Path resolved = resolvePath(filePath);
        if (resolved == null) {
            return null;
        }
        String extension = "." + extensionOf(resolved.getFileName().toString());
        for (LspServerDefinition definition : LspServerRegistry.matchByExtension(extension)) {
            String root = definition.getRootResolver() != null
                    ? definition.getRootResolver().resolve(resolved.toString())
                    : resolved.getParent() != null ? resolved.getParent().toString() : resolved.toString();
            if (root == null) {
                continue;
            }
            LspServerInstanceKey key = new LspServerInstanceKey(definition.getId(), root);
            LspServerInstance existing = runtimeInstances.get(key);
            if (existing != null && existing.isHealthy()) {
                return existing;
            }
            ScopedLspServerConfig config = configs.get(definition.getId());
            if (config == null) {
                continue;
            }
            ScopedLspServerConfig scoped = cloneConfigForRoot(config, root, definition.getLanguageId());
            LspServerInstance instance = new LspServerInstance(scoped, error -> states.put(definition.getId(), LspServerState.ERROR));
            instance.start();
            runtimeInstances.put(key, instance);
            states.put(definition.getId(), instance.getState());
            return instance;
        }
        return null;
    }

    public synchronized Object sendRequest(String filePath, String method, Map<String, Object> params) {
        LspServerInstance instance = getOrStartServer(filePath);
        if (instance == null) {
            return null;
        }
        return instance.sendRequest(method, params != null ? params : Map.of());
    }

    public synchronized void sendNotification(String filePath, String method, Map<String, Object> params) {
        LspServerInstance instance = getOrStartServer(filePath);
        if (instance == null) {
            return;
        }
        instance.sendNotification(method, params != null ? params : Map.of());
    }

    public synchronized void stopAllRuntimeServers() {
        for (LspServerInstance instance : runtimeInstances.values()) {
            instance.stop();
        }
        runtimeInstances.clear();
    }

    private ScopedLspServerConfig cloneConfigForRoot(ScopedLspServerConfig config, String root, String languageId) {
        ScopedLspServerConfig scoped = new ScopedLspServerConfig();
        scoped.setServerId(config.getServerId());
        scoped.setCommand(config.getCommand());
        scoped.setWorkspaceFolder(root);
        scoped.setArgs(config.getArgs());
        scoped.setEnv(config.getEnv());
        scoped.setInitializationOptions(config.getInitializationOptions());
        scoped.setStartupTimeout(config.getStartupTimeout());
        scoped.setExtensionToLanguage(config.getExtensionToLanguage());
        if (languageId != null && !languageId.isBlank()) {
            Map<String, String> extensionMap = new LinkedHashMap<>(scoped.getExtensionToLanguage());
            extensionMap.replaceAll((key, ignored) -> languageId);
            scoped.setExtensionToLanguage(extensionMap);
        }
        return scoped;
    }

    public synchronized LspStatus getLspStatus() {
        return new LspStatus(initialized, getStatus());
    }

    public synchronized String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public synchronized List<LspDiagnostic> getDiagnostics(String filePath, String severity) {
        Path resolved = resolvePath(filePath);
        if (resolved == null) {
            return List.of(new LspDiagnostic("error", "file_path is required", new LspLocation("", 1, 1)));
        }
        if (Files.exists(resolved)) {
            try {
                if (Files.size(resolved) > LspConstants.MAX_LSP_FILE_SIZE_BYTES) {
                    return List.of(new LspDiagnostic("warning", "File exceeds maximum LSP size", new LspLocation(resolved.toString(), 1, 1)));
                }
            } catch (IOException ignored) {
                // fall through and continue with best-effort diagnostics
            }
        }
        if (!Files.exists(resolved)) {
            return filterDiagnostics(List.of(new LspDiagnostic(
                    "error",
                    "File does not exist: " + resolved,
                    new LspLocation(resolved.toString(), 1, 1)
            )), severity);
        }

        List<String> lines = readLinesQuietly(resolved);
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        int braceBalance = 0;
        int parenBalance = 0;
        int bracketBalance = 0;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNo = index + 1;
            if (line.contains("TODO") || line.contains("FIXME")) {
                diagnostics.add(new LspDiagnostic(
                        "information",
                        "Pending marker found: " + line.trim(),
                        new LspLocation(resolved.toString(), lineNo, Math.max(1, line.indexOf("TODO") + 1))
                ));
            }
            braceBalance += count(line, '{') - count(line, '}');
            parenBalance += count(line, '(') - count(line, ')');
            bracketBalance += count(line, '[') - count(line, ']');
        }

        if (braceBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Brace balance is not zero", locationAtEnd(resolved, lines)));
        }
        if (parenBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Parenthesis balance is not zero", locationAtEnd(resolved, lines)));
        }
        if (bracketBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Bracket balance is not zero", locationAtEnd(resolved, lines)));
        }
        LspDiagnosticRegistry.getInstance().push(resolved.toString(), diagnostics);
        return filterDiagnostics(diagnostics, severity);
    }

    public synchronized List<LspSymbol> getDocumentSymbols(String filePath) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }
        if (GitIgnoreFilter.isIgnored(resolved.toString())) {
            return List.of();
        }
        ensureRelevantServer(resolved);
        List<LspSymbol> symbols = collectDocumentSymbols(resolved);
        symbols.sort(Comparator.comparing((LspSymbol symbol) -> symbol.getLocation() != null ? symbol.getLocation().getLine() : 0)
                .thenComparing(LspSymbol::getName));
        return symbols;
    }

    public synchronized List<LspSymbol> getWorkspaceSymbols(String query, int limit) {
        int effectiveLimit = limit > 0 ? limit : 50;
        String normalized = query == null ? "" : query.trim().toLowerCase();
        List<LspSymbol> matches = new ArrayList<>();
        for (Path file : workspaceFiles()) {
            if (GitIgnoreFilter.isIgnored(file.toString())) {
                continue;
            }
            for (LspSymbol symbol : collectDocumentSymbols(file)) {
                if (normalized.isBlank() || symbol.getName().toLowerCase().contains(normalized)) {
                    matches.add(symbol);
                }
            }
        }
        List<LspSymbol> filtered = GitIgnoreFilter.filter(matches, symbol -> symbol != null && symbol.getLocation() != null
                ? symbol.getLocation().getFilePath() : "");
        filtered.sort(Comparator.comparing((LspSymbol symbol) -> symbol.getLocation() != null ? symbol.getLocation().getFilePath() : "")
                .thenComparing(symbol -> symbol.getLocation() != null ? symbol.getLocation().getLine() : 0)
                .thenComparing(LspSymbol::getName));
        return filtered.size() > effectiveLimit ? filtered.subList(0, effectiveLimit) : filtered;
    }

    public synchronized LspLocation gotoDefinition(String filePath, int line, int character) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            return null;
        }
        ensureRelevantServer(resolved);
        LspLocation protocolLocation = locationFromProtocolResponse(sendRequest(
                resolved.toString(),
                "textDocument/definition",
                Map.of(
                        "textDocument", Map.of("uri", FileUriUtils.pathToFileUri(resolved.toString())),
                        "position", Map.of(
                                "line", Math.max(0, line - 1),
                                "character", Math.max(0, character - 1)
                        )
                )
        ));
        if (protocolLocation != null) {
            return protocolLocation;
        }
        String symbol = symbolAt(resolved, line, character);
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        for (LspSymbol candidate : getWorkspaceSymbols(symbol, 200)) {
            if (symbol.equals(candidate.getName())) {
                return candidate.getLocation();
            }
        }
        return null;
    }

    public synchronized List<LspLocation> findReferences(String filePath, int line, int character, boolean includeDeclaration) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }
        ensureRelevantServer(resolved);
        List<LspLocation> protocolLocations = locationsFromProtocolResponse(sendRequest(
                resolved.toString(),
                "textDocument/references",
                Map.of(
                        "textDocument", Map.of("uri", FileUriUtils.pathToFileUri(resolved.toString())),
                        "position", Map.of(
                                "line", Math.max(0, line - 1),
                                "character", Math.max(0, character - 1)
                        ),
                        "context", Map.of("includeDeclaration", includeDeclaration)
                )
        ));
        if (!protocolLocations.isEmpty()) {
            return protocolLocations;
        }
        String symbol = symbolAt(resolved, line, character);
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        Pattern occurrencePattern = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");
        LspLocation definition = gotoDefinition(filePath, line, character);
        List<LspLocation> matches = new ArrayList<>();
        for (Path file : workspaceFiles()) {
            if (GitIgnoreFilter.isIgnored(file.toString())) {
                continue;
            }
            List<String> lines = readLinesQuietly(file);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                Matcher matcher = occurrencePattern.matcher(lines.get(lineIndex));
                while (matcher.find()) {
                    LspLocation location = new LspLocation(file.toString(), lineIndex + 1, matcher.start() + 1);
                    if (!includeDeclaration && sameLocation(location, definition)) {
                        continue;
                    }
                    matches.add(location);
                }
            }
        }
        return matches;
    }

    public synchronized List<LspLocation> gotoImplementation(String filePath, int line, int character) {
        LspLocation definition = gotoDefinition(filePath, line, character);
        if (definition == null) {
            return List.of();
        }
        Path definitionPath = resolvePath(definition.getFilePath());
        if (definitionPath == null || !Files.exists(definitionPath)) {
            return List.of();
        }
        String symbol = symbolAt(definitionPath, definition.getLine(), definition.getCharacter());
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }

        List<LspLocation> candidates = new ArrayList<>();
        Pattern implementationPattern = Pattern.compile("\\b(?:implements|extends)\\b[^\\n]*\\b" + Pattern.quote(symbol) + "\\b");
        for (Path file : workspaceFiles()) {
            if (GitIgnoreFilter.isIgnored(file.toString())) {
                continue;
            }
            List<String> lines = readLinesQuietly(file);
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                Matcher matcher = implementationPattern.matcher(lines.get(lineIndex));
                if (matcher.find()) {
                    candidates.add(new LspLocation(file.toString(), lineIndex + 1, matcher.start() + 1));
                }
            }
        }
        return candidates;
    }

    public synchronized List<LspCallHierarchyItem> prepareCallHierarchy(String filePath, int line, int character) {
        LspLocation definition = gotoDefinition(filePath, line, character);
        Path resolved = definition != null ? resolvePath(definition.getFilePath()) : resolvePath(filePath);
        int effectiveLine = definition != null ? definition.getLine() : line;
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }

        List<LspSymbol> documentSymbols = collectDocumentSymbols(resolved);
        LspSymbol current = null;
        for (LspSymbol symbol : flattenSymbols(documentSymbols)) {
            LspRange range = symbol.getRange();
            int startLine = range != null && range.getStart() != null ? range.getStart().getLine()
                    : symbol.getLocation() != null ? symbol.getLocation().getLine() : Integer.MAX_VALUE;
            int endLine = range != null && range.getEnd() != null ? range.getEnd().getLine() : startLine;
            if (startLine <= effectiveLine && effectiveLine <= endLine) {
                current = symbol;
            }
        }
        List<LspCallHierarchyItem> items = current == null ? List.of()
                : List.of(toCallHierarchyItem(current, current.getDetail()));
        return GitIgnoreFilter.filter(items, item -> item != null ? item.getUri() : "");
    }

    public synchronized List<LspIncomingCall> incomingCalls(String filePath, int line, int character) {
        LspLocation definition = gotoDefinition(filePath, line, character);
        Path definitionPath = definition != null ? resolvePath(definition.getFilePath()) : resolvePath(filePath);
        int definitionLine = definition != null ? definition.getLine() : line;
        int definitionCharacter = definition != null ? definition.getCharacter() : character;
        if (definitionPath == null || !Files.exists(definitionPath)) {
            return List.of();
        }
        String symbol = symbolAt(definitionPath, definitionLine, definitionCharacter);
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        List<LspIncomingCall> calls = findReferences(definitionPath.toString(), definitionLine, definitionCharacter, false).stream()
                .filter(location -> !sameFileLine(location, definitionPath.toString(), definitionLine))
                .map(location -> {
                    LspLocation callerDefinition = enclosingSymbolLocation(location);
                    LspLocation fromLocation = callerDefinition != null ? callerDefinition : location;
                    String callerName = symbolAt(resolvePath(fromLocation.getFilePath()), fromLocation.getLine(), fromLocation.getCharacter());
                    if (callerName == null || callerName.isBlank()) {
                        callerName = symbol;
                    }
                    LspSymbol callerSymbol = symbolCovering(fromLocation);
                    LspRange callSiteRange = expandCallSiteRange(location, symbol);
                    return new LspIncomingCall(
                            callerSymbol != null
                                    ? toCallHierarchyItem(callerSymbol, callerFallbackDetail(callerSymbol, callerName))
                                    : fallbackCallHierarchyItem(callerName, "function", callerName, fromLocation),
                            List.of(callSiteRange)
                    );
                })
                .toList();
        return GitIgnoreFilter.filter(calls, call -> call != null && call.getFrom() != null ? call.getFrom().getUri() : "");
    }

    public synchronized List<LspOutgoingCall> outgoingCalls(String filePath, int line, int character) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }
        List<String> lines = readLinesQuietly(resolved);
        if (line < 1 || line > lines.size()) {
            return List.of();
        }
        int scopeStart = Math.max(0, line - 1);
        int scopeEnd = findScopeEnd(lines, scopeStart);
        Pattern callPattern = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
        List<LspOutgoingCall> outgoing = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (int lineIndex = scopeStart; lineIndex < scopeEnd; lineIndex++) {
            Matcher matcher = callPattern.matcher(lines.get(lineIndex));
            while (matcher.find()) {
                String callee = matcher.group(1);
                if (callee == null || callee.isBlank() || isControlKeyword(callee) || !seen.add(callee + "@" + lineIndex)) {
                    continue;
                }
                LspLocation location = firstSymbolLocation(callee);
                if (location != null) {
                    LspSymbol calleeSymbol = symbolCovering(location);
                    LspRange callSiteRange = expandCallSiteRange(
                            new LspLocation(resolved.toString(), lineIndex + 1, matcher.start() + 1),
                            callee
                    );
                    outgoing.add(new LspOutgoingCall(
                            calleeSymbol != null
                                    ? toCallHierarchyItem(calleeSymbol, calleeFallbackDetail(calleeSymbol, callee))
                                    : fallbackCallHierarchyItem(callee, inferCallHierarchyKind(resolved, location, callee), callee, location),
                            List.of(callSiteRange)
                    ));
                }
            }
        }
        return GitIgnoreFilter.filter(outgoing, call -> call != null && call.getTo() != null ? call.getTo().getUri() : "");
    }

    private ScopedLspServerConfig toCustomConfig(String serverId, CustomServerConfig raw) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId(serverId);
        config.setWorkspaceFolder(workspaceRoot);
        if (raw == null) {
            return config;
        }
        if (raw.isDisabled()) {
            return null;
        }
        config.setCommand(raw.getCommand());
        config.setArgs(raw.getArgs());
        config.setEnv(raw.getEnv());
        config.setInitializationOptions(raw.getInitializationOptions());
        String languageId = raw.getLanguageId();
        for (String extension : raw.getExtensions()) {
            config.getExtensionToLanguage().put(extension, languageId != null && !languageId.isBlank() ? languageId : serverId);
        }
        return config;
    }

    private List<LspSymbol> collectDocumentSymbols(Path file) {
        List<String> lines = readLinesQuietly(file);
        List<SymbolNode> nodes = new ArrayList<>();
        Map<Integer, List<SymbolNode>> childrenByParent = new HashMap<>();
        List<Integer> rootIndexes = new ArrayList<>();
        List<ScopeEntry> scopeStack = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            int indent = indentationOf(line);
            closeScopesForLine(scopeStack, lineNumber, indent);
            for (PatternRule rule : SYMBOL_PATTERNS) {
                Matcher matcher = rule.pattern().matcher(line);
                if (matcher.find()) {
                    String name = matcher.group(rule.nameGroup());
                    if (name != null && !name.isBlank()) {
                        LspLocation location = new LspLocation(file.toString(), lineNumber, matcher.start(rule.nameGroup()) + 1);
                        String detail = line.trim();
                        ScopeEntry parent = nearestParent(scopeStack, rule.kind(), indent);
                        String containerName = parent != null ? parent.qualifiedName() : "";
                        LspRange selectionRange = new LspRange(location, location);
                        int scopeEndLine = opensScope(rule.kind(), line)
                                ? findSymbolScopeEnd(lines, index, indent, line, rule.kind())
                                : lineNumber;
                        int nodeIndex = nodes.size();
                        String qualifiedName = containerName.isBlank() ? name : containerName + "." + name;
                        SymbolNode node = new SymbolNode(nodeIndex, name, rule.kind(), location, selectionRange,
                                containerName, detail, indent, lineNumber, scopeEndLine, qualifiedName);
                        nodes.add(node);
                        if (parent == null) {
                            rootIndexes.add(nodeIndex);
                        } else {
                            childrenByParent.computeIfAbsent(parent.nodeIndex(), key -> new ArrayList<>()).add(node);
                        }
                        if (opensScope(rule.kind(), line)) {
                            scopeStack.add(new ScopeEntry(nodeIndex, qualifiedName, rule.kind(), indent, scopeEndLine));
                        }
                        break;
                    }
                }
            }
        }
        return buildSymbolTree(file, lines, childrenByParent, rootIndexes, nodes);
    }

    public synchronized List<LspDiagnosticFile> getPendingDiagnostics(int maxPerFile, int maxTotal) {
        return LspDiagnosticRegistry.getInstance().getAndClear(maxPerFile, maxTotal);
    }

    public synchronized void ensureDiagnosticHandler(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        registeredDiagnosticHandlers.add(serverId);
    }

    public synchronized void ensureDiagnosticHandler(LspServerInstance instance) {
        if (instance == null || registeredDiagnosticHandlerInstances.contains(instance)) {
            return;
        }
        instance.addNotificationHandler("textDocument/publishDiagnostics", params -> {
            String serverId = instance.getConfig() != null ? instance.getConfig().getServerId() : "";
            handlePublishDiagnostics(serverId, asObjectMap(params));
        });
        registeredDiagnosticHandlerInstances.add(instance);
        String serverId = instance.getConfig() != null ? instance.getConfig().getServerId() : "";
        ensureDiagnosticHandler(serverId);
    }

    public synchronized boolean hasDiagnosticHandler(String serverId) {
        return serverId != null && registeredDiagnosticHandlers.contains(serverId);
    }

    public synchronized String handlePublishDiagnostics(String serverId, Map<String, Object> params) {
        ensureDiagnosticHandler(serverId);
        return notificationBridge.publishDiagnostics(serverId, params);
    }

    public synchronized boolean isFileOpen(String fileUri) {
        return fileUri != null && documentVersions.containsKey(fileUri);
    }

    public synchronized void openFile(String filePath, String languageId) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved) || GitIgnoreFilter.isIgnored(resolved.toString())) {
            return;
        }
        LspServerInstance instance = getOrStartServer(resolved.toString());
        if (instance == null) {
            return;
        }
        ensureDiagnosticHandler(instance);
        String uri = FileUriUtils.pathToFileUri(resolved.toString());
        documentVersions.put(uri, 0);
        String text;
        try {
            text = Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = "";
        }
        instance.sendNotification("textDocument/didOpen", Map.of(
                "textDocument", Map.of(
                        "uri", uri,
                        "languageId", effectiveLanguageId(languageId, resolved, instance),
                        "version", 0,
                        "text", text
                )
        ));
    }

    public synchronized void changeFile(String filePath, String content) {
        changeFile(filePath, "", content);
    }

    public synchronized void changeFile(String filePath, String languageId, String content) {
        Path resolved = resolvePath(filePath);
        if (resolved == null || !Files.exists(resolved) || GitIgnoreFilter.isIgnored(resolved.toString())) {
            return;
        }
        LspServerInstance instance = getOrStartServer(resolved.toString());
        if (instance == null) {
            return;
        }
        ensureDiagnosticHandler(instance);
        String uri = FileUriUtils.pathToFileUri(resolved.toString());
        int version = documentVersions.getOrDefault(uri, 0) + 1;
        documentVersions.put(uri, version);
        String effectiveContent = content;
        if (effectiveContent == null) {
            try {
                effectiveContent = Files.readString(resolved, StandardCharsets.UTF_8);
            } catch (IOException e) {
                effectiveContent = "";
            }
        }
        instance.sendNotification("textDocument/didChange", Map.of(
                "textDocument", Map.of(
                        "uri", uri,
                        "languageId", effectiveLanguageId(languageId, resolved, instance),
                        "version", version
                ),
                "contentChanges", List.of(Map.of("text", effectiveContent))
        ));
    }

    public synchronized String toFileUri(String filePath) {
        return FileUriUtils.pathToFileUri(filePath);
    }

    public synchronized String fromFileUri(String uri) {
        return FileUriUtils.fileUriToPath(uri);
    }

    private void ensureRelevantServer(Path filePath) {
        String extension = extensionOf(filePath.getFileName().toString());
        for (ScopedLspServerConfig config : configs.values()) {
            if (config.getExtensionToLanguage().containsKey(extension)) {
                start(config.getServerId());
            }
        }
    }

    private List<Path> workspaceFiles() {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isLikelyTextFile)
                    .filter(path -> !isInSkippedDirectory(root.relativize(path)))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    private boolean isLikelyTextFile(Path path) {
        try {
            if (Files.size(path) > 1_000_000) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        String extension = extensionOf(path.getFileName().toString());
        return TEXT_EXTENSIONS.contains(extension);
    }

    private String effectiveLanguageId(String requestedLanguageId, Path path, LspServerInstance instance) {
        if (requestedLanguageId != null && !requestedLanguageId.isBlank()) {
            return requestedLanguageId;
        }
        if (instance != null && instance.getConfig() != null) {
            String extension = extensionOf(path.getFileName().toString());
            String mapped = instance.getConfig().getExtensionToLanguage().get(extension);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }
        return serverIdForPath(path);
    }

    private boolean isInSkippedDirectory(Path relativePath) {
        for (Path part : relativePath) {
            if (SKIP_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private Path resolvePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path candidate = Paths.get(filePath);
        if (!candidate.isAbsolute()) {
            candidate = Paths.get(workspaceRoot).resolve(filePath);
        }
        Path resolved = candidate.normalize().toAbsolutePath();
        Path workspace = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        if (!resolved.startsWith(workspace)) {
            return null;
        }
        return resolved;
    }

    private List<String> readLinesQuietly(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<LspDiagnostic> analyzeFile(Path path) {
        return analyzeContent(path, readLinesQuietly(path));
    }

    private List<LspDiagnostic> analyzeContent(Path path, List<String> lines) {
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        int braceBalance = 0;
        int parenBalance = 0;
        int bracketBalance = 0;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNo = index + 1;
            if (line.contains("TODO") || line.contains("FIXME")) {
                diagnostics.add(new LspDiagnostic(
                        "information",
                        "Pending marker found: " + line.trim(),
                        new LspLocation(path.toString(), lineNo, Math.max(1, markerIndex(line) + 1))
                ));
            }
            braceBalance += count(line, '{') - count(line, '}');
            parenBalance += count(line, '(') - count(line, ')');
            bracketBalance += count(line, '[') - count(line, ']');
        }

        if (braceBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Brace balance is not zero", locationAtEnd(path, lines)));
        }
        if (parenBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Parenthesis balance is not zero", locationAtEnd(path, lines)));
        }
        if (bracketBalance != 0) {
            diagnostics.add(new LspDiagnostic("warning", "Bracket balance is not zero", locationAtEnd(path, lines)));
        }
        return diagnostics;
    }

    private int markerIndex(String line) {
        int todoIndex = line.indexOf("TODO");
        int fixmeIndex = line.indexOf("FIXME");
        if (todoIndex >= 0 && fixmeIndex >= 0) {
            return Math.min(todoIndex, fixmeIndex);
        }
        return Math.max(todoIndex, fixmeIndex);
    }

    private String serverIdForPath(Path path) {
        String extension = extensionOf(path.getFileName().toString());
        for (ScopedLspServerConfig config : configs.values()) {
            if (config.getExtensionToLanguage().containsKey(extension)) {
                return config.getServerId();
            }
        }
        return extension.isBlank() ? "text" : extension;
    }

    private List<Map<String, Object>> toRawDiagnostics(List<LspDiagnostic> diagnostics) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (LspDiagnostic diagnostic : diagnostics) {
            int severity = switch (diagnostic.getSeverity().toLowerCase()) {
                case "error" -> 1;
                case "warning" -> 2;
                case "hint" -> 4;
                default -> 3;
            };
            raw.add(Map.of(
                    "message", diagnostic.getMessage(),
                    "severity", severity,
                    "range", Map.of(
                            "start", Map.of(
                                    "line", Math.max(0, diagnostic.getLocation().getLine() - 1),
                                    "character", Math.max(0, diagnostic.getLocation().getCharacter() - 1)
                            ),
                            "end", Map.of(
                                    "line", Math.max(0, diagnostic.getLocation().getLine() - 1),
                                    "character", Math.max(0, diagnostic.getLocation().getCharacter())
                            )
                    )
            ));
        }
        return raw;
    }

    private String symbolAt(Path path, int line, int character) {
        List<String> lines = readLinesQuietly(path);
        if (line < 1 || line > lines.size()) {
            return null;
        }
        String sourceLine = lines.get(line - 1);
        if (sourceLine.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(character - 1, sourceLine.length() - 1));
        if (!isIdentifierChar(sourceLine.charAt(index))) {
            if (index > 0 && isIdentifierChar(sourceLine.charAt(index - 1))) {
                index--;
            } else {
                return null;
            }
        }
        int start = index;
        while (start > 0 && isIdentifierChar(sourceLine.charAt(start - 1))) {
            start--;
        }
        int end = index + 1;
        while (end < sourceLine.length() && isIdentifierChar(sourceLine.charAt(end))) {
            end++;
        }
        return sourceLine.substring(start, end);
    }

    private boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    private boolean isControlKeyword(String candidate) {
        return Set.of("if", "for", "while", "switch", "catch", "return", "new", "super", "this").contains(candidate);
    }

    private int findScopeEnd(List<String> lines, int startIndex) {
        int braceBalance = 0;
        boolean seenOpeningBrace = false;
        for (int index = startIndex; index < lines.size(); index++) {
            String line = lines.get(index);
            braceBalance += count(line, '{');
            if (count(line, '{') > 0) {
                seenOpeningBrace = true;
            }
            braceBalance -= count(line, '}');
            if (seenOpeningBrace && braceBalance <= 0) {
                return index + 1;
            }
        }
        return Math.min(lines.size(), startIndex + 40);
    }

    private LspLocation firstSymbolLocation(String symbol) {
        for (LspSymbol candidate : getWorkspaceSymbols(symbol, 200)) {
            if (symbol.equals(candidate.getName())) {
                return candidate.getLocation();
            }
        }
        return null;
    }

    private LspLocation enclosingSymbolLocation(LspLocation location) {
        if (location == null) {
            return null;
        }
        Path resolved = resolvePath(location.getFilePath());
        if (resolved == null || !Files.exists(resolved)) {
            return null;
        }
        LspLocation current = null;
        for (LspSymbol symbol : collectDocumentSymbols(resolved)) {
            if (symbol.getLocation() != null && symbol.getLocation().getLine() <= location.getLine()) {
                current = symbol.getLocation();
            } else {
                break;
            }
        }
        return current;
    }

    private List<LspSymbol> buildSymbolTree(Path file, List<String> lines,
                                            Map<Integer, List<SymbolNode>> childrenByParent,
                                            List<Integer> rootIndexes,
                                            List<SymbolNode> nodes) {
        if (nodes.isEmpty()) {
            return List.of();
        }
        List<LspSymbol> roots = new ArrayList<>();
        for (Integer rootIndex : rootIndexes) {
            roots.add(materializeSymbol(file, lines, childrenByParent, nodes.get(rootIndex)));
        }
        roots.sort(Comparator.comparing(symbol -> symbol.getLocation() != null ? symbol.getLocation().getLine() : 0));
        return roots;
    }

    private LspSymbol materializeSymbol(Path file, List<String> lines,
                                        Map<Integer, List<SymbolNode>> childrenByParent,
                                        SymbolNode node) {
        List<SymbolNode> childNodes = childrenByParent.getOrDefault(node.index(), Collections.emptyList());
        List<LspSymbol> children = new ArrayList<>();
        for (SymbolNode childNode : childNodes) {
            children.add(materializeSymbol(file, lines, childrenByParent, childNode));
        }
        children.sort(Comparator.comparing(symbol -> symbol.getLocation() != null ? symbol.getLocation().getLine() : 0));
        LspLocation start = node.location();
        int endLine = determineEndLine(node, childNodes);
        LspRange range = new LspRange(start, new LspLocation(file.toString(), endLine, lineLength(lines, endLine)));
        return new LspSymbol(node.name(), node.kind(), start, range, node.selectionRange(),
                node.containerName(), node.detail(), children);
    }

    private int determineEndLine(SymbolNode node, List<SymbolNode> childNodes) {
        int childEnd = childNodes.stream().mapToInt(SymbolNode::scopeEndLine).max().orElse(node.line());
        return Math.max(node.scopeEndLine(), childEnd);
    }

    private int lineLength(List<String> lines, int lineNumber) {
        if (lineNumber < 1 || lineNumber > lines.size()) {
            return 1;
        }
        return Math.max(1, lines.get(lineNumber - 1).length() + 1);
    }

    private List<LspSymbol> flattenSymbols(List<LspSymbol> symbols) {
        List<LspSymbol> flat = new ArrayList<>();
        for (LspSymbol symbol : symbols) {
            flat.add(symbol);
            if (symbol.getChildren() != null && !symbol.getChildren().isEmpty()) {
                flat.addAll(flattenSymbols(symbol.getChildren()));
            }
        }
        flat.sort(Comparator.comparing(symbol -> symbol.getLocation() != null ? symbol.getLocation().getLine() : 0));
        return flat;
    }

    private LspSymbol symbolCovering(LspLocation location) {
        if (location == null) {
            return null;
        }
        Path resolved = resolvePath(location.getFilePath());
        if (resolved == null || !Files.exists(resolved)) {
            return null;
        }
        LspSymbol match = null;
        for (LspSymbol symbol : flattenSymbols(collectDocumentSymbols(resolved))) {
            if (covers(symbol.getRange(), location)) {
                match = symbol;
            }
        }
        return match;
    }

    private LspRange expandCallSiteRange(LspLocation location, String symbol) {
        if (location == null) {
            return new LspRange(null, null);
        }
        Path resolved = resolvePath(location.getFilePath());
        if (resolved == null || !Files.exists(resolved)) {
            return new LspRange(location, location);
        }
        List<String> lines = readLinesQuietly(resolved);
        if (location.getLine() < 1 || location.getLine() > lines.size()) {
            return new LspRange(location, location);
        }
        String sourceLine = lines.get(location.getLine() - 1);
        int startIndex = Math.max(0, location.getCharacter() - 1);
        int symbolIndex = symbolIndexInLine(sourceLine, symbol, startIndex);
        if (symbolIndex < 0) {
            return new LspRange(location, location);
        }
        CallSiteEnd callSiteEnd = locateCallSiteEnd(lines, location.getLine() - 1, symbolIndex, symbol);
        return new LspRange(
                new LspLocation(location.getFilePath(), location.getLine(), symbolIndex + 1),
                new LspLocation(location.getFilePath(), callSiteEnd.lineNumber(), callSiteEnd.character())
        );
    }

    private CallSiteEnd locateCallSiteEnd(List<String> lines, int startLineIndex, int symbolIndex, String symbol) {
        String sourceLine = lines.get(startLineIndex);
        int searchIndex = Math.min(sourceLine.length(), symbolIndex + Math.max(1, symbol.length()));
        int currentLineIndex = startLineIndex;
        while (currentLineIndex < lines.size()) {
            String currentLine = lines.get(currentLineIndex);
            while (searchIndex < currentLine.length() && Character.isWhitespace(currentLine.charAt(searchIndex))) {
                searchIndex++;
            }
            if (searchIndex < currentLine.length() && currentLine.charAt(searchIndex) == '(') {
                return consumeBalancedCall(lines, currentLineIndex, searchIndex);
            }
            if (searchIndex < currentLine.length()) {
                return new CallSiteEnd(currentLineIndex + 1, Math.max(1, searchIndex + 1));
            }
            currentLineIndex++;
            searchIndex = 0;
        }
        return new CallSiteEnd(startLineIndex + 1, Math.max(1, symbolIndex + Math.max(1, symbol.length())));
    }

    private CallSiteEnd consumeBalancedCall(List<String> lines, int startLineIndex, int startCharacterIndex) {
        int parenBalance = 0;
        int interpolationBraceDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktickQuote = false;
        boolean inBlockComment = false;
        for (int lineIndex = startLineIndex; lineIndex < lines.size(); lineIndex++) {
            String currentLine = lines.get(lineIndex);
            int characterIndex = lineIndex == startLineIndex ? startCharacterIndex : 0;
            while (characterIndex < currentLine.length()) {
                char current = currentLine.charAt(characterIndex);
                char next = characterIndex + 1 < currentLine.length() ? currentLine.charAt(characterIndex + 1) : '\0';
                if (inBlockComment) {
                    if (current == '*' && next == '/') {
                        inBlockComment = false;
                        characterIndex += 2;
                        continue;
                    }
                    characterIndex++;
                    continue;
                }
                if (!inSingleQuote && !inDoubleQuote) {
                    if (current == '/' && next == '*') {
                        inBlockComment = true;
                        characterIndex += 2;
                        continue;
                    }
                    if (current == '/' && next == '/') {
                        break;
                    }
                }
                if (inBacktickQuote && interpolationBraceDepth == 0 && isInterpolationStart(currentLine, characterIndex)) {
                    interpolationBraceDepth = 1;
                    characterIndex += 2;
                    continue;
                }
                if (inBacktickQuote && interpolationBraceDepth == 0) {
                    if (current == '`' && !isEscaped(currentLine, characterIndex)) {
                        inBacktickQuote = false;
                    }
                    characterIndex++;
                    continue;
                }
                if (!inDoubleQuote && !inBacktickQuote && current == '\'' && !isEscaped(currentLine, characterIndex)) {
                    inSingleQuote = !inSingleQuote;
                    characterIndex++;
                    continue;
                }
                if (!inSingleQuote && !inBacktickQuote && current == '"' && !isEscaped(currentLine, characterIndex)) {
                    inDoubleQuote = !inDoubleQuote;
                    characterIndex++;
                    continue;
                }
                if (!inSingleQuote && !inDoubleQuote && current == '`' && !isEscaped(currentLine, characterIndex)) {
                    inBacktickQuote = !inBacktickQuote;
                    characterIndex++;
                    continue;
                }
                if (inSingleQuote || inDoubleQuote) {
                    characterIndex++;
                    continue;
                }
                if (inBacktickQuote && interpolationBraceDepth > 0) {
                    if (current == '{') {
                        interpolationBraceDepth++;
                    } else if (current == '}') {
                        interpolationBraceDepth--;
                        if (interpolationBraceDepth == 0) {
                            characterIndex++;
                            continue;
                        }
                    }
                }
                if (current == '(') {
                    parenBalance++;
                } else if (current == ')') {
                    parenBalance--;
                    if (parenBalance == 0) {
                        return new CallSiteEnd(lineIndex + 1, characterIndex + 2);
                    }
                }
                characterIndex++;
            }
        }
        String lastLine = lines.get(startLineIndex);
        return new CallSiteEnd(startLineIndex + 1, Math.max(1, Math.min(lastLine.length() + 1, startCharacterIndex + 2)));
    }

    private boolean isEscaped(String line, int index) {
        if (line == null || index <= 0 || index > line.length()) {
            return false;
        }
        int slashCount = 0;
        for (int cursor = index - 1; cursor >= 0 && line.charAt(cursor) == '\\'; cursor--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private boolean isInterpolationStart(String line, int index) {
        if (line == null || index < 0 || index + 1 >= line.length()) {
            return false;
        }
        return line.charAt(index) == '$' && line.charAt(index + 1) == '{' && !isEscaped(line, index);
    }

    private int symbolIndexInLine(String sourceLine, String symbol, int fallbackStart) {
        if (sourceLine == null || sourceLine.isEmpty() || symbol == null || symbol.isBlank()) {
            return -1;
        }
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b");
        Matcher matcher = pattern.matcher(sourceLine);
        while (matcher.find()) {
            if (matcher.start() <= fallbackStart && fallbackStart < matcher.end()) {
                return matcher.start();
            }
        }
        matcher.reset();
        if (matcher.find()) {
            return matcher.start();
        }
        return -1;
    }

    private boolean covers(LspRange range, LspLocation location) {
        if (range == null || range.getStart() == null || range.getEnd() == null || location == null) {
            return false;
        }
        if (!Objects.equals(range.getStart().getFilePath(), location.getFilePath())) {
            return false;
        }
        int line = location.getLine();
        return range.getStart().getLine() <= line && line <= range.getEnd().getLine();
    }

    private LspCallHierarchyItem toCallHierarchyItem(LspSymbol symbol, String fallbackDetail) {
        String detail = symbol.getDetail().isBlank() ? fallbackDetail : symbol.getDetail();
        String uri = symbol.getLocation() != null ? symbol.getLocation().getFilePath() : "";
        LspRange range = symbol.getRange() != null ? symbol.getRange()
                : new LspRange(symbol.getLocation(), symbol.getLocation());
        LspRange selectionRange = symbol.getSelectionRange() != null ? symbol.getSelectionRange()
                : new LspRange(symbol.getLocation(), symbol.getLocation());
        return new LspCallHierarchyItem(symbol.getName(), symbol.getKind(), detail, uri, range, selectionRange);
    }

    private String callerFallbackDetail(LspSymbol callerSymbol, String callerName) {
        if (callerSymbol == null) {
            return callerName;
        }
        return callerSymbol.getDetail().isBlank() ? callerName : callerSymbol.getDetail();
    }

    private String calleeFallbackDetail(LspSymbol calleeSymbol, String calleeName) {
        if (calleeSymbol == null) {
            return calleeName;
        }
        return calleeSymbol.getDetail().isBlank() ? calleeName : calleeSymbol.getDetail();
    }

    private String inferCallHierarchyKind(Path resolved, LspLocation location, String symbolName) {
        if (resolved == null || location == null || symbolName == null || symbolName.isBlank()) {
            return "function";
        }
        LspSymbol symbol = symbolCovering(location);
        if (symbol != null && symbol.getKind() != null && !symbol.getKind().isBlank()) {
            return symbol.getKind();
        }
        List<String> lines = readLinesQuietly(resolved);
        int lineNumber = location.getLine();
        if (lineNumber >= 1 && lineNumber <= lines.size()) {
            String sourceLine = lines.get(lineNumber - 1).trim();
            if (sourceLine.contains("class ") || sourceLine.contains("interface ") || sourceLine.contains("record ")) {
                return "type";
            }
            if (sourceLine.contains(symbolName + "(")) {
                return "function";
            }
        }
        return Character.isUpperCase(symbolName.charAt(0)) ? "type" : "function";
    }

    private LspCallHierarchyItem fallbackCallHierarchyItem(String name, String kind, String detail, LspLocation location) {
        LspRange range = new LspRange(location, location);
        return new LspCallHierarchyItem(name, kind, detail,
                location != null ? location.getFilePath() : "", range, range);
    }

    private ScopeEntry nearestParent(List<ScopeEntry> scopeStack, String kind, int indent) {
        for (int index = scopeStack.size() - 1; index >= 0; index--) {
            ScopeEntry candidate = scopeStack.get(index);
            if (candidate.indent() < indent || "type".equals(candidate.kind()) || !"type".equals(kind)) {
                return candidate;
            }
        }
        return null;
    }

    private void closeScopesForLine(List<ScopeEntry> scopeStack, int lineNumber, int indent) {
        while (!scopeStack.isEmpty()) {
            ScopeEntry last = scopeStack.get(scopeStack.size() - 1);
            if (lineNumber > last.endLine() || indent <= last.indent()) {
                scopeStack.remove(scopeStack.size() - 1);
                continue;
            }
            break;
        }
    }

    private boolean opensScope(String kind, String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if ("type".equals(kind)) {
            return true;
        }
        return trimmed.endsWith(":") || trimmed.contains("{");
    }

    private int findSymbolScopeEnd(List<String> lines, int startIndex, int indent, String line, String kind) {
        String trimmed = line.trim();
        if (trimmed.endsWith(":")) {
            return findIndentScopeEnd(lines, startIndex, indent);
        }
        if (trimmed.contains("{")) {
            return findBraceScopeEnd(lines, startIndex);
        }
        if ("type".equals(kind)) {
            return findBlockLikeTypeEnd(lines, startIndex, indent);
        }
        return startIndex + 1;
    }

    private int findBraceScopeEnd(List<String> lines, int startIndex) {
        int braceBalance = 0;
        boolean seenOpeningBrace = false;
        for (int index = startIndex; index < lines.size(); index++) {
            String text = lines.get(index);
            int opened = count(text, '{');
            int closed = count(text, '}');
            if (opened > 0) {
                seenOpeningBrace = true;
            }
            braceBalance += opened - closed;
            if (seenOpeningBrace && braceBalance <= 0) {
                return index + 1;
            }
        }
        return lines.size();
    }

    private int findIndentScopeEnd(List<String> lines, int startIndex, int indent) {
        for (int index = startIndex + 1; index < lines.size(); index++) {
            String text = lines.get(index);
            if (text.trim().isEmpty()) {
                continue;
            }
            if (indentationOf(text) <= indent) {
                return index;
            }
        }
        return lines.size();
    }

    private int findBlockLikeTypeEnd(List<String> lines, int startIndex, int indent) {
        String text = lines.get(startIndex).trim();
        if (text.endsWith(":")) {
            return findIndentScopeEnd(lines, startIndex, indent);
        }
        if (text.contains("{")) {
            return findBraceScopeEnd(lines, startIndex);
        }
        return startIndex + 1;
    }

    private int indentationOf(String line) {
        int indent = 0;
        while (indent < line.length()) {
            char current = line.charAt(indent);
            if (current != ' ' && current != '\t') {
                break;
            }
            indent++;
        }
        return indent;
    }

    private boolean sameFileLine(LspLocation location, String filePath, int line) {
        return location != null && Objects.equals(location.getFilePath(), filePath) && location.getLine() == line;
    }

    private List<LspDiagnostic> filterDiagnostics(List<LspDiagnostic> diagnostics, String severity) {
        if (severity == null || severity.isBlank() || "all".equalsIgnoreCase(severity)) {
            return diagnostics;
        }
        return diagnostics.stream()
                .filter(item -> severity.equalsIgnoreCase(item.getSeverity()))
                .toList();
    }

    private LspLocation locationAtEnd(Path path, List<String> lines) {
        int line = Math.max(1, lines.size());
        int character = lines.isEmpty() ? 1 : lines.get(lines.size() - 1).length() + 1;
        return new LspLocation(path.toString(), line, character);
    }

    private int count(String text, char target) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    private boolean sameLocation(LspLocation left, LspLocation right) {
        return left != null && right != null
                && Objects.equals(left.getFilePath(), right.getFilePath())
                && left.getLine() == right.getLine()
                && left.getCharacter() == right.getCharacter();
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 && index < fileName.length() - 1 ? fileName.substring(index + 1).toLowerCase() : "";
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return converted;
    }

    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            converted.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return converted;
    }

    private LspLocation locationFromProtocolResponse(Object response) {
        if (response instanceof List<?> list) {
            for (Object entry : list) {
                LspLocation candidate = locationFromProtocolResponse(entry);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (response == null) {
            return null;
        }
        Map<String, Object> payload = asObjectMap(response);
        if (payload.isEmpty()) {
            return null;
        }
        String uri = stringValue(payload.get("uri"));
        Map<String, Object> range = asObjectMap(payload.get("range"));
        if ((uri == null || uri.isBlank()) || range.isEmpty()) {
            uri = stringValue(payload.get("targetUri"));
            range = asObjectMap(payload.get("targetSelectionRange"));
            if (range.isEmpty()) {
                range = asObjectMap(payload.get("targetRange"));
            }
        }
        if ((uri == null || uri.isBlank()) || range.isEmpty()) {
            return null;
        }
        Map<String, Object> start = asObjectMap(range.get("start"));
        if (start.isEmpty()) {
            return null;
        }
        String filePath = FileUriUtils.fileUriToPath(uri);
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        return new LspLocation(
                filePath,
                asInt(start.get("line"), 0) + 1,
                asInt(start.get("character"), 0) + 1
        );
    }

    private List<LspLocation> locationsFromProtocolResponse(Object response) {
        if (!(response instanceof List<?> list)) {
            LspLocation single = locationFromProtocolResponse(response);
            return single != null ? List.of(single) : List.of();
        }
        List<LspLocation> locations = new ArrayList<>();
        for (Object entry : list) {
            LspLocation location = locationFromProtocolResponse(entry);
            if (location != null) {
                locations.add(location);
            }
        }
        return locations;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record PatternRule(Pattern pattern, int nameGroup, String kind) {
    }

    private record ScopeEntry(int nodeIndex, String qualifiedName, String kind, int indent, int endLine) {
    }

    private record SymbolNode(int index, String name, String kind, LspLocation location, LspRange selectionRange,
                              String containerName, String detail, int indent, int line, int scopeEndLine,
                              String qualifiedName) {
    }

    private record CallSiteEnd(int lineNumber, int character) {
    }
}
