package com.saritha.fashions.modules.core.activity;

/**
 * One activity to record.
 *
 * @param shopId            shop the activity belongs to
 * @param invoiceId         invoice number, if the activity is about an invoice (else null)
 * @param invoiceDbId       invoice database id (else null)
 * @param employeeId        user id of who did it
 * @param employeeName      their name at that moment
 * @param itemQuantity      item quantity, for item activities (else 0)
 * @param itemPrice         item unit price, for item activities (else 0)
 * @param reassignmentCount 1 when an assignment replaced a previous assignee (else 0)
 * @param assignedTo        user id the item was assigned to, for assignments (else null)
 */
public record ActivityEntry(String shopId, String invoiceId, String invoiceDbId, String employeeId,
                            String employeeName, ActivityType type, String notes, double itemQuantity,
                            double itemPrice, int reassignmentCount, String assignedTo) {

    /** An activity without item details (invoice created / deleted, delivery, user deleted, ...). */
    public static ActivityEntry of(String shopId, String invoiceId, String invoiceDbId, String employeeId,
                                   String employeeName, ActivityType type, String notes) {
        return new ActivityEntry(shopId, invoiceId, invoiceDbId, employeeId, employeeName, type, notes,
                0.0, 0.0, 0, null);
    }

    /** The same activity with item details (production steps and assignments). */
    public ActivityEntry withItem(double quantity, double price, int reassignments, String assignee) {
        return new ActivityEntry(shopId, invoiceId, invoiceDbId, employeeId, employeeName, type, notes,
                quantity, price, reassignments, assignee);
    }
}
