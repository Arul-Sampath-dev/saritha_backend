package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.*;
import com.saritha.fashions.payload.InvoiceResponse;
import com.saritha.fashions.payload.ReceiptResponse;
import com.saritha.fashions.payload.ShopUpdateDTO;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.DateTimeUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class InvoiceServiceImpl implements InvoiceService {


    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private InvoiceSequenceService invoiceSequenceService;

    @Autowired
    private ReceiptSequenceService receiptSequenceService;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private final ModelMapper modelMapper;

    @Autowired
    private AuthUtil authUtil;

    public InvoiceServiceImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    @Override
    @Transactional
    public Invoice generateInvoice(Invoice invoice) {

        User user = authUtil.loggedInUser();

        try {

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();
            String formattedDate = sdf.format(istDate);

            if (invoice.getCustomer() != null) {
                Customer customer = invoice.getCustomer();
                if (customer.getId() == null) { // Only save if it's a new customer
                    Customer customerExist = customerRepository.findByPhone(customer.getPhone());
                    if (customerExist == null) {

                        customer.setCustomerId(generateUniqueCustomerId("CUS-"));
                        customer = customerRepository.save(customer);
                        invoice.setCustomer(customer);

                    }else {
                        invoice.setCustomer(customerExist);
                    }
                }
            }

            Invoice finalInvoice = modelMapper.map(invoice, Invoice.class);
            Shop shop = user.getAssignedShops().get(0);
            finalInvoice.setShopId(shop.getId());
//            finalInvoice.setInvoiceId(generateUniqueCustomerId("INV-"));

            String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
            finalInvoice.setInvoiceId(invoiceId);

            finalInvoice.setInvoiceDate(DateTimeUtils.getNowInIST());

            // Set additional fields for Invoice
            finalInvoice.setAdvancePaid(invoice.getAdvancePaid());
            finalInvoice.setBalanceDue(invoice.getTotalAmount() - invoice.getAdvancePaid());
            finalInvoice.setOrderStatus("Pending");
            finalInvoice.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
            finalInvoice.setPaymentMethod(invoice.getPaymentMethod());
            if (invoice.getTotalAmount() - invoice.getAdvancePaid() > 0) {
                finalInvoice.setPaymentStatus("Partial");
            } else if(invoice.getAdvancePaid() == 0) {
                finalInvoice.setPaymentStatus("Dues");
            }
            else {
                finalInvoice.setPaymentStatus("Paid");
            }

            if (invoice.getNotes() == null) {
                finalInvoice.setNotes("Thank you for your order");
            }

            Invoice savedInvoice = invoiceRepository.save(finalInvoice);

            List<Invoice> allInvoiceFromShop = shop.getAllInvoice();
            allInvoiceFromShop.add(savedInvoice);
            shop.setAllInvoice(allInvoiceFromShop);
            shopService.updateShop(shop.getId(), modelMapper.map(shop, ShopUpdateDTO.class));

            Customer customerUpdated = invoice.getCustomer();
            List<Invoice> allInvoiceFromCustomer = customerUpdated.getAllInvoices();
            allInvoiceFromCustomer.add(savedInvoice);
            customerUpdated.setAllInvoices(allInvoiceFromCustomer);

            customerRepository.save(customerUpdated);
            return savedInvoice;

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public Receipt generateReceiptByInvoice(Invoice invoice) {

        Optional<Invoice> invoiceOpt = invoiceRepository.findById(invoice.getId());

        if (invoiceOpt.isEmpty()) {
            throw new APIException("No Invoice Found with ID: " + invoice.getId());
        }

        Invoice invoiceData = invoiceOpt.get();

        // If receipt already generated
        if (invoiceData.getReceiptId() != null) {
            return receiptRepository.findById(invoiceData.getReceiptId())
                    .orElseThrow(() -> new APIException("Receipt ID present in invoice but not found in DB"));
        }

        // Generate new Receipt
        Receipt receipt = new Receipt();
        receipt.setShopId(invoiceData.getShopId());
        receipt.setReceiptDate(DateTimeUtils.getNowInIST());
        receipt.setReceiptNo(receiptSequenceService.generateReceiptId(invoiceData.getTransactionId()));
        receipt.setPaymentStatus("Paid");
        receipt.setTotalAmount(invoiceData.getTotalAmount());
        receipt.setAdvancePaid(invoiceData.getAdvancePaid());
        receipt.setExpectedDeliveryDate(invoiceData.getExpectedDeliveryDate());
        receipt.setPaymentMethod(invoiceData.getPaymentMethod());
        receipt.setReceiptNotes(invoiceData.getNotes());
        receipt.setBalanceDue(0);
        receipt.setOrderStatus("Delivered");
        receipt.setInvoiceId(invoiceData.getId());
        receipt.setCustomer(invoiceData.getCustomer());
        receipt.setItems(invoiceData.getItems());

        // Save Receipt
        Receipt receiptSaved = receiptRepository.save(receipt);

        // Add receipt to the Shop
        Shop shopDataFromDb = shopService.getShopById(invoiceData.getShopId());
        List<Receipt> allReceiptFromShop = shopDataFromDb.getAllReceipts();
        allReceiptFromShop.add(receiptSaved);
        shopDataFromDb.setAllReceipts(allReceiptFromShop);
        Shop shop = shopService.updateShop(shopDataFromDb.getId(), modelMapper.map(shopDataFromDb, ShopUpdateDTO.class));
        System.out.println("Total Receipts under " + shop.getShopName() + "is : " + shop.getAllReceipts().size());
        // Update Invoice
        invoiceData.setPaymentStatus("Paid");
        invoiceData.setOrderStatus("Delivered");
        invoiceData.setBalanceDue(0);
        invoiceData.setPaymentCompletedDate(DateTimeUtils.getNowInIST());
        invoiceData.setReceiptId(receiptSaved.getId());
        invoiceRepository.save(invoiceData);

        // Update Customer with new receipt
        Customer customer = invoiceData.getCustomer();
        List<Receipt> allReceipts = customer.getAllReceipts() != null ? customer.getAllReceipts() : new ArrayList<>();
        allReceipts.add(receiptSaved);
        customer.setAllReceipts(allReceipts);
        customerRepository.save(customer);

        return receiptSaved;
    }

    @Override
    public Optional<Invoice> getInvoice(String id) {
        return invoiceRepository.findByInvoiceId(id);
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Invoice> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Invoice inv = invoice.get();
            inv.setPaymentStatus(status);
            try{
                invoiceRepository.save(inv);
                return true;
            }catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }else {
            return false;
        }
    }

    @Override
    public List<Invoice> getInvoicesByShop(String shopId) {
        return invoiceRepository.findByShopId(shopId);
    }

    @Override
    public Page<Invoice> getAllInvoiceFromParticularShop(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "invoiceDate"));
        return invoiceRepository.findByShopId(authUtil.loggedInUser().getAssignedShops().get(0).getId(), pageable);
    }


    @Override
    public InvoiceResponse getAllInvoiceFromAllShops(Integer page, Integer size, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortBy)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sortByAndOrder);
        Page<Invoice> invoicePage = invoiceRepository.findByShopId(user.getAssignedShops().get(0).getId(), pageable);
        List<Invoice> invoiceList = invoicePage.getContent();
        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoices found");
        }

        List<Invoice> invoices = invoiceList.stream()
                .map(invoice -> modelMapper.map(invoice, Invoice.class))
                .toList();
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoices);
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());
        return invoiceResponse;
    }

    public String generateUniqueReceiptId() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String invoiceId;
        do {
            invoiceId = "REC-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (invoiceRepository.existsByInvoiceId(invoiceId));
        return invoiceId;
    }

    public String generateUniqueCustomerId(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String customerId;
        do {
            customerId = text + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (customerRepository.existsByCustomerId(customerId));
        return customerId;
    }
}
