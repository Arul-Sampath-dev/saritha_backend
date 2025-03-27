package com.saritha.fashions.service;

import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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

    public InvoiceServiceImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    @Override
    @Transactional
    public Receipt generateInvoice(Receipt receipt) {
        try {
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            String dateString = sdf.format(date);
            receipt.setOrderDate(dateString);
            // whatsAppService.sendInvoice(invoice); // Send WhatsApp message

            if (receipt.getCustomer() != null) {
                Customer customer = receipt.getCustomer();
                if (customer.getId() == null) { // Only save if it's a new customer
                    Customer customerExist = customerRepository.findByPhone(customer.getPhone());
                    if (customerExist == null) {
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
            finalReceipt.setAdvancePaid(0);  // Adjust as needed
            finalReceipt.setBalanceDue(receipt.getTotalAmount() - receipt.getAdvancePaid());  // Assuming full amount due
            finalReceipt.setOrderStatus("Pending");
            finalReceipt.setExpectedDeliveryDate(null);

            return receiptRepository.save(finalReceipt);

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Optional<Invoice> getInvoice(String id) {
        return invoiceRepository.findById(id);
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
}
