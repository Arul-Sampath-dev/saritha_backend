package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.User;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.util.AuthUtil;
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

            // Convert Receipt to Invoice using ModelMapper
            Invoice finalInvoice = modelMapper.map(invoice, Invoice.class);
            finalInvoice.setShopId(user.getAssignedShops().get(0).getId());
//            finalInvoice.setInvoiceId(generateUniqueCustomerId("INV-"));

            String invoiceId = invoiceSequenceService.generateInvoiceId(invoice.getTransactionId());
            finalInvoice.setInvoiceId(invoiceId);

            finalInvoice.setInvoiceDate(istDate);

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
            Customer customerUpdated = invoice.getCustomer();
            List<Invoice> allInvoice = customerUpdated.getAllInvoices();
            allInvoice.add(savedInvoice);
            customerUpdated.setAllInvoices(allInvoice);

            customerRepository.save(customerUpdated);
            return savedInvoice;

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public Receipt generateReceiptByInvoice(Invoice invoice) {
        // Format IST Date
        Date istDate = new Date();
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
        receipt.setReceiptDate(istDate);
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

        // Update Invoice
        invoiceData.setPaymentStatus("Paid");
        invoiceData.setOrderStatus("Delivered");
        invoiceData.setBalanceDue(0);
        invoiceData.setPaymentCompletedDate(istDate);
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


//    @Transactional
//    public Receipt generateReceiptByInvoice(Invoice invoice) {
//        try {
//            // Generate Receipt Date
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
//            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//
//            Date istDate = new Date();
//
////            Optional<Invoice> invoiceFromRepository = invoiceRepository.findByInvoiceId(invoice.getInvoiceId());
//            System.out.println("Trying to find invoice with ID: " + invoice.getId());
//            Optional<Invoice> invoiceFromRepository = invoiceRepository.findById(invoice.getId());
//
//            if (invoiceFromRepository.isPresent()) {
//
//                Optional<Receipt> receiptFromDb = receiptRepository.findById(invoiceFromRepository.get().getReceiptId());
//
//                if (invoiceFromRepository.get().getReceiptId() == null) {
//
//
//                    if (receiptFromDb.isEmpty()) {
//                        // Map Receipt to Invoice
//                        Receipt receipt = modelMapper.map(invoiceFromRepository, Receipt.class);
//                        receipt.setShopId(invoice.getShopId());
//                        receipt.setReceiptDate(istDate);
////                    invoice.setReceiptNo(generateUniqueReceiptId());
//
//                        String receiptId = receiptSequenceService.generateReceiptId(invoice.getTransactionId());
//                        receipt.setReceiptNo(receiptId);
//
//                        receipt.setPaymentStatus("Paid");
//                        receipt.setTotalAmount(invoice.getTotalAmount());
//                        receipt.setAdvancePaid(invoice.getAdvancePaid());
//                        receipt.setExpectedDeliveryDate(invoice.getExpectedDeliveryDate());
//                        receipt.setPaymentMethod(invoice.getPaymentMethod());
//                        receipt.setReceiptNotes(invoice.getNotes());
//                        receipt.setBalanceDue(0);
//                        receipt.setOrderStatus("Delivered");
//
//                        Invoice invoiceData = invoiceFromRepository.get();
//                        invoiceData.setPaymentStatus("Paid");
//                        invoiceData.setOrderStatus("Delivered");
//                        invoiceData.setBalanceDue(0);
//                        invoiceData.setPaymentCompletedDate(istDate);
//                        Invoice invoiceSaved = invoiceRepository.save(invoiceData);
//
//                        receipt.setReceiptNotes(invoiceSaved.getNotes());
//                        receipt.setInvoiceId(invoiceSaved.getId());
//                        receipt.setCustomer(invoiceSaved.getCustomer());
//
//                        // Save Receipt
//                        Receipt receiptSaved = receiptRepository.save(receipt);
//                        invoiceSaved.setReceiptId(receiptSaved.getId());
//                        invoiceRepository.save(invoiceSaved);
//
//                        Customer customerFromDb = receiptSaved.getCustomer();
//                        List<Receipt> allReceipt = customerFromDb.getAllReceipts();
//                        allReceipt.add(receiptSaved);
//                        customerFromDb.setAllReceipts(allReceipt);
//
//                        customerRepository.save(customerFromDb);
//                        return receiptSaved;
//                    }else {
//                        return receiptFromDb.get();
//                    }
//                }
//
//            }else {
//                throw new APIException("No Invoice Found");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

//    @Transactional
//    public Receipt generateInvoiceByReceipt(Invoice receipt) {
//        try {
//            // Generate Receipt Date
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
//            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//
//            Date istDate = new Date();
//
//            Optional<Invoice> receipt1 = receiptRepository.findByReceiptNo(receipt.getReceiptNo());
//
//            if (receipt1.isPresent()) {
//
//                Optional<Receipt> invoiceFromDb = invoiceRepository.findByReceipt(receipt1.get());
//                if (invoiceFromDb.isEmpty()) {
//                    // Map Invoice to Receipt
//                    Receipt invoice = modelMapper.map(receipt1, Receipt.class);
//                    invoice.setInvoiceDate(istDate);
//                    invoice.setInvoiceId(generateUniqueInvoiceId());
//                    invoice.setPaymentStatus("Paid");
//
//                    Invoice receipt11Data = receipt1.get();
//                    receipt11Data.setPaymentStatus("Paid");
//                    receipt11Data.setOrderStatus("Delivered");
//                    receipt11Data.setBalanceDue(0);
//                    receipt11Data.setPaymentCompletedDate(istDate);
//                    Invoice invoiceSaved = receiptRepository.save(receipt11Data);
//
//                    invoice.setInvoiceNotes(invoiceSaved.getNotes()); // Map notes
//                    invoice.setInvoice(invoiceSaved); // Maintain reference to Invoice
//                    invoice.setCustomer(receipt11Data.getCustomer());
//
//                    // Save Receipt
//                    return invoiceRepository.save(invoice);
//                }else {
//                    return invoiceFromDb.get();
//                }
//
//            }else {
//                return null;
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

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
