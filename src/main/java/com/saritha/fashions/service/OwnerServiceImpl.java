package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OwnerServiceImpl implements OwnerService {

    private final ShopRepository shopRepository;
    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final ModelMapper modelMapper;
    private final ReceiptService receiptService;

    public OwnerServiceImpl(ShopRepository shopRepository,
                            ModelMapper modelMapper,
                            ReceiptRepository receiptRepository,
                            InvoiceRepository invoiceRepository,
                            ReceiptService receiptService)
    {
        this.shopRepository = shopRepository;
        this.modelMapper = modelMapper;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.receiptService = receiptService;
    }

    @Override
    public ReceiptResponse getAllReceiptByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId) {
        Optional<Shop> shopOptional = shopRepository.findById(shopId);

        if (shopOptional.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortOrder)
                ? Sort.by(sortOrder).ascending()
                : Sort.by(sortOrder).descending();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Receipt> receiptsPage = receiptRepository.findByShopId(shopId, pageable);
        List<Receipt> invoiceList = receiptsPage.getContent();

        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoices found");
        }

//        List<Invoice> invoices = invoiceList.stream()
//                .map(invoice -> modelMapper.map(invoice, Invoice.class))
//                .toList();
        ReceiptResponse receiptResponse = new ReceiptResponse();
        receiptResponse.setContent(invoiceList);
        receiptResponse.setPageNumber(receiptsPage.getNumber());
        receiptResponse.setPageSize(receiptsPage.getSize());
        receiptResponse.setTotalElements(receiptsPage.getTotalElements());
        receiptResponse.setTotalPages(receiptsPage.getTotalPages());
        receiptResponse.setLastPage(receiptsPage.isLast());
        return receiptResponse;
    }

    @Override
    public byte[] downloadReceipt(ReceiptDTO receiptDTO) throws IOException {
//        Optional<Invoice> optionalInvoice = invoiceRepository.findById(receiptDTO.getInvoiceId());
        Optional<Receipt> optionalReceipt = receiptRepository.findById(receiptDTO.getId());
        if (optionalReceipt.isPresent()) {
//            byte[] byteData = receiptService.generateReceiptDocument(optionalInvoice.get(), 2).getBody();
            byte[] byteData = receiptService.getReceiptPdfMethod(optionalReceipt.get()).getBody();
            return byteData;
        } else {
            throw new APIException("Invoice Not Found for the Receipt " + receiptDTO.getReceiptNo());
        }
    }

    @Override
    public ReceiptDTO getReceiptByReceiptId(String shopId, String receiptId) {
        Optional<Shop> optionalShop = shopRepository.findById(shopId);
        if (optionalShop.isPresent()) {
            List<Receipt> allReceipts = optionalShop.get().getAllReceipts();
            for (Receipt receipt : allReceipts) {
                if (receipt.getReceiptNo().equals(receiptId)) {
                    return modelMapper.map(receipt, ReceiptDTO.class);
                }
            }
            throw new APIException("Receipt Not found in " + optionalShop.get().getShopName() + " Shop");
        }else {
            throw new APIException("Shop Not Found, Please contact Developer");
        }
    }

//    ALl invoice related methods starts from here ========================================

    @Override
    public InvoiceResponse getAllInvoiceByShopId(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String shopId) {
        Optional<Shop> shopOptional = shopRepository.findById(shopId);

        if (shopOptional.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase(sortOrder)
                ? Sort.by(sortOrder).ascending()
                : Sort.by(sortOrder).descending();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Invoice> invoicePage = invoiceRepository.findByShopId(shopId, pageable);
        List<Invoice> invoiceList = invoicePage.getContent();

        if (invoiceList.isEmpty()) {
            throw new APIException("No Invoices found");
        }

//        List<Invoice> invoices = invoiceList.stream()
//                .map(invoice -> modelMapper.map(invoice, Invoice.class))
//                .toList();
        InvoiceResponse invoiceResponse = new InvoiceResponse();
        invoiceResponse.setContent(invoiceList);
        invoiceResponse.setPageNumber(invoicePage.getNumber());
        invoiceResponse.setPageSize(invoicePage.getSize());
        invoiceResponse.setTotalElements(invoicePage.getTotalElements());
        invoiceResponse.setTotalPages(invoicePage.getTotalPages());
        invoiceResponse.setLastPage(invoicePage.isLast());
        return invoiceResponse;
    }

    @Override
    public InvoiceDTO getInvoiceByInvoiceId(String shopId, String invoiceId) {
        Optional<Shop> optionalShop = shopRepository.findById(shopId);
        if (optionalShop.isPresent()) {
            List<Invoice> allInvoices = optionalShop.get().getAllInvoice();
            for (Invoice invoice : allInvoices) {
                if (invoice.getInvoiceId().equals(invoiceId)) {
                    return modelMapper.map(invoice, InvoiceDTO.class);
                }
            }
            throw new APIException("Invoice Not found in " + optionalShop.get().getShopName() + " Shop");
        }else {
            throw new APIException("Shop Not Found, Please contact Developer");
        }
    }

    @Override
    public byte[] downloadInvoice(InvoiceDTO invoiceDTO) {
        Optional<Invoice> optionalInvoice = invoiceRepository.findByInvoiceId(invoiceDTO.getInvoiceId());
        if (optionalInvoice.isPresent()) {
            byte[] byteData = receiptService.getInvoicePdfMethod(optionalInvoice.get()).getBody();
            return byteData;
        } else {
            throw new APIException("Invoice Not Found for the Receipt " + invoiceDTO.getReceiptId());
        }
    }
}
