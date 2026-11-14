package com.saritha.fashions.modules.production.service;

import com.saritha.fashions.modules.production.payload.WorkItemResponse;

/**
 * Write side of item management: move items through production.
 */
public interface ProductionWriteService {

    /** Moves the item to its next status (see ItemWorkflow.STATUS_RULES). */
    WorkItemResponse updateStatus(String invoiceId, String itemUniqueId, String newStatus);

    /** Assigns or re-assigns a tailor; the item becomes TAILOR_ASSIGNED. */
    WorkItemResponse assignTailor(String invoiceId, String itemUniqueId, String tailorId);

    /** Assigns or re-assigns a shop assistant; the item becomes SHOP_ASSISTANT_ASSIGNED. */
    WorkItemResponse assignShopAssistant(String invoiceId, String itemUniqueId, String shopAssistantId);

    /** Shop owner removes the tailor (only before stitching is complete); the item goes back to CUTTING. */
    WorkItemResponse unassignTailor(String invoiceId, String itemUniqueId);

    /** Shop owner removes the shop assistant; the item goes back to STITCHING (stitching complete). */
    WorkItemResponse unassignShopAssistant(String invoiceId, String itemUniqueId);
}
