/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's extraction model tests in
 * {@code tests/unit_tests/core/memory/graph/extraction/test_extraction_models.py}.
 */
@DisplayName("Extraction Models Tests")
class ExtractionModelsTest {

    @Nested
    @DisplayName("Datetime Tests")
    class DatetimeTests {

        @Test
        @Tag("level0")
        @DisplayName("valid fields create Datetime instance")
        void validFieldsCreateDatetimeInstance() {
            ExtractionModels.Datetime datetime = new ExtractionModels.Datetime(2025, 3, 18, 12, 30, 0);

            assertEquals(2025, datetime.getYear());
            assertEquals(3, datetime.getMonth());
            assertEquals(18, datetime.getDay());
        }
    }

    @Nested
    @DisplayName("EntityDeclaration Tests")
    class EntityDeclarationTests {

        @Test
        @Tag("level0")
        @DisplayName("name and entity type id are required")
        void nameAndEntityTypeIdAreRequired() {
            ExtractionModels.EntityDeclaration entity = new ExtractionModels.EntityDeclaration("Alice", 0);

            assertEquals("Alice", entity.getName());
            assertEquals(0, entity.getEntityTypeId());
        }

        @Test
        @Tag("level0")
        @DisplayName("missing name raises validation error")
        void missingNameRaisesValidationError() {
            assertThrows(IllegalArgumentException.class, () -> new ExtractionModels.EntityDeclaration(null, 0));
        }
    }

    @Nested
    @DisplayName("Duplication Tests")
    class DuplicationTests {

        @Test
        @Tag("level0")
        @DisplayName("duplication has name id and duplicate ids")
        void duplicationHasNameIdAndDuplicateIds() {
            ExtractionModels.Duplication duplication = new ExtractionModels.Duplication("X", 1, List.of(2, 3));

            assertEquals("X", duplication.getName());
            assertEquals(1, duplication.getId());
            assertEquals(List.of(2, 3), duplication.getDuplicateIds());
        }
    }

    @Nested
    @DisplayName("Fact Tests")
    class FactTests {

        @Test
        @Tag("level0")
        @DisplayName("fact has all relation fields")
        void factHasAllRelationFields() {
            ExtractionModels.Fact fact = new ExtractionModels.Fact(
                    "knows",
                    "works with",
                    "2020-01-01",
                    "2025-01-01",
                    1,
                    2);

            assertEquals("knows", fact.getName());
            assertEquals("works with", fact.getFact());
            assertEquals("2020-01-01", fact.getValidSince());
            assertEquals("2025-01-01", fact.getValidUntil());
            assertEquals(1, fact.getSourceId());
            assertEquals(2, fact.getTargetId());
        }
    }

    @Nested
    @DisplayName("EntityExtraction Tests")
    class EntityExtractionTests {

        @Test
        @Tag("level0")
        @DisplayName("extracted entities is list of entity declarations")
        void extractedEntitiesIsListOfEntityDeclarations() {
            ExtractionModels.EntityDeclaration entity = new ExtractionModels.EntityDeclaration("A", 0);
            ExtractionModels.EntityExtraction model = new ExtractionModels.EntityExtraction(List.of(entity));

            assertEquals(1, model.getExtractedEntities().size());
            assertEquals("A", model.getExtractedEntities().get(0).getName());
        }
    }

    @Nested
    @DisplayName("EntitySummary Tests")
    class EntitySummaryTests {

        @Test
        @Tag("level0")
        @DisplayName("summary and attributes are set")
        void summaryAndAttributesAreSet() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("role", "user");
            ExtractionModels.EntitySummary model = new ExtractionModels.EntitySummary("A person.", attributes);

            assertEquals("A person.", model.getSummary());
            assertEquals(attributes, model.getAttributes());
        }
    }

    @Nested
    @DisplayName("EntityDuplication Tests")
    class EntityDuplicationTests {

        @Test
        @Tag("level0")
        @DisplayName("duplicated entities is list of duplications")
        void duplicatedEntitiesIsListOfDuplications() {
            ExtractionModels.Duplication duplication = new ExtractionModels.Duplication("X", 1, List.of(2));
            ExtractionModels.EntityDuplication model = new ExtractionModels.EntityDuplication(List.of(duplication));

            assertEquals(1, model.getDuplicatedEntities().size());
            assertEquals("X", model.getDuplicatedEntities().get(0).getName());
        }
    }

    @Nested
    @DisplayName("RelationExtraction Tests")
    class RelationExtractionTests {

        @Test
        @Tag("level0")
        @DisplayName("extracted relations is list of facts")
        void extractedRelationsIsListOfFacts() {
            ExtractionModels.Fact fact = new ExtractionModels.Fact("r", "f", "", "", 1, 2);
            ExtractionModels.RelationExtraction model = new ExtractionModels.RelationExtraction(List.of(fact));

            assertEquals(1, model.getExtractedRelations().size());
        }
    }

    @Nested
    @DisplayName("RelevantFacts Tests")
    class RelevantFactsTests {

        @Test
        @Tag("level0")
        @DisplayName("brief reasoning and relevant relations are set")
        void briefReasoningAndRelevantRelationsAreSet() {
            ExtractionModels.RelevantFacts model = new ExtractionModels.RelevantFacts("Because.", List.of(1, 2));

            assertEquals("Because.", model.getBriefReasoning());
            assertEquals(List.of(1, 2), model.getRelevantRelations());
        }
    }

    @Nested
    @DisplayName("TimezonePredictions Tests")
    class TimezonePredictionsTests {

        @Test
        @Tag("level0")
        @DisplayName("extracted relations is list of possible timezones")
        void extractedRelationsIsListOfPossibleTimezones() {
            ExtractionModels.PossibleTimezone timezone =
                    new ExtractionModels.PossibleTimezone("UTC", "+0", "default");
            ExtractionModels.TimezonePredictions model = new ExtractionModels.TimezonePredictions(List.of(timezone));

            assertEquals(1, model.getExtractedRelations().size());
            assertEquals("UTC", model.getExtractedRelations().get(0).getName());
        }
    }

    @Nested
    @DisplayName("MergeRelations Tests")
    class MergeRelationsTests {

        @Test
        @Tag("level0")
        @DisplayName("merge relations has all fields")
        void mergeRelationsHasAllFields() {
            ExtractionModels.MergeRelations model = new ExtractionModels.MergeRelations(
                    true,
                    "Same event",
                    "Merged.",
                    List.of(1, 2),
                    "2020-01-01",
                    "2025-01-01");

            assertTrue(model.isNeedMerging());
            assertEquals(List.of(1, 2), model.getDuplicateIds());
        }

        @Test
        @Tag("level0")
        @DisplayName("merge relations preserves all fields")
        void mergeRelationsPreservesAllFields() {
            ExtractionModels.MergeRelations model = new ExtractionModels.MergeRelations(
                    true,
                    "Same event",
                    "Merged.",
                    List.of(1, 2),
                    "2020-01-01",
                    "2025-01-01");

            assertTrue(model.isNeedMerging());
            assertEquals("Same event", model.getShortReasoning());
            assertEquals("Merged.", model.getCombinedContent());
            assertEquals(List.of(1, 2), model.getDuplicateIds());
            assertEquals("2020-01-01", model.getValidSince());
            assertEquals("2025-01-01", model.getValidUntil());
        }
    }
}
