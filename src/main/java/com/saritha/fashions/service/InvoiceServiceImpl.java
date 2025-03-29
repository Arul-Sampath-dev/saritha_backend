package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
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

    public InvoiceServiceImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    @Override
    @Transactional
    public Invoice generateInvoice(Invoice invoice) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();
            invoice.setInvoiceDate(istDate);
            // whatsAppService.sendInvoice(invoice); // Send WhatsApp message


            return invoiceRepository.save(invoice);

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public Invoice generateInvoiceByReceipt(Receipt receipt) {
        try {
            // Generate Invoice Date
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();

            Optional<Receipt> receipt1 = receiptRepository.findByReceiptNo(receipt.getReceiptNo());

            if (receipt1.isPresent()) {

                Optional<Invoice> invoiceFromDb = invoiceRepository.findByReceipt(receipt1.get());
                if (invoiceFromDb.isEmpty()) {
                    // Map Receipt to Invoice
                    Invoice invoice = modelMapper.map(receipt1, Invoice.class);
                    invoice.setInvoiceDate(istDate);
                    invoice.setInvoiceId(generateUniqueInvoiceId());
                    invoice.setPaymentStatus("Paid");

                    Receipt receipt1Data = receipt1.get();
                    receipt1Data.setPaymentStatus("Paid");
                    receipt1Data.setOrderStatus("Delivered");
                    receipt1Data.setBalanceDue(0);
                    receipt1Data.setPaymentCompletedDate(istDate);
                    Receipt receiptSaved = receiptRepository.save(receipt1Data);

                    invoice.setInvoiceNotes(receiptSaved.getNotes()); // Map notes
                    invoice.setReceipt(receiptSaved); // Maintain reference to Receipt
                    invoice.setCustomer(receipt1Data.getCustomer());

                    // Save Invoice
                    return invoiceRepository.save(invoice);
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

    public String generateUniqueInvoiceId() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String invoiceId;
        do {
            invoiceId = "INV-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (invoiceRepository.existsByInvoiceId(invoiceId));
        return invoiceId;
    }
}
