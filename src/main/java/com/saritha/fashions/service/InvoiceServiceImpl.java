package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
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
    public Receipt generateInvoice(Receipt receipt) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();
            receipt.setInvoiceDate(istDate);
            // whatsAppService.sendInvoice(receipt); // Send WhatsApp message


            return invoiceRepository.save(receipt);

        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Transactional
    public Receipt generateInvoiceByReceipt(Invoice receipt) {
        try {
            // Generate Receipt Date
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));

            Date istDate = new Date();

            Optional<Invoice> receipt1 = receiptRepository.findByReceiptNo(receipt.getReceiptNo());

            if (receipt1.isPresent()) {

                Optional<Receipt> invoiceFromDb = invoiceRepository.findByReceipt(receipt1.get());
                if (invoiceFromDb.isEmpty()) {
                    // Map Invoice to Receipt
                    Receipt invoice = modelMapper.map(receipt1, Receipt.class);
                    invoice.setInvoiceDate(istDate);
                    invoice.setInvoiceId(generateUniqueInvoiceId());
                    invoice.setPaymentStatus("Paid");

                    Invoice receipt11Data = receipt1.get();
                    receipt11Data.setPaymentStatus("Paid");
                    receipt11Data.setOrderStatus("Delivered");
                    receipt11Data.setBalanceDue(0);
                    receipt11Data.setPaymentCompletedDate(istDate);
                    Invoice invoiceSaved = receiptRepository.save(receipt11Data);

                    invoice.setInvoiceNotes(invoiceSaved.getNotes()); // Map notes
                    invoice.setInvoice(invoiceSaved); // Maintain reference to Invoice
                    invoice.setCustomer(receipt11Data.getCustomer());

                    // Save Receipt
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
    public Optional<Receipt> getInvoice(String id) {
        return invoiceRepository.findById(id);
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Receipt> invoice = invoiceRepository.findById(id);
        if (invoice.isPresent()) {
            Receipt inv = invoice.get();
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
    public List<Receipt> getInvoicesByShop(String shopId) {
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
