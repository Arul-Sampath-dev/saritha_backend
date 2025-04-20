package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.Items;
import com.saritha.fashions.payload.ReceiptAndInvoicePDF;
import com.saritha.fashions.payload.ReceiptResponse;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.apache.poi.xwpf.usermodel.*;
import org.modelmapper.ModelMapper;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
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

    @Override
    public ReceiptResponse getAllReceiptFromAllShops(int page, int size, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sortByAndOrder);
        Page<Receipt> receiptPage = receiptRepository.findAll(pageable);
        List<Receipt> receiptList = receiptPage.getContent();
        if (receiptList.isEmpty()) {
            throw new APIException("No Categories found");
        }

        List<Receipt> receipts = receiptList.stream()
                .map(receipt -> modelMapper.map(receipt, Receipt.class))
                .toList();
        ReceiptResponse receiptResponse = new ReceiptResponse();
        receiptResponse.setContent(receipts);
        receiptResponse.setPageNumber(receiptPage.getNumber());
        receiptResponse.setPageSize(receiptPage.getSize());
        receiptResponse.setTotalElements(receiptPage.getTotalElements());
        receiptResponse.setTotalpages(receiptPage.getTotalPages());
        receiptResponse.setLastPage(receiptPage.isLast());
        return receiptResponse;
    }

    public String generateUniqueCustomerId(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String customerId;
        do {
            customerId = text + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (customerRepository.existsByCustomerId(customerId));
        return customerId;
    }

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
                                    replaceText(paragraph, receiptData);
                                }
                            }
                        }
                    }

                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        System.out.println(paragraph.getText());
                    }

                    ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate());

                    replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);

                    // Save to temp file
                    File tempFile = File.createTempFile(tempFileName, ".docx");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        document.write(fos);
                    }

                    byte[] fileContent = Files.readAllBytes(tempFile.toPath());

                    // Call external Cloud Run microservice to convert to PDF
                    RestTemplate restTemplate = new RestTemplate();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    // Create the multipart request
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("file", new ByteArrayResource(fileContent) {
                        @Override
                        public String getFilename() {
                            return "Invoice.docx";
                        }
                    });

                    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                    String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
                    ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
                    byte[] pdfBytes = response.getBody();

                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.setContentType(MediaType.APPLICATION_PDF);
                    responseHeaders.setContentDispositionFormData("attachment", "Invoice.pdf");

                    return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);

                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
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
                                    replaceText(paragraph, receiptData);
                                }
                            }
                        }
                    }

                    for (XWPFParagraph paragraph : document.getParagraphs()) {
                        System.out.println(paragraph.getText());
                    }

                    ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate());

                    replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);

                    File tempFile = File.createTempFile(tempFileName, ".docx");
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        document.write(fos);
                    }

                    byte[] fileContent = Files.readAllBytes(tempFile.toPath());

                    // Call external Cloud Run microservice to convert to PDF
                    RestTemplate restTemplate = new RestTemplate();

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                    // Create the multipart request
                    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                    body.add("file", new ByteArrayResource(fileContent) {
                        @Override
                        public String getFilename() {
                            return "Receipt.docx";
                        }
                    });

                    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                    String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
                    ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
                    byte[] pdfBytes = response.getBody();

                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.setContentType(MediaType.APPLICATION_PDF);
                    responseHeaders.setContentDispositionFormData("attachment", "Receipt.pdf");

                    return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);

//                    byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//
//                    HttpHeaders headers = new HttpHeaders();
//                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//                    headers.setContentDispositionFormData("attachment", "Invoice.docx");
//
//                    return ResponseEntity.ok().headers(headers).body(fileContent);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

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
