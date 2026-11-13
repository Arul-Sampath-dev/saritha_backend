package com.saritha.fashions.modules.employeemanagement.service;

import com.saritha.fashions.modules.employeemanagement.model.EmployeeRole;
import com.saritha.fashions.modules.employeemanagement.payload.EmployeeResponse;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepository;
import com.saritha.fashions.modules.employeemanagement.repository.EmployeeManagementRepositoryCustom.ShopInfo;
import org.springframework.stereotype.Service;

import com.saritha.fashions.modules.employeemanagement.model.ManagedEmployee;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Lists employees. Two queries per call (shop, then its users), each reading only the fields it
 * needs (the old version loaded the full shop document, with every expense DBRef, per employee).
 */
@Service
public class EmployeeManagementReadServiceImpl implements EmployeeManagementReadService {

    private final EmployeeAccess access;
    private final EmployeeManagementRepository repository;

    public EmployeeManagementReadServiceImpl(EmployeeAccess access, EmployeeManagementRepository repository) {
        this.access = access;
        this.repository = repository;
    }

    @Override
    public List<EmployeeResponse> listShopEmployees(String shopId, String search) {
        EmployeeActor actor = access.requireManager();
        ShopInfo shop = access.requireManagedShop(actor, shopId);
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        // Shops have few employees, so filtering after the (single) query is cheaper than a regex query.
        return repository.findEmployees(shop.employeeIds()).stream()
                .filter(employee -> term == null || matches(employee, term))
                .map(employee -> EmployeeAccess.toResponse(employee, shop.shopId(), shop.shopName()))
                .toList();
    }

    /** Replaces the legacy GET /v1/api/owner/employee/{shopId}/find-user (which matched the user id only). */
    private static boolean matches(ManagedEmployee employee, String term) {
        return Stream.of(employee.getName(), employee.getEmail(), employee.getPhone(), employee.getUserId())
                .filter(Objects::nonNull)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(term));
    }

    @Override
    public List<EmployeeResponse> listUnassignedStaff() {
        access.requireManager();
        List<String> staffRoles = EmployeeRole.STAFF_ROLES.stream().map(Enum::name).toList();
        return repository.findUnassignedStaff(staffRoles).stream()
                .map(employee -> EmployeeAccess.toResponse(employee, null, null))
                .toList();
    }
}
