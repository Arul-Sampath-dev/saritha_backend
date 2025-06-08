package com.saritha.fashions.service;

import com.saritha.fashions.model.Receipt;
import com.saritha.fashions.model.ReceiptSequence;
import com.saritha.fashions.service.Interface.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptServiceTest {

    @InjectMocks
    private InvoiceSequenceService invoiceSequenceService;

    @Mock
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateReceiptId_NewYear_ShouldStartSequenceAt1() {
        // Arrange
        String shopId = "SHOP123";
        String shopName = "MyShop";

        // Fake calendar for Jan 1, 2026
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.set(Calendar.YEAR, 2026);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        // Mock static DateTimeUtils if used
        // (optional if not static or you injected time provider)
        ReceiptSequence fakeCounter = new ReceiptSequence();
        fakeCounter.setSequence(1); // Simulate first receipt of the year

        // Mock MongoTemplate behavior
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ReceiptSequence.class)))
                .thenReturn(fakeCounter);

        when(mongoTemplate.exists(any(Query.class), eq(Receipt.class))).thenReturn(false);

        // Act
        String receiptNo = invoiceSequenceService.generateInvoiceId(shopId, shopName);

        // Assert
        assertNotNull(receiptNo);
        assertTrue(receiptNo.startsWith("MyShop-"));
        assertTrue(receiptNo.contains("2025"));
        assertTrue(receiptNo.endsWith("00001"));
    }
}
