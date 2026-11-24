package com.saritha.fashions.modules.invoice.model;

import com.saritha.fashions.config.OrderStatus;
import org.bson.Document;

import java.util.Set;

/**
 * Work items and material items.
 *
 * <p>A <b>material</b> item (Lining Material, or an "Other" item the shop owner marks as a material charge) is only
 * charged for: it has no production steps, is never assigned to anyone and does not count as anyone's work. It is
 * created ORDER_COMPLETED (so it never holds up delivery) with {@code material = true} stored on the item.
 *
 * <p>Every other item is a <b>work</b> item and goes through the production workflow. Embroidery, Saree Fall, Saree
 * Pico and Designing start in CUTTING (no cutting needed), the rest in ORDER_ACCEPTED.
 *
 * <p>Whether a stored item is a material is read from its {@code material} field only: Lining Material items created
 * before this rule have no flag and keep their old workflow (starting in CUTTING).
 */
public final class ItemKind {

    public static final String LINING_MATERIAL = "Lining Material";

    /** Work items that skip ORDER_ACCEPTED and start in CUTTING (Lining Material: only old, unflagged items). */
    private static final Set<String> START_IN_CUTTING = Set.of(
            "Embroidery", LINING_MATERIAL, "Saree Fall", "Saree Pico", "Designing");

    private ItemKind() {
    }

    /** Whether a new / edited item is a material: Lining Material always, any other item when the request says so. */
    public static boolean isMaterial(String itemName, Boolean requestedMaterial) {
        return LINING_MATERIAL.equalsIgnoreCase(itemName == null ? "" : itemName.trim()) || Boolean.TRUE.equals(requestedMaterial);
    }

    /** Status an item is created in. */
    public static String startStatus(String itemName, boolean material) {
        if (material) {
            return OrderStatus.ORDER_COMPLETED;
        }
        return itemName != null && START_IN_CUTTING.contains(itemName.trim()) ? OrderStatus.CUTTING : OrderStatus.ORDER_ACCEPTED;
    }

    /** Whether a stored item (raw document) is a material. */
    public static boolean isMaterial(Document item) {
        return item != null && Boolean.TRUE.equals(item.get("material"));
    }

    /** Whether a stored item is a material. */
    public static boolean isMaterial(InvoiceItem item) {
        return item != null && Boolean.TRUE.equals(item.getMaterial());
    }
}
