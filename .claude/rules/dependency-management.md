---
description: Maven dependency, version, scope, banned-library, plugin, and CVE/upgrade rules for agent-core-java.
language: english
paths:
  - "pom.xml"
  - "src/main/java/**/*.java"
  - "src/test/java/**/*.java"
  - "examples/**/*.java"
  - "documents/**/*.md"
  - "Third_Party_Open_Source_Software_Notice.txt"
---

# Dependency Management Rules

`pom.xml` is the canonical source of truth. Version inventory lives there
in `<properties>` — read it directly rather than relying on this doc.

## Core Rules

1. **All versions as `${property}`** — never hard-code in `<dependency>` or
   `<plugin>`. Naming: `<libname>.version` (library style, e.g.
   `${jackson.version}`, `${bouncy-castle.version}`); plugins use
   `${<plugin-name>.version}`.
2. **One property per library family** — all Jackson artifacts share
   `${jackson.version}`; all Testcontainers artifacts share
   `${testcontainers.version}`. Never create per-artifact properties
   (`${jackson-databind.version}`) for related artifacts.
3. **No duplicate-purpose libraries** — check `pom.xml` before adding.
   Current stack: Jackson (JSON), OkHttp (HTTP), SLF4J+Logback (logging),
   SnakeYAML, Bouncy Castle, Milvus+pgvector (vector DB), PostgreSQL+SQLite
   (RDBMS), Pulsar (MQ), PDFBox+POI (docs), OpenAI Java+DashScope (AI),
   Reactor (reactive), JUnit+Mockito+AssertJ+Testcontainers+H2 (test).
4. **Scope** — default: runtime framework deps; `provided`: annotation
   processors (Lombok) or container-resolved annotations; `test`: test-only;
   `optional`: opt-in extensions. Avoid `runtime` and `system`.
5. **No `*-SNAPSHOT` deps on `main`.** Project's own SNAPSHOT version is OK
   during dev cycles.
6. **Plugin versions locked** — Maven 3.9+ requires it. Prefer
   `<pluginManagement>` for inheritance.
7. **License** — must be Apache 2.0 / MIT / BSD-2 / EPL-LGPL compatible.
   Append to `Third_Party_Open_Source_Software_Notice.txt` when adding.

## Banned Patterns

| Banned | Use instead | Rule |
|---|---|---|
| `gson`, `org.json:json`, `jsonpath` | Jackson | Single JSON lib |
| `org.apache.httpcomponents:*`, `java.net.HttpClient` | OkHttp | Single HTTP client |
| `commons-io`, `commons-lang3` | JDK NIO / JDK 17 native | Avoid Apache Commons |
| `guava` | JDK 17 (`List.of`, `Map.of`, `Optional`) | Avoid Guava |
| JUnit 4 (`junit:junit`) | JUnit 5 (`org.junit.jupiter`) | Single test framework |
| `hamcrest-core`, `jsonassert` | AssertJ | Single assertion lib |
| `Executors.newCachedThreadPool/newFixedThreadPool` | `ThreadPoolExecutor` (bounded queue, named threads) | `X.CON.06` |
| `log4j:*`, `java.util.logging` direct | SLF4J facade | `G.LOG.01` |
| `SimpleDateFormat` | `DateTimeFormatter` | `X.CON.01` |

## Lombok

- `provided` scope; not on runtime classpath.
- Do NOT add Lombok annotations (`@Data`, `@Builder`) to new files — prefer
  explicit code, records, or hand-written builders. Existing annotations stay.
- Must be in `maven-compiler-plugin` `<annotationProcessorPaths>` (already
  configured).

## OK / BAD (canonical)

**OK** — version property + shared family version:
```xml
<properties>
    <jackson.version>2.17.0</jackson.version>
</properties>
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
        <version>${jackson.version}</version>
    </dependency>
</dependencies>
```

**BAD** — hard-coded version, or per-artifact properties:
```xml
<!-- Hard-coded -->
<version>2.17.0</version>

<!-- Per-artifact properties for related artifacts -->
<jackson-databind.version>2.17.0</jackson-databind.version>
<jackson-datatype-jsr310.version>2.17.0</jackson-datatype-jsr310.version>
```

## Upgrade & CVE Check

Run before merging dependency changes:
```bash
mvn versions:display-dependency-updates   # quarterly or pre-release
mvn versions:display-plugin-updates
mvn dependency:tree                         # catch transitive conflicts
mvn dependency:analyze                      # unused declared deps (review before removing)
```
CVE: run `dependency-check-maven:check` or Snyk/trivy pre-release. Fix all
HIGH/CRITICAL before merge.

## Notes

- BOM import in `<dependencyManagement>` is Maven-recommended for
  multi-artifact libraries (Jackson, Testcontainers, Reactor). Current project
  uses shared version properties — migration optional; new multi-artifact
  libs should prefer BOM from the start.
- `X.DEP.02`: avoid large libs for one method — inline or pick a small lib.