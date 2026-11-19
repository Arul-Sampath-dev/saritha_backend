package com.saritha.fashions.modules.core.activity;

/**
 * Kinds of employee activity. Stored by name in employee_activities.type.
 *
 * Same constants (and therefore the same stored values) as the legacy
 * com.saritha.fashions.model.ActivityType, which the legacy code keeps writing until it is
 * removed. Add new kinds to BOTH while the legacy code exists, so reports can read every row.
 */
public enum ActivityType {
    ASSIGNED_TO_TAILOR_MASTER,
    TAILOR_MASTER_CUTTING,
    ASSIGNED_TO_TAILOR,
    TAILOR_STARTED,
    TAILOR_COMPLETED,
    ASSIGNED_TO_ASSISTANT,
    ASSISTANT_IRONING,
    TAILOR_STITCHING,
    ASSISTANT_HEMMING,
    ASSISTANT_COMPLETED,
    INVOICE_CREATED,
    INVOICE_DELETED,
    RECEIPT_DELETED,
    ORDER_COMPLETED,
    /** The shop owner marked an item completed directly, skipping the remaining steps (no worker credit). */
    ITEM_MARKED_COMPLETED,
    ORDER_DELIVERED,
    USER_DELETED
}
