package com.saritha.fashions.modules.production.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.saritha.fashions.modules.production.model.ItemStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemWorkflowTest {

    @Test
    void tailorHandsOverToShopAssistantOnlyFromStitching() {
        assertEquals(EnumSet.of(STITCHING), ItemWorkflow.ASSIGN_SHOP_ASSISTANT.allowedFromFor(false));
    }

    @Test
    void shopOwnerMayStillReassignTheShopAssistantLater() {
        assertEquals(EnumSet.of(STITCHING, SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING),
                ItemWorkflow.ASSIGN_SHOP_ASSISTANT.allowedFromFor(true));
    }

    @Test
    void ruleWithoutWorkerStatusesUsesTheSameStatusesForEveryone() {
        assertEquals(ItemWorkflow.ASSIGN_TAILOR.allowedFrom(), ItemWorkflow.ASSIGN_TAILOR.allowedFromFor(false));
        assertEquals(ItemWorkflow.ASSIGN_TAILOR.allowedFrom(), ItemWorkflow.ASSIGN_TAILOR.allowedFromFor(true));
    }
}
