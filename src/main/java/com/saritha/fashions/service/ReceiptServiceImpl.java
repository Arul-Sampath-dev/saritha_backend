package com.saritha.fashions.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.saritha.fashions.model.Customer;
import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.payload.Items;
import com.saritha.fashions.repository.CustomerRepository;
import com.saritha.fashions.repository.InvoiceRepository;
import com.saritha.fashions.repository.ReceiptRepository;
import com.saritha.fashions.service.Interface.InvoiceService;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    public Receipt generateReceipt(Receipt receipt) {
        try {
//            whatsAppService.sendInvoice(invoice); // Send WhatsApp message

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

                        customer.setCustomerId(generateUniqueCustomerId("CUS-"));
                        customer = customerRepository.save(customer);
                        receipt.setCustomer(customer);

                    }else {
                        receipt.setCustomer(customerExist);
                    }
                }

            }

            // Convert Invoice to Receipt using ModelMapper
            Receipt finalReceipt = modelMapper.map(receipt, Receipt.class);
            finalReceipt.setReceiptNo(generateUniqueCustomerId("REC-"));

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
        return receiptRepository.findByReceiptNo(id);
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
    public ResponseEntity<byte[]> generateReceiptAndInvoice(Receipt receipt , int num) {
        if (num == 1) {
            return generateReceiptDocument(receipt, 1);
        }else {
            return generateReceiptDocument(receipt, 2);
        }
    }

    public ResponseEntity<byte[]> generateReceiptDocument(Receipt receipt, int i) {
        System.out.println("Received Receipt" + receipt);

        try {
            // Load the template from resources folder
            String fileName, tempFileName;
            if (i == 1) {
                ReceiptServiceImpl selfProxyReceipt = applicationContext.getBean(ReceiptServiceImpl.class);
                Receipt  receipt1 = selfProxyReceipt.generateReceipt(receipt);

                fileName = "Receipt_SF.docx";
                tempFileName = "receipt";

                File templateFile = new ClassPathResource(fileName).getFile();
                File tempFile = File.createTempFile(tempFileName, ".docx");

                // Copy template to temp file
                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Open the Word document
                FileInputStream fis = new FileInputStream(tempFile);
                XWPFDocument document = new XWPFDocument(fis);
                fis.close();

                // Format dates
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                String orderDateFormatted = sdf.format(new Date());
                String expectedDeliveryDateFormatted = receipt1.getExpectedDeliveryDate() != null ? receipt1.getExpectedDeliveryDate() : "N/A";

                // Map data for placeholders
                Map<String, String> receiptData = new HashMap<>();
                receiptData.put("date", orderDateFormatted);
                receiptData.put("Invoice_No", orderDateFormatted);
                receiptData.put("ReceiptId", receipt1.getReceiptNo());

                receiptData.put("customer_name", receipt1.getCustomer().getName());
                receiptData.put("customer_phone", receipt1.getCustomer().getPhone());
                receiptData.put("shop_id", receipt1.getShopId());
                receiptData.put("order_date", String.valueOf(receipt1.getOrderDate()));
                receiptData.put("expected_delivery_date", expectedDeliveryDateFormatted);

                receiptData.put("total_amount", String.valueOf(receipt1.getTotalAmount()));
                receiptData.put("advance_paid", String.valueOf(receipt1.getAdvancePaid()));
                receiptData.put("balance_due", String.valueOf(receipt1.getBalanceDue()));

                receiptData.put("payment_method", receipt1.getPaymentMethod());
                receiptData.put("payment_status", receipt1.getPaymentStatus());
                receiptData.put("order_status", receipt1.getOrderStatus());
                receiptData.put("notes", receipt1.getNotes() != null ? receipt1.getNotes() : "Thank you for your order");

                // Replace placeholders in document
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

                // Handle items list replacement
                replaceItemList(document, receipt1.getItems(), receipt1);

                System.out.println("Till here its working 2");

                // Save document
                FileOutputStream fos = new FileOutputStream(tempFile);
                document.write(fos);
                fos.close();
                document.close();

                System.out.println("Till here its working 3");


                // Return file as response
                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", "Receipt.docx");

                return ResponseEntity.ok().headers(headers).body(fileContent);
            }else {
                Invoice  invoice = invoiceService.generateInvoiceByReceipt(receipt);
                fileName = "Invoice_SF.docx";
                tempFileName = "invoice";

                File templateFile = new ClassPathResource(fileName).getFile();
                File tempFile = File.createTempFile(tempFileName, ".docx");

                // Copy template to temp file
                Files.copy(templateFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Open the Word document
                FileInputStream fis = new FileInputStream(tempFile);
                XWPFDocument document = new XWPFDocument(fis);
                fis.close();

                // Format dates
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
                String orderDateFormatted = sdf.format(new Date());
                String expectedDeliveryDateFormatted = invoice.getReceipt().getExpectedDeliveryDate() != null ? invoice.getReceipt().getExpectedDeliveryDate() : "N/A";

                // Map data for placeholders
                Map<String, String> receiptData = new HashMap<>();
                receiptData.put("Date", orderDateFormatted);
                receiptData.put("Invoice_No", invoice.getInvoiceId());
                receiptData.put("Grand_Total", String.valueOf(invoice.getReceipt().getTotalAmount()));

                receiptData.put("Customer_Name", invoice.getCustomer().getName());
                receiptData.put("Customer_Contact", invoice.getCustomer().getPhone());
                receiptData.put("Customer_Id", invoice.getCustomer().getCustomerId());
                receiptData.put("order_date", String.valueOf(invoice.getReceipt().getOrderDate()));
                receiptData.put("Due_Date", expectedDeliveryDateFormatted);

                receiptData.put("total_amount", String.valueOf(invoice.getTotalAmount()));
                receiptData.put("advance_paid", String.valueOf(invoice.getReceipt().getAdvancePaid()));
                receiptData.put("balance_due", String.valueOf(invoice.getReceipt().getBalanceDue()));

                receiptData.put("Payment_Method", invoice.getReceipt().getPaymentMethod());
                receiptData.put("Payment_Status", invoice.getReceipt().getPaymentStatus());
                receiptData.put("order_status", invoice.getReceipt().getOrderStatus());
                receiptData.put("notes", invoice.getReceipt().getNotes() != null ? invoice.getReceipt().getNotes() : "Thank you for your order");

                // Replace placeholders in document
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

                // Handle items list replacement
                replaceItemList(document, invoice.getReceipt().getItems(), invoice.getReceipt());

                System.out.println("Till here its working 2");

                // Save document
                FileOutputStream fos = new FileOutputStream(tempFile);
                document.write(fos);
                fos.close();
                document.close();

                System.out.println("Till here its working 3");


                // Return file as response
                byte[] fileContent = Files.readAllBytes(tempFile.toPath());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", "Receipt.docx");

                return ResponseEntity.ok().headers(headers).body(fileContent);
            }


        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

//    public ResponseEntity<byte[]> generateReceiptDocument(Receipt receipt, int i) {
//        System.out.println("Received Receipt" + receipt);
//
//        ReceiptServiceImpl selfProxy = applicationContext.getBean(ReceiptServiceImpl.class);
//        Receipt receipt1 = selfProxy.generateReceipt(receipt);
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
//            headers.setContentDispositionFormData("attachment", "Receipt.docx");
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

    private void replaceItemList(XWPFDocument document, List<Items> items, Receipt receipt) {
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
    private void addFooterRows(XWPFTable table, Receipt receipt) {
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
        dueDateRow.getCell(3).setText(String.valueOf(receipt.getExpectedDeliveryDate()));
        setColumnWidth(dueDateRow, 0, 4000, rowHeight);
        setColumnWidth(dueDateRow, 3, 3000, rowHeight);


    }

}
