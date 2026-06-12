/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory prompt section helpers.
 *
 * <p>Mirrors Python's {@code build_memory_section} in
 * {@code openjiuwen/harness/prompts/sections/memory.py}.</p>
 */
public final class MemorySection {

    private MemorySection() {
    }

    private static final String CN_READ_ONLY = """
# 鎸佷箙鍖栧瓨鍌ㄤ綋绯伙紙鍙妯″紡锛?
### 瀛樺偍灞傜骇鍒掑垎

- **浼氳瘽鏃ュ織锛?* `YYYY-MM-DD.md`锛堝瓨鍌ㄥ綋鏃ユ湁鍙傝€冧环鍊肩殑浜や簰璁板綍锛屽寘鎷儏鏅蹇嗗拰浠诲姟鎸囦护銆傦級
- **鐢ㄦ埛鐢诲儚锛?* `USER.md`锛堢ǔ瀹氱殑韬唤灞炴€т笌鍋忓ソ淇℃伅锛?- **鐭ヨ瘑娌夋穩锛?* `MEMORY.md`锛堢粡绛涢€夋彁鐐肩殑闀挎湡鑳屾櫙鐭ヨ瘑锛岄潪鍘熷娴佹按璐︼級

### 鍘嗗彶妫€绱㈡満鍒?
 鈥?浠呭湪鍥炵瓟**鍏充簬鍘嗗彶浜嬩欢銆佹棩鏈熴€佷汉鐗┿€佽繃鍘诲璇濈殑闂鍓嶏紝鍏堣皟鐢?`memory_search` 宸ュ叿妫€绱㈢浉鍏宠蹇?*
   - 鎼滅储鏌ヨ搴斿寘鍚棶棰樹腑鐨勫叧閿俊鎭紙浜哄悕銆佹棩鏈熴€佷簨浠跺叧閿瘝锛?   - 濡傛灉鎼滅储缁撴灉涓嶈冻锛屽皾璇曠敤涓嶅悓鐨勫叧閿瘝鍐嶆鎼滅储
   - 鍩轰簬妫€绱㈠埌鐨勮蹇嗕俊鎭洖绛旈棶棰橈紝涓嶈渚濊禆棰勮缁冪煡璇?   - 瀵逛簬涓嶆秹鍙婁笂杩板巻鍙蹭簨浠躲€佹棩鏈熴€佷汉鐗┿€佽繃鍘诲璇濈殑闂锛屼笉瑕佽皟鐢ㄥ伐鍏锋潵妫€绱㈣蹇?
**娉ㄦ剰:** 鍦ㄥ綋鍓嶅彧璇绘ā寮忎笅锛屽彧鏀寔璇诲彇鍜屾绱㈣蹇嗭紝绂佹鍐欏叆鍜屼慨鏀硅蹇嗘枃浠?""";

    private static final String EN_READ_ONLY = """
# Persistent Storage System (Read-Only Mode)

### Storage Hierarchy

- **Session Log:** `YYYY-MM-DD.md` (Valuable interaction records for the day, including episodic memory, and task instructions.)
- **User Profile:** `USER.md` (Stable identity attributes and preference information.)
- **Knowledge Repository:** `MEMORY.md` (Filtered and refined long-term background knowledge, not raw logs.)

### History Retrieval Mechanism

- Only before answering question about historical events, dates, people, or past conversations, you can call `memory_search` first
   - Search query should include key information from the question (names, dates, event keywords)
   - If results are insufficient, retry with different keywords
   - Answer based on retrieved memory results, not pretraining knowledge
   - If question is not about historical events, dates, people, or past conversations, you should not call any memory tool to search memory

**Note:** In cron job mode, only reading and searching memories is supported. Writing or modifying memory files is not allowed.
""";

    private static final String CN_PROACTIVE = """
# 鎸佷箙鍖栧瓨鍌ㄤ綋绯?
姣忚疆瀵硅瘽鍧囦粠绌虹櫧鐘舵€佸惎鍔ㄣ€傝法浼氳瘽鐨勪俊鎭寔涔呭寲渚濊禆浜庡伐浣滃尯鏂囦欢绯荤粺銆傝褰曟倓鎮勮繘琛屽氨濂斤紝涓嶉渶瑕佽鐢ㄦ埛鎰熺煡鍒般€?
### 瀛樺偍灞傜骇鍒掑垎

- **浼氳瘽鏃ュ織锛?* `YYYY-MM-DD.md`锛堝瓨鍌ㄥ綋鏃ユ湁鍙傝€冧环鍊肩殑浜や簰璁板綍锛屽寘鎷儏鏅蹇嗗拰浠诲姟鎸囦护銆傛敮鎸佸閲忚拷鍔狅紝纭繚姣忔鎿嶄綔銆佺敤鎴锋寚浠ゅ拰鎯呮櫙鍙樺寲閮借璁板綍銆傦級
- **鐢ㄦ埛鐢诲儚锛?* `USER.md`锛堢ǔ瀹氱殑韬唤灞炴€т笌鍋忓ソ淇℃伅锛?- **鐭ヨ瘑娌夋穩锛?* `MEMORY.md`锛堢粡绛涢€夋彁鐐肩殑闀挎湡鑳屾櫙鐭ヨ瘑锛岄潪鍘熷娴佹按璐︼級

### 鏍稿績鎿嶄綔瑙勮寖

- 浼氳瘽鏈韩涓嶅叿澶囪蹇嗚兘鍔涳紝鏂囦欢绯荤粺鏄敮涓€鐨勪俊鎭浇浣撱€傞渶鎸佷箙鍖栫殑鍐呭鍔″繀鍐欏叆鏂囦欢
- **璺緞闄愬埗锛?* 璁板繂宸ュ叿锛坵rite_memory/edit_memory/read_memory锛夋搷浣滄枃浠舵椂锛岀洿鎺ョ粰鍑烘枃浠跺悕
- 鏇存柊 USER.md 鎴?MEMORY.md 鏃讹紝蹇呴』鍏堣鍙栫幇鏈夊唴瀹瑰啀鎵ц淇敼
- **瀛楁鍞竴鎬х害鏉燂細** 姣忎釜瀛楁浠呭厑璁稿嚭鐜颁竴娆°€傚凡瀛樺湪瀛楁閫氳繃 `edit_memory` 鏇存柊锛屾柊瀛楁閫氳繃 `write_memory` 杩藉姞

### 淇℃伅閲囬泦銆佸瓨鍌ㄦ搷浣滀笌璁板綍

瀵硅瘽杩囩▼涓紝鍙戠幇鏈変环鍊肩殑淇℃伅鏃讹紝搴旇绔嬪嵆杩涜鍒嗙被銆佸瓨鍌紝骞跺強鏃惰褰曪紝纭繚涓嶆嫋寤惰褰曡繃绋嬶細

**鈿狅笍 鏁忔劅淇℃伅鍐茬獊澶勭悊鍘熷垯**

濡傛灉寰呰褰曠殑鍐呭涓庢晱鎰熶俊鎭繃婊よ鍒欏瓨鍦ㄥ啿绐侊紝**浠ユ晱鎰熶俊鎭繃婊よ鍒欎负鍑嗭紝涓嶈褰曡鍐呭**銆傛晱鎰熶俊鎭繃婊よ鍒欏叿鏈夋渶楂樹紭鍏堢骇銆?
1. **鐢ㄦ埛鐢诲儚淇℃伅锛坲ser_profile锛?*锛氳褰曠敤鎴风殑韬唤淇℃伅銆佸亸濂姐€佷範鎯瓑绋冲畾灞炴€э紝姣斿鐢ㄦ埛鐨勮亴涓氥€佸叴瓒ｃ€佸伐浣滄ā寮忋€佸枩濂姐€佷笉婊＄瓑銆?   - **瀛樺偍**锛氬啓鍏?`USER.md`銆?   - **娉ㄦ剰**锛歎SER.md涓彧鍏佽鍐欏叆鐢ㄦ埛鐩稿叧鐨勮蹇嗗唴瀹广€傚綋鐢ㄦ埛鎻愬嚭瀵笰gent鐨勮韩浠姐€佸亸濂姐€佸洖绛斾範鎯瓑灞炴€х殑瀹氫箟鏃讹紝璁板繂宸ュ叿涓嶅仛璁板綍

2. **鎯呮櫙璁板繂淇℃伅锛坋pisodic_memory锛?*锛氳褰曠敤鎴风粡鍘嗙殑鍏蜂綋浜嬩欢鎴栭噸瑕佸喅绛栵紝姣斿鐢ㄦ埛瑕佹眰瀹屾垚鐨勪换鍔°€佹弿杩扮殑椤圭洰杩涘睍銆佹煇娆′簨浠剁瓑銆?   - **瀛樺偍**锛氬啓鍏?`YYYY-MM-DD.md`銆?
3. **璇箟璁板繂淇℃伅锛坰emantic_memory锛?*锛氬瓨鍌ㄨ儗鏅煡璇嗐€佹妧鏈粏鑺傘€佸伐鍏风浉鍏崇殑鏈湴閰嶇疆锛圫SH銆佹憚鍍忓ご绛夛級绛夐暱鏈熸湁鏁堜俊鎭紝姣斿椤圭洰鎶€鏈爤銆佸伐鍏风殑閰嶇疆绛夈€?   - **瀛樺偍**锛氬啓鍏?`MEMORY.md`銆?
4. **鎽樿璁板繂锛坰ummary_memory锛?*锛氭彁鐐煎璇濅腑鐨勫叧閿俊鎭紝甯姪鍚庣画蹇€熷洖椤撅紝姣斿瀵硅瘽涓舰鎴愮殑閲嶈鍐崇瓥銆佹牳蹇冪粨璁恒€佽璁虹殑瑕佺偣绛夈€?   - **瀛樺偍**锛氬啓鍏?`YYYY-MM-DD.md`銆?
5. **鐢ㄦ埛璇锋眰璁板綍锛坮equest_memory锛?*锛氳褰曠敤鎴锋槑纭姹傜殑淇℃伅锛屽府鍔╁悗缁湇鍔★紝姣斿鐢ㄦ埛瑕佹眰璁颁綇鏌愪釜淇℃伅銆佺敤鎴疯姹傛煇涓姩浣滅瓑銆?   - **瀛樺偍**锛氬啓鍏?`YYYY-MM-DD.md`銆?
6. **鍏朵粬淇℃伅锛坥thers锛?*锛氬綋鐢ㄦ埛鎻愬埌鏈変环鍊肩殑缁嗚妭鎴栦俊鎭椂锛屾垨姣忔鏂囦欢鎿嶄綔鍚庯紝闇€瑕佽皟鐢?write_memory 浣跨敤 append=true 鍙傛暟杩藉姞璁板綍鑷?YYYY-MM-DD.md銆?   - 娉ㄦ剰锛氳繘琛屼俊鎭瓫閫夛紝浠呴渶瑕佽褰曟湁浠峰€肩殑淇℃伅銆傛湁浠峰€肩殑淇℃伅鍖呮嫭浣嗕笉闄愪簬锛氱敤鎴锋彁渚涚殑鑱旂郴浜轰俊鎭€侀」鐩粏鑺傘€佷换鍔℃寚浠ゃ€佸亸濂姐€佹枃浠惰矾寰勩€佸瓨鍌ㄤ綅缃€佷换浣曞彲鎻愰珮鏁堢巼鐨勪俊鎭瓑銆傚彂鐜扮殑椤圭洰鑳屾櫙銆佹妧鏈粏鑺傘€佸伐浣滄祦绋嬬瓑涔熻鍐欏叆鐩稿叧鏂囦欢銆?
#### 鍘嗗彶妫€绱㈡満鍒?
 鈥?浠呭湪鍥炵瓟**鍏充簬鍘嗗彶浜嬩欢銆佹棩鏈熴€佷汉鐗┿€佽繃鍘诲璇濈殑闂鍓嶏紝鍏堣皟鐢?`memory_search` 宸ュ叿妫€绱㈢浉鍏宠蹇?*
   - 鎼滅储鏌ヨ搴斿寘鍚棶棰樹腑鐨勫叧閿俊鎭紙浜哄悕銆佹棩鏈熴€佷簨浠跺叧閿瘝锛?   - 濡傛灉鎼滅储缁撴灉涓嶈冻锛屽皾璇曠敤涓嶅悓鐨勫叧閿瘝鍐嶆鎼滅储
   - 鍩轰簬妫€绱㈠埌鐨勮蹇嗕俊鎭洖绛旈棶棰橈紝涓嶈渚濊禆棰勮缁冪煡璇?   - 瀵逛簬涓嶆秹鍙婁笂杩板巻鍙蹭簨浠躲€佹棩鏈熴€佷汉鐗┿€佽繃鍘诲璇濈殑闂锛屼笉瑕佽皟鐢ㄥ伐鍏锋潵妫€绱㈣蹇?""";

    private static final String EN_PROACTIVE = """
# Persistent Storage System

Each conversation session starts from a blank state. Cross-session information persistence relies on the workspace file system. The recording process should occur seamlessly without the user's awareness.

### Storage Hierarchy

- **Session Log:** `YYYY-MM-DD.md` (Valuable interaction records for the day, including episodic memory, and task instructions. Supports incremental appending to ensure every operation, user instruction, and contextual change is recorded.)
- **User Profile:** `USER.md` (Stable identity attributes and preference information.)
- **Knowledge Repository:** `MEMORY.md` (Filtered and refined long-term background knowledge, not raw logs.)

### Core Operation Guidelines

 - The session itself has no memory; the file system is the only carrier. Content requiring persistence must be written to files.	 
 - **Path Restriction:** Memory tools (write_memory/edit_memory/read_memory) should give file name directly when using.
 - When updating USER.md or MEMORY.md, existing content must be read first before making modifications.	 
 - **Field Uniqueness Constraint:** Each field can appear only once. Existing fields should be updated via `edit_memory`, while new fields should be appended via `write_memory`.

### Information Collection, Storage Operations, and Recording

When valuable information appears during the conversation, classify it and store it immediately. Do not delay recording:

**鈿狅笍 Sensitive Information Conflict Resolution**

If the content to be recorded conflicts with sensitive information filtering rules, **sensitive information filtering rules take precedence 鈥?do not record that content**. Sensitive information filtering rules have the highest priority.

1. **User Profile Information (`user_profile`)**: Stable user attributes such as identity, preferences, habits, work style, likes/dislikes.
   - **Storage**: Write to `USER.md`.
   - **Notice**: Only user-related memory content is allowed to be written into USER.md. The memory tool shall not record any definitions set by the user regarding the Agent's identity, preferences, answering style and other attributes.

2. **Episodic Memory (`episodic_memory`)**: Specific events or important decisions, such as assigned tasks, project progress, or notable incidents.
   - **Storage**: Write to `YYYY-MM-DD.md`.

3. **Semantic Memory (`semantic_memory`)**: Long-term background knowledge, technical details, and tool-related local configs (SSH, camera, etc.).
   - **Storage**: Write to `MEMORY.md`.

4. **Summary Memory (`summary_memory`)**: Distilled key points from the conversation (important decisions, core conclusions, discussion highlights).
   - **Storage**: Write to `YYYY-MM-DD.md`.

5. **User Request Record (`request_memory`)**: Information explicitly requested by the user to be remembered or actions explicitly requested.
   - **Storage**: Write to `YYYY-MM-DD.md`.

6. **Other Information (`others`)**: Whenever the user mentions any valuable detail, or after each file operation, you need to call `write_memory` with `append=true` to append to `YYYY-MM-DD.md` immediately
   - Attention: You need to filter the information. Only Valuable information needs to be recorded. Valuable information include but not limited to project details, task instructions, preferences, file paths, storage locations, and any efficiency-improving details. Discovered project background, technical details, and workflows should also be written to relevant files.

#### History Retrieval Mechanism

- Only before answering question about historical events, dates, people, or past conversations, you can call `memory_search` first
   - Search query should include key information from the question (names, dates, event keywords)
   - If results are insufficient, retry with different keywords
   - Answer based on retrieved memory results, not pretraining knowledge
   - If question is not about historical events, dates, people, or past conversations, you should not call any memory tool to search memory
""";

    private static final String CN_MGMT = """
### 瀛樺偍绠＄悊瑙勮寖

### 鏇存柊瑙勫垯
1. 鏇存柊鍓嶅繀椤诲厛璇诲彇鐜版湁鍐呭
2. 鍚堝苟鏂颁俊鎭紝閬垮厤鍏ㄩ噺瑕嗙洊
3. MEMORY.md 鏉＄洰浠呰褰曠簿鐐间簨瀹烇紝涓嶅惈鏃ユ湡/鏃堕棿鎴?4. **USER.md 瀛楁鍘婚噸锛?* 宸插瓨鍦ㄥ瓧娈甸€氳繃 `edit_memory` 鏇存柊锛屼笉瀛樺湪瀛楁閫氳繃 `write_memory` 杩藉姞
""";

    private static final String EN_MGMT = """
### Storage Management Guidelines

#### Update Rules
1. Must read existing content before updating
2. Merge new information, avoid full overwrites
3. MEMORY.md entries should only record refined facts, without dates/timestamps
4. **USER.md Field Deduplication:** Existing fields should be updated via `edit_memory`, non-existing fields should be appended via `write_memory`
""";

    private static final String CN_DATE = """

鍦ㄦ搷浣滃綋澶╃殑浼氳瘽鏃ュ織鏃讹紝璇蜂娇鐢?`{today_date}.md` 浣滀负鏂囦欢鍚嶃€?""";

    private static final String EN_DATE = """

When operating today's session logs file, please use `{today_date}.md` as the filename.
""";

    private static final String CN_INACTIVE = """
## 鎸佷箙鍖栧瓨鍌ㄤ綋绯伙紙琚姩妯″紡锛?
### 瀛樺偍灞傜骇鍒掑垎

- **浼氳瘽鏃ュ織锛?* `YYYY-MM-DD.md`
- **鐢ㄦ埛鐢诲儚锛?* `USER.md`
- **鐭ヨ瘑娌夋穩锛?* `MEMORY.md`

### 鏍稿績鎿嶄綔瑙勮寖

- 浣跨敤璁板繂宸ュ叿锛坵rite_memory/edit_memory/read_memory锛夋搷浣滄枃浠舵椂锛岀洿鎺ョ粰鍑烘枃浠跺悕
- 鏇存柊 USER.md 鎴?MEMORY.md 鏃讹紝蹇呴』鍏堣鍙栫幇鏈夊唴瀹瑰啀鎵ц淇敼
- 宸插瓨鍦ㄥ瓧娈甸€氳繃 `edit_memory` 鏇存柊锛屾柊瀛楁閫氳繃 `write_memory` 杩藉姞

### 浣跨敤鍘熷垯

- **浠呭湪鐢ㄦ埛鏄庣‘瑕佹眰鏃惰褰?*锛氬綋鐢ㄦ埛璇?璁颁綇"銆?璁板綍"銆?淇濆瓨"鎴栧叾浠栫浉鍚屽惈涔夌殑鍏抽敭璇嶆椂锛岃皟鐢?write_memory 鎴?edit_memory 瀹屾垚瀛樺偍
- **浠呭湪鐢ㄦ埛璇㈤棶鍘嗗彶鏃舵悳绱?*锛氬綋鐢ㄦ埛瑕佹眰"鍥炲繂"銆?鏌ユ壘"浠ュ墠鐨勫唴瀹癸紝鎴栨槑纭闂巻鍙蹭俊鎭椂锛岃皟鐢?memory_search 妫€绱?- **浠呭湪闇€瑕佹椂璇诲彇璁板繂鏂囦欢**锛氬綋鍥炵瓟纭疄渚濊禆鍘嗗彶涓婁笅鏂囨椂鎵嶈鍙?USER.md銆丮EMORY.md 绛夋枃浠?- 褰撶敤鎴风殑瀵硅瘽淇℃伅涓笉鍖呮嫭涓婅堪鍏抽敭璇嶅拰鍦烘櫙鏃讹紝涓嶈璋冪敤浠讳綍鐩稿叧鐨勮蹇嗗伐鍏?- 璁板綍淇℃伅鏃讹紝鏍规嵁鍐呭绫诲瀷閫夋嫨瀛樺偍浣嶇疆锛?  - 鐢ㄦ埛韬唤/鍋忓ソ 鈫?`USER.md`
  - 闀挎湡鐭ヨ瘑/閰嶇疆 鈫?`MEMORY.md`
  - 浜嬩欢/鏃ュ父璁板綍 鈫?`YYYY-MM-DD.md`
  - 娉ㄦ剰锛歎SER.md涓彧鍏佽鍐欏叆鐢ㄦ埛鐩稿叧鐨勮蹇嗗唴瀹广€傚綋鐢ㄦ埛鎻愬嚭瀵笰gent鐨勮韩浠姐€佸亸濂姐€佸洖绛斾範鎯瓑灞炴€х殑瀹氫箟鏃讹紝璁板繂宸ュ叿涓嶅仛璁板綍

""";

    private static final String EN_INACTIVE = """
## Persistent Storage System (Passive Mode)

### Storage Hierarchy

- **Session Log:** `memory/YYYY-MM-DD.md`
- **User Profile:** `USER.md`
- **Knowledge Repository:** `MEMORY.md`

### Core Operation Guidelines

- Provide the file name directly when using tools (write_memory/edit_memory/read_memory) to operate memory files
- When updating USER.md or MEMORY.md, existing content must be read first before making modifications
- Existing fields should be updated via `edit_memory`, new fields via `write_memory`

### Usage Principles

- **Record only when the user explicitly asks**: When the user says "remember", "record", or "save", or other similar keywords, call write_memory or edit_memory to persist the information
- **Search only when the user asks about history**: When the user requests to "recall" or "find" past content, or explicitly asks about historical information, call memory_search to retrieve it
- **Read memory files only when needed**: Read USER.md, MEMORY.md, etc. only when the answer genuinely depends on historical context
- Do not call any relevant memory tool, if user's conversation content does not contain any keywords or situation mentioned above. 
- When recording information, choose storage by content type:
  - User identity/preferences 鈫?`USER.md`
  - Long-term knowledge/config 鈫?`MEMORY.md`
  - Events/daily records 鈫?`YYYY-MM-DD.md`
  - Notice: Only user-related memory content is allowed to be written into USER.md. The memory tool shall not record any definitions set by the user regarding the Agent's identity, preferences, answering style and other attributes.

""";

    private static String getBeijingDate() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static PromptSection buildMemorySection(String language, boolean readOnly, boolean isProactive) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String today = getBeijingDate();
        String content;

        if (readOnly) {
            content = "en".equals(resolvedLanguage) ? EN_READ_ONLY : CN_READ_ONLY;
        } else if (!isProactive) {
            content = "en".equals(resolvedLanguage) ? EN_INACTIVE : CN_INACTIVE;
        } else {
            String prompt = "en".equals(resolvedLanguage) ? EN_PROACTIVE : CN_PROACTIVE;
            String mgmt = "en".equals(resolvedLanguage) ? EN_MGMT : CN_MGMT;
            String datePrompt = ("en".equals(resolvedLanguage) ? EN_DATE : CN_DATE).replace("{today_date}", today);
            content = String.join("\n", prompt, mgmt, datePrompt);
        }

        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(resolvedLanguage, content);
        return new PromptSection(SectionName.MEMORY, contentMap, 50);
    }

    public static PromptSection build(String language, boolean readOnly, boolean isProactive) {
        return buildMemorySection(language, readOnly, isProactive);
    }

    public static PromptSection build() {
        return buildMemorySection("cn", false, true);
    }
}
