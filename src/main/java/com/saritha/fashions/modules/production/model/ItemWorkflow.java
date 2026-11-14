package com.saritha.fashions.modules.production.model;

import com.saritha.fashions.model.AppRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.saritha.fashions.modules.production.model.ItemStatus.*;

/**
 * The ONE place that defines how an item may move through production and who may move it.
 * Every endpoint of the module checks against these rules.
 *
 * Status changes (PATCH .../status) — ONLY the person responsible for the step (no overrides, so
 * the activity log always credits the person who really did the work):
 *   ORDER_ACCEPTED          → CUTTING          tailor master
 *   TAILOR_ASSIGNED         → STITCHING        the assigned tailor
 *   SHOP_ASSISTANT_ASSIGNED → HEMMING          the assigned shop assistant
 *   HEMMING                 → IRONING          the assigned shop assistant
 *   IRONING                 → ORDER_COMPLETED  the assigned shop assistant
 * The shop owner does not change statuses; when someone is absent, the shop owner re-assigns
 * (or unassigns) the item so another tailor / shop assistant can continue.
 *
 * Assignments (PUT .../tailor, PUT .../shop-assistant):
 *   assign tailor          from CUTTING / TAILOR_ASSIGNED / STITCHING              → TAILOR_ASSIGNED
 *                          by tailor master or shop owner
 *   assign shop assistant  from STITCHING / SHOP_ASSISTANT_ASSIGNED / HEMMING / IRONING → SHOP_ASSISTANT_ASSIGNED
 *                          by the item's assigned tailor or shop owner (NOT the tailor master)
 *
 * Unassignments (DELETE .../tailor, DELETE .../shop-assistant), shop owner only:
 *   unassign tailor          from TAILOR_ASSIGNED / STITCHING → CUTTING
 *                            (not possible once a shop assistant is assigned: stitching is then complete)
 *   unassign shop assistant  from SHOP_ASSISTANT_ASSIGNED / HEMMING / IRONING → STITCHING
 *                            (stitching stays complete; the item waits for a new shop assistant)
 *
 * "Supervisors" may act on any item of their shop; "workers" only on items assigned to them.
 * To change the workflow (e.g. make hemming optional), change this table only.
 */
public final class ItemWorkflow {

    private ItemWorkflow() {
    }

    /** Who must the acting worker be assigned as on the item. */
    public enum Assignee {
        /** Worker must be item.assignedTo (tailor). */
        TAILOR,
        /** Worker must be item.assignedToWhichShopAssistant. */
        SHOP_ASSISTANT,
        /** No assignment needed (supervisor-only step). */
        NONE
    }

    /**
     * @param from        required current status
     * @param to          new status
     * @param supervisors roles allowed on any item of the shop
     * @param workerRole  role allowed only when assigned (null if none)
     * @param assignee    which assignment the worker must hold
     */
    public record StatusRule(ItemStatus from, ItemStatus to, Set<AppRole> supervisors,
                             AppRole workerRole, Assignee assignee) {
    }

    /**
     * @param allowedFrom current statuses from which the assignment is allowed
     * @param supervisors roles allowed on any item of the shop
     * @param workerRole  role allowed only when assigned (null if none)
     * @param assignee    which assignment the worker must hold
     */
    public record AssignmentRule(Set<ItemStatus> allowedFrom, ItemStatus resultingStatus,
                                 Set<AppRole> supervisors, AppRole workerRole, Assignee assignee,
                                 AppRole assigneeRole) {
    }

    private static final Set<AppRole> NOBODY = EnumSet.noneOf(AppRole.class);
    private static final Set<AppRole> MASTER = EnumSet.of(AppRole.TAILOR_MASTER);
    private static final Set<AppRole> OWNER = EnumSet.of(AppRole.SHOP_OWNER);
    private static final Set<AppRole> OWNER_AND_MASTER = EnumSet.of(AppRole.SHOP_OWNER, AppRole.TAILOR_MASTER);

    /**
     * Cutting has no assignee, so the tailor master acts as the step's only "supervisor". All other
     * steps have NO supervisors: only the assigned worker may move them (and the update is guarded
     * so it only applies while the item is still assigned to that worker).
     */
    public static final List<StatusRule> STATUS_RULES = List.of(
            new StatusRule(ORDER_ACCEPTED, CUTTING, MASTER, null, Assignee.NONE),
            new StatusRule(TAILOR_ASSIGNED, STITCHING, NOBODY, AppRole.TAILOR, Assignee.TAILOR),
            new StatusRule(SHOP_ASSISTANT_ASSIGNED, HEMMING, NOBODY, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT),
            new StatusRule(HEMMING, IRONING, NOBODY, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT),
            new StatusRule(IRONING, ORDER_COMPLETED, NOBODY, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT)
    );

    public static final AssignmentRule ASSIGN_TAILOR = new AssignmentRule(
            EnumSet.of(CUTTING, TAILOR_ASSIGNED, STITCHING),
            TAILOR_ASSIGNED,
            OWNER_AND_MASTER, null, Assignee.NONE,
            AppRole.TAILOR);

    /** Shop owner, or the item's own tailor. The tailor master is deliberately NOT allowed. */
    public static final AssignmentRule ASSIGN_SHOP_ASSISTANT = new AssignmentRule(
            EnumSet.of(STITCHING, SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING),
            SHOP_ASSISTANT_ASSIGNED,
            OWNER, AppRole.TAILOR, Assignee.TAILOR,
            AppRole.SHOP_ASSISTANT);

    /**
     * @param allowedFrom     current statuses from which the unassignment is allowed
     * @param resultingStatus status the item goes back to
     * @param supervisors     roles allowed to unassign
     */
    public record UnassignmentRule(Set<ItemStatus> allowedFrom, ItemStatus resultingStatus, Set<AppRole> supervisors) {
    }

    /** Only before a shop assistant is assigned (after that, stitching is complete). */
    public static final UnassignmentRule UNASSIGN_TAILOR = new UnassignmentRule(
            EnumSet.of(TAILOR_ASSIGNED, STITCHING), CUTTING, OWNER);

    /** Back to STITCHING: stitching stays complete, the item waits for a new shop assistant. */
    public static final UnassignmentRule UNASSIGN_SHOP_ASSISTANT = new UnassignmentRule(
            EnumSet.of(SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING), STITCHING, OWNER);

    /** The status rule for from → to, if that move exists at all. */
    public static Optional<StatusRule> statusRule(ItemStatus from, ItemStatus to) {
        return STATUS_RULES.stream().filter(rule -> rule.from() == from && rule.to() == to).findFirst();
    }

    /** The single next status reachable from the given status via PATCH .../status, if any. */
    public static Optional<StatusRule> nextStatusRule(ItemStatus from) {
        return STATUS_RULES.stream().filter(rule -> rule.from() == from).findFirst();
    }

    /**
     * True if a user with these roles may act under the rule on an item with these assignees.
     */
    public static boolean isAllowed(Set<AppRole> actorRoles, String actorUserId,
                                    Set<AppRole> supervisors, AppRole workerRole, Assignee assignee,
                                    String itemTailorId, String itemShopAssistantId) {
        if (actorRoles.stream().anyMatch(supervisors::contains)) {
            return true;
        }
        if (workerRole == null || !actorRoles.contains(workerRole)) {
            return false;
        }
        return switch (assignee) {
            case TAILOR -> actorUserId.equals(itemTailorId);
            case SHOP_ASSISTANT -> actorUserId.equals(itemShopAssistantId);
            case NONE -> true;
        };
    }
}
