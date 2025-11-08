package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.Shop;
import com.saritha.fashions.payload.items.Items;
import com.saritha.fashions.payload.receipt.ReceiptAndInvoicePDF;
import com.saritha.fashions.payload.receipt.ReceiptResponse;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Custom.TwilioWhatsAppSender;
import com.saritha.fashions.service.Interface.InvoiceService;
import com.saritha.fashions.service.Interface.ReceiptService;
import com.saritha.fashions.service.Interface.ShopService;
import com.saritha.fashions.util.DateTimeUtils;
import com.saritha.fashions.util.GcsImageUploader;
import com.saritha.fashions.util.TaxUtils;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.modelmapper.ModelMapper;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private static final Logger logger = LoggerFactory.getLogger(ReceiptServiceImpl.class);


    // Remove @Autowired from all fields
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final GcsImageUploader gcsImageUploader;
    private final InvoiceService invoiceService;
    private final ReceiptRepository receiptRepository;
    private final WhatsAppService whatsAppService;
    private final ModelMapper modelMapper;
    private final ApplicationContext applicationContext;
    private final ShopService shopService;
    private final MongoTemplate mongoTemplate;
    private final TwilioWhatsAppSender twilioWhatsAppSender;

    // Single constructor with all dependencies
    public ReceiptServiceImpl(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            GcsImageUploader gcsImageUploader,
            @Lazy InvoiceService invoiceService,
            ReceiptRepository receiptRepository,
            WhatsAppService whatsAppService,
            ModelMapper modelMapper,
            ShopService shopService,
            MongoTemplate mongoTemplate,
            TwilioWhatsAppSender twilioWhatsAppSender,
            ApplicationContext applicationContext) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.gcsImageUploader = gcsImageUploader;
        this.invoiceService = invoiceService;
        this.receiptRepository = receiptRepository;
        this.whatsAppService = whatsAppService;
        this.modelMapper = modelMapper;
        this.applicationContext = applicationContext;
        this.shopService = shopService;
        this.mongoTemplate = mongoTemplate;
        this.twilioWhatsAppSender = twilioWhatsAppSender;
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
        receiptResponse.setTotalPages(receiptPage.getTotalPages());
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

    public String generateUniqueInvoiceIdForCloudBucket() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String invoiceId;
        do {
            invoiceId = "CLD-IMG-" + NanoIdUtils.randomNanoId(NanoIdUtils.DEFAULT_NUMBER_GENERATOR, alphabet.toCharArray(), 10);
        } while (invoiceRepository.existsByInvoiceIdForCloudBucket(invoiceId));
        return invoiceId;
    }

    private void addImageToCell(XWPFTableCell cell, String imageUrl) throws Exception {
        // Fetch image from URL
        URL url = new URL(imageUrl);
        BufferedImage originalImage = ImageIO.read(url);

        // Resize if needed
        int width = 100; // desired width
        int height = (int) (originalImage.getHeight() *
                ((double) width / originalImage.getWidth()));

        BufferedImage resizedImage = new BufferedImage(width, height, originalImage.getType());
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();

        // Convert to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        // Add image to document
        XWPFRun run = cell.addParagraph().createRun();
        run.addPicture(
                new ByteArrayInputStream(imageBytes),
                Document.PICTURE_TYPE_PNG,
                "image.png",
                Units.toEMU(width),
                Units.toEMU(height)
        );
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

        try {
            String fileName, tempFileName;

            if (i == 1) {
                Invoice invoice = invoiceService.generateInvoice(invoiceFromFrontend);

                logger.info("Invoice generated and saved in db");
                return getInvoicePdf(invoice);

            } else {
                Receipt invoice = invoiceService.generateReceiptByInvoice(invoiceFromFrontend);

                return getReceiptPdf(invoice);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @Override
    public ResponseEntity<byte[]> getInvoicePdfMethod(Invoice invoice) {
        return getInvoicePdf(invoice);
    }

    @Override
    public ResponseEntity<byte[]> getReceiptPdfMethod(Receipt receipt) throws IOException {
        return getReceiptPdf(receipt);
    }

    @Override
    public ResponseEntity<byte[]> downloadInvoiceByCustomerPdf(String invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceId(invoiceId).orElseThrow(() -> new APIException("No Invoice Found"));
        return getInvoicePdf(invoice);
    }

    @Override
    public ResponseEntity<byte[]> downloadReceiptByCustomerPdf(String receiptId) throws IOException {
        Receipt receipt = receiptRepository.findByReceiptNo(receiptId).orElseThrow(() -> new APIException("No ReceiptId Found"));
        return getReceiptPdf(receipt);
    }

//    @NotNull
//    private ResponseEntity<byte[]> getReceiptPdf(Receipt invoice) throws IOException {
//        String fileName;
//        String tempFileName;
//        fileName = "Invoice_SF.docx";
//        tempFileName = "receipt";
//
//        ClassPathResource resource = new ClassPathResource(fileName);
//        try (InputStream is = resource.getInputStream(); XWPFDocument document = new XWPFDocument(is)) {
//
//            Shop shop = shopService.getShopById(invoice.getShopId());
//            String shopAddress = shop.getShopAddress();
//            String shopMobileNumber = shop.getShopContactNumber();
//
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//            String orderDateFormatted = sdf.format(new Date());
//            String expectedDeliveryDateFormatted = "N/A";
//            if (invoice != null) {
//                expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";
//            }
//
//            Map<String, String> receiptData = new HashMap<>();
//            receiptData.put("Date", orderDateFormatted);
//            receiptData.put("Receipt_No", invoice.getReceiptNo());
//            receiptData.put("Grand_Total", String.valueOf(invoice.getTotalAmount()));
//
//            receiptData.put("Mob_No", shopMobileNumber);
//            receiptData.put("Shop_Address", shopAddress);
//
//            receiptData.put("Customer_Name", invoice.getCustomer().getName());
//            receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
//            receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
//            receiptData.put("order_date", String.valueOf(invoice.getReceiptDate()));
//            receiptData.put("Due_Date", expectedDeliveryDateFormatted);
//
//            receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//            receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
//            receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));
//
//            receiptData.put("Payment_Method", invoice.getPaymentMethod());
//            receiptData.put("Payment_Status", invoice.getPaymentStatus());
//            receiptData.put("order_status", invoice.getOrderStatus());
//
//            // Discount
//            receiptData.put("discount", String.valueOf(invoice.getDiscount()));
//
//            // Taxable amount after discount
//            receiptData.put("taxable_amount", String.valueOf(invoice.getTaxableAmount()));
//
//            // GST details
//            receiptData.put("cgst_rate", String.valueOf(invoice.getCgstRate()));
//            receiptData.put("sgst_rate", String.valueOf(invoice.getSgstRate()));
//            receiptData.put("igst_rate", String.valueOf(invoice.getIgstRate()));
//
//            receiptData.put("cgst_amount", String.valueOf(invoice.getCgstAmount()));
//            receiptData.put("sgst_amount", String.valueOf(invoice.getSgstAmount()));
//            receiptData.put("igst_amount", String.valueOf(invoice.getIgstAmount()));
//
//            // Final total including GST
//            receiptData.put("total_amount_with_gst", String.valueOf(invoice.getTotalAmountWithGST()));
//
//            // GSTIN & SAC/HSN codes
//            receiptData.put("gstin", invoice.getGstin() != null ? invoice.getGstin() : "N/A");
//            receiptData.put("sac_code", invoice.getSacCode() != null ? invoice.getSacCode() : "9988");
//            receiptData.put("hsn_code", invoice.getHsnCode() != null ? invoice.getHsnCode() : "N/A");
//
//            receiptData.put("notes", invoice.getReceiptNotes() != null ? invoice.getReceiptNotes() : "Thank you for your order");
//
//            for (XWPFParagraph paragraph : document.getParagraphs()) {
//                replaceText(paragraph, receiptData);
//            }
//
//            for (XWPFTable table : document.getTables()) {
//                for (XWPFTableRow row : table.getRows()) {
//                    for (XWPFTableCell cell : row.getTableCells()) {
//                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                            replaceText(paragraph, receiptData);
//                        }
//                    }
//                }
//            }
//
////                    for (XWPFParagraph paragraph : document.getParagraphs()) {
////                        System.out.println(paragraph.getText());
////                    }
//
////            ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate());
//            ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate(), invoice.getDiscount(), invoice.getTotalAmountWithGST() );
//
//            replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);
//
//            File tempFile = File.createTempFile(tempFileName, ".docx");
//            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
//                document.write(fos);
//            }
//
//            byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//
//            // Call external Cloud Run microservice to convert to PDF
//            RestTemplate restTemplate = new RestTemplate();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//            // Create the multipart request
//            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//            body.add("file", new ByteArrayResource(fileContent) {
//                @Override
//                public String getFilename() {
//                    return "Receipt.docx";
//                }
//            });
//
//            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
//
//            String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
//            ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
//            byte[] pdfBytes = response.getBody();
//
//            HttpHeaders responseHeaders = new HttpHeaders();
//            responseHeaders.setContentType(MediaType.APPLICATION_PDF);
//            responseHeaders.setContentDispositionFormData("attachment", "Receipt.pdf");
//
//            return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);
//
//        }
//    }
//
//    @NotNull
//    private ResponseEntity<byte[]> getInvoicePdf(Invoice invoice) {
//        String tempFileName;
//        String fileName;
//        fileName = "Receipt_SF.docx";
//        tempFileName = "invoice";
//
//        ClassPathResource resource = new ClassPathResource(fileName);
//        try (InputStream is = resource.getInputStream(); XWPFDocument document = new XWPFDocument(is)) {
//
//            Shop shop = shopService.getShopById(invoice.getShopId());
//            String shopAddress = shop.getShopAddress();
//            String shopMobileNumber = shop.getShopContactNumber();
//
//            // Format dates
//            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
//            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
//            String orderDateFormatted = sdf.format(new Date());
//            String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";
//
//            Map<String, String> receiptData = new HashMap<>();
//            receiptData.put("date", orderDateFormatted);
//            receiptData.put("Invoice_No", orderDateFormatted);
//            receiptData.put("InvoiceId", invoice.getInvoiceId());
//
//            receiptData.put("Mob_No", shopMobileNumber);
//            receiptData.put("Shop_Address", shopAddress);
//
//            receiptData.put("customer_name", invoice.getCustomer().getName());
//            receiptData.put("customer_phone", invoice.getCustomer().getPhone());
//            receiptData.put("shop_id", invoice.getShopId());
//            receiptData.put("order_date", String.valueOf(invoice.getOrderDate()));
//            receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);
//
//            receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
//            receiptData.put("advance_paid", String.valueOf(invoice.getAdvancePaid()));
//            receiptData.put("balance_due", String.valueOf(invoice.getBalanceDue()));
//
//            receiptData.put("payment_method", invoice.getPaymentMethod());
//            receiptData.put("payment_status", invoice.getPaymentStatus());
//            receiptData.put("order_status", invoice.getOrderStatus());
//
//            // Discount
//            receiptData.put("discount", String.valueOf(invoice.getDiscount()));
//
//            // Taxable amount after discount
//            receiptData.put("taxable_amount", String.valueOf(invoice.getTaxableAmount()));
//
//            // GST details
//            receiptData.put("cgst_rate", String.valueOf(invoice.getCgstRate()));
//            receiptData.put("sgst_rate", String.valueOf(invoice.getSgstRate()));
//            receiptData.put("igst_rate", String.valueOf(invoice.getIgstRate()));
//
//            receiptData.put("cgst_amount", String.valueOf(invoice.getCgstAmount()));
//            receiptData.put("sgst_amount", String.valueOf(invoice.getSgstAmount()));
//            receiptData.put("igst_amount", String.valueOf(invoice.getIgstAmount()));
//
//            // Final total including GST
//            receiptData.put("total_amount_with_gst", String.valueOf(invoice.getTotalAmountWithGST()));
//
//            // GSTIN & SAC/HSN codes
//            receiptData.put("gstin", invoice.getGstin() != null ? invoice.getGstin() : "N/A");
//            receiptData.put("sac_code", invoice.getSacCode() != null ? invoice.getSacCode() : "9988");
//            receiptData.put("hsn_code", invoice.getHsnCode() != null ? invoice.getHsnCode() : "N/A");
//
//
//            receiptData.put("notes", invoice.getNotes() != null ? invoice.getNotes() : "Thank you for your order");
//
//            for (XWPFParagraph paragraph : document.getParagraphs()) {
//                replaceText(paragraph, receiptData);
//            }
//
//            for (XWPFTable table : document.getTables()) {
//                for (XWPFTableRow row : table.getRows()) {
//                    for (XWPFTableCell cell : row.getTableCells()) {
//                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
//                            replaceText(paragraph, receiptData);
//                        }
//                    }
//                }
//            }
//
////                    for (XWPFParagraph paragraph : document.getParagraphs()) {
////                        System.out.println(paragraph.getText());
////                    }
//
//            ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(invoice.getTotalAmount(), invoice.getAdvancePaid(), invoice.getBalanceDue(), invoice.getPaymentMethod(), invoice.getPaymentStatus(), invoice.getExpectedDeliveryDate(), invoice.getDiscount(), invoice.getTotalAmountWithGST() );
//
//            replaceItemList(document, invoice.getItems(), receiptAndInvoicePDF);
//
//            // Save to temp file
//            File tempFile = File.createTempFile(tempFileName, ".docx");
//            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
//                document.write(fos);
//            }
//
//            byte[] fileContent = Files.readAllBytes(tempFile.toPath());
//
//            // Call external Cloud Run microservice to convert to PDF
//            RestTemplate restTemplate = new RestTemplate();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
//
//            // Create the multipart request
//            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//            body.add("file", new ByteArrayResource(fileContent) {
//                @Override
//                public String getFilename() {
//                    return "Invoice.docx";
//                }
//            });
//
//            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
//
//            logger.info("Just before calling the microservice python server for converting docs to pdf");
//
//            String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
//            ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
//            byte[] pdfBytes = response.getBody();
//
//            HttpHeaders responseHeaders = new HttpHeaders();
//            responseHeaders.setContentType(MediaType.APPLICATION_PDF);
//            responseHeaders.setContentDispositionFormData("attachment", "Invoice.pdf");
//
////            String customerNumber = "+916203932586";
////            String orderId = invoice.getInvoiceId();
////            String invoiceUrl = "https://sarithasdesignerstudio.com/download-invoice/"+orderId;
////
////            if (pdfBytes != null) {
////                twilioWhatsAppSender.sendOrderConfirmation(customerNumber, orderId, invoiceUrl);
////            }
//
//            return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);
//
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private void replaceText(XWPFParagraph paragraph, Map<String, String> receiptData) {
//        StringBuilder paragraphText = new StringBuilder();
//        List<XWPFRun> runs = paragraph.getRuns();
//
//        // Collect full paragraph text
//        for (XWPFRun run : runs) {
//            paragraphText.append(run.getText(0));
//        }
//
//        String text = paragraphText.toString();
//        boolean replaced = false;
//
//        // Replace placeholders if they exist
//        for (Map.Entry<String, String> entry : receiptData.entrySet()) {
//            if (text.contains("{{" + entry.getKey() + "}}")) {
//                text = text.replace("{{" + entry.getKey() + "}}", entry.getValue());
//                replaced = true;
//            }
//        }
//
//        // Update text in document only if replacement happened
//        if (replaced) {
//            for (int i = runs.size() - 1; i >= 0; i--) {
//                paragraph.removeRun(i);
//            }
//
//            XWPFRun newRun = paragraph.createRun();
//            newRun.setText(text);
//        }
//    }
//
//    private void replaceItemList(XWPFDocument document, List<Items> items, ReceiptAndInvoicePDF receipt) {
//        for (XWPFTable table : document.getTables()) {
//            if (table.getRows().size() > 1) { // Ensure at least one row (header)
//
//                // Preserve the last row to keep totals (if already present)
//                int lastRowIndex = table.getRows().size() - 1;
//                XWPFTableRow lastRow = table.getRow(lastRowIndex);
//                boolean hasTotalRow = lastRow.getCell(0).getText().toLowerCase().contains("total");
//
//                // Remove all item rows except the header
//                while (table.getRows().size() > (hasTotalRow ? 2 : 1)) {
//                    table.removeRow(1);
//                }
//
//                // Add item rows dynamically
//                for (Items item : items) {
//                    XWPFTableRow newRow = table.createRow();
//                    while (newRow.getTableCells().size() < 3) {
//                        newRow.addNewTableCell();
//                    }
//
//                    newRow.getCell(0).setText(item.getItemName() != null ? item.getItemName() : "N/A");
//                    newRow.getCell(1).setText(String.valueOf(item.getItemQuantity()));
//                    newRow.getCell(2).setText(String.valueOf(item.getItemPrice()));
//
//                    // Adjust column width to avoid congestion
//                    setColumnWidth(newRow, 0, 3000, 500); // Item Name
//                    setColumnWidth(newRow, 1, 1500, 500); // Quantity
//                    setColumnWidth(newRow, 2, 2000, 500); // Price
//                }
//
//                // Add an empty row for better spacing
//                addOneRowGap(table);
//
//                // If there was no existing total row, add footer
//                if (!hasTotalRow) {
//                    addFooterRows(table, receipt);
//                }
//            }
//        }
//    }
//
//    private void addOneRowGap(XWPFTable table) {
//        XWPFTableRow emptyRow = table.createRow();
//        emptyRow.getCell(0).setText("");
//        emptyRow.getCell(1).setText("");
//        emptyRow.getCell(2).setText("");
//        setRowHeight(emptyRow, 800);
//    }
//
//    // Helper method to set column width
//    private void setColumnWidth(XWPFTableRow row, int cellIndex, int width, int height) {
//        if (row.getCell(cellIndex) != null) {
//            CTTcPr tcPr = row.getCell(cellIndex).getCTTc().addNewTcPr();
//            CTTblWidth cellWidth = tcPr.addNewTcW();
//            cellWidth.setType(STTblWidth.DXA);
//            cellWidth.setW(BigInteger.valueOf(width));
//        }
//
//        setRowHeight(row, height); // Set row height for better spacing
//    }
//
//    // Helper method to set row height
//    private void setRowHeight(XWPFTableRow row, int height) {
//        CTRow ctRow = row.getCtRow();
//        CTTrPr trPr = (ctRow.isSetTrPr()) ? ctRow.getTrPr() : ctRow.addNewTrPr();
//
//        // Instead of isSetTrHeight(), directly create a new height property
//        CTHeight trHeight = trPr.addNewTrHeight();
//        trHeight.setVal(BigInteger.valueOf(height));
//    }
//
//
//    // Footer rows with proper column width
//    private void addFooterRows(XWPFTable table, ReceiptAndInvoicePDF receipt) {
//        int rowHeight = 500;
//
//        XWPFTableRow footerRow = table.createRow();
//        while (footerRow.getTableCells().size() < 4) {
//            footerRow.addNewTableCell();
//        }
//        footerRow.getCell(0).setText("Total Amount:");
//        footerRow.getCell(3).setText(String.valueOf(receipt.getTotalAmount()));
//        setColumnWidth(footerRow, 0, 4000, rowHeight);
//        setColumnWidth(footerRow, 3, 3000, rowHeight);
//
//        XWPFTableRow advanceRow = table.createRow();
//        while (advanceRow.getTableCells().size() < 4) {
//            advanceRow.addNewTableCell();
//        }
//        advanceRow.getCell(0).setText("Advance Paid:");
//        advanceRow.getCell(3).setText(String.valueOf(receipt.getAdvancePaid()));
//        setColumnWidth(advanceRow, 0, 4000, rowHeight);
//        setColumnWidth(advanceRow, 3, 3000, rowHeight);
//
//        XWPFTableRow balanceRow = table.createRow();
//        while (balanceRow.getTableCells().size() < 4) {
//            balanceRow.addNewTableCell();
//        }
//        balanceRow.getCell(0).setText("Balance Due:");
//        balanceRow.getCell(3).setText(String.valueOf(receipt.getBalanceDue()));
//        setColumnWidth(balanceRow, 0, 4000, rowHeight);
//        setColumnWidth(balanceRow, 3, 3000, rowHeight);
//
//        addOneRowGap(table);
//
//        XWPFTableRow paymentMethodRow = table.createRow();
//        while (paymentMethodRow.getTableCells().size() < 4) {
//            paymentMethodRow.addNewTableCell();
//        }
//        paymentMethodRow.getCell(0).setText("Payment Method:");
//        paymentMethodRow.getCell(3).setText(String.valueOf(receipt.getPaymentMethod()));
//        setColumnWidth(paymentMethodRow, 0, 4000, rowHeight);
//        setColumnWidth(paymentMethodRow, 3, 3000, rowHeight);
//
//        XWPFTableRow paymentStatusRow = table.createRow();
//        while (paymentStatusRow.getTableCells().size() < 4) {
//            paymentStatusRow.addNewTableCell();
//        }
//        paymentStatusRow.getCell(0).setText("Payment Status:");
//        paymentStatusRow.getCell(3).setText(String.valueOf(receipt.getPaymentStatus()));
//        setColumnWidth(paymentStatusRow, 0, 4000, rowHeight);
//        setColumnWidth(paymentStatusRow, 3, 3000, rowHeight);
//
//        addOneRowGap(table);
//
//        XWPFTableRow dueDateRow = table.createRow();
//        while (dueDateRow.getTableCells().size() < 4) {
//            dueDateRow.addNewTableCell();
//        }
//        dueDateRow.getCell(0).setText("Expected Delivery Date:");
//        dueDateRow.getCell(3).setText(String.valueOf(receipt.getDeliveryDate()));
//        setColumnWidth(dueDateRow, 0, 4000, rowHeight);
//        setColumnWidth(dueDateRow, 3, 3000, rowHeight);
//
//        XWPFTableRow discountRow = table.createRow();
//        while (discountRow.getTableCells().size() < 4) {
//            discountRow.addNewTableCell();
//        }
//        discountRow.getCell(0).setText("Discount:");
//        discountRow.getCell(3).setText(String.valueOf(receipt.getDiscount()));
//        setColumnWidth(discountRow, 0, 4000, rowHeight);
//        setColumnWidth(discountRow, 3, 3000, rowHeight);
//
//        XWPFTableRow gstRow = table.createRow();
//        while (gstRow.getTableCells().size() < 4) {
//            gstRow.addNewTableCell();
//        }
//        gstRow.getCell(0).setText("Total Amount (Incl. GST):");
//        gstRow.getCell(3).setText(String.valueOf(receipt.getTotalAmountWithGST()));
//        setColumnWidth(gstRow, 0, 4000, rowHeight);
//        setColumnWidth(gstRow, 3, 3000, rowHeight);
//
//
//
//    }


    @NotNull
    private ResponseEntity<byte[]> getReceiptPdf(Receipt invoice) throws IOException {
        String fileName;
        String tempFileName;
        fileName = "Invoice_SF.docx";
        tempFileName = "receipt";

        ClassPathResource resource = new ClassPathResource(fileName);
        try (InputStream is = resource.getInputStream(); XWPFDocument document = new XWPFDocument(is)) {

//            Shop shop = shopService.getShopById(invoice.getShopId());
            Query shopQuery = Query.query(Criteria.where("id").is(invoice.getShopId()));
            shopQuery.fields()
                    .include("shopName")
                    .include("shopAddress")
                    .include("shopContactNumber")
                    .include("gstNumber")
                    .include("shopAddress")
                    .include("shopState");

            Shop shop = mongoTemplate.findOne(shopQuery, Shop.class);

            if (shop == null) {
                throw new APIException("Shop not found for ID: " + invoice.getShopId());
            }

            logger.info("Shop data from mongoTemplate Query: {}", shop);
            String shopAddress = shop.getShopAddress();
            String shopMobileNumber = shop.getShopContactNumber();
            String shopGST = shop.getGstNumber();

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            String orderDateFormatted = sdf.format(new Date());
            String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";

            // Replace header placeholders
            Map<String, String> receiptData = new HashMap<>();
            receiptData.put("Date", invoice.getReceiptDate() != null ? DateTimeUtils.formatDateToIST(invoice.getReceiptDate()) : orderDateFormatted);
            receiptData.put("Receipt_No", invoice.getReceiptNo());

            receiptData.put("Mob_No", shopMobileNumber);
            receiptData.put("Shop_Address", shopAddress);
            receiptData.put("Shop_GST", shopGST);

            receiptData.put("Customer_Name", invoice.getCustomer().getName());
            receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
            receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
            receiptData.put("order_date", String.valueOf(invoice.getReceiptDate()));
            receiptData.put("Due_Date", expectedDeliveryDateFormatted);

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

            // ✅ Build ReceiptAndInvoicePDF with all GST & Discount fields
            ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(
                    invoice.getTotalAmount(),
                    invoice.getAdvancePaid(),
                    invoice.getBalanceDue(),
                    invoice.getPaymentMethod(),
                    invoice.getPaymentStatus(),
                    invoice.getExpectedDeliveryDate(),
                    invoice.getDiscount(),
                    invoice.getTaxableAmount(),
                    invoice.getCgstRate(),
                    invoice.getSgstRate(),
                    invoice.getIgstRate(),
                    invoice.getCgstAmount(),
                    invoice.getSgstAmount(),
                    invoice.getIgstAmount(),
                    invoice.getTotalAmountWithGST(),
                    invoice.getHsnCode()
            );

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

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return "Receipt.docx";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

//            String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
            String pdfServiceUrl = "https://docs-to-pdf-new-156538435086.asia-south1.run.app/convert";
            ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
            byte[] pdfBytes = response.getBody();

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_PDF);
            responseHeaders.setContentDispositionFormData("attachment", "Receipt.pdf");

            return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);

        }
    }

    @NotNull
    private ResponseEntity<byte[]> getInvoicePdf(Invoice invoice) {
        String tempFileName;
        String fileName;
        fileName = "Receipt_SF.docx";
        tempFileName = "invoice";

        ClassPathResource resource = new ClassPathResource(fileName);
        try (InputStream is = resource.getInputStream(); XWPFDocument document = new XWPFDocument(is)) {

//            Shop shop = shopService.getShopById(invoice.getShopId());

            Query shopQuery = Query.query(Criteria.where("id").is(invoice.getShopId()));
            shopQuery.fields()
                    .include("shopName")
                    .include("shopAddress")
                    .include("shopContactNumber")
                    .include("gstNumber")
                    .include("shopAddress")
                    .include("shopState");

            Shop shop = mongoTemplate.findOne(shopQuery, Shop.class);

            if (shop == null) {
                throw new APIException("Shop not found for ID: " + invoice.getShopId());
            }

            logger.info("Shop data from mongoTemplate Query: {}", shop);
            String shopAddress = shop.getShopAddress();
            String shopMobileNumber = shop.getShopContactNumber();
            String shopGST = shop.getGstNumber();

            // Format dates
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            String orderDateFormatted = sdf.format(new Date());
            String expectedDeliveryDateFormatted = invoice.getExpectedDeliveryDate() != null ? invoice.getExpectedDeliveryDate() : "N/A";

            // Replace header placeholders
            Map<String, String> receiptData = new HashMap<>();
            receiptData.put("date", invoice.getInvoiceDate() != null ? DateTimeUtils.formatDateToIST(invoice.getInvoiceDate()) : orderDateFormatted);
            receiptData.put("Invoice_No", orderDateFormatted);
            receiptData.put("InvoiceId", invoice.getInvoiceId());

            receiptData.put("Mob_No", shopMobileNumber);
            receiptData.put("Shop_Address", shopAddress);
            receiptData.put("Shop_GST", shopGST);

            receiptData.put("customer_name", invoice.getCustomer().getName());
            receiptData.put("customer_phone", invoice.getCustomer().getPhone());
            receiptData.put("shop_id", invoice.getShopId());
            receiptData.put("order_date", String.valueOf(invoice.getOrderDate()));
            receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);

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

            // ✅ Build ReceiptAndInvoicePDF with all GST & Discount fields
            ReceiptAndInvoicePDF receiptAndInvoicePDF = new ReceiptAndInvoicePDF(
                    invoice.getTotalAmount(),
                    invoice.getAdvancePaid(),
                    invoice.getBalanceDue(),
                    invoice.getPaymentMethod(),
                    invoice.getPaymentStatus(),
                    invoice.getExpectedDeliveryDate(),
                    invoice.getDiscount(),
                    invoice.getTaxableAmount(),
                    invoice.getCgstRate(),
                    invoice.getSgstRate(),
                    invoice.getIgstRate(),
                    invoice.getCgstAmount(),
                    invoice.getSgstAmount(),
                    invoice.getIgstAmount(),
                    invoice.getTotalAmountWithGST(),
                    invoice.getHsnCode()
            );

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

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return "Invoice.docx";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

//            String pdfServiceUrl = "https://docx-to-pdf-156538435086.asia-south1.run.app/convert";
            String pdfServiceUrl = "https://docs-to-pdf-new-156538435086.asia-south1.run.app/convert";
            ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, requestEntity, byte[].class);
            byte[] pdfBytes = response.getBody();

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_PDF);
            responseHeaders.setContentDispositionFormData("attachment", "Invoice.pdf");

            return ResponseEntity.ok().headers(responseHeaders).body(pdfBytes);

        } catch (IOException e) {
            throw new RuntimeException(e);
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

                // --- Step 1: Clear old item rows except header (and maybe total rows) ---
                int lastRowIndex = table.getRows().size() - 1;
                XWPFTableRow lastRow = table.getRow(lastRowIndex);
                boolean hasTotalRow = lastRow.getCell(0).getText().toLowerCase().contains("total");

                while (table.getRows().size() > (hasTotalRow ? 2 : 1)) {
                    table.removeRow(1);
                }

                // --- Step 2: Add new item rows dynamically ---
                for (Items item : items) {
                    XWPFTableRow newRow = table.createRow();
                    while (newRow.getTableCells().size() < 3) {
                        newRow.addNewTableCell();
                    }

                    newRow.getCell(0).setText(item.getItemName() != null ? item.getItemName() : "N/A");
                    newRow.getCell(1).setText(String.valueOf(item.getItemQuantity()));
                    newRow.getCell(2).setText(String.valueOf(item.getItemPrice()));

                    // Adjust column widths for neatness
                    setColumnWidth(newRow, 0, 3000, 500); // Item Name
                    setColumnWidth(newRow, 1, 1500, 500); // Quantity
                    setColumnWidth(newRow, 2, 2000, 500); // Price
                }

                // --- Step 3: Add spacing row ---
                addOneRowGap(table);

                // --- Step 4: Add footer rows with totals, GST, discount, etc. ---
                addFooterRows(table, receipt);
            }
        }
    }


//    private void replaceItemList(XWPFDocument document, List<Items> items, ReceiptAndInvoicePDF receipt) {
//        for (XWPFTable table : document.getTables()) {
//            if (table.getRows().size() > 1) { // Ensure at least one row (header)
//
//                // Preserve the last row to keep totals (if already present)
//                int lastRowIndex = table.getRows().size() - 1;
//                XWPFTableRow lastRow = table.getRow(lastRowIndex);
//                boolean hasTotalRow = lastRow.getCell(0).getText().toLowerCase().contains("total");
//
//                // Remove all item rows except the header
//                while (table.getRows().size() > (hasTotalRow ? 2 : 1)) {
//                    table.removeRow(1);
//                }
//
//                // Add item rows dynamically
//                for (Items item : items) {
//                    XWPFTableRow newRow = table.createRow();
//                    while (newRow.getTableCells().size() < 3) {
//                        newRow.addNewTableCell();
//                    }
//
//                    newRow.getCell(0).setText(item.getItemName() != null ? item.getItemName() : "N/A");
//                    newRow.getCell(1).setText(String.valueOf(item.getItemQuantity()));
//                    newRow.getCell(2).setText(String.valueOf(item.getItemPrice()));
//
//                    // Adjust column width to avoid congestion
//                    setColumnWidth(newRow, 0, 3000, 500); // Item Name
//                    setColumnWidth(newRow, 1, 1500, 500); // Quantity
//                    setColumnWidth(newRow, 2, 2000, 500); // Price
//                }
//
//                // Add an empty row for better spacing
//               addOneRowGap(table);
//
//                // If there was no existing total row, add footer
//                if (!hasTotalRow) {
//                    addFooterRows(table, receipt);
//                }
//            }
//        }
//    }

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

        // Total Amount (before discount)
        XWPFTableRow totalRow = table.createRow();
        while (totalRow.getTableCells().size() < 4) {
            totalRow.addNewTableCell();
        }
        totalRow.getCell(0).setText("Total Amount:");
//        totalRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getTotalAmount()));
        totalRow.getCell(3).setText(String.valueOf(receipt.getTotalAmount()));
        setColumnWidth(totalRow, 0, 4000, rowHeight);
        setColumnWidth(totalRow, 3, 3000, rowHeight);

        // Discount
        XWPFTableRow discountRow = table.createRow();
        while (discountRow.getTableCells().size() < 4) {
            discountRow.addNewTableCell();
        }
        discountRow.getCell(0).setText("Discount:");
//        discountRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getDiscount()));
        discountRow.getCell(3).setText(String.valueOf(receipt.getDiscount()));
        setColumnWidth(discountRow, 0, 4000, rowHeight);
        setColumnWidth(discountRow, 3, 3000, rowHeight);

        // Taxable Amount
        XWPFTableRow taxableRow = table.createRow();
        while (taxableRow.getTableCells().size() < 4) {
            taxableRow.addNewTableCell();
        }
        taxableRow.getCell(0).setText("Taxable Amount:");
//        taxableRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getTaxableAmount()));
        taxableRow.getCell(3).setText(String.valueOf(receipt.getTaxableAmount()));
        setColumnWidth(taxableRow, 0, 4000, rowHeight);
        setColumnWidth(taxableRow, 3, 3000, rowHeight);

        // GST Section
        if (receipt.getIgstRate() > 0) {
            // Inter-state (IGST)
            XWPFTableRow igstRow = table.createRow();
            while (igstRow.getTableCells().size() < 4) {
                igstRow.addNewTableCell();
            }
            igstRow.getCell(0).setText("IGST (" + receipt.getIgstRate() + "%):");
//            igstRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getIgstAmount()));
            igstRow.getCell(3).setText(String.valueOf(receipt.getIgstAmount()));
            setColumnWidth(igstRow, 0, 4000, rowHeight);
            setColumnWidth(igstRow, 3, 3000, rowHeight);
        } else {
            // Intra-state (CGST + SGST)
            XWPFTableRow cgstRow = table.createRow();
            while (cgstRow.getTableCells().size() < 4) {
                cgstRow.addNewTableCell();
            }
            cgstRow.getCell(0).setText("CGST (" + receipt.getCgstRate() + "%):");
//            cgstRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getCgstAmount()));
            cgstRow.getCell(3).setText(String.valueOf(receipt.getCgstAmount()));
            setColumnWidth(cgstRow, 0, 4000, rowHeight);
            setColumnWidth(cgstRow, 3, 3000, rowHeight);

            XWPFTableRow sgstRow = table.createRow();
            while (sgstRow.getTableCells().size() < 4) {
                sgstRow.addNewTableCell();
            }
            sgstRow.getCell(0).setText("SGST (" + receipt.getSgstRate() + "%):");
//            sgstRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getSgstAmount()));
            sgstRow.getCell(3).setText(String.valueOf(receipt.getSgstAmount()));
            setColumnWidth(sgstRow, 0, 4000, rowHeight);
            setColumnWidth(sgstRow, 3, 3000, rowHeight);
        }

        // Total With GST
        XWPFTableRow totalWithGstRow = table.createRow();
        while (totalWithGstRow.getTableCells().size() < 4) {
            totalWithGstRow.addNewTableCell();
        }
        totalWithGstRow.getCell(0).setText("Total Amount (Incl. GST):");
//        totalWithGstRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getTotalAmountWithGST()));
        totalWithGstRow.getCell(3).setText(String.valueOf(receipt.getTotalAmountWithGST()));
        setColumnWidth(totalWithGstRow, 0, 4000, rowHeight);
        setColumnWidth(totalWithGstRow, 3, 3000, rowHeight);

        addOneRowGap(table);

        // Advance Paid
        XWPFTableRow advanceRow = table.createRow();
        while (advanceRow.getTableCells().size() < 4) {
            advanceRow.addNewTableCell();
        }
        advanceRow.getCell(0).setText("Advance Paid:");
//        advanceRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getAdvancePaid()));
        advanceRow.getCell(3).setText(String.valueOf(receipt.getAdvancePaid()));
        setColumnWidth(advanceRow, 0, 4000, rowHeight);
        setColumnWidth(advanceRow, 3, 3000, rowHeight);

        // Balance Due
        XWPFTableRow balanceRow = table.createRow();
        while (balanceRow.getTableCells().size() < 4) {
            balanceRow.addNewTableCell();
        }
        balanceRow.getCell(0).setText("Balance Due:");
//        balanceRow.getCell(3).setText(TaxUtils.formatAmount(receipt.getBalanceDue()));
        balanceRow.getCell(3).setText(String.valueOf(receipt.getBalanceDue()));
        setColumnWidth(balanceRow, 0, 4000, rowHeight);
        setColumnWidth(balanceRow, 3, 3000, rowHeight);

        addOneRowGap(table);

        // Payment Method
        XWPFTableRow paymentMethodRow = table.createRow();
        while (paymentMethodRow.getTableCells().size() < 4) {
            paymentMethodRow.addNewTableCell();
        }
        paymentMethodRow.getCell(0).setText("Payment Method:");
        paymentMethodRow.getCell(3).setText(String.valueOf(receipt.getPaymentMethod()));
        setColumnWidth(paymentMethodRow, 0, 4000, rowHeight);
        setColumnWidth(paymentMethodRow, 3, 3000, rowHeight);

        // Payment Status
        XWPFTableRow paymentStatusRow = table.createRow();
        while (paymentStatusRow.getTableCells().size() < 4) {
            paymentStatusRow.addNewTableCell();
        }
        paymentStatusRow.getCell(0).setText("Payment Status:");
        paymentStatusRow.getCell(3).setText(String.valueOf(receipt.getPaymentStatus()));
        setColumnWidth(paymentStatusRow, 0, 4000, rowHeight);
        setColumnWidth(paymentStatusRow, 3, 3000, rowHeight);

        addOneRowGap(table);

        // Expected Delivery Date
        XWPFTableRow dueDateRow = table.createRow();
        while (dueDateRow.getTableCells().size() < 4) {
            dueDateRow.addNewTableCell();
        }
        dueDateRow.getCell(0).setText("Expected Delivery Date:");
        dueDateRow.getCell(3).setText(String.valueOf(receipt.getDeliveryDate()));
        setColumnWidth(dueDateRow, 0, 4000, rowHeight);
        setColumnWidth(dueDateRow, 3, 3000, rowHeight);

        // HSN Code
        XWPFTableRow hsnRow = table.createRow();
        while (hsnRow.getTableCells().size() < 4) {
            hsnRow.addNewTableCell();
        }
        hsnRow.getCell(0).setText("HSN Code:");
        hsnRow.getCell(3).setText(String.valueOf(receipt.getHsnCode()));
        setColumnWidth(hsnRow, 0, 4000, rowHeight);
        setColumnWidth(hsnRow, 3, 3000, rowHeight);
    }



//    // Footer rows with proper column width
//    private void addFooterRows(XWPFTable table, ReceiptAndInvoicePDF receipt) {
//        int rowHeight = 500;
//
//        XWPFTableRow footerRow = table.createRow();
//        while (footerRow.getTableCells().size() < 4) {
//            footerRow.addNewTableCell();
//        }
//        footerRow.getCell(0).setText("Total Amount:");
//        footerRow.getCell(3).setText(String.valueOf(receipt.getTotalAmount()));
//        setColumnWidth(footerRow, 0, 4000, rowHeight);
//        setColumnWidth(footerRow, 3, 3000, rowHeight);
//
//        XWPFTableRow advanceRow = table.createRow();
//        while (advanceRow.getTableCells().size() < 4) {
//            advanceRow.addNewTableCell();
//        }
//        advanceRow.getCell(0).setText("Advance Paid:");
//        advanceRow.getCell(3).setText(String.valueOf(receipt.getAdvancePaid()));
//        setColumnWidth(advanceRow, 0, 4000, rowHeight);
//        setColumnWidth(advanceRow, 3, 3000, rowHeight);
//
//        XWPFTableRow balanceRow = table.createRow();
//        while (balanceRow.getTableCells().size() < 4) {
//            balanceRow.addNewTableCell();
//        }
//        balanceRow.getCell(0).setText("Balance Due:");
//        balanceRow.getCell(3).setText(String.valueOf(receipt.getBalanceDue()));
//        setColumnWidth(balanceRow, 0, 4000, rowHeight);
//        setColumnWidth(balanceRow, 3, 3000, rowHeight);
//
//        addOneRowGap(table);
//
//        XWPFTableRow paymentMethodRow = table.createRow();
//        while (paymentMethodRow.getTableCells().size() < 4) {
//            paymentMethodRow.addNewTableCell();
//        }
//        paymentMethodRow.getCell(0).setText("Payment Method:");
//        paymentMethodRow.getCell(3).setText(String.valueOf(receipt.getPaymentMethod()));
//        setColumnWidth(paymentMethodRow, 0, 4000, rowHeight);
//        setColumnWidth(paymentMethodRow, 3, 3000, rowHeight);
//
//        XWPFTableRow paymentStatusRow = table.createRow();
//        while (paymentStatusRow.getTableCells().size() < 4) {
//            paymentStatusRow.addNewTableCell();
//        }
//        paymentStatusRow.getCell(0).setText("Payment Status:");
//        paymentStatusRow.getCell(3).setText(String.valueOf(receipt.getPaymentStatus()));
//        setColumnWidth(paymentStatusRow, 0, 4000, rowHeight);
//        setColumnWidth(paymentStatusRow, 3, 3000, rowHeight);
//
//        addOneRowGap(table);
//
//        XWPFTableRow dueDateRow = table.createRow();
//        while (dueDateRow.getTableCells().size() < 4) {
//            dueDateRow.addNewTableCell();
//        }
//        dueDateRow.getCell(0).setText("Expected Delivery Date:");
//        dueDateRow.getCell(3).setText(String.valueOf(receipt.getDeliveryDate()));
//        setColumnWidth(dueDateRow, 0, 4000, rowHeight);
//        setColumnWidth(dueDateRow, 3, 3000, rowHeight);
//
//        XWPFTableRow discountRow = table.createRow();
//        while (discountRow.getTableCells().size() < 4) {
//            discountRow.addNewTableCell();
//        }
//        discountRow.getCell(0).setText("Discount:");
//        discountRow.getCell(3).setText(String.valueOf(receipt.getDiscount()));
//        setColumnWidth(discountRow, 0, 4000, rowHeight);
//        setColumnWidth(discountRow, 3, 3000, rowHeight);
//
//        XWPFTableRow taxableRow = table.createRow();
//        while (taxableRow.getTableCells().size() < 4) {
//            taxableRow.addNewTableCell();
//        }
//        taxableRow.getCell(0).setText("Taxable Amount:");
//        taxableRow.getCell(3).setText(String.valueOf(receipt.getTotalAmount() - receipt.getDiscount()));
//        setColumnWidth(taxableRow, 0, 4000, rowHeight);
//        setColumnWidth(taxableRow, 3, 3000, rowHeight);
//
//        XWPFTableRow cgstRow = table.createRow();
//        while (cgstRow.getTableCells().size() < 4) {
//            cgstRow.addNewTableCell();
//        }
//        cgstRow.getCell(0).setText("CGST (" + receipt.getCgstRate() + "%):");
//        cgstRow.getCell(3).setText(String.valueOf(receipt.getCgstAmount()));
//        setColumnWidth(cgstRow, 0, 4000, rowHeight);
//        setColumnWidth(cgstRow, 3, 3000, rowHeight);
//
//        XWPFTableRow sgstRow = table.createRow();
//        while (sgstRow.getTableCells().size() < 4) {
//            sgstRow.addNewTableCell();
//        }
//        sgstRow.getCell(0).setText("SGST (" + receipt.getSgstRate() + "%):");
//        sgstRow.getCell(3).setText(String.valueOf(receipt.getSgstAmount()));
//        setColumnWidth(sgstRow, 0, 4000, rowHeight);
//        setColumnWidth(sgstRow, 3, 3000, rowHeight);
//
//        XWPFTableRow igstRow = table.createRow();
//        while (igstRow.getTableCells().size() < 4) {
//            igstRow.addNewTableCell();
//        }
//        igstRow.getCell(0).setText("IGST (" + receipt.getIgstRate() + "%):");
//        igstRow.getCell(3).setText(String.valueOf(receipt.getIgstAmount()));
//        setColumnWidth(igstRow, 0, 4000, rowHeight);
//        setColumnWidth(igstRow, 3, 3000, rowHeight);
//
//        XWPFTableRow totalWithGstRow = table.createRow();
//        while (totalWithGstRow.getTableCells().size() < 4) {
//            totalWithGstRow.addNewTableCell();
//        }
//        totalWithGstRow.getCell(0).setText("Total Amount (Incl. GST):");
//        totalWithGstRow.getCell(3).setText(String.valueOf(receipt.getTotalAmountWithGST()));
//        setColumnWidth(totalWithGstRow, 0, 4000, rowHeight);
//        setColumnWidth(totalWithGstRow, 3, 3000, rowHeight);
//
//
//
//
//    }

}
