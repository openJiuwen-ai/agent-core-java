# JIT and Compilation Optimization

This file supplements SKILL.md's compilation optimization content, providing in-depth coverage of JIT tiered compilation, inlining, loop unrolling, C2 vs Graal, etc. Read on demand when users ask "how does JIT work" or "why does it get faster after warmup".

## JIT Basic Principles

JVM interprets bytecode -> collects method invocation frequency -> hot methods trigger JIT compilation -> compiled code executes as machine code directly.

**Advantages**:
- Interpreted execution has fast startup; compiled execution has fast runtime
- JIT does aggressive optimization based on runtime profiling (virtual method monomorphization, branch prediction)
- Has an advantage over AOT (C++) through runtime feedback

**Disadvantages**:
- Warmup overhead
- Single compilation may be deoptimized due to inaccurate profile

## Tiered Compilation

JDK8+ enables `-XX:+TieredCompilation` by default; JVM uses 5 compilation tiers:

| Tier | Interpreter | C1 | C2 | Purpose |
|---|---|---|---|---|
| 0 | Yes | | | Interpreted execution, collects profile |
| 1 | | Yes | | C1 compiled, no profile |
| 2 | | Yes | | C1 + lightweight profile |
| 3 | | Yes | | C1 + full profile (most methods stop here) |
| 4 | | | Yes | C2 compiled, aggressive optimization |

**Flow**:
1. Method starts at tier 0 in interpreted mode
2. Invocation count reaches threshold -> tier 3 (C1 + profile)
3. C2 queue compiles -> tier 4 (C2 aggressive optimization)
4. Profile invalidated (e.g., new virtual method branch) -> deoptimize back to tier 0

**Thresholds**:
- Tier 3: ~1500 invocations
- Tier 4: ~10000 invocations (adaptive under tiered compilation)

### C1 (Client Compiler)

- Fast compilation, medium code quality
- Simple optimizations: method inlining, constant folding
- Suitable for short-running applications / CLI

### C2 (Server Compiler)

- Slow compilation, aggressive optimization
- Advanced optimizations: escape analysis + scalar replacement + lock elision + loop unrolling + virtual method monomorphization
- Suitable for long-running applications

### Graal (JDK10+ experimental)

- Java-written C2 replacement
- More aggressive optimizations (20%+ faster than C2 in some scenarios)
- Enable: `-XX:+UnlockExperimentalVMOptions -XX:+UseJVMCICompiler`
- Partially stable in JDK17+, but still experimental

## Inlining

Inlining is JIT's most important optimization: replacing method calls with the method body itself, saving call overhead + enabling subsequent optimizations.

### Inlining Benefits

- Save call overhead (stack frame, parameter passing)
- After inlining, more optimizations become possible (constant folding, dead code elimination)
- Prerequisite for other optimizations (escape analysis depends on inlining)

### Inlining Conditions

| Condition | Default Value | Description |
|---|---|---|
| Method size | < 35 bytecodes (`-XX:MaxInlineSize`) | Small methods prioritized |
| Frequency | Hot (> CompileThreshold) | High invocation frequency |
| Type statically determinable | Non-virtual / single-implementation virtual | Virtual method inlining requires CHA (Class Hierarchy Analysis) |

### Virtual Method Inlining

Virtual methods (non-final, non-private) cannot be inlined by default -- JIT doesn't know which implementation at runtime. But JIT uses CHA (Class Hierarchy Analysis):

- **Monomorphic**: Only one implementation of the method -> inline
- **Bimorphic**: Two implementations -> inline + branch
- **Megamorphic**: > 2 implementations -> no inlining, virtual method table lookup

**Help inlining**:
- Add `final` to classes/methods -> compile-time determines non-virtual call
- Avoid unnecessary interfaces (let JIT determine monomorphic)
- Don't use complex inheritance hierarchies on hot paths

### -XX:MaxInlineSize vs -XX:FreqInlineSize

- `MaxInlineSize=35`: Regular method inlining upper limit
- `FreqInlineSize=325`: Hot method inlining upper limit (larger methods can still be inlined if hot)

### Inlining Log

```bash
-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining
```

Output looks like:
```
  @ 27   java.lang.String::hashCode (49 bytes)   inline (hot)
  @ 12   java.util.HashMap::getNode (110 bytes)   inline (hot)
  @ 7   com.foo.Bar::cachedCall (5 bytes)   inline (hot)
```

`inline (hot)` = inlining succeeded; `too big` = method too large; `not inline (no static binding)` = polymorphic.

## Loop Optimization

### Loop Unrolling

Copy the loop body multiple times to reduce loop overhead:

```java
// Before optimization
for (int i = 0; i < 1000; i++) { ... }

// After unrolling (4 iterations of work per loop)
for (int i = 0; i < 1000; i += 4) {
    body(); body(); body(); body();
}
```

C2 unrolls small loops by default; adjust via `-XX:LoopMaxUnroll=64`.

### Loop Peeling

Peel off the first/last iteration of the loop to simplify the middle loop.

### Loop Vectorization

C2 converts contiguous operations into SIMD instructions:

```java
// Array element-wise addition
for (int i = 0; i < n; i++) {
    c[i] = a[i] + b[i];
}

// After vectorization (CPU SIMD instructions, process 4-16 at once)
// c[i:i+8] = a[i:i+8] + b[i:i+8]
```

**Trigger conditions**:
- Contiguous array access
- Primitive element types (int/long/float/double)
- Simple loop body
- No dependencies (previous result doesn't affect next)

### Loop Unrolling Limitations

- Too large won't unroll (default <= 64 bytecodes)
- Exceptions prevent unrolling
- Synchronized blocks prevent unrolling

## Escape Analysis and Optimization

Escape analysis is C2's core optimization; see `code_level_optimization.md`'s "Escape Analysis" section for details.

**Core benefits**:
- Stack allocation -> no heap, no GC
- Scalar replacement -> object fields split into local variables
- Lock elision -> uncontended locks eliminated

**Parameters**:
- `-XX:+DoEscapeAnalysis` (enabled by default since JDK8)
- `-XX:+EliminateAllocations`: Stack allocation (depends on escape analysis, enabled by default)
- `-XX:+EliminateLocks`: Lock elision (depends on escape analysis, enabled by default)

## Branch Prediction

JIT uses profile data for branch prediction:

```java
if (log.isDebugEnabled()) {  // JIT sees isDebugEnabled mostly returns false
    log.debug("...");        // Entire if block code is not optimized
}
```

**Benefits**:
- Frequently-taken branch placed first, CPU branch prediction hit rate is high
- Rarely-taken branch may be treated as "cold" code, performance nearly eliminated

## Devirtualization

Virtual method inlining depends on CHA, but class hierarchy may change at runtime (dynamic loading). JIT conservatively:
- Inlines monomorphic methods
- Assumes class hierarchy is stable -> if newly loaded class breaks assumption -> deoptimization

**GraalVM Native Image**: AOT compilation knows all classes at build time, can aggressively devirtualize.

## Deoptimization

JIT does aggressive optimization based on runtime profile. If profile is invalidated -> deoptimize back to interpreted execution.

**Trigger conditions**:
- Newly loaded class breaks CHA assumption (e.g., new virtual method implementation)
- Exception path triggered
- `-XX:+CompileCommand=exclude,Class.method` excludes certain methods

**Manifestation**:
- Sudden performance drop (compiled version discarded)
- Re-profiling + recompilation

**Debugging**:
```bash
-XX:+TraceDeoptimization
-XX:+PrintDeoptimization
```

## Code Cache

JIT-compiled machine code is stored in the Code Cache:

| Parameter | Meaning | Default |
|---|---|---|
| `-XX:ReservedCodeCacheSize` | Upper limit | 240M (JDK9+) / 48M (JDK8) |
| `-XX:InitialCodeCacheSize` | Initial | 160K |
| `-XX:CodeCacheExpansionSize` | Expansion step | 64K |

**Behavior when full**:
- No more JIT compilation of new methods -> performance degradation
- Previously compiled methods may be discarded -> deoptimization
- No error reported, hard to notice

**Monitoring**:
```bash
jcmd <pid> Compiler.CodeCache
```

**Tuning**:
- JDK8: Default 48M is too small, recommend 256M
- JDK9+: Default 240M is sufficient for most cases
- Large applications (100k+ classes): increase to 512M

## JIT Tuning Parameter Summary

### Enable / Disable

```
-XX:+TieredCompilation         # Tiered compilation (enabled by default in JDK8+)
-XX:-TieredCompilation         # Disable, use only C2 (short-running may benefit)
-XX:+PrintCompilation          # Print JIT compilation log
-XX:+PrintInlining             # Print inlining decisions (requires +UnlockDiagnosticVMOptions)
-XX:+PrintCodeCache            # Print Code Cache status
-XX:+PrintDeoptimization       # Print deoptimization events
-XX:+TraceClassLoading         # Print class loading
```

### Thresholds

```
-XX:CompileThreshold=10000             # C2 compilation threshold
-XX:BackEdgeThreshold=100000           # OSR threshold (loop back edge)
-XX:OnStackReplacePercentage=140       # OSR ratio
-XX:MaxInlineSize=35                   # Max bytecodes for inlined method
-XX:FreqInlineSize=325                 # Max bytecodes for hot method inlining
-XX:LoopMaxUnroll=64                   # Max bytecodes for loop unrolling
```

### Compiler Selection

```
-XX:+UseC1                      # Use C1 (disable C2)
-XX:+UseC2                      # Use C2 (disable C1)
-XX:+UseJVMCICompiler           # Use Graal (experimental)
-XX:TieredStopAtLevel=4         # Compile up to tier 4 (default)
-XX:TieredStopAtLevel=1         # Use only C1, stop at tier 1 (short-running)
```

## Warmup

JIT needs time to warm up:
- Short-running applications (CLI, batch): May never compile to tier 4 -> slow
- Long-running applications (services): Stable after warmup

### Short-Running Application Optimization

**Option 1: Tiered compilation + C1 priority**
```
-XX:+TieredCompilation -XX:TieredStopAtLevel=1
```

**Option 2: CDS (Class Data Sharing)**
```
# Generate shared archive
java -Xshare:dump

# Generate shared archive
java -Xshare:dump

# Use at runtime
java -Xshare:on -jar app.jar
```

**Option 3: AppCDS** (Custom class CDS)
```
# JDK13+
java -XX:ArchiveClassesAtExit=app.jsa -cp app.jar App

# Run
java -XX:SharedArchiveFile=app.jsa -cp app.jar App
```

**Option 4: GraalVM Native Image**
```bash
native-image -jar app.jar app
./app
```
- AOT compilation, millisecond startup, no JIT
- Suitable for CLI / function computing
- Sacrifices some peak performance for startup speed

### Long-Running Application Optimization

**Warmup methods**:
- Send simulated traffic for 5-10 minutes after startup
- Or manually loop-call hot methods in `main`

**Note**: Production environments with G1/ZGC + tiered compilation default configuration is fine; don't randomly adjust JIT parameters.

## Monitoring JIT Status

```bash
# View Code Cache usage
jcmd <pid> Compiler.CodeCache

# View compiled methods
jcmd <pid> Compiler.list

# View JIT compilation statistics
jcmd <pid> Compiler.stats

# View C2 queue
jcmd <pid> Compiler.queue

# View JVM startup parameters
jcmd <pid> VM.flags
```

## GraalVM Comparison (JDK 17 baseline)

| Dimension | HotSpot JIT (C1/C2) | GraalVM Native Image | GraalVM JIT (Graal) |
|---|---|---|---|
| Compilation timing | Runtime | Build time (AOT) | Runtime (Graal) |
| Startup speed | Slow | Millisecond-level | Slow |
| Peak performance | High | Medium (80-95% in some scenarios) | High (20%+ faster than C2 in some scenarios) |
| Memory usage | High | Low | High |
| Reflection support | Full | Requires configuration (reachability metadata) | Full |
| Applicable | Long-running services | CLI / function computing / microservices | Experimental |

**JDK 17 GraalVM Native Image status**:
- Production-ready
- Millisecond startup, single binary deployment
- Reflection requires `reachability-metadata.json` or `@RegisterReflectionForBinding`
- Spring Boot 3 / Micronaut / Quarkus native support
- Sacrifices about 5-20% peak performance for startup speed

**Using GraalVM JIT (experimental)**:
```
-XX:+UnlockExperimentalVMOptions -XX:+UseJVMCICompiler
```

## Common JIT Optimization Quick Reference

| Optimization | Benefit | Trigger Condition |
|---|---|---|
| Method inlining | 5-10x | Small method + hot |
| Stack allocation | Reduce GC | Object doesn't escape |
| Scalar replacement | Object disappears | Object doesn't escape |
| Lock elision | No lock overhead | Object in synchronized block doesn't escape |
| Lock coarsening | Reduce lock count | Adjacent synchronized blocks |
| Loop unrolling | Reduce loop overhead | Small loop |
| Loop vectorization | SIMD acceleration | Contiguous array access |
| Branch prediction | Pipeline not broken | Profile data |
| Virtual method monomorphization | Inlining | Single implementation |
| Dead code elimination | Remove unexecuted code | Compile-time analysis |
| Constant folding | Compile-time computation | Operands are constants |

## JDK 17 JIT Features

### Tiered Compilation Enabled by Default

JDK 17 has tiered compilation enabled by default (`-XX:+TieredCompilation`), all 5 tiers active. Don't disable for most scenarios.

**Short-running application exception**:
- CLI tools, batch jobs may never warm up to tier 4
- Consider `-XX:TieredStopAtLevel=1` (use only C1)
- Or GraalVM Native Image (AOT)

### Sealed Class Helps Devirtualization

JDK 17 sealed class knows all implementations at compile time -> JIT monomorphic inlining:

```java
public sealed interface Shape permits Circle, Rectangle, Triangle {}

// JIT sees Shape actually has only 3 implementations
// If only Circle is used at runtime -> monomorphic inlining
// Circle + Rectangle used -> bimorphic (branch + inline)
```

**Compared to non-sealed**: Non-sealed interfaces theoretically have unlimited implementations -> JIT doesn't dare to aggressively inline.

### Pattern Matching Compilation Optimization

JDK 17 pattern matching for switch (preview), compiled to `tableswitch`:

```java
switch (shape) {
    case Circle c -> ...;       // Compiled to jump table, no instanceof chain
    case Rectangle r -> ...;
    case Triangle t -> ...;
}
```

Faster than `if instanceof + cast` chain (multiple type checks -> 1).

### Vector API (incubator)

JDK 17 incubator (`--add-modules jdk.incubator.vector`), explicit SIMD:

```java
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_256;

void vectorAdd(float[] a, float[] b, float[] c) {
    int i = 0;
    int upper = SPECIES.loopBound(a.length);
    for (; i < upper; i += SPECIES.length()) {
        FloatVector va = FloatVector.fromArray(SPECIES, a, i);
        FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
        va.add(vb).intoArray(c, i);
    }
    for (; i < a.length; i++) c[i] = a[i] + b[i];
}
```

**Compared to C2 auto-vectorization**:
- C2 auto: Depends on JIT judgment, unstable (code change may disable vectorization)
- Vector API: Explicit vectorization, cross-platform guarantee

**JDK 17 status**: Incubator, API may change. JDK 19+ preview.

### Compact Strings (JDK 9+ default)

JDK 9+ String internally uses `byte[]` + coder flag:
- ASCII strings: Latin-1 encoding, 1 byte/character (JDK 8 was 2 bytes)
- Non-ASCII: UTF-16, 2 bytes/character

**Benefit**: ASCII string memory halved. Enabled by default in JDK 17, no configuration needed.

### CDS / AppCDS

Class Data Sharing, startup acceleration:

```bash
# JDK 17: Generate default CDS archive
java -Xshare:dump

# Use at runtime
java -Xshare:on -jar app.jar

# AppCDS (custom classes)
java -XX:DumpLoadedClassList=classes.lst -jar app.jar
java -Xshare:dump -XX:SharedClassList=classes.lst -XX:SharedArchiveFile=app.jsa -jar app.jar
java -Xshare:on -XX:SharedArchiveFile=app.jsa -jar app.jar
```

**Benefit**: Startup time reduced 30-50%, memory usage lowered (multiple processes share metadata).

## Anti-patterns

- Short-running application tuning `-XX:+TieredCompilation` expecting warmup optimization -> JIT may not take effect, actually slower
- Code Cache set too small -> deoptimization
- Complex inheritance hierarchies on hot paths -> virtual method polymorphism, no inlining
- Heavy reflection -> JIT has difficulty optimizing
- Lambda capturing variables on hot path -> creates Lambda object each time
- JDK 17 waiting for C2 auto-vectorization -> unstable, use Vector API on critical paths
- JDK 17 using Lombok @Data -> Records are compiler-generated, no reflection
- JDK 17 `instanceof` chain + cast -> sealed + pattern matching
- JDK 17 reflective access to non-exported packages without `--add-opens` -> `InaccessibleObjectException`

## Reference Documentation

- OpenJDK HotSpot documentation: `https://openjdk.org/groups/hotspot/`
- JIT Watcher (JIT visualization): `https://github.com/AdoptOpenJDK/jitwatch`
- GraalVM: `https://www.graalvm.org/`
- Project Leyden (AOT evolution): `https://openjdk.org/projects/leyden/`
