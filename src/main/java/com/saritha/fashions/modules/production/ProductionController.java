package com.saritha.fashions.modules.production;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.production.payload.AssignShopAssistantRequest;
import com.saritha.fashions.modules.production.payload.AssignTailorRequest;
import com.saritha.fashions.modules.production.payload.AssigneeResponse;
import com.saritha.fashions.modules.production.payload.UpdateItemStatusRequest;
import com.saritha.fashions.modules.production.payload.WorkItemResponse;
import com.saritha.fashions.modules.production.service.ProductionReadService;
import com.saritha.fashions.modules.production.service.ProductionWriteService;
import com.saritha.fashions.payload.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for the production workflow of invoice items.
 *
 *  GET   /items/queue                                         my work queue (depends on my role)
 *  GET   /invoices/{invoiceId}/items/{itemUniqueId}           one item + what I can do next
 *  PATCH /invoices/{invoiceId}/items/{itemUniqueId}/status    move to the next status
 *  PUT   /invoices/{invoiceId}/items/{itemUniqueId}/tailor           assign / re-assign tailor
 *  PUT   /invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant   assign / re-assign shop assistant
 *  DELETE /invoices/{invoiceId}/items/{itemUniqueId}/tailor          unassign tailor (shop owner)
 *  DELETE /invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant  unassign shop assistant (shop owner)
 *  GET   /assignees/tailors | /assignees/shop-assistants      people for the assignment dropdowns
 *
 * Replaces the status / assignment endpoints under /v1/api/tailor-master, /v1/api/tailor
 * and /v1/api/shop-assistant. All rules live in model/ItemWorkflow.
 *
 * Access: WebSecurityConfig allows SHOP_OWNER, TAILOR_MASTER, TAILOR and SHOP_ASSISTANT on
 * the item routes (/v1/api/items/... and /v1/api/invoices/{id}/items/...); the services check shop, role and
 * assignment per item
 * (model/ItemWorkflow).
 */
@RestController
@RequestMapping("/v1/api")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Production",
        description = "Production workflow of invoice items: "
                + "ORDER_ACCEPTED â†’ CUTTING â†’ TAILOR_ASSIGNED â†’ STITCHING â†’ SHOP_ASSISTANT_ASSIGNED â†’ "
                + "HEMMING â†’ IRONING â†’ ORDER_COMPLETED. Only the next step is allowed, only by the right "
                + "role, and workers only on items assigned to them. Every response lists the "
                + "`availableActions` of the current user, so the frontend can show exactly the right buttons.")
public class ProductionController {

    private static final String AUTH_DESCRIPTION =
            "Bearer token of a SHOP_OWNER, TAILOR_MASTER, TAILOR or SHOP_ASSISTANT";

    private static final String NOT_FOUND_EXAMPLE = "{\"message\": \"Item not found\", \"status\": false}";
    private static final String FORBIDDEN_EXAMPLE =
            "{\"message\": \"You are not authorized to access this resource\", \"status\": false}";
    private static final String CONFLICT_EXAMPLE = "{\"message\": \"This item was changed by someone else in the "
            + "meantime. Please reload it and try again.\", \"status\": false}";

    private final ProductionReadService readService;
    private final ProductionWriteService writeService;

    public ProductionController(ProductionReadService readService, ProductionWriteService writeService) {
        this.readService = readService;
        this.writeService = writeService;
    }

    // ------------------------------------------------------------------ reads

    @Operation(
            summary = "My work queue",
            description = "Items the logged-in user should work on, oldest invoice first. Depends on the role:\n"
                    + "- **SHOP_OWNER**: every item not yet completed (add `status=ORDER_COMPLETED` to see items ready to deliver)\n"
                    + "- **TAILOR_MASTER**: ORDER_ACCEPTED, CUTTING, TAILOR_ASSIGNED, STITCHING\n"
                    + "- **TAILOR**: TAILOR_ASSIGNED, STITCHING â€” only items assigned to them\n"
                    + "- **SHOP_ASSISTANT**: SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING â€” only items assigned to them\n\n"
                    + "Items of delivered invoices never appear. Optional `status` (repeatable) narrows the queue.",
            parameters = {
                    @Parameter(name = "status", in = ParameterIn.QUERY,
                            description = "Optional status filter, e.g. status=CUTTING&status=TAILOR_ASSIGNED",
                            array = @ArraySchema(schema = @Schema(type = "string", example = "CUTTING"))),
                    @Parameter(name = "pageNumber", in = ParameterIn.QUERY, description = "0-based page", example = "0"),
                    @Parameter(name = "pageSize", in = ParameterIn.QUERY, description = "1â€“100", example = "20"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "A page of work items (content = WorkItem[])"),
                    @ApiResponse(responseCode = "400", description = "Unknown status, a status not in your queue, or bad paging",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "No token, or not a workflow role",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping("/items/queue")
    public ResponseEntity<PaginatedResponse<WorkItemResponse>> getMyQueue(
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(readService.getMyQueue(statuses, pageNumber, pageSize));
    }

    @Operation(
            summary = "Get one item",
            description = "One item of an invoice of your shop, with its assignees, measurements, history and "
                    + "the `availableActions` you may take.",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The item",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Item not found in your shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
                    @ApiResponse(responseCode = "403", description = "No token, or not a workflow role",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/invoices/{invoiceId}/items/{itemUniqueId}")
    public ResponseEntity<WorkItemResponse> getItem(@PathVariable String invoiceId, @PathVariable String itemUniqueId) {
        return ResponseEntity.ok(readService.getItem(invoiceId, itemUniqueId));
    }

    @Operation(
            summary = "Tailors I can assign",
            description = "Tailors of your shop, for the assign-tailor dropdown. For SHOP_OWNER and TAILOR_MASTER.",
            parameters = @Parameter(hidden = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tailors, sorted by name",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssigneeResponse.class)))),
                    @ApiResponse(responseCode = "403", description = "Not allowed to assign tailors",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/items/assignees/tailors")
    public ResponseEntity<List<AssigneeResponse>> getAssignableTailors() {
        return ResponseEntity.ok(readService.getAssignableTailors());
    }

    @Operation(
            summary = "Shop assistants I can assign",
            description = "Shop assistants of your shop, for the assign-shop-assistant dropdown. "
                    + "For SHOP_OWNER and TAILOR.",
            parameters = @Parameter(hidden = true),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shop assistants, sorted by name",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AssigneeResponse.class)))),
                    @ApiResponse(responseCode = "403", description = "Not allowed to assign shop assistants",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/items/assignees/shop-assistants")
    public ResponseEntity<List<AssigneeResponse>> getAssignableShopAssistants() {
        return ResponseEntity.ok(readService.getAssignableShopAssistants());
    }

    // ------------------------------------------------------------------ writes

    @Operation(
            summary = "Move an item to its next status",
            description = "Allowed moves and who may make them:\n"
                    + "| From | To | Who |\n|---|---|---|\n"
                    + "| ORDER_ACCEPTED | CUTTING | Tailor master, shop owner |\n"
                    + "| TAILOR_ASSIGNED | STITCHING | The assigned tailor, tailor master, shop owner |\n"
                    + "| SHOP_ASSISTANT_ASSIGNED | HEMMING | The assigned shop assistant, shop owner |\n"
                    + "| HEMMING | IRONING | The assigned shop assistant, shop owner |\n"
                    + "| IRONING | ORDER_COMPLETED | The assigned shop assistant, shop owner |\n\n"
                    + "TAILOR_ASSIGNED and SHOP_ASSISTANT_ASSIGNED are set by the assignment endpoints. "
                    + "Skipping steps, going back, or acting on someone else's item is rejected.",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = UpdateItemStatusRequest.class),
                            examples = @ExampleObject(value = "{ \"status\": \"STITCHING\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item updated",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Unknown status, not the next step, invoice delivered, or item not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = {
                                            @ExampleObject(name = "Not the next step", value = "{\"message\": \"Cannot move the item "
                                                    + "from TAILOR_ASSIGNED to ORDER_COMPLETED. Next step: STITCHING.\", \"status\": false}"),
                                            @ExampleObject(name = "Item not found", value = NOT_FOUND_EXAMPLE)
                                    })),
                    @ApiResponse(responseCode = "403", description = "Not your item / not your role",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
                    @ApiResponse(responseCode = "409", description = "Someone else changed the item meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PatchMapping("/invoices/{invoiceId}/items/{itemUniqueId}/status")
    public ResponseEntity<WorkItemResponse> updateStatus(@PathVariable String invoiceId,
                                                         @PathVariable String itemUniqueId,
                                                         @Valid @RequestBody UpdateItemStatusRequest request) {
        return ResponseEntity.ok(writeService.updateStatus(invoiceId, itemUniqueId, request.getStatus()));
    }

    @Operation(
            summary = "Assign or re-assign a tailor",
            description = "Sets the item's tailor and moves it to TAILOR_ASSIGNED. Allowed when the item is CUTTING "
                    + "(first assignment), or TAILOR_ASSIGNED / STITCHING (re-assignment: the new tailor starts "
                    + "again from TAILOR_ASSIGNED). For tailor master and shop owner. The tailor must be a TAILOR "
                    + "of your shop.",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AssignTailorRequest.class),
                            examples = @ExampleObject(value = "{ \"tailorId\": \"665f0a1b2c3d4e5f6a7b8c9d\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tailor assigned",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Wrong item status, already assigned to this tailor, "
                            + "not a tailor of your shop, invoice delivered, or item not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"User 665f... is not a tailor of your shop\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not allowed to assign tailors",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Someone else changed the item meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PutMapping("/invoices/{invoiceId}/items/{itemUniqueId}/tailor")
    public ResponseEntity<WorkItemResponse> assignTailor(@PathVariable String invoiceId,
                                                         @PathVariable String itemUniqueId,
                                                         @Valid @RequestBody AssignTailorRequest request) {
        return ResponseEntity.ok(writeService.assignTailor(invoiceId, itemUniqueId, request.getTailorId()));
    }

    @Operation(
            summary = "Assign or re-assign a shop assistant",
            description = "Sets the item's shop assistant and moves it to SHOP_ASSISTANT_ASSIGNED for finishing. "
                    + "Allowed when the item is STITCHING (first assignment), or SHOP_ASSISTANT_ASSIGNED / HEMMING / "
                    + "IRONING (re-assignment: the new assistant starts again from SHOP_ASSISTANT_ASSIGNED). Only the "
                    + "**item's assigned tailor** or the **shop owner** (not the tailor master). The assistant must be "
                    + "a SHOP_ASSISTANT of your shop.",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AssignShopAssistantRequest.class),
                            examples = @ExampleObject(value = "{ \"shopAssistantId\": \"665f0a1b2c3d4e5f6a7b8c9e\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shop assistant assigned",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Wrong item status, already assigned to this assistant, "
                            + "not a shop assistant of your shop, invoice delivered, or item not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not allowed (e.g. a tailor who is not this item's tailor)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Someone else changed the item meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PutMapping("/invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant")
    public ResponseEntity<WorkItemResponse> assignShopAssistant(@PathVariable String invoiceId,
                                                                @PathVariable String itemUniqueId,
                                                                @Valid @RequestBody AssignShopAssistantRequest request) {
        return ResponseEntity.ok(writeService.assignShopAssistant(invoiceId, itemUniqueId, request.getShopAssistantId()));
    }

    @Operation(
            summary = "Unassign the tailor (shop owner)",
            description = "Removes the item's tailor and moves the item back to CUTTING, ready for another tailor. "
                    + "Only the **shop owner**, and only while the item is TAILOR_ASSIGNED or STITCHING. Once a shop "
                    + "assistant is assigned, stitching is complete and the tailor can no longer be removed. "
                    + "The removal is kept in the item history (TAILOR_UNASSIGNED).",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Tailor removed; item is CUTTING",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Wrong status (e.g. stitching already complete), "
                            + "no tailor assigned, invoice delivered, or item not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"The tailor can be removed only while the "
                                            + "item is TAILOR_ASSIGNED or STITCHING. Once a shop assistant is assigned, stitching "
                                            + "is complete. It is HEMMING.\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not the shop owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
                    @ApiResponse(responseCode = "409", description = "Someone else changed the item meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @DeleteMapping("/invoices/{invoiceId}/items/{itemUniqueId}/tailor")
    public ResponseEntity<WorkItemResponse> unassignTailor(@PathVariable String invoiceId,
                                                           @PathVariable String itemUniqueId) {
        return ResponseEntity.ok(writeService.unassignTailor(invoiceId, itemUniqueId));
    }

    @Operation(
            summary = "Unassign the shop assistant (shop owner)",
            description = "Removes the item's shop assistant and moves the item back to STITCHING: stitching stays "
                    + "complete and the item waits for another shop assistant. Only the **shop owner**, and only "
                    + "while the item is SHOP_ASSISTANT_ASSIGNED, HEMMING or IRONING (not after ORDER_COMPLETED). "
                    + "The removal is kept in the item history (SHOP_ASSISTANT_UNASSIGNED).",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "itemUniqueId", in = ParameterIn.PATH, required = true, example = "Blouse_1762935000000_0"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shop assistant removed; item is STITCHING",
                            content = @Content(schema = @Schema(implementation = WorkItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Wrong status, no shop assistant assigned, "
                            + "invoice delivered, or item not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not the shop owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
                    @ApiResponse(responseCode = "409", description = "Someone else changed the item meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @DeleteMapping("/invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant")
    public ResponseEntity<WorkItemResponse> unassignShopAssistant(@PathVariable String invoiceId,
                                                                  @PathVariable String itemUniqueId) {
        return ResponseEntity.ok(writeService.unassignShopAssistant(invoiceId, itemUniqueId));
    }
}
