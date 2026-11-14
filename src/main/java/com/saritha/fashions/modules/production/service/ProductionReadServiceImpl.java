package com.saritha.fashions.modules.production.service;

import com.mongodb.DBRef;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.production.model.ItemStatus;
import com.saritha.fashions.modules.production.model.ItemWorkflow;
import com.saritha.fashions.modules.production.payload.AssigneeResponse;
import com.saritha.fashions.modules.production.payload.WorkItemResponse;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.ItemSnapshot;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.PageResult;
import com.saritha.fashions.payload.PaginatedResponse;
import org.bson.Document;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.saritha.fashions.modules.production.model.ItemStatus.*;

/**
 * Work queues, item details and assignee lists.
 *
 * Queue per role (if a user has several roles, the first in this order is used):
 *   SHOP_OWNER      every item not yet completed (can also ask for ORDER_COMPLETED = ready to deliver)
 *   TAILOR_MASTER   ORDER_ACCEPTED, CUTTING, TAILOR_ASSIGNED, STITCHING
 *   TAILOR          TAILOR_ASSIGNED, STITCHING                      assigned to them
 *   SHOP_ASSISTANT  SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING       assigned to them
 * Items of delivered invoices never appear.
 */
@Service
public class ProductionReadServiceImpl implements ProductionReadService {

    private static final int MAX_PAGE_SIZE = 100;

    static final String ACTION_ASSIGN_TAILOR = "ASSIGN_TAILOR";
    static final String ACTION_ASSIGN_SHOP_ASSISTANT = "ASSIGN_SHOP_ASSISTANT";
    static final String ACTION_UNASSIGN_TAILOR = "UNASSIGN_TAILOR";
    static final String ACTION_UNASSIGN_SHOP_ASSISTANT = "UNASSIGN_SHOP_ASSISTANT";

    private final ItemActorResolver actorResolver;
    private final ItemWorkflowRepository repository;

    public ProductionReadServiceImpl(ItemActorResolver actorResolver, ItemWorkflowRepository repository) {
        this.actorResolver = actorResolver;
        this.repository = repository;
    }

    @Override
    public PaginatedResponse<WorkItemResponse> getMyQueue(List<String> statuses, int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }

        ItemActor actor = actorResolver.resolve();
        QueueDefinition queue = queueFor(actor);

        Set<ItemStatus> selected = queue.defaultStatuses();
        if (statuses != null && !statuses.isEmpty()) {
            selected = EnumSet.noneOf(ItemStatus.class);
            for (String requested : statuses) {
                ItemStatus status = ItemStatus.from(requested)
                        .orElseThrow(() -> new APIException("Unknown status: " + requested));
                if (!queue.allowedStatuses().contains(status)) {
                    throw new APIException("Status " + status + " is not part of your work queue; allowed: "
                            + queue.allowedStatuses());
                }
                selected.add(status);
            }
        }

        PageResult page = repository.findItems(actor.shopId(), selected.stream().map(Enum::name).toList(),
                queue.assigneeField(), actor.userId(), pageNumber, pageSize);

        List<WorkItemResponse> content = toResponses(actor, page.items());
        int totalPages = (int) Math.ceil((double) page.total() / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, page.total(), totalPages,
                pageNumber >= totalPages - 1);
    }

    @Override
    public WorkItemResponse getItem(String invoiceId, String itemUniqueId) {
        ItemActor actor = actorResolver.resolve();
        ItemSnapshot snapshot = repository.findItem(actor.shopId(), invoiceId, itemUniqueId)
                .orElseThrow(() -> new APIException("Item not found"));
        return toResponse(actor, snapshot);
    }

    @Override
    public List<AssigneeResponse> getAssignableTailors() {
        ItemActor actor = actorResolver.resolve();
        requireAnyRole(actor, ItemWorkflow.ASSIGN_TAILOR, "assign tailors");
        return repository.findShopMembersWithRole(actor.shopId(), AppRole.TAILOR.name()).stream()
                .map(member -> new AssigneeResponse(member[0], member[1])).toList();
    }

    @Override
    public List<AssigneeResponse> getAssignableShopAssistants() {
        ItemActor actor = actorResolver.resolve();
        requireAnyRole(actor, ItemWorkflow.ASSIGN_SHOP_ASSISTANT, "assign shop assistants");
        return repository.findShopMembersWithRole(actor.shopId(), AppRole.SHOP_ASSISTANT.name()).stream()
                .map(member -> new AssigneeResponse(member[0], member[1])).toList();
    }

    // ------------------------------------------------------------------ mapping (also used by the write side)

    WorkItemResponse toResponse(ItemActor actor, ItemSnapshot snapshot) {
        return toResponses(actor, List.of(snapshot)).get(0);
    }

    /** Maps snapshots to responses, resolving user and customer names in two batched queries. */
    List<WorkItemResponse> toResponses(ItemActor actor, List<ItemSnapshot> snapshots) {
        Set<String> userIds = new HashSet<>();
        List<Object> customerIds = new ArrayList<>();
        for (ItemSnapshot snapshot : snapshots) {
            userIds.add(asString(snapshot.item().get("assignedTo")));
            userIds.add(asString(snapshot.item().get("assignedToWhichShopAssistant")));
            if (snapshot.customerRef() instanceof DBRef ref) {
                customerIds.add(ref.getId());
            }
        }
        userIds.remove(null);
        Map<String, String> userNames = repository.findUserNames(userIds);
        Map<String, String> customerNames = repository.findCustomerNames(customerIds);

        List<WorkItemResponse> responses = new ArrayList<>();
        for (ItemSnapshot snapshot : snapshots) {
            Document item = snapshot.item();
            String tailorId = asString(item.get("assignedTo"));
            String shopAssistantId = asString(item.get("assignedToWhichShopAssistant"));
            String customerName = snapshot.customerRef() instanceof DBRef ref
                    ? customerNames.get(ref.getId().toString()) : null;

            responses.add(WorkItemResponse.builder()
                    .invoiceId(snapshot.invoiceId())
                    .itemUniqueId(asString(item.get("itemUniqueId")))
                    .itemName(asString(item.get("itemName")))
                    .itemQuantity(item.get("itemQuantity") instanceof Number number ? number.intValue() : 0)
                    .imageUrl(asString(item.get("imageUrl")))
                    .status(asString(item.get("itemOrderStatus")))
                    .tailor(person(tailorId, userNames))
                    .shopAssistant(person(shopAssistantId, userNames))
                    .measurements(stringMap(item.get("measurements")))
                    .measurementList(documentList(item.get("measurementList")))
                    .customerName(customerName)
                    .expectedDeliveryDate(snapshot.expectedDeliveryDate())
                    .invoiceDate(snapshot.invoiceDate())
                    .availableActions(availableActions(actor, snapshot))
                    .history(history(item.get("assignmentHistory")))
                    .build());
        }
        return responses;
    }

    /**
     * What the actor may do next with this item, derived from the same ItemWorkflow
     * rules the write side enforces.
     */
    static List<String> availableActions(ItemActor actor, ItemSnapshot snapshot) {
        List<String> actions = new ArrayList<>();
        if (snapshot.invoiceDelivered()) {
            return actions;
        }
        Document item = snapshot.item();
        ItemStatus current = ItemStatus.from(asString(item.get("itemOrderStatus"))).orElse(null);
        if (current == null) {
            return actions;
        }
        String tailorId = asString(item.get("assignedTo"));
        String shopAssistantId = asString(item.get("assignedToWhichShopAssistant"));

        ItemWorkflow.nextStatusRule(current)
                .filter(rule -> ItemWorkflow.isAllowed(actor.roles(), actor.userId(), rule.supervisors(),
                        rule.workerRole(), rule.assignee(), tailorId, shopAssistantId))
                .ifPresent(rule -> actions.add(rule.to().name()));

        if (canAssign(actor, ItemWorkflow.ASSIGN_TAILOR, current, tailorId, shopAssistantId)) {
            actions.add(ACTION_ASSIGN_TAILOR);
        }
        if (canAssign(actor, ItemWorkflow.ASSIGN_SHOP_ASSISTANT, current, tailorId, shopAssistantId)) {
            actions.add(ACTION_ASSIGN_SHOP_ASSISTANT);
        }
        if (canUnassign(actor, ItemWorkflow.UNASSIGN_TAILOR, current, tailorId)) {
            actions.add(ACTION_UNASSIGN_TAILOR);
        }
        if (canUnassign(actor, ItemWorkflow.UNASSIGN_SHOP_ASSISTANT, current, shopAssistantId)) {
            actions.add(ACTION_UNASSIGN_SHOP_ASSISTANT);
        }
        return actions;
    }

    private static boolean canUnassign(ItemActor actor, ItemWorkflow.UnassignmentRule rule, ItemStatus current,
                                       String currentAssignee) {
        return rule.allowedFrom().contains(current)
                && currentAssignee != null && !currentAssignee.isBlank()
                && actor.roles().stream().anyMatch(rule.supervisors()::contains);
    }

    private static boolean canAssign(ItemActor actor, ItemWorkflow.AssignmentRule rule, ItemStatus current,
                                     String tailorId, String shopAssistantId) {
        return rule.allowedFrom().contains(current)
                && ItemWorkflow.isAllowed(actor.roles(), actor.userId(), rule.supervisors(), rule.workerRole(),
                rule.assignee(), tailorId, shopAssistantId);
    }

    // ------------------------------------------------------------------ queues

    record QueueDefinition(Set<ItemStatus> defaultStatuses, Set<ItemStatus> allowedStatuses, String assigneeField) {
    }

    static QueueDefinition queueFor(ItemActor actor) {
        if (actor.has(AppRole.SHOP_OWNER)) {
            Set<ItemStatus> notCompleted = EnumSet.complementOf(EnumSet.of(ORDER_COMPLETED));
            return new QueueDefinition(notCompleted, EnumSet.allOf(ItemStatus.class), null);
        }
        if (actor.has(AppRole.TAILOR_MASTER)) {
            Set<ItemStatus> statuses = EnumSet.of(ORDER_ACCEPTED, CUTTING, TAILOR_ASSIGNED, STITCHING);
            return new QueueDefinition(statuses, statuses, null);
        }
        if (actor.has(AppRole.TAILOR)) {
            Set<ItemStatus> statuses = EnumSet.of(TAILOR_ASSIGNED, STITCHING);
            return new QueueDefinition(statuses, statuses, "assignedTo");
        }
        if (actor.has(AppRole.SHOP_ASSISTANT)) {
            Set<ItemStatus> statuses = EnumSet.of(SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING);
            return new QueueDefinition(statuses, statuses, "assignedToWhichShopAssistant");
        }
        throw new AccessDeniedException("No work queue for your role");
    }

    // ------------------------------------------------------------------ helpers

    private static void requireAnyRole(ItemActor actor, ItemWorkflow.AssignmentRule rule, String what) {
        boolean allowed = actor.roles().stream().anyMatch(role ->
                rule.supervisors().contains(role) || role == rule.workerRole());
        if (!allowed) {
            throw new AccessDeniedException("You are not allowed to " + what);
        }
    }

    private static WorkItemResponse.Person person(String userId, Map<String, String> names) {
        return userId == null ? null : new WorkItemResponse.Person(userId, names.get(userId));
    }

    private static List<WorkItemResponse.HistoryEntry> history(Object value) {
        List<WorkItemResponse.HistoryEntry> history = new ArrayList<>();
        if (value instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Document document) {
                    Object timestamp = document.get("timestamp");
                    history.add(new WorkItemResponse.HistoryEntry(
                            asString(document.get("tailorId")),
                            asString(document.get("action")),
                            timestamp instanceof Date date ? date : null));
                }
            }
        }
        return history;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Document document)) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        document.forEach((key, entryValue) -> map.put(key, entryValue == null ? null : entryValue.toString()));
        return map;
    }

    private static List<Map<String, Object>> documentList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        list.stream().filter(Objects::nonNull).filter(Document.class::isInstance)
                .forEach(entry -> result.add(new LinkedHashMap<>((Document) entry)));
        return result;
    }

    static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
