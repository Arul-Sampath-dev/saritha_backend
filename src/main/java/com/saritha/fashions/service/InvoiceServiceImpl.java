package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class InvoiceServiceImpl implements InvoiceService {


    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

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
            finalInvoice.setInvoiceId(generateUniqueCustomerId("INV-"));
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

            return invoiceRepository.save(finalInvoice);

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public Receipt generateReceiptByInvoice(Invoice receipt) {
        try {
            // Generate Receipt Date
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();

            Optional<Invoice> receipt1 = invoiceRepository.findByInvoiceId(receipt.getInvoiceId());

            if (receipt1.isPresent()) {

                Optional<Receipt> invoiceFromDb = receiptRepository.findByInvoice(receipt1.get());
                if (invoiceFromDb.isEmpty()) {
                    // Map Receipt to Invoice
                    Receipt invoice = modelMapper.map(receipt1, Receipt.class);
                    invoice.setShopId(receipt.getShopId());
                    invoice.setReceiptDate(istDate);
                    invoice.setReceiptNo(generateUniqueReceiptId());
                    invoice.setPaymentStatus("Paid");
                    invoice.setTotalAmount(receipt.getTotalAmount());
                    invoice.setAdvancePaid(receipt.getAdvancePaid());
                    invoice.setExpectedDeliveryDate(receipt.getExpectedDeliveryDate());
                    invoice.setPaymentMethod(receipt.getPaymentMethod());
                    invoice.setReceiptNotes(receipt.getNotes());
                    invoice.setBalanceDue(0);
                    invoice.setOrderStatus("Delivered");

                    Invoice receipt11Data = receipt1.get();
                    receipt11Data.setPaymentStatus("Paid");
                    receipt11Data.setOrderStatus("Delivered");
                    receipt11Data.setBalanceDue(0);
                    receipt11Data.setPaymentCompletedDate(istDate);
                    Invoice invoiceSaved = invoiceRepository.save(receipt11Data);

                    invoice.setReceiptNotes(invoiceSaved.getNotes()); // Map notes
                    invoice.setInvoice(invoiceSaved); // Maintain reference to Invoice
                    invoice.setCustomer(invoiceSaved.getCustomer());

                    // Save Receipt
                    return receiptRepository.save(invoice);
                }else {
                    return invoiceFromDb.get();
                }

            }else {
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

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
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
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
