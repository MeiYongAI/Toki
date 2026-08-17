package com.seepd.toki;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class FeedFilterTest {
    @Test
    public void countRangeIncludesBothConfiguredBounds() {
        assertFalse(FeedFilter.isOutsideInclusiveRange(100, 100, 1_000));
        assertFalse(FeedFilter.isOutsideInclusiveRange(1_000, 100, 1_000));
    }

    @Test
    public void countRangeExcludesValuesOutsideConfiguredBounds() {
        assertTrue(FeedFilter.isOutsideInclusiveRange(99, 100, 1_000));
        assertTrue(FeedFilter.isOutsideInclusiveRange(1_001, 100, 1_000));
    }

    @Test
    public void concreteListSetterReceivesFilteredItems() {
        ArrayList<Object> original = new ArrayList<>(Arrays.asList("keep", "remove"));
        ArrayList<Object> filtered = new ArrayList<>(Arrays.asList("keep"));
        ConcreteListModel model = new ConcreteListModel();

        assertTrue(FeedFilter.replaceItems(model, original, filtered));
        assertSame(filtered, model.items);
    }

    @Test
    public void mutableListIsUpdatedWhenNoSetterExists() {
        List<Object> original = new ArrayList<>(Arrays.asList("keep", "remove"));
        List<Object> filtered = Arrays.asList("keep");

        assertTrue(FeedFilter.replaceItems(new Object(), original, filtered));
        assertTrue(original.equals(filtered));
    }

    @Test
    public void aiGeneratedMetadataIsRecognizedWithoutHeuristics() {
        assertTrue(FeedFilter.isAiGeneratedContent(new AiContent(true, 0, -1)));
        assertTrue(FeedFilter.isAiGeneratedContent(new AiContent(false, 2, -1)));
        assertTrue(FeedFilter.isAiGeneratedContent(new AiContent(false, 0, 1)));
        assertFalse(FeedFilter.isAiGeneratedContent(new AiContent(false, 0, 0)));
    }

    public static final class ConcreteListModel {
        ArrayList<Object> items;

        public void setItems(ArrayList<Object> items) {
            this.items = items;
        }
    }

    public static final class AiContent {
        private final AigcInfo aigcInfo;
        private final ModerationAigcInfo moderationAigcInfo;

        AiContent(boolean createdByAi, int labelType, int moderationLabelType) {
            aigcInfo = new AigcInfo(createdByAi, labelType);
            moderationAigcInfo = new ModerationAigcInfo(moderationLabelType);
        }

        public AigcInfo getAigcInfo() {
            return aigcInfo;
        }

        public ModerationAigcInfo getModerationAigcInfo() {
            return moderationAigcInfo;
        }
    }

    public static final class AigcInfo {
        public final boolean createByAI;
        private final int labelType;

        AigcInfo(boolean createByAI, int labelType) {
            this.createByAI = createByAI;
            this.labelType = labelType;
        }

        public int getAIGCLabelType() {
            return labelType;
        }
    }

    public static final class ModerationAigcInfo {
        private final int labelType;

        ModerationAigcInfo(int labelType) {
            this.labelType = labelType;
        }

        public int getModerationAigcLabelType() {
            return labelType;
        }
    }
}
