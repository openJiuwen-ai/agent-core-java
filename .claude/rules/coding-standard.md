# Java Coding Standards

Huawei CodeArts Check Java rule set. 124 base rules (`G.*`) + extended rules (`X.*`). Security rules (marked `[S]`) carry higher risk and should be prioritized. For detailed descriptions and OK/BAD code examples, use `/coding-standard`.

## Quick Self-Check (covers 80% of common issues)

1. `G.NAM.03` Class/enum/interface: PascalCase
2. `G.NAM.04` Method names: camelCase
3. `G.NAM.05` Constants: ALL_UPPERCASE with underscores
4. `G.ERR.01` No empty catch blocks
5. `G.ERR.02` No catching Throwable/Exception/RuntimeException directly
6. `G.ERR.06` [S] Pass original exception as cause when re-throwing
7. `G.FMT.07` Avoid empty blocks; add comment when necessary
8. `G.LOG.01` Use SLF4J facade, not Log4j/Logback directly
9. `G.LOG.02` Logger: private static final
10. `G.OBJ.06` Override hashCode when overriding equals
11. `G.PRM.05` No unnecessary objects
12. `G.CON.07` New threads must specify a name

## Scenario → Rules

| Scenario | Rules |
|---|---|
| Service/business classes | `G.OBJ.01-08`, `G.NAM.03/04`, `G.CMT.01/03` |
| Exception handling | `G.ERR.01-09`, `G.CTL.03` |
| Multi-threaded code | `G.CON.01/05-12`, `G.TYP.03`, `X.CON.01-06` |
| Logging | `G.LOG.01-04` |
| Collections/generics | `G.COL.02-04`, `G.PRM.01`, `X.COL.01-04` |
| IO operations | `G.PRM.07`, `G.FIO.01-04`, `G.TYP.09` |
| Method signatures | `G.MET.01-07`, `G.NAM.04`, `G.ERR.07` |
| Constants/enums | `G.NAM.05`, `G.DCL.04/05`, `G.FMT.15` |
| Overriding equals | `G.OBJ.06`, `G.EXP.04` |
| Serializable classes | `G.SER.01/02/04/05/07` |
| Tests | `X.TST.01-06` |
| SQL/database | `X.SQL.01-07` |
| Code Review | Self-Check List first, then by scenario |

## All Rules

### G.CMT Comments — `G.CMT.01` Public/protected must have Javadoc · `02` Top-level class Javadoc: description+date/version · `03` Method Javadoc: description+@param/@return/@throws · `04` No empty formatted method header comments · `05` File header: copyright/license · `06` Blank line/space between comments and code · `07` No TODO/FIXME in production code

### G.COL Collections — `G.COL.02` Prefer generic collections over arrays · `03` Use bounds to restrict generic types · `04` No remove/add inside foreach

### G.CON Concurrency — `G.CON.01` [S] Avoid synchronization pitfalls (no Lock/Condition as synchronized lock, no class objects as lock, no reusable object locks, no public lock objects, no instance locks for static data) · `05` [S] No overriding thread-safe with non-thread-safe · `06` Use concurrency utils, not wait/notify · `07` New threads must have a name · `08` Use setUncaughtExceptionHandler · `09` No relying on thread scheduler/priority/yield · `10` Handle thread interruption cooperatively · `11` [S] No Thread.stop() · `12` Use thread pools, not uncontrolled new threads

### G.CTL Control Flow — `G.CTL.01` No assignments in control conditions · `02` else-if chains need else · `03` switch needs default · `05` No modifying loop variable in body

### G.DCL Declarations — `G.DCL.01` One variable per line · `02` Declare local variables close to first use · `03` No C-style array declarations · `04` No ordinal() for enum ordering · `05` No mutable constants

### G.ERR Exceptions — `G.ERR.01` No empty catch · `02` No catching Throwable/Exception/RuntimeException · `03` No catching pre-checkable RuntimeExceptions · `04` [S] No leaking sensitive info via exceptions · `05` Exceptions match abstraction level · `06` [S] Pass original as cause when re-throwing · `07` Max 5 exceptions per method; document in @throws · `08` [S] No return/break/continue/throw in finally · `09` [S] No System.exit() · `10` Eliminate unchecked; no SuppressWarning on class · `11` Log GeneralSecurityException

### G.EXP Expressions — `G.EXP.01` No double assignment in one expression · `02` Use parentheses for clarity · `03` Ternary operands same type · `04` Left varies, right constant; equals for String · `06` [S] No assert in code

### G.EDV XML Security — `G.EDV.05` [S] Prevent XXE attacks when parsing external XML · `06` [S] Prevent XML Entity Expansion attacks · `07` [S] No unsafe XSLT transformations

### G.FIO File IO — `G.FIO.01` [S] Validate+normalize external file paths · `02` [S] Security-check ZipInputStream entries before extraction · `03` [S] Use int return for single byte/char reads from streams · `04` [S] Prevent external process blocking on IO streams

### G.FMT Formatting — `G.FMT.01` UTF-8 encoding · `02` Source file structure: copyright/package/import/class · `03` Import order: static→Android→Huawei→commercial→open-source→net/org→Java · `04` Class member order: class vars→static init→instance vars→instance init→constructors→methods · `05` Braces required · `06` Left brace end-of-line, right brace new line · `07` Avoid empty blocks · `08` 4-space indentation, no tabs · `09` One statement per line · `10` Line width ≤120 chars · `11` Line break before operator · `12` Reduce blank lines · `13` Spaces highlight keywords · `14` No vertical alignment spaces · `15` Enum: comma-separated · `16` Fall-through must be commented · `17` Each annotation own line · `18` Comment indentation matches code · `19` Modifiers in JLS order · `20` Long uses L suffix

### G.LOG Logging — `G.LOG.01` SLF4J facade · `02` Logger: private static final · `03` Leveled (DEBUG/INFO/WARN/ERROR) · `04` No Chinese in logs for non-Chinese-only products

### G.MET Methods — `G.MET.01` Short methods; ≤5 params · `03` No params as temp vars · `04` Varargs with caution · `05` Return empty collection, not null · `06` Optional instead of null · `07` [S] No ignoring return values

### G.NAM Naming — `G.NAM.01` Identifiers ≤64 chars · `02` Packages lowercase dot-separated · `03` Class/enum/interface: PascalCase · `04` Methods: camelCase · `05` Constants: ALL_UPPERCASE · `06` Variables: camelCase · `07` No negative boolean names · `08` Booleans start with true/false verb

### G.OBJ Classes — `G.OBJ.01` No public non-final fields · `02` No overridable calls in constructor · `03` Reuse constructors · `04` No name reuse/shadowing · `05` No primitive/wrapper same-name overloads · `06` hashCode with equals · `07` @Override required on override methods · `08` Singleton correctly · `09` [S] Use class name for static method calls · `10` Remove redundant interface modifiers

### G.OTH Other — `G.OTH.01` [S] Use cryptographically secure random in security contexts · `02` [S] Use SSLSocket not Socket · `03` Delete unused code/imports · `04` [S] No public network addresses in code · `05` [S] Remove dead code

### G.PRM Performance — `G.PRM.01` toArray(T[]); Java11+ toArray(IntFunction) · `02` System.arraycopy/Arrays.copyOf · `04` No repeated regex pre-compile · `05` No unnecessary objects · `07` Close IO in try-with-resources · `08` No explicit GC · `09` No Finalizer · `10` No temp vars just for return

### G.SEC Security — `G.SEC.01` [S] Security-check methods must be private/final · `02` [S] Custom ClassLoader getPermissions() must call super · `04` [S] Security manager for sensitive ops

### G.SER Serialization — `G.SER.01` Avoid implementing Serializable · `02` Explicit serialVersionUID · `04` No serializing system resources · `05` [S] No serializing non-static inner classes · `07` [S] Prevent deserialization bypassing constructor security

### G.TYP Types — `G.TYP.03` No float loop counters · `04` Use BigDecimal for exact calculations · `05` No == for float; no equals for wrappers · `06` Use isNaN() · `07` No hardcoded separators · `08` Locale.ROOT/ENGLISH for case/format · `09` Specify encoding for char↔byte · `11` Prefer primitives · `12` Explicit conversions · `13` instanceof before downcast

### X.TST Testing — `X.TST.01` No shared test state · `02` Effective assertions on business logic · `03` No static mocks; prefer injectable deps · `04` Tag integration tests · `05` Test names match CUT; methodName_scenario_expected · `06` Given/When/Then structure

### X.SQL Database — `X.SQL.01` No SQL concatenation; use PreparedStatement · `02` Paginate large queries · `03` WHERE fields indexed; leftmost prefix · `04` No SELECT * · `05` Batch bulk ops · `06` Minimize transaction scope · `07` Set transaction timeout

### X.CON Concurrency — `X.CON.01` SimpleDateFormat not thread-safe; use DateTimeFormatter/ThreadLocal · `02` DCL must use volatile · `03` No locking String/Integer/Boolean · `04` computeIfAbsent not get+put · `05` No HashMap concurrent writes; ConcurrentHashMap · `06` ThreadPoolExecutor only; no Executors convenience

### X.COL Collections — `X.COL.01` Arrays.asList() fixed-size; wrap for mutable · `02` subList() is view; copy for independent · `03` isEmpty() not size()==0 · `04` No LinkedList random access

### X.PRM Performance — `X.PRM.01` No DB/RPC in loops · `02` StringBuilder in loops · `03` No multi-traverse Stream · `04` Pattern.compile not String.matches · `05` No large objects in hot paths

### X.DEP Dependencies — `X.DEP.01` Versions in parent dependencyManagement · `02` No large libs for one method · `03` provided only for container deps · `04` No circular deps; extract shared module
