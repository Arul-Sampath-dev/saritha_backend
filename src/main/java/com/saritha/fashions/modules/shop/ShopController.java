package com.saritha.fashions.modules.shop;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.shop.model.ManagedShop;
import com.saritha.fashions.modules.shop.payload.AddPettyCashRequest;
import com.saritha.fashions.modules.shop.payload.CreateShopRequest;
import com.saritha.fashions.modules.shop.payload.PettyCashTopUpResponse;
import jakarta.validation.Valid;
import com.saritha.fashions.modules.shop.payload.PettyCashHistoryResponse;
import com.saritha.fashions.modules.shop.payload.ShopNameResponse;
import com.saritha.fashions.modules.shop.payload.ShopSummaryResponse;
import com.saritha.fashions.modules.shop.payload.UpdateShopRequest;
import com.saritha.fashions.modules.shop.payload.ShopOwnerUpdateResponse;
import com.saritha.fashions.modules.shop.payload.UpdateShopOwnerRequest;
import com.saritha.fashions.payload.PaginatedResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.saritha.fashions.modules.shop.service.ShopReadService;
import com.saritha.fashions.modules.shop.service.ShopWriteService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for shop management (create, view, update).
 *
 * Access: platform OWNER only (WebSecurityConfig rule for /v1/api/owner/**), except
 * POST and GET /{shopId}/petty-cash, which the shop's SHOP_OWNER may also call (the services
 * check that they belong to that shop).
 *
 * Delete is intentionally not exposed because ShopWriteServiceImpl.delete()
 * is not implemented (shops are not meant to be deleted).
 */
@RestController
@RequestMapping("/v1/api/shops")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Shops",
        description = "Platform owner APIs to create, list, view and update shops, and add petty cash. "
                + "Requires an OWNER token (adding petty cash: OWNER or the shop's SHOP_OWNER).")
public class ShopController {

    private static final String ERROR_EXAMPLE_SHOP_NOT_FOUND =
            "{\"message\": \"No Shop Found\", \"status\": false}";

    private final ShopReadService shopReadService;
    private final ShopWriteService shopWriteService;

    public ShopController(ShopReadService shopReadService,
                                    ShopWriteService shopWriteService) {
        this.shopReadService = shopReadService;
        this.shopWriteService = shopWriteService;
    }

    /**
     * Creates a new shop and registers its owner account.
     * POST /v1/api/shops
     */
    @Operation(
            summary = "Create a shop and its owner account",
            description = "Creates a shop and its owner account (role SHOP_OWNER, linked to the shop) through "
                    + "the authentication module's registration. The request is validated first (owner "
                    + "password: 8-100 characters with a letter and a digit; ownerNumber: 10 digits) and a "
                    + "taken email is rejected before anything is saved. If the owner account still cannot "
                    + "be created, the shop is removed again and the error is returned.\n\n"
                    + "Requires an OWNER token.",
            parameters = @Parameter(hidden = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Shop details and owner account details",
                    content = @Content(schema = @Schema(implementation = CreateShopRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Shop and owner created",
                            content = @Content(schema = @Schema(implementation = ManagedShop.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Invalid fields, email already in use, or the owner account could not "
                                    + "be created; the shop is not saved",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Field errors", value = "{\"password\": \"password must contain at "
                                            + "least one letter and one digit\", \"ownerNumber\": \"ownerNumber must be a 10-digit number\"}"),
                                    @ExampleObject(name = "Email taken", value =
                                            "{\"message\": \"Email is already in use: owner@example.com\", \"status\": false}")
                            })),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER")
            })
    @PostMapping
    public ResponseEntity<ManagedShop> createShop(@Valid @RequestBody CreateShopRequest shopRequest) {
        ManagedShop createdShop = shopWriteService.create(shopRequest);
        return new ResponseEntity<>(createdShop, HttpStatus.CREATED);
    }

    /**
     * Returns all shops.
     * GET /v1/api/shops
     */
    @Operation(
            summary = "List all shops (full documents)",
            description = "Returns every shop with all stored fields, including petty cash history, "
                    + "expenses, receipts and invoices. Heavy for many shops; use "
                    + "GET /summaries for listings.",
            responses = @ApiResponse(responseCode = "200", description = "All shops",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = ManagedShop.class)))))
    @GetMapping
    public ResponseEntity<List<ManagedShop>> getAllShops() {
        return ResponseEntity.ok(shopReadService.getAll());
    }

    /**
     * Returns a lightweight summary of every shop for the platform owner
     * (details + pettyCash + totalExpense), sorted by shop name.
     * GET /v1/api/shops/summaries
     *
     * Declared before "/{shopId}"; Spring prefers the literal path anyway, so
     * "summaries" is never treated as a shop id.
     */
    @Operation(
            summary = "List shop summaries",
            description = "Platform owner view of all shops, sorted by shop name (A-Z). Returns "
                    + "basic details (name, type, GST, owner, contact, address, state) plus the "
                    + "current petty cash balance and total expense. Only these fields are read "
                    + "from the database, so it is much lighter than listing full shops.",
            responses = @ApiResponse(responseCode = "200",
                    description = "Shop summaries (empty list if there are no shops)",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = ShopSummaryResponse.class)))))
    @GetMapping("/summaries")
    public ResponseEntity<List<ShopSummaryResponse>> getAllShopSummaries() {
        return ResponseEntity.ok(shopReadService.getAllShopSummaries());
    }

    /**
     * Returns a single shop by id.
     * GET /v1/api/shops/{shopId}
     */
    @Operation(
            summary = "Get a shop by id",
            description = "Returns one shop with all stored fields.",
            parameters = @Parameter(name = "shopId", in = ParameterIn.PATH, required = true,
                    description = "Shop id (MongoDB ObjectId)", example = "665f1c2e9b1e8a3d4c7f0a12"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shop found",
                            content = @Content(schema = @Schema(implementation = ManagedShop.class))),
                    @ApiResponse(responseCode = "400", description = "No shop exists with this id",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = ERROR_EXAMPLE_SHOP_NOT_FOUND)))
            })
    @GetMapping("/{shopId}")
    public ResponseEntity<ManagedShop> getShopById(@PathVariable String shopId) {
        return ResponseEntity.ok(shopReadService.getById(shopId));
    }

    /**
     * Partially updates a shop (null fields in the body are ignored).
     * PUT /v1/api/shops/{shopId}
     */
    @Operation(
            summary = "Update a shop",
            description = "Partial update: only the fields sent in the body are changed; fields "
                    + "left out or sent as null keep their current values. Owner account "
                    + "details (email, password, phone) are not changed here.",
            parameters = @Parameter(name = "shopId", in = ParameterIn.PATH, required = true,
                    description = "Shop id (MongoDB ObjectId)", example = "665f1c2e9b1e8a3d4c7f0a12"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Fields to change",
                    content = @Content(schema = @Schema(implementation = UpdateShopRequest.class),
                            examples = @ExampleObject(
                                    value = "{\"shopName\": \"Saritha Fashions - T Nagar\", \"shopContactNumber\": \"9876543210\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shop updated",
                            content = @Content(schema = @Schema(implementation = ManagedShop.class))),
                    @ApiResponse(responseCode = "400", description = "No shop exists with this id",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = ERROR_EXAMPLE_SHOP_NOT_FOUND)))
            })
    @PutMapping("/{shopId}")
    public ResponseEntity<ManagedShop> updateShop(@PathVariable String shopId,
                                           @RequestBody UpdateShopRequest shopUpdate) {
        ManagedShop updatedShop = shopWriteService.update(shopId, shopUpdate);
        if (updatedShop == null) {
            throw new APIException("No Shop Found");
        }
        return ResponseEntity.ok(updatedShop);
    }

    /**
     * Updates the shop owner's account details.
     * PUT /v1/api/shops/{shopId}/owner
     */
    @Operation(
            summary = "Update the shop owner's details",
            description = "Platform OWNER only. Changes the shop owner's name, phone, email, password and/or salary "
                    + "(fields left out are unchanged; send at least one). Replaces "
                    + "PUT /v1/api/owner/shop/update-owner-details.\n\n"
                    + "- The previous details are backed up in user_backup_history (password redacted), with "
                    + "the optional `reason`.\n"
                    + "- A new name is also saved as the shop's ownerName; a new phone as the shop's contact number.\n"
                    + "- A new email or password **signs the owner out** on every device.\n"
                    + "- `ownerUserId` is optional: without it the shop's SHOP_OWNER is used.",
            parameters = @Parameter(name = "shopId", in = ParameterIn.PATH, required = true,
                    description = "Shop id", example = "665f1c2e9b1e8a3d4c7f0a12"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateShopOwnerRequest.class),
                            examples = @ExampleObject(value = "{\"phone\": \"9876500000\", \"salary\": 27000, "
                                    + "\"reason\": \"Owner changed phone number\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Updated (or nothing to change)",
                            content = @Content(schema = @Schema(implementation = ShopOwnerUpdateResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Invalid fields, shop not found, no / several shop owners, email in use",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Field errors", value =
                                            "{\"phone\": \"phone must be a 10-digit number\", \"anyFieldProvided\": "
                                                    + "\"Provide at least one of name, phone, email, password, salary\"}"),
                                    @ExampleObject(name = "Email in use", value =
                                            "{\"message\": \"Email is already in use: owner.new@example.com\", \"status\": false}")
                            })),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "Not the platform OWNER")
            })
    @PutMapping("/{shopId}/owner")
    public ResponseEntity<ShopOwnerUpdateResponse> updateShopOwner(@PathVariable String shopId,
                                                                   @Valid @RequestBody UpdateShopOwnerRequest request) {
        return ResponseEntity.ok(shopWriteService.updateShopOwner(shopId, request));
    }

    /**
     * Adds petty cash to a shop.
     * POST /v1/api/shops/{shopId}/petty-cash
     */
    @Operation(
            summary = "Add petty cash to a shop",
            description = "Adds the given amount to the shop's petty cash balance and records a "
                    + "top-up entry (amount, time, and the user who added it) in the shop's petty "
                    + "cash history. The balance change and the history entry are saved together "
                    + "in one atomic update.\n\n"
                    + "Allowed for the platform OWNER, or the SHOP_OWNER of this shop. The user who added "
                    + "the cash is recorded.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true,
                            description = "Shop id (MongoDB ObjectId)", example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Amount to add",
                    content = @Content(schema = @Schema(implementation = AddPettyCashRequest.class),
                            examples = @ExampleObject(value = "{\"amount\": 5000}"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Petty cash added",
                            content = @Content(schema = @Schema(implementation = PettyCashTopUpResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Invalid amount (missing, zero, negative or too large), "
                                    + "or no shop exists with this id",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Invalid amount",
                                            value = "{\"amount\": \"amount must be greater than 0\"}"),
                                    @ExampleObject(name = "Shop not found",
                                            value = "{\"message\": \"Shop Not Found\", \"status\": false}")
                            })),
                    @ApiResponse(responseCode = "403",
                            description = "No token, or the user is neither a platform OWNER nor assigned to this shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(
                                            value = "{\"message\": \"You are not authorized to access this resource\", \"status\": false}")))
            })
    @PostMapping("/{shopId}/petty-cash")
    public ResponseEntity<PettyCashTopUpResponse> addPettyCash(@PathVariable String shopId,
                                                               @Valid @RequestBody AddPettyCashRequest request) {
        PettyCashTopUpResponse response = shopWriteService.addPettyCash(shopId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Shop ids and names (for dropdowns).
     * GET /v1/api/shops/names
     */
    @Operation(
            summary = "Shop names",
            description = "Every shop's id and name, sorted by name. Only those two fields are read. "
                    + "Replaces GET /v1/api/shops/shop-name.",
            responses = @ApiResponse(responseCode = "200", description = "Shop ids and names",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ShopNameResponse.class)))))
    @GetMapping("/names")
    public ResponseEntity<List<ShopNameResponse>> getShopNames() {
        return ResponseEntity.ok(shopReadService.getShopNames());
    }

    /**
     * A shop's petty cash top-up history.
     * GET /v1/api/shops/{shopId}/petty-cash
     */
    @Operation(
            summary = "Petty cash history",
            description = "The shop's petty cash top-ups, newest first, with who added each one. Optional filters: "
                    + "`from` / `to` (dates, inclusive, IST) and an exact `amount`. Platform OWNER: any shop; "
                    + "SHOP_OWNER: their own shop. Replaces /v1/api/owner/petty-cash/{shopId}/all-pettyCash and "
                    + "/search-by-amount/{term}. The current balance is in GET /summaries.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.PATH, required = true, example = "665f1c2e9b1e8a3d4c7f0a12"),
                    @Parameter(name = "from", in = ParameterIn.QUERY, description = "First day (yyyy-MM-dd)", example = "2026-11-01"),
                    @Parameter(name = "to", in = ParameterIn.QUERY, description = "Last day (yyyy-MM-dd)", example = "2026-11-30"),
                    @Parameter(name = "amount", in = ParameterIn.QUERY, description = "Exact amount", example = "5000"),
                    @Parameter(name = "pageNumber", in = ParameterIn.QUERY, example = "0"),
                    @Parameter(name = "pageSize", in = ParameterIn.QUERY, description = "1-100", example = "20")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "A page of top-ups (content = PettyCashHistoryEntry[])"),
                    @ApiResponse(responseCode = "400", description = "Shop not found, bad dates or paging",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Shop Not Found\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not the platform owner or this shop's owner",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @GetMapping("/{shopId}/petty-cash")
    public ResponseEntity<PaginatedResponse<PettyCashHistoryResponse>> getPettyCashHistory(
            @PathVariable String shopId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "amount", required = false) Double amount,
            @io.swagger.v3.oas.annotations.Parameter(description = "newest (default), oldest, highest or lowest (amount)")
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(shopReadService.getPettyCashHistory(shopId, from, to, amount, sort, pageNumber, pageSize));
    }
}
