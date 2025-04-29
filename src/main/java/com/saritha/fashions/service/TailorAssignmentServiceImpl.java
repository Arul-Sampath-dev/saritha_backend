package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.Interface.TailorAssignmentService;
import com.saritha.fashions.util.AuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TailorAssignmentServiceImpl implements TailorAssignmentService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Override
    public Invoice assignTailorToInvoice(String invoiceId, String tailorId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(tailorId);
            invoice.setAssignmentStatus("ASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public Invoice unassignTailorFromInvoice(String invoiceId) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findById(invoiceId);
        if (optionalInvoice.isPresent()) {
            Invoice invoice = optionalInvoice.get();
            invoice.setAssignedTailorId(null);
            invoice.setAssignmentStatus("UNASSIGNED");
            return invoiceRepository.save(invoice);
        } else {
            throw new RuntimeException("Invoice not found with ID: " + invoiceId);
        }
    }

    @Override
    public List<Invoice> getInvoicesForShop() {
        User user = authUtil.loggedInUser();
        return invoiceRepository.findByShopId(user.getAssignedShops().get(0).getId());
    }

    @Override
    public List<Invoice> getInvoicesAssignedToTailor(String tailorId) {
        return invoiceRepository.findByAssignedTailorId(tailorId);
    }

    @Override
    public List<User> fetchAllTailorFromParticularShop() {

        User loggedInUser = authUtil.loggedInUser();
        String shopId = loggedInUser.getAssignedShops().get(0).getId();

        if (shopId == null || shopId.isBlank()) {
            throw new IllegalArgumentException("Shop ID must not be null or blank.");
        }

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new NoSuchElementException("Shop not found with ID: " + shopId));

        List<User> employees = shop.getEmployees();

        if (employees == null || employees.isEmpty()) {
            throw new NoSuchElementException("No employees found for the shop with ID: " + shopId);
        }

//        List<User> userList = new ArrayList<>();
//
//        for (User user : employees) {
//            Set<Role> role = user.getRoles();
//            for (Role roles : role) {
//                if (roles.getRoleName().equals(AppRole.TAILOR.toString())) {
//                    System.out.println("Role" + roles.getRoleName());
//                    userList.add(user);
//                }
//            }
//        }

        List<User> userList = employees.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> role.getRoleName().equals(AppRole.TAILOR))
                )
                .collect(Collectors.toList());

        return userList;
    }

}

