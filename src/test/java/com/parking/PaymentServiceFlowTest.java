package com.parking;

import com.parking.enums.*;
import com.parking.exceptions.*;
import com.parking.model.*;
import com.parking.services.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PaymentServiceFlowTest extends TestFixtures {

    @Test
    void testBasePaymentCalculation() {
        Payment payment = new Payment(
                "PAY-001",
                "TICKET-001",
                "CUSTOMER-001",
                100.0,
                "TEST"
        );

        payment.setTaxAmount(10.0);

        double finalAmount =
                payment.calculateFinalAmount();

        assertEquals(
                110.0,
                finalAmount,
                0.001
        );

        assertEquals(
                110.0,
                payment.getFinalAmount(),
                0.001
        );
    }


    @Test
    void testBasePaymentProcessing() {
        Payment payment = new Payment(
                "PAY-002",
                "TICKET-002",
                "CUSTOMER-002",
                100.0,
                "TEST"
        );

        payment.setTaxAmount(10.0);

        assertTrue(
                payment.processPayment()
        );

        assertEquals(
                PaymentStatus.COMPLETED,
                payment.getStatus()
        );

        assertNotNull(
                payment.getTransactionId()
        );
    }


    @Test
    void testCashPaymentValidation() {
        CashPayment cashPayment =
                new CashPayment(
                        "PAY-CASH",
                        "TICKET-CASH",
                        testCustomer.getUserId(),
                        100.0,
                        110.0,
                        "Tester"
                );

        cashPayment.setFinalAmount(100.0);

        assertTrue(
                cashPayment.validatePaymentDetails()
        );

        assertTrue(
                cashPayment.isCashSufficient()
        );

        assertEquals(
                10.0,
                cashPayment.calculateChange(),
                0.001
        );

        assertFalse(
                cashPayment.isExactChange()
        );
    }


    @Test
    void testCashPaymentExactChange() {
        CashPayment cashPayment =
                new CashPayment(
                        "PAY-CASH-2",
                        "TICKET-CASH-2",
                        testCustomer.getUserId(),
                        50.0,
                        50.0,
                        "Tester"
                );

        cashPayment.setFinalAmount(50.0);

        assertTrue(
                cashPayment.isExactChange()
        );

        assertEquals(
                0.0,
                cashPayment.calculateChange(),
                0.001
        );
    }


    @Test
    void testCashPaymentInsufficientCash() {
        CashPayment cashPayment =
                new CashPayment(
                        "PAY-CASH-3",
                        "TICKET-CASH-3",
                        testCustomer.getUserId(),
                        100.0,
                        50.0,
                        "Tester"
                );

        cashPayment.setFinalAmount(100.0);

        assertFalse(
                cashPayment.validatePaymentDetails()
        );

        assertThrows(
                PaymentFailedException.class,
                () -> cashPayment.processPayment(100.0)
        );
    }


    @Test
    void testCashPaymentProcessing() throws Exception {
        CashPayment cashPayment =
                new CashPayment(
                        "PAY-CASH-4",
                        "TICKET-CASH-4",
                        testCustomer.getUserId(),
                        50.0,
                        60.0,
                        "Tester"
                );

        cashPayment.setFinalAmount(50.0);

        assertTrue(
                cashPayment.processPayment(50.0)
        );

        assertEquals(
                PaymentStatus.COMPLETED,
                cashPayment.getStatus()
        );

        assertNotNull(
                cashPayment.getTransactionId()
        );

        assertEquals(
                10.0,
                cashPayment.getChangeAmount(),
                0.001
        );
    }


    @Test
    void testValidCardLuhn() {
        CardPayment cardPayment =
                new CardPayment(
                        "PAY-CARD-LUHN",
                        "TICKET-CARD-LUHN",
                        testCustomer.getUserId(),
                        100.0,
                        "4111111111111111",
                        "Test Customer",
                        "12/30",
                        "123"
                );

        assertTrue(
                cardPayment.validatePaymentDetails()
        );
    }


    @Test
    void testInvalidCardLuhn() {
        CardPayment cardPayment =
                new CardPayment(
                        "PAY-CARD-LUHN-BAD",
                        "TICKET-CARD-LUHN-BAD",
                        testCustomer.getUserId(),
                        100.0,
                        "4111111111111112",
                        "Test Customer",
                        "12/30",
                        "123"
                );

        assertFalse(
                cardPayment.validatePaymentDetails()
        );
    }


    @Test
    void testCardPaymentValidation() {
        CardPayment cardPayment =
                new CardPayment(
                        "PAY-CARD",
                        "TICKET-CARD",
                        testCustomer.getUserId(),
                        100.0,
                        "4111111111111111",
                        "Test Customer",
                        "12/30",
                        "123"
                );

        assertTrue(
                cardPayment.validatePaymentDetails()
        );

        assertEquals(
                "VISA",
                cardPayment.detectCardType()
        );
    }


    @Test
    void testInvalidCardPayment() {
        CardPayment cardPayment =
                new CardPayment(
                        "PAY-CARD-INVALID",
                        "TICKET-CARD",
                        testCustomer.getUserId(),
                        100.0,
                        "123",
                        "Test Customer",
                        "12/30",
                        "1"
                );

        assertFalse(
                cardPayment.validatePaymentDetails()
        );

        assertThrows(
                PaymentFailedException.class,
                () -> cardPayment.processPayment(100.0)
        );
    }


    @Test
    void testCardMasking() {
        CardPayment cardPayment =
                new CardPayment(
                        "PAY-CARD-MASK",
                        "TICKET-CARD",
                        testCustomer.getUserId(),
                        100.0,
                        "4111111111111111",
                        "Test Customer",
                        "12/30",
                        "123"
                );

        assertEquals(
                "**** **** **** 1111",
                cardPayment.getMaskedCardNumber()
        );
    }


    @Test
    void testCardTypes() {
        CardPayment visa =
                new CardPayment(
                        "P1", "T1", "C1", 10,
                        "4111111111111111",
                        "Test",
                        "12/30",
                        "123"
                );

        CardPayment mastercard =
                new CardPayment(
                        "P2", "T2", "C1", 10,
                        "5111111111111111",
                        "Test",
                        "12/30",
                        "123"
                );

        CardPayment amex =
                new CardPayment(
                        "P3", "T3", "C1", 10,
                        "3111111111111111",
                        "Test",
                        "12/30",
                        "123"
                );

        assertEquals("VISA", visa.detectCardType());
        assertEquals("MASTERCARD", mastercard.detectCardType());
        assertEquals("AMEX", amex.detectCardType());
    }


    @Test
    void testWalletPayment() throws Exception {
        testCustomer.setWalletBalance(200.0);

        WalletPayment walletPayment =
                new WalletPayment(
                        "PAY-WALLET",
                        "TICKET-WALLET",
                        testCustomer.getUserId(),
                        100.0,
                        "WALLET-001",
                        "Internal",
                        testCustomer.getEmail(),
                        testCustomer
                );

        walletPayment.setFinalAmount(100.0);

        assertTrue(
                walletPayment.validatePaymentDetails()
        );

        assertTrue(
                walletPayment.processPayment(100.0)
        );

        assertEquals(
                PaymentStatus.COMPLETED,
                walletPayment.getStatus()
        );

        assertEquals(
                100.0,
                testCustomer.getWalletBalance(),
                0.001
        );
    }


    @Test
    void testWalletInsufficientBalance() {
        testCustomer.setWalletBalance(20.0);

        WalletPayment walletPayment =
                new WalletPayment(
                        "PAY-WALLET-2",
                        "TICKET-WALLET-2",
                        testCustomer.getUserId(),
                        100.0,
                        "WALLET-002",
                        "Internal",
                        testCustomer.getEmail(),
                        testCustomer
                );

        walletPayment.setFinalAmount(100.0);

        assertThrows(
                PaymentFailedException.class,
                () -> walletPayment.processPayment(100.0)
        );
    }


    @Test
    void testWalletDeactivateActivate() {
        WalletPayment walletPayment =
                new WalletPayment(
                        "PAY-WALLET-3",
                        "TICKET-WALLET-3",
                        testCustomer.getUserId(),
                        50.0,
                        "WALLET-003",
                        "Internal",
                        testCustomer.getEmail(),
                        testCustomer
                );

        walletPayment.deactivateWallet();

        assertFalse(
                walletPayment.isWalletActive()
        );

        walletPayment.activateWallet();

        assertTrue(
                walletPayment.isWalletActive()
        );
    }


    @Test
    void testCalculateParkingFee() {
        ParkingSpot spot =
                garage.getSpotById("S-1");

        LocalDateTime entry =
                LocalDateTime.of(2026, 1, 1, 10, 0);

        LocalDateTime exit =
                LocalDateTime.of(2026, 1, 1, 11, 30);

        double fee =
                paymentService.calculateParkingFee(
                        entry,
                        exit,
                        spot
                );

        // 90 minutes -> 2 hours
        // Standard rate = $5
        assertEquals(
                10.0,
                fee,
                0.001
        );
    }


    @Test
    void testCalculateMinimumParkingFee() {
        ParkingSpot spot =
                garage.getSpotById("S-1");

        LocalDateTime entry =
                LocalDateTime.of(2026, 1, 1, 10, 0);

        LocalDateTime exit =
                LocalDateTime.of(2026, 1, 1, 10, 0);

        double fee =
                paymentService.calculateParkingFee(
                        entry,
                        exit,
                        spot
                );

        assertEquals(
                5.0,
                fee,
                0.001
        );
    }


    @Test
    void testPaymentServiceCardPayment() throws Exception {
        // Enter vehicle
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        // Make the ticket represent at least 1 hour of parking
        ticket.setEntryTime(
                LocalDateTime.now().minusHours(2)
        );
        // Exit vehicle
        parkingService.vehicleExit(ticket);
    
        // Use a valid card
        CardPayment card = new CardPayment(
                "PAY-SERVICE-CARD",
                ticket.getTicketId(),
                testCustomer.getUserId(),
                10.0,
                "4111111111111111",
                "Test Customer",
                "12/30",
                "123"
        );
        Payment payment = paymentService.processPayment(
                ticket,
                card
        );
        assertNotNull(payment);
        assertEquals(
                PaymentStatus.COMPLETED,
                payment.getStatus()
        );
        assertTrue(
                payment.getFinalAmount() > 0
        );
        assertTrue(
                ticket.isPaid()
        );
    }


    @Test
    void testPaymentServiceCashPayment() throws Exception {
        // Enter vehicle
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        // Simulate 2 hours of parking
        ticket.setEntryTime(
                LocalDateTime.now().minusHours(2)
        );
        // Exit vehicle
        parkingService.vehicleExit(ticket);
        // Pay more than enough cash
        CashPayment cash = new CashPayment(
                "PAY-SERVICE-CASH",
                ticket.getTicketId(),
                testCustomer.getUserId(),
                10.0,
                100.0,
                "Tester"
        );
        Payment payment = paymentService.processPayment(
                ticket,
                cash
        );
        assertNotNull(payment);
        assertEquals(
                PaymentStatus.COMPLETED,
                payment.getStatus()
        );
        assertTrue(
                ticket.isPaid()
        );
        assertTrue(
                cash.getChangeAmount() >= 0
        );
    }


    @Test
    void testPaymentServiceRejectsNullPayment() {
        assertThrows(
                PaymentFailedException.class,
                () -> paymentService.processPayment(
                        null,
                        null
                )
        );
    }


    @Test
    void testPaymentServiceRejectsDoublePayment() throws Exception {
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        parkingService.vehicleExit(ticket);

        CashPayment firstPayment =
                new CashPayment(
                        "PAY-DOUBLE-1",
                        ticket.getTicketId(),
                        testCustomer.getUserId(),
                        10.0,
                        100.0,
                        "Tester"
                );

        paymentService.processPayment(ticket, firstPayment);

        CashPayment secondPayment =
                new CashPayment(
                        "PAY-DOUBLE-2",
                        ticket.getTicketId(),
                        testCustomer.getUserId(),
                        10.0,
                        100.0,
                        "Tester"
                );

        assertThrows(
                PaymentFailedException.class,
                () -> paymentService.processPayment(ticket, secondPayment)
        );
    }


    @Test
    void testGenerateReceipt() {
        Payment payment = new Payment(
                "PAY-RECEIPT",
                "TICKET-RECEIPT",
                testCustomer.getUserId(),
                50.0,
                "Cash"
        );

        payment.setTaxAmount(5.0);
        payment.setFinalAmount(55.0);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId("TXN-123");

        String receipt =
                paymentService.generateReceipt(payment);

        assertNotNull(receipt);

        assertTrue(
                receipt.contains("PARKING PAYMENT RECEIPT")
        );

        assertTrue(
                receipt.contains("PAY-RECEIPT")
        );

        assertTrue(
                receipt.contains("TXN-123")
        );

        assertTrue(
                receipt.contains("55.00")
        );
    }


    @Test
    void testPaymentLookup() {
        Payment payment = new Payment(
                "PAY-LOOKUP",
                "TICKET-LOOKUP",
                testCustomer.getUserId(),
                20.0,
                "Cash"
        );

        // PaymentService only knows payments processed through it.
        // Therefore first verify the initial lookup behavior.
        assertNull(
                paymentService.getPaymentById(
                        payment.getPaymentId()
                )
        );
    }


    @Test
    void testTotalRevenue() throws Exception {
    assertEquals(
            0.0,
            paymentService.getTotalRevenue(),
            0.001
    );
    // Enter vehicle
    Ticket ticket = parkingService.vehicleEntry(testVehicle);
    // Simulate 2 hours of parking
    ticket.setEntryTime(
            LocalDateTime.now().minusHours(2)
    );
    // Exit vehicle
    parkingService.vehicleExit(ticket);
    // Pay with sufficient cash
    CashPayment cash = new CashPayment(
            "PAY-REVENUE",
            ticket.getTicketId(),
            testCustomer.getUserId(),
            10.0,
            100.0,
            "Tester"
    );
    Payment payment = paymentService.processPayment(
            ticket,
            cash
    );
    assertNotNull(payment);
    assertEquals(
            PaymentStatus.COMPLETED,
            payment.getStatus()
    );
    assertEquals(
            payment.getFinalAmount(),
            paymentService.getTotalRevenue(),
            0.001
    );
    }


    @Test
    void testBasePaymentRefund() {
        Payment payment = new Payment(
                "PAY-REFUND",
                "TICKET-REFUND",
                testCustomer.getUserId(),
                50.0,
                "Cash"
        );

        payment.setTaxAmount(5.0);
        payment.setFinalAmount(55.0);
        payment.setStatus(PaymentStatus.COMPLETED);

        assertTrue(
                payment.canBeRefunded()
        );

        assertTrue(
                payment.refundPayment()
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );
    }


    @Test
    void testCashPaymentRefund() throws Exception {
        CashPayment cash =
                new CashPayment(
                        "PAY-REFUND-CASH",
                        "TICKET-REFUND-CASH",
                        testCustomer.getUserId(),
                        50.0,
                        60.0,
                        "Tester"
                );

        cash.setFinalAmount(50.0);

        cash.processPayment(50.0);

        assertEquals(
                PaymentStatus.COMPLETED,
                cash.getStatus()
        );

        assertTrue(
                cash.processRefund(50.0)
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                cash.getStatus()
        );
    }


    @Test
    void testCashRefundDoesNotChangeWallet() throws Exception {
        CashPayment cash =
                new CashPayment(
                        "PAY-REFUND-CASH-WALLET",
                        "TICKET-REFUND-CASH-WALLET",
                        testCustomer.getUserId(),
                        50.0,
                        50.0,
                        "Tester"
                );

        cash.setFinalAmount(50.0);
        cash.processPayment(50.0);

        double walletBefore = testCustomer.getWalletBalance();

        paymentService.refundPayment(cash);

        assertEquals(
                walletBefore,
                testCustomer.getWalletBalance(),
                0.001
        );
    }


    @Test
    void testWalletRefundIncreasesWalletByFinalAmount() throws Exception {
        testCustomer.setWalletBalance(100.0);

        WalletPayment wallet =
                new WalletPayment(
                        "PAY-REFUND-WALLET",
                        "TICKET-REFUND-WALLET",
                        testCustomer.getUserId(),
                        50.0,
                        "WALLET-REFUND",
                        "Internal",
                        testCustomer.getEmail(),
                        testCustomer
                );

        wallet.setFinalAmount(55.0);
        wallet.setTaxAmount(5.0);
        wallet.setAmount(50.0);
        wallet.processPayment(50.0);
        wallet.setFinalAmount(55.0);

        double walletBefore = testCustomer.getWalletBalance();

        wallet.processRefund(55.0);

        assertEquals(
                walletBefore + 55.0,
                testCustomer.getWalletBalance(),
                0.001
        );
    }


    @Test
    void testRefundAfter15DaysFails() throws Exception {
        CashPayment cash =
                new CashPayment(
                        "PAY-REFUND-15D",
                        "TICKET-REFUND-15D",
                        testCustomer.getUserId(),
                        50.0,
                        50.0,
                        "Tester"
                );

        cash.setFinalAmount(50.0);
        cash.processPayment(50.0);
        cash.setPaymentTime(LocalDateTime.now().minusDays(15));

        assertThrows(
                PaymentFailedException.class,
                () -> cash.processRefund(50.0)
        );
    }


    @Test
    void testFailedPaymentLeavesVehicleAndSpotOccupied() throws Exception {
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        parkingService.vehicleExit(ticket);

        CashPayment insufficient = new CashPayment(
                "PAY-FAIL-LIFECYCLE", ticket.getTicketId(), testCustomer.getUserId(),
                10.0, 0.01, "Tester");

        assertThrows(
                PaymentFailedException.class,
                () -> paymentService.processPayment(ticket, insufficient)
        );
        assertEquals(TicketStatus.AWAITING_PAYMENT, ticket.getStatus());
        assertTrue(testVehicle.isParked());
        assertEquals(testVehicle.getVehicleId(),
                garage.getSpotById(ticket.getParkingSpotId()).getVehicleId());
    }


    @Test
    void testRepeatedExitAndReleaseDoNotDuplicateEffects() throws Exception {
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        double firstAmount = parkingService.vehicleExit(ticket);
        double secondAmount = parkingService.vehicleExit(ticket);

        assertEquals(firstAmount, secondAmount, 0.001);
        assertEquals(TicketStatus.AWAITING_PAYMENT, ticket.getStatus());
        assertTrue(testVehicle.isParked());

        CashPayment payment = new CashPayment(
                "PAY-IDEMPOTENT-LIFECYCLE", ticket.getTicketId(), testCustomer.getUserId(),
                firstAmount, firstAmount + 10.0, "Tester");
        paymentService.processPayment(ticket, payment);
        parkingService.releaseAfterPayment(ticket);
        parkingService.releaseAfterPayment(ticket);

        assertEquals(TicketStatus.CLOSED, ticket.getStatus());
        assertFalse(testVehicle.isParked());
    }


    @Test
    void testDuplicatePaymentIdIsRejectedBeforeCharging() throws Exception {
        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        parkingService.vehicleExit(ticket);
        CashPayment first = new CashPayment(
                "PAY-SAME-ID", ticket.getTicketId(), testCustomer.getUserId(), 10.0, 100.0, "Tester");
        paymentService.processPayment(ticket, first);

        CashPayment duplicate = new CashPayment(
                "PAY-SAME-ID", ticket.getTicketId(), testCustomer.getUserId(), 10.0, 100.0, "Tester");
        assertThrows(PaymentFailedException.class,
                () -> paymentService.processPayment(ticket, duplicate));
    }


}
