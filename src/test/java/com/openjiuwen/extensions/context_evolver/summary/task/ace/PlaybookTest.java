/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.ace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaybookTest {

    @Test
    void bulletCrudPromptAndStatsMirrorPythonBehavior() {
        Playbook playbook = new Playbook();

        Playbook.Bullet alpha = playbook.addBullet("Alpha section", "First tip", null, Map.of("helpful", 2));
        Playbook.Bullet general = playbook.addBullet("   ", "Fallback section");

        assertEquals("alpha-00001", alpha.getId());
        assertEquals("general-00002", general.getId());

        playbook.updateBullet(alpha.getId(), "Updated tip", Map.of("harmful", 1));
        playbook.tagBullet(alpha.getId(), "neutral", 3);

        assertEquals("Updated tip", playbook.getBullet(alpha.getId()).getContent());
        assertEquals(2, playbook.getBullet(alpha.getId()).getHelpful());
        assertEquals(1, playbook.getBullet(alpha.getId()).getHarmful());
        assertEquals(3, playbook.getBullet(alpha.getId()).getNeutral());
        assertTrue(playbook.asPrompt().contains("## Alpha section"));
        assertTrue(playbook.asPrompt().contains("(helpful=2, harmful=1, neutral=3)"));

        Map<String, Object> stats = playbook.stats();
        assertEquals(2, stats.get("sections"));
        assertEquals(2, stats.get("bullets"));
        assertEquals(Map.of("helpful", 2, "harmful", 1, "neutral", 3), stats.get("tags"));

        playbook.removeBullet(general.getId());
        assertNull(playbook.getBullet(general.getId()));
    }

    @Test
    void deltaSerializationAndApplicationMirrorPythonFlow() {
        Playbook playbook = new Playbook();
        Playbook.DeltaBatch delta = Playbook.DeltaBatch.fromJson(Map.of(
            "reasoning", "apply changes",
            "operations", List.of(
                Map.of("type", "ADD", "section", "Alpha", "content", "Keep this"),
                Map.of("type", "TAG", "section", "Alpha", "bullet_id", "alpha-00001", "metadata", Map.of("helpful", 2)),
                Map.of("type", "UPDATE", "section", "Alpha", "bullet_id", "alpha-00001", "content", "Keep this better"),
                Map.of("type", "REMOVE", "section", "Alpha", "bullet_id", "missing")
            )
        ));

        playbook.applyDelta(delta);

        Playbook.Bullet bullet = playbook.getBullet("alpha-00001");
        assertNotNull(bullet);
        assertEquals("Keep this better", bullet.getContent());
        assertEquals(2, bullet.getHelpful());
        assertEquals("apply changes", delta.getReasoning());
        assertEquals("ADD", delta.getOperations().get(0).toJson().get("type"));
        assertEquals("[alpha-00001] Keep this better", playbook.makePlaybookExcerpt(List.of("alpha-00001", "alpha-00001")));
    }

    @Test
    void dumpsLoadsAndFromDictMirrorPythonSerialization() {
        Playbook playbook = new Playbook();
        playbook.addBullet("Alpha", "One");
        String serialized = playbook.dumps();

        assertTrue(serialized.contains("\"next_id\" : 1"));
        assertTrue(serialized.contains("\"sections\""));

        Playbook roundTrip = Playbook.loads(serialized);
        assertEquals(List.of("alpha-00001"), roundTrip.bulletIds());
        assertEquals("One", roundTrip.getBullet("alpha-00001").getContent());

        Playbook rebuilt = Playbook.fromDict(Map.of(
            "bullets", Map.of(
                "custom-1", Map.of(
                    "id", "custom-1",
                    "section", "Beta",
                    "content", "Stored",
                    "helpful", 1,
                    "harmful", 0,
                    "neutral", 0,
                    "created_at", "2026-06-06T00:00:00+00:00",
                    "updated_at", "2026-06-06T00:00:00+00:00"
                )
            ),
            "sections", Map.of("Beta", List.of("custom-1")),
            "next_id", 9
        ));
        assertEquals(List.of("custom-1"), rebuilt.bulletIds());
        assertEquals("Stored", rebuilt.getBullet("custom-1").getContent());
    }

    @Test
    void invalidLoadAndTagErrorMatchPythonFailureModes() {
        Playbook playbook = new Playbook();
        Playbook.Bullet bullet = playbook.addBullet("Alpha", "One");
        Playbook.BulletTag tag = new Playbook.BulletTag(bullet.getId(), "helpful");

        assertEquals(bullet.getId(), tag.getId());
        assertEquals("helpful", tag.getTag());
        assertThrows(IllegalArgumentException.class, () -> bullet.tag("weird", 1));
        assertThrows(IllegalArgumentException.class, () -> Playbook.loads("[]"));
    }
}
