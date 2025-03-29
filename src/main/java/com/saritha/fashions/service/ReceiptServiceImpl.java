package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class ReceiptServiceImpl implements ReceiptService {


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

    public ReceiptServiceImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    @Override
    @Transactional
    public Receipt generateReceipt(Receipt receipt) {
        try {
            // whatsAppService.sendInvoice(invoice); // Send WhatsApp message

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();
            String formattedDate = sdf.format(istDate);

            receipt.setOrderDate(istDate);

            if (receipt.getCustomer() != null) {
                Customer customer = receipt.getCustomer();
                if (customer.getId() == null) { // Only save if it's a new customer
                    Customer customerExist = customerRepository.findByPhone(customer.getPhone());
                    if (customerExist == null) {

                        customer.setCustomerId(generateUniqueCustomerId());
                        customer = customerRepository.save(customer);
                        receipt.setCustomer(customer);

                    }else {
                        receipt.setCustomer(customerExist);
                    }
                }

            }

            // Convert Invoice to Receipt using ModelMapper
            Receipt finalReceipt = modelMapper.map(receipt, Receipt.class);

            // Set additional fields for Receipt
            finalReceipt.setAdvancePaid(receipt.getAdvancePaid());
            finalReceipt.setBalanceDue(receipt.getTotalAmount() - receipt.getAdvancePaid());
            finalReceipt.setOrderStatus("Pending");
            finalReceipt.setExpectedDeliveryDate(receipt.getExpectedDeliveryDate());
            finalReceipt.setPaymentMethod(receipt.getPaymentMethod());
            if (receipt.getTotalAmount() - receipt.getAdvancePaid() > 0) {
                finalReceipt.setPaymentStatus("Partial");
            } else if(receipt.getAdvancePaid() == 0) {
                finalReceipt.setPaymentStatus("Dues");
            }
            else {
                finalReceipt.setPaymentStatus("Paid");
            }

            if (receipt.getNotes() == null) {
                finalReceipt.setNotes("Thank you for your order");
            }


            return receiptRepository.save(finalReceipt);

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Optional<Receipt> getReceipt(String id) {
        return receiptRepository.findById(id);
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Receipt> invoice = receiptRepository.findById(id);
        if (invoice.isPresent()) {
            Receipt inv = invoice.get();
            inv.setPaymentStatus(status);
            try{
                receiptRepository.save(inv);
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
    public List<Receipt> getReceiptByShop(String shopId) {
        return receiptRepository.findByShopId(shopId);
    }

//    @Override
//    public Page<Receipt> getAllReceiptFromAllShop(int page, int size) {
//        Pageable pageable = PageRequest.of(page, size);
//        return receiptRepository.findAll(pageable);
//    }

    @Override
    public Page<Receipt> getAllReceiptFromAllShop(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "orderDate"));
        return receiptRepository.findAll(pageable);
    }

    public String generateUniqueCustomerId() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String customerId;
        do {
            customerId = "CUST-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (customerRepository.existsByCustomerId(customerId));
        return customerId;
    }



}
