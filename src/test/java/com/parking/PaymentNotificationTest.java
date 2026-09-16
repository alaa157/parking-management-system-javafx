package com.parking;

import com.parking.enums.NotificationType;
import com.parking.enums.PaymentStatus;
import com.parking.model.CashPayment;
import com.parking.model.NotificationPreference;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.services.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final-review fix: every successful payment through
 * {@link com.parking.services.PaymentService} publishes exactly one
 * {@code PAYMENT_SUCCESS} backbone notification, unless the recipient
 * disabled that type (then no row is stored).
 */
class PaymentNotificationTest extends TestFixtures {

    @Test
    void successfulPaymentPublishesOnePaymentSuccessNotification() throws Exception {
        NotificationService notifications = new NotificationService(persistence);
        paymentService.setNotificationService(notifications);

        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        ticket.setEntryTime(LocalDateTime.now().minusHours(2));
        parkingService.vehicleExit(ticket);
        Payment payment = paymentService.processPayment(ticket,
                new CashPayment("PAY-NOTIF-1", ticket.getTicketId(), testCustomer.getUserId(), 10.0, 100.0, "Tester"));

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        List<NotificationService.Notification> unread = notifications.listUnread(testCustomer);
        assertEquals(1, unread.size());
        assertEquals(NotificationType.PAYMENT_SUCCESS, unread.get(0).type());
    }

    @Test
    void disabledPaymentSuccessTypeProducesNoRows() throws Exception {
        NotificationService notifications = new NotificationService(persistence);
        paymentService.setNotificationService(notifications);
        Set<NotificationType> enabled = EnumSet.allOf(NotificationType.class);
        enabled.remove(NotificationType.PAYMENT_SUCCESS);
        notifications.savePreference(new NotificationPreference(testCustomer.getUserId(), enabled, "IMMEDIATE", false));

        Ticket ticket = parkingService.vehicleEntry(testVehicle);
        ticket.setEntryTime(LocalDateTime.now().minusHours(2));
        parkingService.vehicleExit(ticket);
        Payment payment = paymentService.processPayment(ticket,
                new CashPayment("PAY-NOTIF-2", ticket.getTicketId(), testCustomer.getUserId(), 10.0, 100.0, "Tester"));

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        assertTrue(notifications.listUnread(testCustomer).isEmpty());
    }
}
