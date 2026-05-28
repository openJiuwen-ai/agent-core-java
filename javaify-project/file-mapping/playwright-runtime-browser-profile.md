# Python To Java Mapping: profiles.py

## 1. Basic Info

- Python source: `agent-core-0.1.12\openjiuwen\harness\tools\browser_move\playwright_runtime\profiles.py`
- Java target: 
  - `agent-core-java-0.1.12\src\main\java\com\openjiuwen\harness\tools\browser_move\playwright_runtime\BrowserProfile.java`
  - `agent-core-java-0.1.12\src\main\java\com\openjiuwen\harness\tools\browser_move\playwright_runtime\BrowserProfileStore.java`
- Python project root: `./agent-core-0.1.12`
- Java project root: `./agent-core-java-0.1.12`
- Java package: `com.openjiuwen.harness.tools.browser_move.playwright_runtime`
- Java classes: `BrowserProfile`, `BrowserProfileStore`
- Conversion date: 2026-05-21

## 2. Reference Baseline

- Fixed reference version: `0.1.7`
- Python reference: `D:\gitcode\zhangjun\agent-core-0.1.7\openjiuwen\core\application\workflow_agent\workflow_agent.py`
- Java reference: `D:\gitcode\zhangjun\agent-core-java-0.1.7\src\main\java\com\openjiuwen\core\application\workflow\WorkflowAgent.java`
- Canonical reference files present: Yes
- Fallback substitute references used: None

## 3. File-Level Mapping

- Python module responsibility: Defines BrowserProfile dataclass and BrowserProfileStore class for JSON-backed browser profile storage with selected-profile tracking
- Java class responsibility: Same - provides browser profile metadata and profile store with JSON persistence
- Key framework abstractions reused: 
  - Jackson ObjectMapper for JSON parsing
  - Java NIO Path for file operations

## 4. Field Mapping (BrowserProfile)

| Python field | Java field | Notes |
| --- | --- | --- |
| name: str | String name | Profile name |
| driver_type: str = "remote" | String driverType = "remote" | Driver type (remote/local) |
| cdp_url: str = "" | String cdpUrl = "" | Chrome DevTools Protocol URL |
| browser_binary: str = "" | String browserBinary = "" | Browser executable path |
| user_data_dir: str = "" | String userDataDir = "" | User data directory |
| debug_port: int = 0 | int debugPort = 0 | Debug port number |
| host: str = "127.0.0.1" | String host = "127.0.0.1" | Host address |
| extra_args: List[str] | List<String> extraArgs | Extra browser arguments |

## 5. Constructor And Initialization Mapping

| Python location | Java location | Mapping detail |
| --- | --- | --- |
| @dataclass class BrowserProfile | BrowserProfile class | Converted to Java POJO with getters/setters |
| from_dict() @classmethod | static fromDict(Map) | Factory method from raw dictionary |
| to_dict() method | toDict() method | Convert to Map representation |

## 6. Method Mapping (BrowserProfileStore)

| Python method | Java method | Mapping detail |
| --- | --- | --- |
| __init__(path) | constructor BrowserProfileStore(Path) | Initialize with path, call load() |
| _load() | private load() | Load profiles from JSON file |
| save() | save() | Save profiles to JSON file |
| list_profiles() | listProfiles() | Return sorted list of profiles |
| get_profile(name) | getProfile(String) | Get profile by name |
| upsert_profile(profile, select) | upsertProfile(BrowserProfile, boolean) | Insert or update profile |
| remove_profile(name) | removeProfile(String) | Remove profile by name |
| select_profile(name) | selectProfile(String) | Select profile by name |
| selected_name() | selectedName() | Get selected profile name |
| selected_profile() | selectedProfile() | Get selected profile |

## 7. Behavior Preservation

- Validation logic: name trimming, driver_type normalization (lowercase, default "remote")
- Branch logic: If selected profile not in profiles, clear selection
- State updates: profiles HashMap, selected String
- Error handling: IllegalArgumentException for missing profile, IOException handling in load/save
- Python's `KeyError` converted to Java's `IllegalArgumentException`

## 8. Java-Specific Adaptations

- Additional helpers introduced: 
  - expandUser() method for path expansion (~ to user home)
  - Multiple constructors (default, name-only, full)
- Type-system adaptations:
  - Python Dict → Java Map<String, Object>
  - Python Path → Java Path (NIO)
  - Python dataclass → Java POJO with getters/setters
- Framework integration differences:
  - Jackson ObjectMapper instead of Python's json module
  - Java NIO Files instead of Python's pathlib

## 9. Placeholder Tracking

No placeholders introduced.

## 10. Compile Verification

- Module: agent-core-java-0.1.12
- Result: No errors for BrowserProfile.java or BrowserProfileStore.java (pre-existing errors in other files)
- Notes: Compilation errors exist in CallbackFramework.java but are unrelated to this translation

## 11. Review Result

- `Mirrors Python's` present: Yes (in both class Javadocs)
- No missing Python behavior detected: Yes
- Mapping file refreshed after final code update: Yes
- Review passed: Yes
- Reviewer notes: Full conversion of Python dataclass and store class to Java POJOs with all methods preserved