package com.saritha.fashions.modules.production.service;

import com.saritha.fashions.modules.production.payload.AssigneeResponse;
import com.saritha.fashions.modules.production.payload.WorkItemResponse;
import com.saritha.fashions.payload.PaginatedResponse;

import java.util.List;

/**
 * Read side of item management.
 */
public interface ProductionReadService {

    /**
     * The logged-in user's work queue (depends on their role), oldest invoice first.
     *
     * @param statuses optional filter; must be statuses the user's queue can contain
     */
    PaginatedResponse<WorkItemResponse> getMyQueue(List<String> statuses, int pageNumber, int pageSize);

    /** One item of an invoice of the user's shop, with the actions the user may take. */
    WorkItemResponse getItem(String invoiceId, String itemUniqueId);

    /** Tailors of the user's shop (for the assign-tailor dropdown). */
    List<AssigneeResponse> getAssignableTailors();

    /** Shop assistants of the user's shop (for the assign-shop-assistant dropdown). */
    List<AssigneeResponse> getAssignableShopAssistants();
}
