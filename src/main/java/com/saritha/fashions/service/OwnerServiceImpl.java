package com.saritha.fashions.service;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.payload.invoice.InvoiceDTO;
import com.saritha.fashions.payload.invoice.InvoiceResponse;
import com.saritha.fashions.payload.owner.UserDtoForOwner;
import com.saritha.fashions.payload.receipt.ReceiptDTO;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.repository.ShopRepository;
import com.saritha.fashions.repository.UserRepository;
import com.saritha.fashions.service.Interface.OwnerService;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OwnerServiceImpl implements OwnerService {

    private final ShopRepository shopRepository;
    private final ReceiptRepository receiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final ModelMapper modelMapper;
    private final ReceiptService receiptService;
    private final UserRepository userRepository;

    public OwnerServiceImpl(ShopRepository shopRepository,
                            ModelMapper modelMapper,
                            ReceiptRepository receiptRepository,
                            InvoiceRepository invoiceRepository,
                            ReceiptService receiptService,
                            UserRepository userRepository)
    {
        this.shopRepository = shopRepository;
        this.modelMapper = modelMapper;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.receiptService = receiptService;
        this.userRepository = userRepository;
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

    @Override
    public PaginatedResponse<UserDtoForOwner> getAllEmployeeByShopId(
            Integer pageNumber,
            Integer pageSize,
            String sortBy,
            String sortOrder,
            String shopId
    ) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new APIException("Shop Not Found"));

        List<User> employees = shop.getEmployees();

        // Optional: Sort the list manually based on `sortBy` and `sortOrder`
        employees.sort((u1, u2) -> {
            Comparator<User> comparator;
            if (sortBy.equalsIgnoreCase("accountcreationdate")) {
                comparator = Comparator.comparing(User::getAccountCreationDate);
            } else {
                comparator = Comparator.comparing(User::getUserId);
            }
            return sortOrder.equalsIgnoreCase("desc") ? comparator.reversed().compare(u1, u2) : comparator.compare(u1, u2);
        });

        int totalElements = employees.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        if (fromIndex >= totalElements) {
            throw new APIException("Page number out of bounds");
        }

        List<UserDtoForOwner> pagedContent = employees.subList(fromIndex, toIndex).stream()
                .map(user -> modelMapper.map(user, UserDtoForOwner.class))
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                pagedContent,
                pageNumber,
                pageSize,
                (long) totalElements,
                totalPages,
                pageNumber == totalPages - 1
        );
    }

    @Override
    public List<UserDtoForOwner> getAllEmployeeListByShopId(String shopId) {
        Optional<Shop> shopOptional = shopRepository.findById(shopId);

        if (shopOptional.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        List<User> userList = shopOptional.get().getEmployees();
        List<UserDtoForOwner> userDtoForOwners = userList.stream().map(user -> modelMapper.map(user, UserDtoForOwner.class)).toList();
        System.out.println("UserDtos For Owner" + userDtoForOwners);
        return userDtoForOwners;


    }

    @Override
    public UserDtoForOwner getUserByUserId(String shopId, String userId) {
        Optional<Shop> shop = shopRepository.findById(shopId);
        if (shop.isEmpty()) {
            throw new APIException("Shop Not Found");
        }

        List<User> userList = shop.get().getEmployees();
        for (User user : userList) {
            if (user.getUserId().equals(userId)) {
                return modelMapper.map(user, UserDtoForOwner.class);
            }
        }

        throw new APIException("User Not found in " + shop.get().getShopName() + " Shop");
    }

}
