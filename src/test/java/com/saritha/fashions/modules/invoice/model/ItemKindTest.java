package com.saritha.fashions.modules.invoice.model;

import com.saritha.fashions.config.MaterialItemMigration;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemKindTest {

    private static InvoiceItem item(String name, String status, Boolean material) {
        return InvoiceItem.builder().itemName(name).itemOrderStatus(status).material(material).build();
    }

    @Test
    void liningMaterialIsAlwaysAMaterialOtherOnlyWhenAsked() {
        assertTrue(ItemKind.isMaterial("Lining Material", null));
        assertTrue(ItemKind.isMaterial(" lining material ", false));
        assertTrue(ItemKind.isMaterial("Cotton cloth", true));
        assertFalse(ItemKind.isMaterial("Cotton cloth", null));
        assertFalse(ItemKind.isMaterial("Blouse", false));
    }

    @Test
    void startStatus() {
        assertEquals("ORDER_COMPLETED", ItemKind.startStatus("Lining Material", true));
        assertEquals("ORDER_COMPLETED", ItemKind.startStatus("Other cloth", true));
        assertEquals("CUTTING", ItemKind.startStatus("Embroidery", false));
        assertEquals("CUTTING", ItemKind.startStatus("Lining Material", false)); // old, unflagged items
        assertEquals("ORDER_ACCEPTED", ItemKind.startStatus("Blouse", false));
    }

    @Test
    void storedFlagOnly() {
        assertTrue(ItemKind.isMaterial(new Document("itemName", "X").append("material", true)));
        assertFalse(ItemKind.isMaterial(new Document("itemName", "Lining Material"))); // old item: no flag
        assertFalse(ItemKind.isMaterial(item("Lining Material", "CUTTING", null)));
    }

    @Test
    void materialsDoNotStartAnInvoiceAndNeverHoldItUp() {
        InvoiceItem lining = item("Lining Material", "ORDER_COMPLETED", true);
        assertEquals(InvoiceStage.NEW, InvoiceStage.of("ORDER_ACCEPTED", List.of(item("Blouse", "ORDER_ACCEPTED", null), lining)));
        assertEquals(InvoiceStage.IN_PRODUCTION, InvoiceStage.of("ORDER_ACCEPTED", List.of(item("Blouse", "STITCHING", null), lining)));
        assertEquals(InvoiceStage.READY, InvoiceStage.of("ORDER_ACCEPTED", List.of(item("Blouse", "ORDER_COMPLETED", null), lining)));
        assertEquals(InvoiceStage.READY, InvoiceStage.of("ORDER_ACCEPTED", List.of(lining)));
        assertEquals(0, InvoiceStage.statusCounts(List.of(lining)).get("ORDER_COMPLETED"));
    }

    @Test
    void migrationTouchesOnlyWaitingUnassignedLiningMaterial() {
        Document match = MaterialItemMigration.waitingLiningMaterial("m.");
        assertEquals("Lining Material", match.get("m.itemName"));
        assertEquals("CUTTING", match.get("m.itemOrderStatus"));
        assertTrue(match.containsKey("m.assignedTo") && match.containsKey("m.assignedToWhichShopAssistant"));
        Document set = (Document) MaterialItemMigration.update().get("$set");
        assertEquals(true, set.get("items.$[m].material"));
        assertEquals("ORDER_COMPLETED", set.get("items.$[m].itemOrderStatus"));
    }
}
