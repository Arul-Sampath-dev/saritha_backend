package com.saritha.fashions.modules.production.service;

import com.mongodb.DBRef;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.AppRole;
import com.saritha.fashions.modules.core.date.DeliveryDates;
import com.saritha.fashions.modules.production.model.ItemStatus;
import com.saritha.fashions.modules.production.model.ItemWorkflow;
import com.saritha.fashions.modules.production.payload.AssigneeResponse;
import com.saritha.fashions.modules.production.payload.QueueOrderResponse;
import com.saritha.fashions.modules.production.payload.QueueSummaryResponse;
import com.saritha.fashions.modules.production.payload.WorkItemResponse;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.ItemSnapshot;
import com.saritha.fashions.modules.production.repository.ItemWorkflowRepository.PageResult;
import com.saritha.fashions.payload.PaginatedResponse;
import org.bson.Document;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    /** Safety cap for the whole-queue reads (search / sort / grouping / counts); far above a shop's open items. */
    static final int MAX_QUEUE_ITEMS = 5000;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    static final String ACTION_ASSIGN_TAILOR = "ASSIGN_TAILOR";
    static final String ACTION_ASSIGN_SHOP_ASSISTANT = "ASSIGN_SHOP_ASSISTANT";
    static final String ACTION_UNASSIGN_TAILOR = "UNASSIGN_TAILOR";
    static final String ACTION_UNASSIGN_SHOP_ASSISTANT = "UNASSIGN_SHOP_ASSISTANT";
    /** Shop owner: mark the item completed directly (POST .../complete). */
    static final String ACTION_COMPLETE = "COMPLETE";

    private final ItemActorResolver actorResolver;
    private final ItemWorkflowRepository repository;

    public ProductionReadServiceImpl(ItemActorResolver actorResolver, ItemWorkflowRepository repository) {
        this.actorResolver = actorResolver;
        this.repository = repository;
    }

    @Override
    public PaginatedResponse<WorkItemResponse> getMyQueue(List<String> statuses, int pageNumber, int pageSize) {
        return getMyQueue(new QueueFilter(statuses, null, QueueFilter.Sort.OLDEST, null), pageNumber, pageSize);
    }

    @Override
    public PaginatedResponse<WorkItemResponse> getMyQueue(QueueFilter filter, int pageNumber, int pageSize) {
        checkPaging(pageNumber, pageSize);
        ItemActor actor = actorResolver.resolve();
        QueueDefinition queue = queueFor(actor);
        Set<ItemStatus> selected = selectedStatuses(queue, filter.statuses());

        if (filter.isPlain()) {
            // Only statuses: paged in the database (unchanged behaviour).
            PageResult page = repository.findItems(actor.shopId(), names(selected),
                    queue.assigneeField(), actor.userId(), pageNumber, pageSize);
            return page(toResponses(actor, page.items()), page.total(), pageNumber, pageSize);
        }

        LocalDate today = today();
        List<QueueEntry> entries = matching(loadQueue(actor, queue, selected, filter.search() != null), filter, today);
        entries.sort(order(filter.sort()));
        int from = (int) Math.min((long) pageNumber * pageSize, entries.size());
        int to = Math.min(from + pageSize, entries.size());
        List<ItemSnapshot> pageItems = entries.subList(from, to).stream().map(QueueEntry::snapshot).toList();
        return page(toResponses(actor, pageItems), entries.size(), pageNumber, pageSize);
    }

    @Override
    public QueueSummaryResponse getMyQueueSummary() {
        ItemActor actor = actorResolver.resolve();
        QueueDefinition queue = queueFor(actor);
        LocalDate today = today();
        List<QueueEntry> entries = loadQueue(actor, queue, queue.defaultStatuses(), false);

        Map<String, Long> counts = zeroCounts(queue.defaultStatuses());
        Set<String> orders = new HashSet<>();
        long late = 0;
        long dueToday = 0;
        long dueThisWeek = 0;
        long withoutDate = 0;
        for (QueueEntry entry : entries) {
            counts.merge(asString(entry.snapshot().item().get("itemOrderStatus")), 1L, Long::sum);
            orders.add(entry.snapshot().invoiceDbId());
            LocalDate due = entry.due();
            if (due == null) {
                withoutDate++;
            } else if (due.isBefore(today)) {
                late++;
            } else {
                if (due.isEqual(today)) {
                    dueToday++;
                }
                if (!due.isAfter(today.plusDays(6))) {
                    dueThisWeek++;
                }
            }
        }
        return new QueueSummaryResponse(today, counts, entries.size(), orders.size(), late, dueToday, dueThisWeek,
                withoutDate);
    }

    @Override
    public PaginatedResponse<QueueOrderResponse> getMyQueueOrders(QueueFilter filter, int pageNumber, int pageSize) {
        checkPaging(pageNumber, pageSize);
        ItemActor actor = actorResolver.resolve();
        QueueDefinition queue = queueFor(actor);
        Set<ItemStatus> selected = selectedStatuses(queue, filter.statuses());
        LocalDate today = today();

        List<QueueEntry> entries = matching(loadQueue(actor, queue, selected, filter.search() != null), filter, today);
        entries.sort(order(filter.sort()));
        // Group by invoice; an order's place is the place of its first item in the sorted list.
        Map<String, List<ItemSnapshot>> byInvoice = new LinkedHashMap<>();
        for (QueueEntry entry : entries) {
            byInvoice.computeIfAbsent(entry.snapshot().invoiceDbId(), key -> new ArrayList<>()).add(entry.snapshot());
        }
        List<List<ItemSnapshot>> orders = new ArrayList<>(byInvoice.values());
        int from = (int) Math.min((long) pageNumber * pageSize, orders.size());
        int to = Math.min(from + pageSize, orders.size());
        List<List<ItemSnapshot>> pageOrders = orders.subList(from, to);

        // One batch for the names of every item on the page, then split back per order.
        List<WorkItemResponse> responses = toResponses(actor, pageOrders.stream().flatMap(List::stream).toList());
        List<QueueOrderResponse> content = new ArrayList<>();
        int index = 0;
        for (List<ItemSnapshot> order : pageOrders) {
            List<WorkItemResponse> items = responses.subList(index, index + order.size());
            index += order.size();
            ItemSnapshot first = order.get(0);
            WorkItemResponse firstItem = items.get(0);
            Map<String, Long> counts = zeroCounts(selected);
            items.forEach(item -> counts.merge(item.getStatus(), 1L, Long::sum));
            content.add(new QueueOrderResponse(first.invoiceId(), firstItem.getCustomerName(), first.invoiceDate(),
                    first.expectedDeliveryDate(), firstItem.getDueDate(), firstItem.getDaysOverdue(), counts,
                    items.size(), List.copyOf(items)));
        }
        return page(content, orders.size(), pageNumber, pageSize);
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
        return getAssignableTailors(false);
    }

    @Override
    public List<AssigneeResponse> getAssignableTailors(boolean withLoad) {
        ItemActor actor = actorResolver.resolve();
        requireAnyRole(actor, ItemWorkflow.ASSIGN_TAILOR, "assign tailors");
        List<String[]> tailors = repository.findShopMembersWithRole(actor.shopId(), AppRole.TAILOR.name());
        if (!withLoad) {
            return tailors.stream().map(member -> new AssigneeResponse(member[0], member[1])).toList();
        }
        // Current load: items assigned (not started) / stitching per tailor, and how many of them are late.
        return withLoad(actor.shopId(), tailors, "assignedTo", TAILOR_ASSIGNED, EnumSet.of(STITCHING));
    }

    /**
     * People with their current load: assigned = items in {@code waiting}, inProgress = items in {@code working},
     * late = of those, delivery date passed (IST).
     */
    private List<AssigneeResponse> withLoad(String shopId, List<String[]> members, String assigneeField,
                                            ItemStatus waiting, Set<ItemStatus> working) {
        LocalDate today = today();
        List<String> statuses = new ArrayList<>();
        statuses.add(waiting.name());
        working.forEach(status -> statuses.add(status.name()));
        Map<String, long[]> load = new HashMap<>();
        for (ItemSnapshot snapshot : repository.findAllItems(shopId, statuses, null, null, MAX_QUEUE_ITEMS)) {
            String userId = asString(snapshot.item().get(assigneeField));
            if (userId == null) {
                continue;
            }
            long[] counts = load.computeIfAbsent(userId, key -> new long[3]);
            counts[waiting.name().equals(asString(snapshot.item().get("itemOrderStatus"))) ? 0 : 1]++;
            LocalDate due = DeliveryDates.parse(snapshot.expectedDeliveryDate()).orElse(null);
            if (due != null && due.isBefore(today)) {
                counts[2]++;
            }
        }
        return members.stream().map(member -> {
            long[] counts = load.getOrDefault(member[0], new long[3]);
            return new AssigneeResponse(member[0], member[1], counts[0], counts[1], counts[2]);
        }).toList();
    }

    @Override
    public List<AssigneeResponse> getAssignableShopAssistants() {
        return getAssignableShopAssistants(false);
    }

    @Override
    public List<AssigneeResponse> getAssignableShopAssistants(boolean withLoad) {
        ItemActor actor = actorResolver.resolve();
        requireAnyRole(actor, ItemWorkflow.ASSIGN_SHOP_ASSISTANT, "assign shop assistants");
        List<String[]> assistants = repository.findShopMembersWithRole(actor.shopId(), AppRole.SHOP_ASSISTANT.name());
        if (!withLoad) {
            return assistants.stream().map(member -> new AssigneeResponse(member[0], member[1])).toList();
        }
        // Current load: assigned (not started) / hemming + ironing per shop assistant, and how many are late.
        return withLoad(actor.shopId(), assistants, "assignedToWhichShopAssistant", SHOP_ASSISTANT_ASSIGNED,
                EnumSet.of(HEMMING, IRONING));
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
        LocalDate today = today();

        List<WorkItemResponse> responses = new ArrayList<>();
        for (ItemSnapshot snapshot : snapshots) {
            LocalDate due = DeliveryDates.parse(snapshot.expectedDeliveryDate()).orElse(null);
            Document item = snapshot.item();
            String tailorId = asString(item.get("assignedTo"));
            String shopAssistantId = asString(item.get("assignedToWhichShopAssistant"));
            String customerName = snapshot.customerRef() instanceof DBRef ref
                    ? customerNames.get(ref.getId().toString()) : null;

            responses.add(WorkItemResponse.builder()
                    .invoiceId(snapshot.invoiceId())
                    .itemUniqueId(asString(item.get("itemUniqueId")))
                    .itemName(asString(item.get("itemName")))
                    .itemQuantity(item.get("itemQuantity") instanceof Number number ? number.doubleValue() : 0)
                    .imageUrl(asString(item.get("imageUrl")))
                    .status(asString(item.get("itemOrderStatus")))
                    .tailor(person(tailorId, userNames))
                    .shopAssistant(person(shopAssistantId, userNames))
                    .measurements(stringMap(item.get("measurements")))
                    .measurementList(documentList(item.get("measurementList")))
                    .customerName(customerName)
                    .expectedDeliveryDate(snapshot.expectedDeliveryDate())
                    .invoiceDate(snapshot.invoiceDate())
                    .dueDate(due == null ? null : DeliveryDates.format(due))
                    .daysOverdue(daysOverdue(due, today))
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
        if (actor.has(AppRole.SHOP_OWNER) && current != ItemStatus.ORDER_COMPLETED) {
            actions.add(ACTION_COMPLETE);
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
        boolean supervisor = actor.roles().stream().anyMatch(rule.supervisors()::contains);
        return rule.allowedFromFor(supervisor).contains(current)
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

    // ------------------------------------------------------------------ whole-queue helpers (search / sort / due)

    /** A queue item with its parsed delivery date and (when searching) customer name. */
    record QueueEntry(ItemSnapshot snapshot, LocalDate due, String customerName) {
    }

    private List<QueueEntry> loadQueue(ItemActor actor, QueueDefinition queue, Set<ItemStatus> statuses,
                                       boolean withCustomerNames) {
        List<ItemSnapshot> snapshots = repository.findAllItems(actor.shopId(), names(statuses),
                queue.assigneeField(), actor.userId(), MAX_QUEUE_ITEMS);
        Map<String, String> customerNames = Map.of();
        if (withCustomerNames) {
            List<Object> ids = new ArrayList<>();
            snapshots.forEach(snapshot -> {
                if (snapshot.customerRef() instanceof DBRef ref) {
                    ids.add(ref.getId());
                }
            });
            customerNames = repository.findCustomerNames(ids);
        }
        List<QueueEntry> entries = new ArrayList<>(snapshots.size());
        for (ItemSnapshot snapshot : snapshots) {
            String customerName = snapshot.customerRef() instanceof DBRef ref && ref.getId() != null
                    ? customerNames.get(ref.getId().toString()) : null;
            entries.add(new QueueEntry(snapshot, DeliveryDates.parse(snapshot.expectedDeliveryDate()).orElse(null),
                    customerName));
        }
        return entries;
    }

    /** Entries matching the search text and the due filter (a new, modifiable list). */
    static List<QueueEntry> matching(List<QueueEntry> entries, QueueFilter filter, LocalDate today) {
        String text = filter.search() == null ? null : filter.search().toLowerCase(Locale.ROOT);
        List<QueueEntry> result = new ArrayList<>();
        for (QueueEntry entry : entries) {
            if (text != null && !contains(entry.snapshot().invoiceId(), text) && !contains(entry.customerName(), text)
                    && !contains(asString(entry.snapshot().item().get("itemName")), text)) {
                continue;
            }
            if (filter.due() != null && !dueMatches(filter.due(), entry.due(), today)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    static boolean dueMatches(QueueFilter.Due due, LocalDate date, LocalDate today) {
        return switch (due) {
            case NONE -> date == null;
            case TODAY -> date != null && date.isEqual(today);
            case OVERDUE -> date != null && date.isBefore(today);
            case WEEK -> date != null && !date.isBefore(today) && !date.isAfter(today.plusDays(6));
        };
    }

    /** OLDEST = invoice date (as the database order), NEWEST = newest invoice first, DUE_SOONEST = no date last. */
    static Comparator<QueueEntry> order(QueueFilter.Sort sort) {
        Comparator<QueueEntry> byInvoiceDate = Comparator.comparing(
                (QueueEntry entry) -> entry.snapshot().invoiceDate(), Comparator.nullsFirst(Comparator.naturalOrder()));
        Comparator<QueueEntry> byItem = Comparator.comparing(
                (QueueEntry entry) -> asString(entry.snapshot().item().get("itemUniqueId")),
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<QueueEntry> byInvoice = Comparator.comparing(
                (QueueEntry entry) -> entry.snapshot().invoiceDbId(), Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sort == null ? QueueFilter.Sort.OLDEST : sort) {
            case OLDEST -> byInvoiceDate.thenComparing(byInvoice).thenComparing(byItem);
            case NEWEST -> byInvoiceDate.reversed().thenComparing(byInvoice).thenComparing(byItem);
            case DUE_SOONEST -> Comparator.comparing(QueueEntry::due, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(byInvoiceDate).thenComparing(byInvoice).thenComparing(byItem);
        };
    }

    private static Set<ItemStatus> selectedStatuses(QueueDefinition queue, List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return queue.defaultStatuses();
        }
        Set<ItemStatus> selected = EnumSet.noneOf(ItemStatus.class);
        for (String requested : statuses) {
            ItemStatus status = ItemStatus.from(requested)
                    .orElseThrow(() -> new APIException("Unknown status: " + requested));
            if (!queue.allowedStatuses().contains(status)) {
                throw new APIException("Status " + status + " is not part of your work queue; allowed: "
                        + queue.allowedStatuses());
            }
            selected.add(status);
        }
        return selected;
    }

    private static void checkPaging(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new APIException("pageNumber must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new APIException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static <T> PaginatedResponse<T> page(List<T> content, long total, int pageNumber, int pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PaginatedResponse<>(content, pageNumber, pageSize, total, totalPages, pageNumber >= totalPages - 1);
    }

    /** Every given status with 0, in workflow order. */
    private static Map<String, Long> zeroCounts(Set<ItemStatus> statuses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        EnumSet.copyOf(statuses.isEmpty() ? EnumSet.noneOf(ItemStatus.class) : statuses)
                .forEach(status -> counts.put(status.name(), 0L));
        return counts;
    }

    private static List<String> names(Set<ItemStatus> statuses) {
        return statuses.stream().map(Enum::name).toList();
    }

    static long daysOverdue(LocalDate due, LocalDate today) {
        return due != null && due.isBefore(today) ? ChronoUnit.DAYS.between(due, today) : 0;
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    private static LocalDate today() {
        return LocalDate.now(IST);
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
