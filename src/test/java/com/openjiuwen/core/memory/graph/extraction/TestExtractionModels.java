/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtractionModels.
 * <p>
 * Mirrors Python's test_extraction_models.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_extraction_models.py</code>.
 */
@DisplayName("Extraction Models Tests")
class TestExtractionModels {

    @Nested
    @DisplayName("EntityDeclaration Tests")
    class TestEntityDeclaration {

        @Test
        @DisplayName("entity declaration required fields")
        void testEntityDeclarationRequiredFields() {
            ExtractionModels.EntityDeclaration e = new ExtractionModels.EntityDeclaration();
            e.setName("Alice");
            e.setEntityTypeId(0);

            assertEquals("Alice", e.getName());
            assertEquals(0, e.getEntityTypeId());
        }

        @Test
        @DisplayName("entity declaration can be created")
        void testEntityDeclarationCanBeCreated() {
            ExtractionModels.EntityDeclaration e = new ExtractionModels.EntityDeclaration();
            assertNotNull(e);
        }
    }

    @Nested
    @DisplayName("Duplication Tests")
    class TestDuplication {

        @Test
        @DisplayName("duplication fields")
        void testDuplicationFields() {
            ExtractionModels.Duplication d = new ExtractionModels.Duplication();
            d.setName("X");
            d.setId(1);
            List<Integer> duplicateIds = new ArrayList<>();
            duplicateIds.add(2);
            duplicateIds.add(3);
            d.setDuplicateIds(duplicateIds);

            assertEquals("X", d.getName());
            assertEquals(1, d.getId());
            assertEquals(2, d.getDuplicateIds().size());
        }

        @Test
        @DisplayName("duplication default duplicateIds is empty list")
        void testDuplicationDefaultDuplicateIdsIsEmptyList() {
            ExtractionModels.Duplication d = new ExtractionModels.Duplication();
            assertNotNull(d.getDuplicateIds());
            assertTrue(d.getDuplicateIds().isEmpty());
        }
    }

    @Nested
    @DisplayName("Fact Tests")
    class TestFact {

        @Test
        @DisplayName("fact all fields")
        void testFactAllFields() {
            ExtractionModels.Fact f = new ExtractionModels.Fact();
            f.setName("knows");
            f.setFact("works with");
            f.setValidSince("2020-01-01");
            f.setValidUntil("2025-01-01");
            f.setSourceId(1);
            f.setTargetId(2);

            assertEquals("knows", f.getName());
            assertEquals(1, f.getSourceId());
            assertEquals(2, f.getTargetId());
        }

        @Test
        @DisplayName("fact can be created")
        void testFactCanBeCreated() {
            ExtractionModels.Fact f = new ExtractionModels.Fact();
            assertNotNull(f);
        }
    }

    @Nested
    @DisplayName("PossibleTimezone Tests")
    class TestPossibleTimezone {

        @Test
        @DisplayName("possible timezone can be created")
        void testPossibleTimezoneCanBeCreated() {
            ExtractionModels.PossibleTimezone pt = new ExtractionModels.PossibleTimezone();
            assertNotNull(pt);
        }

        @Test
        @DisplayName("possible timezone fields")
        void testPossibleTimezoneFields() {
            ExtractionModels.PossibleTimezone pt = new ExtractionModels.PossibleTimezone();
            pt.setName("UTC");

            assertEquals("UTC", pt.getName());
        }
    }

    @Nested
    @DisplayName("EntityExtraction Tests")
    class TestEntityExtraction {

        @Test
        @DisplayName("entity extraction can be created")
        void testEntityExtractionCanBeCreated() {
            ExtractionModels.EntityExtraction ee = new ExtractionModels.EntityExtraction();
            assertNotNull(ee);
        }
    }

    @Nested
    @DisplayName("RelationExtraction Tests")
    class TestRelationExtraction {

        @Test
        @DisplayName("relation extraction can be created")
        void testRelationExtractionCanBeCreated() {
            ExtractionModels.RelationExtraction re = new ExtractionModels.RelationExtraction();
            assertNotNull(re);
        }
    }

    @Nested
    @DisplayName("Datetime Tests")
    class TestDatetime {

        @Test
        @DisplayName("datetime can be created")
        void testDatetimeCanBeCreated() {
            ExtractionModels.Datetime d = new ExtractionModels.Datetime();
            assertNotNull(d);
        }

        @Test
        @DisplayName("datetime fields")
        void testDatetimeFields() {
            ExtractionModels.Datetime d = new ExtractionModels.Datetime();
            d.setYear(2025);
            d.setMonth(3);
            d.setDay(18);

            assertEquals(2025, d.getYear());
            assertEquals(3, d.getMonth());
            assertEquals(18, d.getDay());
        }
    }

    @Nested
    @DisplayName("MergeRelations Tests")
    class TestMergeRelations {

        @Test
        @DisplayName("merge relations can be created")
        void testMergeRelationsCanBeCreated() {
            ExtractionModels.MergeRelations mr = new ExtractionModels.MergeRelations();
            assertNotNull(mr);
        }
    }

    @Nested
    @DisplayName("RelevantFacts Tests")
    class TestRelevantFacts {

        @Test
        @DisplayName("relevant facts can be created")
        void testRelevantFactsCanBeCreated() {
            ExtractionModels.RelevantFacts rf = new ExtractionModels.RelevantFacts();
            assertNotNull(rf);
        }
    }

    @Nested
    @DisplayName("EntitySummary Tests")
    class TestEntitySummary {

        @Test
        @DisplayName("entity summary can be created")
        void testEntitySummaryCanBeCreated() {
            ExtractionModels.EntitySummary es = new ExtractionModels.EntitySummary();
            assertNotNull(es);
        }
    }

    @Nested
    @DisplayName("TimezonePredictions Tests")
    class TestTimezonePredictions {

        @Test
        @DisplayName("timezone predictions can be created")
        void testTimezonePredictionsCanBeCreated() {
            ExtractionModels.TimezonePredictions tp = new ExtractionModels.TimezonePredictions();
            assertNotNull(tp);
        }
    }

    @Nested
    @DisplayName("EntityDuplication Tests")
    class TestEntityDuplication {

        @Test
        @DisplayName("entity duplication can be created")
        void testEntityDuplicationCanBeCreated() {
            ExtractionModels.EntityDuplication ed = new ExtractionModels.EntityDuplication();
            assertNotNull(ed);
        }
    }
}