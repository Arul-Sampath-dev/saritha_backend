package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.Items;
import com.saritha.fashions.payload.ReceiptAndInvoicePDF;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.apache.poi.xwpf.usermodel.*;
import org.modelmapper.ModelMapper;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ReceiptServiceImpl implements ReceiptService {


    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private InvoiceServiceImpl invoiceService;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private WhatsAppService whatsAppService;

    @Autowired
    private final ModelMapper modelMapper;

    @Autowired
    private ApplicationContext applicationContext;

    public ReceiptServiceImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }


    @Override
    @Transactional
    public Invoice generateReceipt(Invoice invoice) {
       return null;
    }

    @Override
    public Optional<Receipt> getReceipt(String id) {
        return receiptRepository.findByReceiptNo(id);
    }

    @Override
    public boolean updatePaymentStatus(String id, String status) {
        Optional<Receipt> invoice = receiptRepository.findById(id);
        if (invoice.isPresent()) {
            Receipt receipt = invoice.get();
            receipt.setPaymentStatus(status);
            try{
                receiptRepository.save(receipt);
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


    @Override
    public Page<Receipt> getAllReceiptFromAllShop(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receiptDate"));
        return receiptRepository.findAll(pageable);

    }

    public String generateUniqueCustomerId(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String customerId;
        do {
            customerId = text + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (customerRepository.existsByCustomerId(customerId));
        return customerId;
    }


//    New code for recept generation

    @Override
    public ResponseEntity<byte[]> generateReceiptAndInvoice(Invoice invoice, int num) {
        if (num == 1) {
            return generateReceiptDocument(invoice, 1);
        }else {
            return generateReceiptDocument(invoice, 2);
        }
    }

    public ResponseEntity<byte[]> generateReceiptDocument(Invoice invoiceFromFrontend, int i) {
        System.out.println("Received Invoice" + invoiceFromFrontend);

        try {
            String fileName, tempFileName;

            if (i == 1) {
                Invoice invoice = invoiceService.generateInvoice(invoiceFromFrontend);

                fileName = "Receipt_SF.docx";
                tempFileName = "invoice";

                ClassPathResource resource = new ClassPathResource(fileName);
                try (InputStream is = resource.getInputStream();
                     XWPFDocument document = new XWPFDocument(is)) {

                    // Format dates
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                    String orderDateFormatted = sdf.format(new Date());
                    String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";

                    Map<String, String> receiptData = new HashMap<>();
                    receiptData.put("date", orderDateFormatted);
                    receiptData.put("Invoice_No", orderDateFormatted);
                    receiptData.put("InvoiceId", invoice.getInvoiceId());

                    receiptData.put("customer_name", invoice.getCustomer().getName());
                    receiptData.put("customer_phone", invoice.getCustomer().getPhone());
                    receiptData.put("shop_id", invoice.getShopId());
                    receiptData.put("order_date", String.valueOf(invoice.getOrderDate()));
                    receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);

                    receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
                    receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
                    receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));

                    receiptData.put("payment_method", invoice.getPaymentMethod());
                    receiptData.put("payment_status", invoice.getPaymentStatus());
                    receiptData.put("order_status", invoice.getOrderStatus());
                    receiptData.put("notes", invoice.getNotes() != null ? invoice.getNotes() : "Thank you for your order");

                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        replaceText(paragraph, receiptData);
                    }

                    for (XWPFTable table : document.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            for (XWPFTableCell cell : row.getTableCells()) {
                                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                    System.out.println("Processing text: " + paragraph.getText());
                                    replaceText(paragraph, receiptData);
                                }
                            }
                        }
                    }

                    System.out.println("Modified Document Content:");
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        System.out.println(paragraph.getText());
                    }

                    System.out.println("Till here its working");

                    ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate());

                    replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);

                    System.out.println("Till here its working 2");

                    // Save to temp file
                    File tempFile = File.createTempFile(tempFileName, ".docx");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        document.write(fos);
                    }

                    System.out.println("Till here its working 3");

                    byte[] fileContent = Files.readAllBytes(tempFile.toPath());

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", "Invoice.docx");

                    return ResponseEntity.ok().headers(headers).body(fileContent);
                }

            } else {
                Receipt invoice = invoiceService.generateReceiptByInvoice(invoiceFromFrontend);

                fileName = "Invoice_SF.docx";
                tempFileName = "receipt";

                ClassPathResource resource = new ClassPathResource(fileName);
                try (InputStream is = resource.getInputStream();
                     XWPFDocument document = new XWPFDocument(is)) {

                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                    String orderDateFormatted = sdf.format(new Date());
                    String expectedDeliveryDateFormatted = "N/A";
                    if (invoice != null) {
                        expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";
                    }

                    Map<String, String> receiptData = new HashMap<>();
                    receiptData.put("Date", orderDateFormatted);
                    receiptData.put("Receipt_No", invoice.getReceiptNo());
                    receiptData.put("Grand_Total", String.valueOf(invoice.getTotalAmount()));

                    receiptData.put("Customer_Name", invoice.getCustomer().getName());
                    receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
                    receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
                    receiptData.put("order_date", String.valueOf(invoice.getReceiptDate()));
                    receiptData.put("Due_Date", expectedDeliveryDateFormatted);

                    receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
                    receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
                    receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));

                    receiptData.put("Payment_Method", invoice.getPaymentMethod());
                    receiptData.put("Payment_Status", invoice.getPaymentStatus());
                    receiptData.put("order_status", invoice.getOrderStatus());
                    receiptData.put("notes", invoice.getReceiptNotes() != null ? invoice.getReceiptNotes() : "Thank you for your order");

                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        replaceText(paragraph, receiptData);
                    }

                    for (XWPFTable table : document.getTables()) {
                        for (XWPFTableRow row : table.getRows()) {
                            for (XWPFTableCell cell : row.getTableCells()) {
                                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                    System.out.println("Processing text: " + paragraph.getText());
                                    replaceText(paragraph, receiptData);
                                }
                            }
                        }
                    }

                    System.out.println("Modified Document Content:");
                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        System.out.println(paragraph.getText());
                    }

                    System.out.println("Till here its working");

                    ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate());

                    replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);

                    System.out.println("Till here its working 2");

                    File tempFile = File.createTempFile(tempFileName, ".docx");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        document.write(fos);
                    }

                    System.out.println("Till here its working 3");

                    byte[] fileContent = Files.readAllBytes(tempFile.toPath());

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    headers.setContentDispositionFormData("attachment", "Invoice.docx");

                    return ResponseEntity.ok().headers(headers).body(fileContent);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }


//    public ResponseEntity<byte[]> generateReceiptDocument(Invoice invoiceFromFrontend, int i) {
//        System.out.println("Received Invoice" + invoiceFromFrontend);
//
//        try {
//            // Load the template from resources folder
//            String fileName, tempFileName;
//            if (i == 1) {
////                ReceiptServiceImpl selfProxyReceipt = applicationContext.getBean(ReceiptServiceImpl.class);
////                Invoice invoice = selfProxyReceipt.generateReceipt(invoiceFromFrontend);
//
//                Invoice invoice = invoiceService.generateInvoice(invoiceFromFrontend);
//
//                fileName = "Receipt_SF.docx";
//                tempFileName = "invoice";
//
//                File templateFile = new ClassPathResource(fileName).getFile();
//                File tempFile = File.createTempFile(tempFileName, ".docx");
//
//                // Copy template to temp file
//                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//
//                // Open the Word document
//                FileInputStream fis = new FileInputStream(tempFile);
//                XWPFDocument document = new XWPFDocument(fis);
//                fis.close();
//
//                // Format dates
//                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//                String orderDateFormatted = sdf.format(new Date());
//                String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";
//
//                // Map data for placeholders
//                Map<String, String> receiptData = new HashMap<>();
//                receiptData.put("date", orderDateFormatted);
//                receiptData.put("Invoice_No", orderDateFormatted);
//                receiptData.put("InvoiceId", invoice.getInvoiceId());
//
//                receiptData.put("customer_name", invoice.getCustomer().getName());
//                receiptData.put("customer_phone", invoice.getCustomer().getPhone());
//                receiptData.put("shop_id", invoice.getShopId());
//                receiptData.put("order_date", String.valueOf(invoice.getOrderDate()));
//                receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);
//
//                receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//                receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
//                receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));
//
//                receiptData.put("payment_method", invoice.getPaymentMethod());
//                receiptData.put("payment_status", invoice.getPaymentStatus());
//                receiptData.put("order_status", invoice.getOrderStatus());
//                receiptData.put("notes", invoice.getNotes() != null ? invoice.getNotes() : "Thank you for your order");
//
//                // Replace placeholders in document
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    replaceText(paragraph, receiptData);
//                }
//
//                for (XWPFTable table : document.getTables()) {
//                    for (XWPFTableRow row : table.getRows()) {
//                        for (XWPFTableCell cell : row.getTableCells()) {
//                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                                System.out.println("Processing text: " + paragraph.getText());
//                                replaceText(paragraph, receiptData);
//                            }
//                        }
//                    }
//                }
//
//                System.out.println("Modified Document Content:");
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    System.out.println(paragraph.getText());
//                }
//
//
//                System.out.println("Till here its working");
//
//                // Handle items list replacement
//                replaceItemList(document, invoice.getItems(), invoice);
//
//                System.out.println("Till here its working 2");
//
//                // Save document
//                FileOutputStream fos = new FileOutputStream(tempFile);
//                document.write(fos);
//                fos.close();
//                document.close();
//
//                System.out.println("Till here its working 3");
//
//
//                // Return file as response
//                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//                return ResponseEntity.ok().headers(headers).body(fileContent);
//            }else {
//                Receipt invoice = invoiceService.generateReceiptByInvoice(invoiceFromFrontend);
//                fileName = "Invoice_SF.docx";
//                tempFileName = "receipt";
//
//                File templateFile = new ClassPathResource(fileName).getFile();
//                File tempFile = File.createTempFile(tempFileName, ".docx");
//
//                // Copy template to temp file
//                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//
//                // Open the Word document
//                FileInputStream fis = new FileInputStream(tempFile);
//                XWPFDocument document = new XWPFDocument(fis);
//                fis.close();
//
//                // Format dates
//                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//                String orderDateFormatted = sdf.format(new Date());
//                String expectedDeliveryDateFormatted = invoice.getInvoice().getExpectedDeliveryDate() != null ? invoice.getInvoice().getExpectedDeliveryDate() : "N/A";
//
//                // Map data for placeholders
//                Map<String, String> receiptData = new HashMap<>();
//                receiptData.put("Date", orderDateFormatted);
//                receiptData.put("Receipt_No", invoice.getReceiptNo());
//                receiptData.put("Grand_Total", String.valueOf(invoice.getInvoice().getTotalAmount()));
//
//                receiptData.put("Customer_Name", invoice.getCustomer().getName());
//                receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
//                receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
//                receiptData.put("order_date", String.valueOf(invoice.getInvoice().getInvoiceDate()));
//                receiptData.put("Due_Date", expectedDeliveryDateFormatted);
//
//                receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//                receiptData.put("advance_paid", String.valueOf(invoice.getInvoice().getAdvancePaid()));
//                receiptData.put("balance_due", String.valueOf(invoice.getInvoice().getBalanceDue()));
//
//                receiptData.put("Payment_Method", invoice.getInvoice().getPaymentMethod());
//                receiptData.put("Payment_Status", invoice.getInvoice().getPaymentStatus());
//                receiptData.put("order_status", invoice.getInvoice().getOrderStatus());
//                receiptData.put("notes", invoice.getInvoice().getNotes() != null ? invoice.getInvoice().getNotes() : "Thank you for your order");
//
//                // Replace placeholders in document
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    replaceText(paragraph, receiptData);
//                }
//
//                for (XWPFTable table : document.getTables()) {
//                    for (XWPFTableRow row : table.getRows()) {
//                        for (XWPFTableCell cell : row.getTableCells()) {
//                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                                System.out.println("Processing text: " + paragraph.getText());
//                                replaceText(paragraph, receiptData);
//                            }
//                        }
//                    }
//                }
//
//                System.out.println("Modified Document Content:");
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    System.out.println(paragraph.getText());
//                }
//
//
//                System.out.println("Till here its working");
//
//                // Handle items list replacement
//                replaceItemList(document, invoice.getInvoice().getItems(), invoice.getInvoice());
//
//                System.out.println("Till here its working 2");
//
//                // Save document
//                FileOutputStream fos = new FileOutputStream(tempFile);
//                document.write(fos);
//                fos.close();
//                document.close();
//
//                System.out.println("Till here its working 3");
//
//
//                // Return file as response
//                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//                return ResponseEntity.ok().headers(headers).body(fileContent);
//            }
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(null);
//        }
//    }

    // before receipt and invoice swapping code

//    public ResponseEntity<byte[]> generateReceiptDocument(Invoice receipt, int i) {
//        System.out.println("Received Invoice" + receipt);
//
//        try {
//            // Load the template from resources folder
//            String fileName, tempFileName;
//            if (i == 1) {
//                ReceiptServiceImpl selfProxyReceipt = applicationContext.getBean(ReceiptServiceImpl.class);
//                Invoice invoice = selfProxyReceipt.generateReceipt(receipt);
//
//                fileName = "Receipt_SF.docx";
//                tempFileName = "receipt";
//
//                File templateFile = new ClassPathResource(fileName).getFile();
//                File tempFile = File.createTempFile(tempFileName, ".docx");
//
//                // Copy template to temp file
//                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//
//                // Open the Word document
//                FileInputStream fis = new FileInputStream(tempFile);
//                XWPFDocument document = new XWPFDocument(fis);
//                fis.close();
//
//                // Format dates
//                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//                String orderDateFormatted = sdf.format(new Date());
//                String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";
//
//                // Map data for placeholders
//                Map<String, String> receiptData = new HashMap<>();
//                receiptData.put("date", orderDateFormatted);
//                receiptData.put("Invoice_No", orderDateFormatted);
//                receiptData.put("ReceiptId", invoice.getReceiptNo());
//
//                receiptData.put("customer_name", invoice.getCustomer().getName());
//                receiptData.put("customer_phone", invoice.getCustomer().getPhone());
//                receiptData.put("shop_id", invoice.getShopId());
//                receiptData.put("order_date", String.valueOf(invoice.getOrderDate()));
//                receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);
//
//                receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//                receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
//                receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));
//
//                receiptData.put("payment_method", invoice.getPaymentMethod());
//                receiptData.put("payment_status", invoice.getPaymentStatus());
//                receiptData.put("order_status", invoice.getOrderStatus());
//                receiptData.put("notes", invoice.getNotes() != null ? invoice.getNotes() : "Thank you for your order");
//
//                // Replace placeholders in document
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    replaceText(paragraph, receiptData);
//                }
//
//                for (XWPFTable table : document.getTables()) {
//                    for (XWPFTableRow row : table.getRows()) {
//                        for (XWPFTableCell cell : row.getTableCells()) {
//                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                                System.out.println("Processing text: " + paragraph.getText());
//                                replaceText(paragraph, receiptData);
//                            }
//                        }
//                    }
//                }
//
//                System.out.println("Modified Document Content:");
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    System.out.println(paragraph.getText());
//                }
//
//
//                System.out.println("Till here its working");
//
//                // Handle items list replacement
//                replaceItemList(document, invoice.getItems(), invoice);
//
//                System.out.println("Till here its working 2");
//
//                // Save document
//                FileOutputStream fos = new FileOutputStream(tempFile);
//                document.write(fos);
//                fos.close();
//                document.close();
//
//                System.out.println("Till here its working 3");
//
//
//                // Return file as response
//                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//                return ResponseEntity.ok().headers(headers).body(fileContent);
//            }else {
//                Receipt invoice = invoiceService.generateInvoiceByReceipt(receipt);
//                fileName = "Invoice_SF.docx";
//                tempFileName = "invoice";
//
//                File templateFile = new ClassPathResource(fileName).getFile();
//                File tempFile = File.createTempFile(tempFileName, ".docx");
//
//                // Copy template to temp file
//                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//
//                // Open the Word document
//                FileInputStream fis = new FileInputStream(tempFile);
//                XWPFDocument document = new XWPFDocument(fis);
//                fis.close();
//
//                // Format dates
//                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//                String orderDateFormatted = sdf.format(new Date());
//                String expectedDeliveryDateFormatted = invoice.getInvoice().getExpectedDeliveryDate() != null ? invoice.getInvoice().getExpectedDeliveryDate() : "N/A";
//
//                // Map data for placeholders
//                Map<String, String> receiptData = new HashMap<>();
//                receiptData.put("Date", orderDateFormatted);
//                receiptData.put("Invoice_No", invoice.getInvoiceId());
//                receiptData.put("Grand_Total", String.valueOf(invoice.getInvoice().getTotalAmount()));
//
//                receiptData.put("Customer_Name", invoice.getCustomer().getName());
//                receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
//                receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
//                receiptData.put("order_date", String.valueOf(invoice.getInvoice().getOrderDate()));
//                receiptData.put("Due_Date", expectedDeliveryDateFormatted);
//
//                receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//                receiptData.put("advance_paid", String.valueOf(invoice.getInvoice().getAdvancePaid()));
//                receiptData.put("balance_due", String.valueOf(invoice.getInvoice().getBalanceDue()));
//
//                receiptData.put("Payment_Method", invoice.getInvoice().getPaymentMethod());
//                receiptData.put("Payment_Status", invoice.getInvoice().getPaymentStatus());
//                receiptData.put("order_status", invoice.getInvoice().getOrderStatus());
//                receiptData.put("notes", invoice.getInvoice().getNotes() != null ? invoice.getInvoice().getNotes() : "Thank you for your order");
//
//                // Replace placeholders in document
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    replaceText(paragraph, receiptData);
//                }
//
//                for (XWPFTable table : document.getTables()) {
//                    for (XWPFTableRow row : table.getRows()) {
//                        for (XWPFTableCell cell : row.getTableCells()) {
//                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                                System.out.println("Processing text: " + paragraph.getText());
//                                replaceText(paragraph, receiptData);
//                            }
//                        }
//                    }
//                }
//
//                System.out.println("Modified Document Content:");
//                for (XWPFParagraph paragraph : document.getParagraphs()) {
//                    System.out.println(paragraph.getText());
//                }
//
//
//                System.out.println("Till here its working");
//
//                // Handle items list replacement
//                replaceItemList(document, invoice.getInvoice().getItems(), invoice.getInvoice());
//
//                System.out.println("Till here its working 2");
//
//                // Save document
//                FileOutputStream fos = new FileOutputStream(tempFile);
//                document.write(fos);
//                fos.close();
//                document.close();
//
//                System.out.println("Till here its working 3");
//
//
//                // Return file as response
//                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//                HttpHeaders headers = new HttpHeaders();
//                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//                return ResponseEntity.ok().headers(headers).body(fileContent);
//            }
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(null);
//        }
//    }

//    public ResponseEntity<byte[]> generateReceiptDocument(Invoice receipt, int i) {
//        System.out.println("Received Invoice" + receipt);
//
//        ReceiptServiceImpl selfProxy = applicationContext.getBean(ReceiptServiceImpl.class);
//        Invoice receipt1 = selfProxy.generateReceipt(receipt);
//
//        try {
//            // Load the template from resources folder
//            File templateFile = new ClassPathResource("Receipt_SF.docx").getFile();
//            File tempFile = File.createTempFile("receipt", ".docx");
//
//            // Copy template to temp file
//            Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
//
//            // Open the Word document
//            FileInputStream fis = new FileInputStream(tempFile);
//            XWPFDocument document = new XWPFDocument(fis);
//            fis.close();
//
//            // Format dates
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//            String orderDateFormatted = sdf.format(new Date());
//            String expectedDeliveryDateFormatted = receipt1.getExpectedDeliveryDate() != null ? receipt1.getExpectedDeliveryDate() : "N/A";
//
//            // Map data for placeholders
//            Map<String, String> receiptData = new HashMap<>();
//            receiptData.put("date", orderDateFormatted);
//            receiptData.put("Name", orderDateFormatted);
//            receiptData.put("ReceiptId", receipt1.getReceiptNo());
//
//            receiptData.put("customer_name", receipt1.getCustomer().getName());
//            receiptData.put("customer_phone", receipt1.getCustomer().getPhone());
//            receiptData.put("shop_id", receipt1.getShopId());
//            receiptData.put("order_date", String.valueOf(receipt1.getOrderDate()));
//            receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);
//
//            receiptData.put("total_amount", String.valueOf(receipt1.getTotalAmount()));
//            receiptData.put("advance_paid", String.valueOf(receipt1.getAdvancePaid()));
//            receiptData.put("balance_due", String.valueOf(receipt1.getBalanceDue()));
//
//            receiptData.put("payment_method", receipt1.getPaymentMethod());
//            receiptData.put("payment_status", receipt1.getPaymentStatus());
//            receiptData.put("order_status", receipt1.getOrderStatus());
//            receiptData.put("notes", receipt1.getNotes() != null ? receipt1.getNotes() : "Thank you for your order");
//
//            // Replace placeholders in document
//            for (XWPFParagraph paragraph : document.getParagraphs()) {
//                replaceText(paragraph, receiptData);
//            }
//
//            for (XWPFTable table : document.getTables()) {
//                for (XWPFTableRow row : table.getRows()) {
//                    for (XWPFTableCell cell : row.getTableCells()) {
//                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                            System.out.println("Processing text: " + paragraph.getText());
//                            replaceText(paragraph, receiptData);
//                        }
//                    }
//                }
//            }
//
//            System.out.println("Modified Document Content:");
//            for (XWPFParagraph paragraph : document.getParagraphs()) {
//                System.out.println(paragraph.getText());
//            }
//
//
//            System.out.println("Till here its working");
//
//            // Handle items list replacement
//            replaceItemList(document, receipt1.getItems(), receipt1);
//
//            System.out.println("Till here its working 2");
//
//            // Save document
//            FileOutputStream fos = new FileOutputStream(tempFile);
//            document.write(fos);
//            fos.close();
//            document.close();
//
//            System.out.println("Till here its working 3");
//
//
//            // Return file as response
//            byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//            headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//            return ResponseEntity.ok().headers(headers).body(fileContent);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(null);
//        }
//    }

    private void replaceText(XWPFParagraph paragraph, Map<String, String> receiptData) {
        StringBuilder paragraphText = new StringBuilder();
        List<XWPFRun> runs = paragraph.getRuns();

        // Collect full paragraph text
        for (XWPFRun run : runs) {
            paragraphText.append(run.getText(0));
        }

        String text = paragraphText.toString();
        boolean replaced = false;

        // Replace placeholders if they exist
        for (Map.Entry<String, String> entry : receiptData.entrySet()) {
            if (text.contains("{{" + entry.getKey() + "}}")) {
                text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
                replaced = true;
            }
        }

        // Update text in document only if replacement happened
        if (replaced) {
            for (int i = runs.size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }

            XWPFRun newRun = paragraph.createRun();
            newRun.setText(text);
        }
    }

    private void replaceItemList(XWPFDocument document, List<Items> items, ReceiptAndInvoicePDF receipt) {
        for (XWPFTable table : document.getTables()) {
            if (table.getRows().size() > 1) { // Ensure at least one row (header)

                // Preserve the last row to keep totals (if already present)
                int lastRowIndex = table.getRows().size() - 1;
                XWPFTableRow lastRow = table.getRow(lastRowIndex);
                boolean hasTotalRow = lastRow.getCell(0).getText().toLowerCase().contains("total");

                // Remove all item rows except the header
                while (table.getRows().size() > (hasTotalRow ? 2 : 1)) {
                    table.removeRow(1);
                }

                // Add item rows dynamically
                for (Items item : items) {
                    XWPFTableRow newRow = table.createRow();
                    while (newRow.getTableCells().size() < 3) {
                        newRow.addNewTableCell();
                    }

                    newRow.getCell(0).setText(item.getItemName() != null ? item.getItemName() : "N/A");
                    newRow.getCell(1).setText(String.valueOf(item.getItemQuantity()));
                    newRow.getCell(2).setText(String.valueOf(item.getItemPrice()));

                    // Adjust column width to avoid congestion
                    setColumnWidth(newRow, 0, 3000, 500); // Item Name
                    setColumnWidth(newRow, 1, 1500, 500); // Quantity
                    setColumnWidth(newRow, 2, 2000, 500); // Price
                }

                // Add an empty row for better spacing
               addOneRowGap(table);

                // If there was no existing total row, add footer
                if (!hasTotalRow) {
                    addFooterRows(table, receipt);
                }
            }
        }
    }

    private void addOneRowGap(XWPFTable table) {
        XWPFTableRow emptyRow = table.createRow();
        emptyRow.getCell(0).setText("");
        emptyRow.getCell(1).setText("");
        emptyRow.getCell(2).setText("");
        setRowHeight(emptyRow, 800);
    }

    // Helper method to set column width
    private void setColumnWidth(XWPFTableRow row, int cellIndex, int width, int height) {
        if (row.getCell(cellIndex) != null) {
            CTTcPr tcPr = row.getCell(cellIndex).getCTTc().addNewTcPr();
            CTTblWidth cellWidth = tcPr.addNewTcW();
            cellWidth.setType(STTblWidth.DXA);
            cellWidth.setW(BigInteger.valueOf(width));
        }

        setRowHeight(row, height); // Set row height for better spacing
    }

    // Helper method to set row height
    private void setRowHeight(XWPFTableRow row, int height) {
        CTRow ctRow = row.getCtRow();
        CTTrPr trPr = (ctRow.isSetTrPr()) ? ctRow.getTrPr() : ctRow.addNewTrPr();

        // Instead of isSetTrHeight(), directly create a new height property
        CTHeight trHeight = trPr.addNewTrHeight();
        trHeight.setVal(BigInteger.valueOf(height));
    }


    // Footer rows with proper column width
    private void addFooterRows(XWPFTable table, ReceiptAndInvoicePDF receipt) {
        int rowHeight = 500;

        XWPFTableRow footerRow = table.createRow();
        while (footerRow.getTableCells().size() < 4) {
            footerRow.addNewTableCell();
        }
        footerRow.getCell(0).setText("Total Amount:");
        footerRow.getCell(3).setText(String.valueOf(receipt.getTotalAmount()));
        setColumnWidth(footerRow, 0, 4000, rowHeight);
        setColumnWidth(footerRow, 3, 3000, rowHeight);

        XWPFTableRow advanceRow = table.createRow();
        while (advanceRow.getTableCells().size() < 4) {
            advanceRow.addNewTableCell();
        }
        advanceRow.getCell(0).setText("Advance Paid:");
        advanceRow.getCell(3).setText(String.valueOf(receipt.getAdvancePaid()));
        setColumnWidth(advanceRow, 0, 4000, rowHeight);
        setColumnWidth(advanceRow, 3, 3000, rowHeight);

        XWPFTableRow balanceRow = table.createRow();
        while (balanceRow.getTableCells().size() < 4) {
            balanceRow.addNewTableCell();
        }
        balanceRow.getCell(0).setText("Balance Due:");
        balanceRow.getCell(3).setText(String.valueOf(receipt.getBalanceDue()));
        setColumnWidth(balanceRow, 0, 4000, rowHeight);
        setColumnWidth(balanceRow, 3, 3000, rowHeight);

        addOneRowGap(table);

        XWPFTableRow paymentMethodRow = table.createRow();
        while (paymentMethodRow.getTableCells().size() < 4) {
            paymentMethodRow.addNewTableCell();
        }
        paymentMethodRow.getCell(0).setText("Payment Method:");
        paymentMethodRow.getCell(3).setText(String.valueOf(receipt.getPaymentMethod()));
        setColumnWidth(paymentMethodRow, 0, 4000, rowHeight);
        setColumnWidth(paymentMethodRow, 3, 3000, rowHeight);

        XWPFTableRow paymentStatusRow = table.createRow();
        while (paymentStatusRow.getTableCells().size() < 4) {
            paymentStatusRow.addNewTableCell();
        }
        paymentStatusRow.getCell(0).setText("Payment Status:");
        paymentStatusRow.getCell(3).setText(String.valueOf(receipt.getPaymentStatus()));
        setColumnWidth(paymentStatusRow, 0, 4000, rowHeight);
        setColumnWidth(paymentStatusRow, 3, 3000, rowHeight);

        addOneRowGap(table);

        XWPFTableRow dueDateRow = table.createRow();
        while (dueDateRow.getTableCells().size() < 4) {
            dueDateRow.addNewTableCell();
        }
        dueDateRow.getCell(0).setText("Expected Delivery Date:");
        dueDateRow.getCell(3).setText(String.valueOf(receipt.getDeliveryDate()));
        setColumnWidth(dueDateRow, 0, 4000, rowHeight);
        setColumnWidth(dueDateRow, 3, 3000, rowHeight);


    }

}
