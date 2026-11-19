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
 * Status changes (PATCH .../status) — the person responsible for the step, or the shop owner:
 *   ORDER_ACCEPTED          → CUTTING          tailor master, shop owner
 *   TAILOR_ASSIGNED         → STITCHING        the assigned tailor, shop owner
 *   SHOP_ASSISTANT_ASSIGNED → HEMMING          the assigned shop assistant, shop owner
 *   HEMMING                 → IRONING          the assigned shop assistant, shop owner
 *   IRONING                 → ORDER_COMPLETED  the assigned shop assistant, shop owner
 * The shop owner may move any item one step forward (same order of steps, no skipping), e.g. when a
 * worker forgot to update it. Such a change is recorded under the shop owner (item history + activity
 * log, note "by shop owner"), so the log still shows who really pressed the button. When someone is
 * absent, the shop owner can also re-assign (or unassign) the item.
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
     * @param allowedFrom       current statuses from which the assignment is allowed (supervisors)
     * @param supervisors       roles allowed on any item of the shop
     * @param workerRole        role allowed only when assigned (null if none)
     * @param assignee          which assignment the worker must hold
     * @param workerAllowedFrom the (narrower) statuses from which the worker may assign; same as allowedFrom
     *                          unless given
     */
    public record AssignmentRule(Set<ItemStatus> allowedFrom, ItemStatus resultingStatus,
                                 Set<AppRole> supervisors, AppRole workerRole, Assignee assignee,
                                 AppRole assigneeRole, Set<ItemStatus> workerAllowedFrom) {

        public AssignmentRule(Set<ItemStatus> allowedFrom, ItemStatus resultingStatus, Set<AppRole> supervisors,
                              AppRole workerRole, Assignee assignee, AppRole assigneeRole) {
            this(allowedFrom, resultingStatus, supervisors, workerRole, assignee, assigneeRole, allowedFrom);
        }

        /** Statuses from which this actor may assign: supervisors use allowedFrom, workers workerAllowedFrom. */
        public Set<ItemStatus> allowedFromFor(boolean supervisor) {
            return supervisor ? allowedFrom : workerAllowedFrom;
        }
    }

    private static final Set<AppRole> MASTER = EnumSet.of(AppRole.TAILOR_MASTER);
    private static final Set<AppRole> OWNER = EnumSet.of(AppRole.SHOP_OWNER);
    private static final Set<AppRole> OWNER_AND_MASTER = EnumSet.of(AppRole.SHOP_OWNER, AppRole.TAILOR_MASTER);

    /**
     * Cutting has no assignee: the tailor master and the shop owner are its "supervisors". The other
     * steps belong to the assigned worker (the update is guarded so it only applies while the item is
     * still assigned to that worker); the shop owner is their only supervisor.
     */
    public static final List<StatusRule> STATUS_RULES = List.of(
            new StatusRule(ORDER_ACCEPTED, CUTTING, OWNER_AND_MASTER, null, Assignee.NONE),
            new StatusRule(TAILOR_ASSIGNED, STITCHING, OWNER, AppRole.TAILOR, Assignee.TAILOR),
            new StatusRule(SHOP_ASSISTANT_ASSIGNED, HEMMING, OWNER, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT),
            new StatusRule(HEMMING, IRONING, OWNER, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT),
            new StatusRule(IRONING, ORDER_COMPLETED, OWNER, AppRole.SHOP_ASSISTANT, Assignee.SHOP_ASSISTANT)
    );

    public static final AssignmentRule ASSIGN_TAILOR = new AssignmentRule(
            EnumSet.of(CUTTING, TAILOR_ASSIGNED, STITCHING),
            TAILOR_ASSIGNED,
            OWNER_AND_MASTER, null, Assignee.NONE,
            AppRole.TAILOR);

    /**
     * Shop owner, or the item's own tailor. The tailor master is deliberately NOT allowed.
     * The tailor only hands over once, from STITCHING (no re-assign); the shop owner may re-assign later.
     */
    public static final AssignmentRule ASSIGN_SHOP_ASSISTANT = new AssignmentRule(
            EnumSet.of(STITCHING, SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING),
            SHOP_ASSISTANT_ASSIGNED,
            OWNER, AppRole.TAILOR, Assignee.TAILOR,
            AppRole.SHOP_ASSISTANT,
            EnumSet.of(STITCHING));

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
