package com.saritha.fashions.service.ShopOwnerServices;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.shop.ShopUpdateDTO;
import com.saritha.fashions.repository.*;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.service.Interface.ShopOwner.InvoiceAPIService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.service.InvoiceSequenceService;
import com.saritha.fashions.service.ReceiptSequenceService;
import com.saritha.fashions.service.WhatsAppService;
import com.saritha.fashions.util.AuthUtil;
import com.saritha.fashions.util.GcsImageUploader;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InvoiceAPIServiceImpl implements InvoiceAPIService {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceAPIServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final GcsImageUploader gcsImageUploader;
    private final InvoiceSequenceService invoiceSequenceService;
    private final ReceiptSequenceService receiptSequenceService;
    private final WhatsAppService whatsAppService;
    private final ShopService shopService;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ReceiptService receiptService;

    public InvoiceAPIServiceImpl(InvoiceRepository invoiceRepository,
                                 GcsImageUploader gcsImageUploader,
                                 CustomerRepository customerRepository,
                                 ReceiptRepository receiptRepository,
                                 ShopRepository shopRepository,
                                 InvoiceSequenceService invoiceSequenceService,
                                 ReceiptSequenceService receiptSequenceService,
                                 WhatsAppService whatsAppService,
                                 ShopService shopService,
                                 ModelMapper modelMapper,
                                 AuthUtil authUtil, UserRepository userRepository, @Lazy ReceiptService receiptService) {
        this.invoiceRepository = invoiceRepository;
        this.gcsImageUploader = gcsImageUploader;
        this.customerRepository = customerRepository;
        this.receiptRepository = receiptRepository;
        this.shopRepository = shopRepository;
        this.invoiceSequenceService = invoiceSequenceService;
        this.receiptSequenceService = receiptSequenceService;
        this.whatsAppService = whatsAppService;
        this.shopService = shopService;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public Invoice deleteInvoiceByInvoiceId(String shopId, String invoiceId) {
//        Todo -> Implement Invoice deletion from other collections as well where it was stored for reference
        Optional<Shop> shop = shopRepository.findById(shopId);
        if (shop.isEmpty()) {
            throw new APIException("No Shop Found");
        }
        List<Invoice> allInvoice = shop.get().getAllInvoice();
        for (Invoice invoice : allInvoice) {
            if (invoice.getInvoiceId().equals(invoiceId)) {
                allInvoice.remove(invoice);
                break;
            }
        }
        shop.get().setAllInvoice(allInvoice);
        shopRepository.save(shop.get());

        Invoice invoice = invoiceRepository.deleteByInvoiceId(invoiceId);
        Optional<Customer> customer = customerRepository.findById(invoice.getCustomer().getCustomerId());
        if (customer.isEmpty()) {
            throw new APIException("No User found");
        }

        List<Invoice> allInvoicesFromCustomers = customer.get().getAllInvoices();
        for (Invoice invoiceFromCustomer : allInvoicesFromCustomers){
            if (invoiceFromCustomer.getInvoiceId().equals(invoiceId)) {
                allInvoicesFromCustomers.remove(invoiceFromCustomer);
                break;
            }
        }

        customer.get().setAllInvoices(allInvoicesFromCustomers);
        customerRepository.save(customer.get());
        return invoice;
    }
}
