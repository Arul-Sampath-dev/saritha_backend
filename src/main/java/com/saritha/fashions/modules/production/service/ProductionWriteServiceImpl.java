package com.saritha.fashions.modules.production.service;

import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.modules.invoice.model.ItemKind;
import com.saritha.fashions.modules.core.activity.ActivityEntry;
import com.saritha.fashions.modules.core.activity.ActivityLogService;
import com.saritha.fashions.modules.core.activity.ActivityType;
import com.saritha.fashions.modules.production.exception.ItemConflictException;
import com.saritha.fashions.modules.production.model.ItemStatus;
import com.saritha.fashions.modules.production.model.ItemWorkflow;
import com.saritha.fashions.modules.production.model.ItemWorkflow.AssignmentRule;
import com.saritha.fashions.modules.production.model.ItemWorkflow.StatusRule;
import com.saritha.fashions.modules.production.payload.CompleteItemsResponse;
import com.saritha.fashions.modules.production.payload.WorkItemResponse;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.ItemSnapshot;
import com.saritha.fashions.util.DateTimeUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.saritha.fashions.modules.production.service.ProductionReadServiceImpl.asString;

/**
 * Moves items through production. Replaces TailorOrderServiceImpl.updateOrderStatus /
 * updateOrderStatusByTailor, ShopAssistantServiceImpl.updateOrderStatusByTailor and
 * InvoiceServiceImpl.assignTailorToInvoice / assignShopAssistantToInvoice.
 *
 * Every request:
 *  1. resolves the user, their workflow roles and their shop;
 *  2. loads ONLY that item, and only from an invoice of the user's shop
 *     (old code: any invoice of any shop);
 *  3. checks the move against ItemWorkflow: the target must be the next step
 *     (old code: any free-text status) and the user must be a supervisor or the
 *     assigned worker (old code: the check was inverted, so any TAILOR / SHOP_ASSISTANT
 *     could change any item);
 *  4. for assignments, checks the new assignee works in the shop and has the right role;
 *  5. updates the single item atomically, only if it is still in the state checked in
 *     step 3; otherwise 409 Conflict (old code re-saved the whole invoice, which could
 *     overwrite a concurrent update of another item);
 *  6. records the employee activity (failures logged, never returned).
 * Items of delivered invoices cannot be changed.
 */
@Service
public class ProductionWriteServiceImpl implements ProductionWriteService {

    private static final Logger log = LoggerFactory.getLogger(ProductionWriteServiceImpl.class);

    private static final String FIELD_STATUS = "itemOrderStatus";
    private static final String FIELD_TAILOR = "assignedTo";
    private static final String FIELD_TAILOR_ASSIGNMENT = "assignmentStatus";
    private static final String FIELD_SHOP_ASSISTANT = "assignedToWhichShopAssistant";
    /** Stored with a capital S by the old Items model; kept for compatibility. */
    private static final String FIELD_SHOP_ASSISTANT_ASSIGNMENT = "ShopAssistantStatus";
    private static final String ASSIGNED = "ASSIGNED";
    /** Same value the old OwnerServiceImpl writes when it unassigns. */
    private static final String UNASSIGNED = "UNASSIGNED";

    private static final String CONFLICT_MESSAGE =
            "This item was changed by someone else in the meantime. Please reload it and try again.";

    private final ItemActorResolver actorResolver;
    private final ItemWorkflowRepository repository;
    private final ProductionReadServiceImpl readService;
    private final ActivityLogService activityLog;

    public ProductionWriteServiceImpl(ItemActorResolver actorResolver,
                                          ItemWorkflowRepository repository,
                                          ProductionReadServiceImpl readService,
                                          ActivityLogService activityLog) {
        this.actorResolver = actorResolver;
        this.repository = repository;
        this.readService = readService;
        this.activityLog = activityLog;
    }

    // ------------------------------------------------------------------ status changes

    @Override
    public WorkItemResponse updateStatus(String invoiceId, String itemUniqueId, String newStatus) {
        ItemActor actor = actorResolver.resolve();

        ItemStatus target = ItemStatus.from(newStatus)
                .orElseThrow(() -> new APIException("Unknown status: " + newStatus));
        if (target == ItemStatus.TAILOR_ASSIGNED) {
            throw new APIException("Use PUT .../tailor to assign a tailor");
        }
        if (target == ItemStatus.SHOP_ASSISTANT_ASSIGNED) {
            throw new APIException("Use PUT .../shop-assistant to assign a shop assistant");
        }

        ItemSnapshot snapshot = loadChangeableItem(actor, invoiceId, itemUniqueId);
        ItemStatus current = currentStatus(snapshot);
        String tailorId = asString(snapshot.item().get(FIELD_TAILOR));
        String shopAssistantId = asString(snapshot.item().get(FIELD_SHOP_ASSISTANT));

        StatusRule rule = ItemWorkflow.statusRule(current, target).orElseThrow(() -> new APIException(
                "Cannot move the item from " + current + " to " + target + ". " + nextStepHint(current)));

        boolean supervisor = actor.roles().stream().anyMatch(rule.supervisors()::contains);
        if (!ItemWorkflow.isAllowed(actor.roles(), actor.userId(), rule.supervisors(), rule.workerRole(),
                rule.assignee(), tailorId, shopAssistantId)) {
            throw new AccessDeniedException("You are not allowed to move this item to " + target);
        }

        // Guard: status unchanged, and for workers also still assigned to them.
        Map<String, Object> expected = new HashMap<>();
        if (!supervisor) {
            expected.put(rule.assignee() == ItemWorkflow.Assignee.TAILOR ? FIELD_TAILOR : FIELD_SHOP_ASSISTANT,
                    actor.userId());
        }

        boolean updated = repository.updateItemIfUnchanged(
                actor.shopId(), invoiceId, itemUniqueId, current.name(), expected,
                Map.of(FIELD_STATUS, target.name()),
                historyEntry(actor.userId(), target.name()),
                historyIsArray(snapshot));
        if (!updated) {
            throw new ItemConflictException(CONFLICT_MESSAGE);
        }

        recordStatusActivity(actor, snapshot, target);
        log.info("Item {} of invoice {} moved {} -> {} by {}", itemUniqueId, invoiceId, current, target, actor.userId());
        return reload(actor, invoiceId, itemUniqueId);
    }

    // ------------------------------------------------------------------ mark completed (shop owner)

    private static final String COMPLETED = ItemStatus.ORDER_COMPLETED.name();
    private static final int MAX_NOTE_LENGTH = 200;

    @Override
    public WorkItemResponse completeItem(String invoiceId, String itemUniqueId, String note) {
        ItemActor actor = requireShopOwner();
        String cleanNote = cleanNote(note);
        ItemSnapshot snapshot = loadChangeableItem(actor, invoiceId, itemUniqueId);
        String current = asString(snapshot.item().get(FIELD_STATUS));
        if (COMPLETED.equals(current)) {
            throw new APIException("The item is already completed");
        }
        if (!markCompleted(actor, snapshot, current, cleanNote)) {
            throw new ItemConflictException(CONFLICT_MESSAGE);
        }
        return reload(actor, invoiceId, itemUniqueId);
    }

    @Override
    public CompleteItemsResponse completeAllItems(String invoiceId, String note) {
        ItemActor actor = requireShopOwner();
        String cleanNote = cleanNote(note);
        List<ItemSnapshot> items = repository.findInvoiceItems(actor.shopId(), invoiceId);
        if (items.isEmpty()) {
            throw new APIException("Invoice not found");
        }
        if (items.get(0).invoiceDelivered()) {
            throw new APIException("The invoice has already been delivered; its items can no longer change");
        }
        List<String> completed = new ArrayList<>();
        List<CompleteItemsResponse.Failure> failed = new ArrayList<>();
        int alreadyCompleted = 0;
        for (ItemSnapshot snapshot : items) {
            String itemId = asString(snapshot.item().get("itemUniqueId"));
            String current = asString(snapshot.item().get(FIELD_STATUS));
            if (COMPLETED.equals(current)) {
                alreadyCompleted++;
            } else if (itemId == null) {
                failed.add(new CompleteItemsResponse.Failure(null, asString(snapshot.item().get("itemName")),
                        "The item has no id; edit the invoice to fix it"));
            } else if (markCompleted(actor, snapshot, current, cleanNote)) {
                completed.add(itemId);
            } else {
                failed.add(new CompleteItemsResponse.Failure(itemId, asString(snapshot.item().get("itemName")),
                        CONFLICT_MESSAGE));
            }
        }
        log.info("Invoice {}: {} item(s) marked completed by {}, {} already completed, {} failed",
                invoiceId, completed.size(), actor.userId(), alreadyCompleted, failed.size());
        return new CompleteItemsResponse(invoiceId, completed, alreadyCompleted, failed);
    }

    /**
     * Compare-and-set the item from its current status to ORDER_COMPLETED; history notes the skipped step.
     * Assignments are left as they are. Returns false when the item changed meanwhile.
     */
    private boolean markCompleted(ItemActor actor, ItemSnapshot snapshot, String current, String note) {
        String itemId = asString(snapshot.item().get("itemUniqueId"));
        Document history = historyEntry(actor.userId(), COMPLETED)
                .append("markedByShopOwner", true)
                .append("skippedFrom", current);
        if (note != null) {
            history.append("note", note);
        }
        boolean updated = repository.updateItemIfUnchanged(
                actor.shopId(), snapshot.invoiceId(), itemId, current, Map.of(),
                Map.of(FIELD_STATUS, COMPLETED), history, historyIsArray(snapshot));
        if (updated) {
            String text = "Marked completed by shop owner (was " + (current == null ? "no status" : current) + ")"
                    + (note == null ? "" : ": " + note);
            recordActivity("record ITEM_MARKED_COMPLETED", () -> activityLog.record(ActivityEntry.of(
                    snapshot.shopId(), snapshot.invoiceId(), snapshot.invoiceDbId(),
                    actor.userId(), actor.name(), ActivityType.ITEM_MARKED_COMPLETED, text)
                    .withItem(quantity(snapshot), price(snapshot), 0, null)));
            log.info("Item {} of invoice {} marked completed (was {}) by {}", itemId, snapshot.invoiceId(), current,
                    actor.userId());
        }
        return updated;
    }

    private ItemActor requireShopOwner() {
        ItemActor actor = actorResolver.resolve();
        if (!actor.has(AppRole.SHOP_OWNER)) {
            throw new AccessDeniedException("Only the shop owner can mark items completed");
        }
        return actor;
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new APIException("note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return trimmed;
    }

    // ------------------------------------------------------------------ assignments

    @Override
    public WorkItemResponse assignTailor(String invoiceId, String itemUniqueId, String tailorId) {
        return assign(invoiceId, itemUniqueId, tailorId.trim(), ItemWorkflow.ASSIGN_TAILOR,
                FIELD_TAILOR, FIELD_TAILOR_ASSIGNMENT, "tailor", ActivityType.ASSIGNED_TO_TAILOR);
    }

    @Override
    public WorkItemResponse assignShopAssistant(String invoiceId, String itemUniqueId, String shopAssistantId) {
        return assign(invoiceId, itemUniqueId, shopAssistantId.trim(), ItemWorkflow.ASSIGN_SHOP_ASSISTANT,
                FIELD_SHOP_ASSISTANT, FIELD_SHOP_ASSISTANT_ASSIGNMENT, "shop assistant",
                ActivityType.ASSIGNED_TO_ASSISTANT);
    }

    private WorkItemResponse assign(String invoiceId, String itemUniqueId, String assigneeId, AssignmentRule rule,
                                    String assigneeField, String assignmentStatusField, String assigneeLabel,
                                    ActivityType activityType) {
        ItemActor actor = actorResolver.resolve();

        ItemSnapshot snapshot = loadChangeableItem(actor, invoiceId, itemUniqueId);
        ItemStatus current = currentStatus(snapshot);
        String tailorId = asString(snapshot.item().get(FIELD_TAILOR));
        String shopAssistantId = asString(snapshot.item().get(FIELD_SHOP_ASSISTANT));
        String previousAssignee = asString(snapshot.item().get(assigneeField));

        if (!rule.allowedFrom().contains(current)) {
            throw new APIException("A " + assigneeLabel + " can be assigned only when the item is "
                    + rule.allowedFrom().stream().map(Enum::name).collect(Collectors.joining(" / "))
                    + "; it is " + current + ". " + nextStepHint(current));
        }

        boolean supervisor = actor.roles().stream().anyMatch(rule.supervisors()::contains);
        if (!ItemWorkflow.isAllowed(actor.roles(), actor.userId(), rule.supervisors(), rule.workerRole(),
                rule.assignee(), tailorId, shopAssistantId)) {
            throw new AccessDeniedException("You are not allowed to assign a " + assigneeLabel + " to this item");
        }
        if (!rule.allowedFromFor(supervisor).contains(current)) {
            throw new APIException("You can assign the " + assigneeLabel + " only when the item is "
                    + rule.allowedFromFor(supervisor).stream().map(Enum::name).collect(Collectors.joining(" / "))
                    + "; it is " + current + ". Ask the shop owner to change it.");
        }

        if (assigneeId.equals(previousAssignee)) {
            throw new APIException("This " + assigneeLabel + " is already assigned to the item");
        }
        if (!repository.isShopMemberWithRole(actor.shopId(), assigneeId, rule.assigneeRole().name())) {
            throw new APIException("User " + assigneeId + " is not a " + assigneeLabel + " of your shop");
        }

        // Guard: status and current assignee unchanged; for workers (tailor) also still assigned to them.
        Map<String, Object> expected = new HashMap<>();
        expected.put(assigneeField, previousAssignee);
        if (!supervisor && rule.assignee() == ItemWorkflow.Assignee.TAILOR) {
            expected.put(FIELD_TAILOR, actor.userId());
        }

        Map<String, Object> set = new LinkedHashMap<>();
        set.put(assigneeField, assigneeId);
        set.put(assignmentStatusField, ASSIGNED);
        set.put(FIELD_STATUS, rule.resultingStatus().name());

        boolean updated = repository.updateItemIfUnchanged(
                actor.shopId(), invoiceId, itemUniqueId, current.name(), expected, set,
                historyEntry(assigneeId, rule.resultingStatus().name()),
                historyIsArray(snapshot));
        if (!updated) {
            throw new ItemConflictException(CONFLICT_MESSAGE);
        }

        boolean reassigned = previousAssignee != null && !previousAssignee.isBlank();
        recordActivity("record " + activityType, () -> activityLog.record(ActivityEntry.of(
                snapshot.shopId(), snapshot.invoiceId(), snapshot.invoiceDbId(),
                actor.userId(), actor.name(), activityType,
                (reassigned ? "Re-assigned to " : "Assigned to ") + assigneeLabel)
                .withItem(quantity(snapshot), price(snapshot), reassigned ? 1 : 0, assigneeId)));

        log.info("Item {} of invoice {}: {} {} -> {} by {}", itemUniqueId, invoiceId, assigneeLabel,
                previousAssignee, assigneeId, actor.userId());
        return reload(actor, invoiceId, itemUniqueId);
    }

    // ------------------------------------------------------------------ unassignments (shop owner)

    @Override
    public WorkItemResponse unassignTailor(String invoiceId, String itemUniqueId) {
        return unassign(invoiceId, itemUniqueId, ItemWorkflow.UNASSIGN_TAILOR,
                FIELD_TAILOR, FIELD_TAILOR_ASSIGNMENT, "tailor", "TAILOR_UNASSIGNED",
                "The tailor can be removed only while the item is TAILOR_ASSIGNED or STITCHING. "
                        + "Once a shop assistant is assigned, stitching is complete.");
    }

    @Override
    public WorkItemResponse unassignShopAssistant(String invoiceId, String itemUniqueId) {
        return unassign(invoiceId, itemUniqueId, ItemWorkflow.UNASSIGN_SHOP_ASSISTANT,
                FIELD_SHOP_ASSISTANT, FIELD_SHOP_ASSISTANT_ASSIGNMENT, "shop assistant", "SHOP_ASSISTANT_UNASSIGNED",
                "The shop assistant can be removed only while the item is SHOP_ASSISTANT_ASSIGNED, HEMMING or IRONING.");
    }

    /**
     * Removes the assignee, sets the assignment status to UNASSIGNED and moves the item back
     * (tailor → CUTTING, shop assistant → STITCHING). Shop owner only.
     */
    private WorkItemResponse unassign(String invoiceId, String itemUniqueId, ItemWorkflow.UnassignmentRule rule,
                                      String assigneeField, String assignmentStatusField, String assigneeLabel,
                                      String historyAction, String wrongStatusMessage) {
        ItemActor actor = actorResolver.resolve();
        if (actor.roles().stream().noneMatch(rule.supervisors()::contains)) {
            throw new AccessDeniedException("Only the shop owner can remove a " + assigneeLabel);
        }

        ItemSnapshot snapshot = loadChangeableItem(actor, invoiceId, itemUniqueId);
        ItemStatus current = currentStatus(snapshot);
        if (!rule.allowedFrom().contains(current)) {
            throw new APIException(wrongStatusMessage + " It is " + current + ".");
        }

        String previousAssignee = asString(snapshot.item().get(assigneeField));
        if (previousAssignee == null || previousAssignee.isBlank()) {
            throw new APIException("No " + assigneeLabel + " is assigned to this item");
        }

        // Guard: status and assignee unchanged since we read them.
        Map<String, Object> expected = new HashMap<>();
        expected.put(assigneeField, previousAssignee);

        Map<String, Object> set = new HashMap<>(); // HashMap: values may be null
        set.put(assigneeField, null);
        set.put(assignmentStatusField, UNASSIGNED);
        set.put(FIELD_STATUS, rule.resultingStatus().name());

        boolean updated = repository.updateItemIfUnchanged(
                actor.shopId(), invoiceId, itemUniqueId, current.name(), expected, set,
                historyEntry(previousAssignee, historyAction),
                historyIsArray(snapshot));
        if (!updated) {
            throw new ItemConflictException(CONFLICT_MESSAGE);
        }

        // No ActivityType exists for unassignment, so it is logged (and kept in the item history).
        log.info("Item {} of invoice {}: {} {} removed by shop owner {}; status {} -> {}", itemUniqueId, invoiceId,
                assigneeLabel, previousAssignee, actor.userId(), current, rule.resultingStatus());
        return reload(actor, invoiceId, itemUniqueId);
    }

    // ------------------------------------------------------------------ helpers

    private ItemSnapshot loadChangeableItem(ItemActor actor, String invoiceId, String itemUniqueId) {
        ItemSnapshot snapshot = repository.findItem(actor.shopId(), invoiceId, itemUniqueId)
                .orElseThrow(() -> new APIException("Item not found"));
        if (snapshot.invoiceDelivered()) {
            throw new APIException("The invoice has already been delivered; its items can no longer change");
        }
        if (ItemKind.isMaterial(snapshot.item())) {
            throw new APIException("Material items have no production steps and cannot be moved or assigned");
        }
        return snapshot;
    }

    private static ItemStatus currentStatus(ItemSnapshot snapshot) {
        String stored = asString(snapshot.item().get(FIELD_STATUS));
        return ItemStatus.from(stored).orElseThrow(() -> new APIException(
                "The item has an unknown status '" + stored + "'; please ask the shop owner to correct it"));
    }

    private static String nextStepHint(ItemStatus current) {
        return switch (current) {
            case CUTTING -> "Next step: assign a tailor.";
            case STITCHING -> "Next step: assign a shop assistant.";
            case ORDER_COMPLETED -> "The item is already completed.";
            default -> ItemWorkflow.nextStatusRule(current)
                    .map(rule -> "Next step: " + rule.to() + ".")
                    .orElse("");
        };
    }

    private static Document historyEntry(String userId, String action) {
        return new Document("tailorId", userId)
                .append("action", action)
                .append("timestamp", DateTimeUtils.getNowInIST());
    }

    private static boolean historyIsArray(ItemSnapshot snapshot) {
        // Missing field: $push creates the array. Present but null / not an array: must be replaced.
        return !snapshot.item().containsKey("assignmentHistory")
                || snapshot.item().get("assignmentHistory") instanceof List<?>;
    }

    private WorkItemResponse reload(ItemActor actor, String invoiceId, String itemUniqueId) {
        return repository.findItem(actor.shopId(), invoiceId, itemUniqueId)
                .map(snapshot -> readService.toResponse(actor, snapshot))
                .orElseThrow(() -> new APIException("Item not found"));
    }

    private void recordStatusActivity(ItemActor actor, ItemSnapshot snapshot, ItemStatus target) {
        // A shop owner moving a worker's step is recorded under the shop owner, marked as such.
        String by = actor.roles().contains(AppRole.SHOP_OWNER) ? " (by shop owner)" : "";
        switch (target) {
            case CUTTING -> recordSimple(actor, snapshot, ActivityType.TAILOR_MASTER_CUTTING, "Moved to cutting" + by);
            case STITCHING -> recordSimple(actor, snapshot, ActivityType.TAILOR_STITCHING, "Stitching started" + by);
            case HEMMING -> recordSimple(actor, snapshot, ActivityType.ASSISTANT_HEMMING, "Hemming started" + by);
            case IRONING -> recordSimple(actor, snapshot, ActivityType.ASSISTANT_IRONING, "Ironing started" + by);
            case ORDER_COMPLETED -> recordActivity("record ORDER_COMPLETED", () -> activityLog.record(ActivityEntry.of(
                    snapshot.shopId(), snapshot.invoiceId(), snapshot.invoiceDbId(),
                    actor.userId(), actor.name(), ActivityType.ORDER_COMPLETED, "Item completed" + by)
                    .withItem(quantity(snapshot), price(snapshot), 0, null)));
            default -> {
                // assignments are recorded by assign(...)
            }
        }
    }

    private void recordSimple(ItemActor actor, ItemSnapshot snapshot, ActivityType type, String note) {
        recordActivity("record " + type, () -> activityLog.record(ActivityEntry.of(
                snapshot.shopId(), snapshot.invoiceId(), snapshot.invoiceDbId(),
                actor.userId(), actor.name(), type, note)));
    }

    private static void recordActivity(String description, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.error("Item change saved, but could not {}: {}", description, exception.getMessage(), exception);
        }
    }

    private static double quantity(ItemSnapshot snapshot) {
        return snapshot.item().get("itemQuantity") instanceof Number number ? number.doubleValue() : 0;
    }

    private static double price(ItemSnapshot snapshot) {
        return snapshot.item().get("itemPrice") instanceof Number number ? number.doubleValue() : 0.0;
    }
}
