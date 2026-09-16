package com.parking;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.parking.enums.SpotStatus;
import com.parking.enums.SpotType;
import com.parking.enums.UserRole;
import com.parking.enums.VehicleType;
import com.parking.model.CashPayment;
import com.parking.model.Customer;
import com.parking.model.ParkingGarage;
import com.parking.model.ParkingSpot;
import com.parking.model.Payment;
import com.parking.model.Ticket;
import com.parking.model.User;
import com.parking.model.Vehicle;
import com.parking.persistence.PersistenceStore;
import com.parking.services.OperationsSnapshot;
import com.parking.services.OperationsSnapshotService;
import com.parking.services.ParkingService;
import com.parking.services.PaymentService;
import com.parking.services.TicketService;
import com.parking.services.UserService;

class OperationsSnapshotServiceTest {

    @TempDir
    Path tempDir;

    private static class Fixture {
        PersistenceStore store;
        ParkingGarage garage;
        UserService users;
        TicketService tickets;
        ParkingService parking;
        PaymentService payments;
        User admin;
        User attendant;
        Customer customerA;
        Customer customerB;
    }

    private Fixture fixtureWithSpots(String... spotIds) throws Exception {
        Fixture f = new Fixture();
        f.store = new PersistenceStore(tempDir.resolve("ops-" + System.nanoTime() + ".db"));
        f.garage = new ParkingGarage("G-OPS", "Ops Garage", "Addr", 1, 5.0);
        for (String spotId : spotIds) {
            f.garage.addParkingSpot(new ParkingSpot(spotId, SpotType.STANDARD, "Level 1", 5.0), 0);
        }
        f.garage.updateAvailability();
        f.users = new UserService(f.store);
        f.tickets = new TicketService(f.store);
        f.parking = new ParkingService(f.garage, f.tickets, f.users, f.store);
        f.payments = new PaymentService(f.garage, f.users, f.tickets, f.store, f.parking);
        f.admin = f.users.registerUser("opsadmin", "Admin@123!", "opsadmin@example.com", UserRole.ADMIN);
        f.attendant = f.users.registerUser("opsatt", "Attendant@123!", "opsatt@example.com", UserRole.ATTENDANT);
        f.customerA = (Customer) f.users.registerUser("opsca", "Customer@123!", "opsca@example.com", UserRole.CUSTOMER);
        f.customerB = (Customer) f.users.registerUser("opscb", "Customer@123!", "opscb@example.com", UserRole.CUSTOMER);
        return f;
    }

    private Vehicle vehicleFor(String vehicleId, String plate, String ownerId) {
        return new Vehicle(vehicleId, plate, VehicleType.CAR, "Make", "Model", "White", 2022, ownerId);
    }

    private Payment payTicket(Fixture f, Ticket ticket, String paymentId, String customerId) throws Exception {
        f.parking.vehicleExit(ticket);
        CashPayment cash = new CashPayment(paymentId, ticket.getTicketId(), customerId, 10.0, 100.0, "Tester");
        return f.payments.processPayment(ticket, cash);
    }

    @Test
    void adminSnapshotReflectsLiveSpotTicketAndRevenueTotals() throws Exception {
        Fixture f = fixtureWithSpots("S-1", "S-2", "S-3", "S-4");
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);

        Vehicle v1 = vehicleFor("VEH-A1", "PLATE-A1", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v1);
        Ticket active = f.parking.vehicleEntry(v1);

        f.garage.getSpotById("S-2").reserveSpot(f.customerB.getUserId());
        f.garage.getSpotById("S-3").setUnderMaintenance("test");
        f.garage.updateAvailability();

        Vehicle v2 = vehicleFor("VEH-A2", "PLATE-A2", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v2);
        Ticket toPay = f.parking.vehicleEntry(v2);
        Payment payment = payTicket(f, toPay, "PAY-OPS-1", f.customerA.getUserId());
        payment.setPaymentTime(now.minusHours(1));

        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);
        OperationsSnapshot snapshot = service.snapshot(f.admin, now);

        assertEquals(1, snapshot.available());
        assertEquals(1, snapshot.occupied());
        assertEquals(1, snapshot.reserved());
        assertEquals(1, snapshot.maintenance());
        assertEquals(25.0, snapshot.occupancyPercent(), 0.001);
        assertEquals(1, snapshot.activeTicketCount());
        assertEquals(payment.getFinalAmount(), snapshot.completedRevenue(), 0.001);
        assertNotNull(snapshot.occupancyHistory());
        assertEquals(10, snapshot.occupancyHistory().size());
        assertTrue(active.getTicketId() != null && !active.getTicketId().isBlank());
    }

    @Test
    void attendantSeesSameOperationalTotalsAsAdmin() throws Exception {
        Fixture f = fixtureWithSpots("S-1", "S-2");
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);

        Vehicle v = vehicleFor("VEH-ATT", "PLATE-ATT", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v);
        f.parking.vehicleEntry(v);

        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);
        OperationsSnapshot admin = service.snapshot(f.admin, now);
        OperationsSnapshot attendant = service.snapshot(f.attendant, now);

        assertEquals(admin.available(), attendant.available());
        assertEquals(admin.occupied(), attendant.occupied());
        assertEquals(admin.reserved(), attendant.reserved());
        assertEquals(admin.maintenance(), attendant.maintenance());
        assertEquals(admin.occupancyPercent(), attendant.occupancyPercent(), 0.001);
        assertEquals(admin.activeTicketCount(), attendant.activeTicketCount());
        assertEquals(admin.completedRevenue(), attendant.completedRevenue(), 0.001);
    }

    @Test
    void customerSnapshotIsScopedToOwnTicketsAndPayments() throws Exception {
        Fixture f = fixtureWithSpots("S-1", "S-2", "S-3", "S-4");
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);

        Vehicle va = vehicleFor("VEH-CA", "PLATE-CA", f.customerA.getUserId());
        f.garage.registerVehicleDetails(va);
        f.parking.vehicleEntry(va);

        Vehicle vb = vehicleFor("VEH-CB", "PLATE-CB", f.customerB.getUserId());
        f.garage.registerVehicleDetails(vb);
        Ticket tb = f.parking.vehicleEntry(vb);
        Payment pb = payTicket(f, tb, "PAY-OPS-B", f.customerB.getUserId());
        pb.setPaymentTime(now.minusHours(2));

        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);

        OperationsSnapshot scopedA = service.snapshot(f.customerA, now);
        assertEquals(1, scopedA.activeTicketCount());
        assertEquals(0.0, scopedA.completedRevenue(), 0.001);

        OperationsSnapshot scopedB = service.snapshot(f.customerB, now);
        assertEquals(0, scopedB.activeTicketCount());
        assertEquals(pb.getFinalAmount(), scopedB.completedRevenue(), 0.001);

        OperationsSnapshot admin = service.snapshot(f.admin, now);
        assertEquals(1, admin.activeTicketCount());
        assertEquals(pb.getFinalAmount(), admin.completedRevenue(), 0.001);

        // Garage-wide spot counts remain visible to customers.
        assertEquals(admin.available(), scopedA.available());
        assertEquals(admin.occupied(), scopedA.occupied());
    }

    @Test
    void zeroCapacityGarageReturnsZerosWithoutDivisionError() throws Exception {
        Fixture f = fixtureWithSpots();
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);

        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);
        OperationsSnapshot snapshot = service.snapshot(f.admin, now);

        assertEquals(0, snapshot.available());
        assertEquals(0, snapshot.occupied());
        assertEquals(0, snapshot.reserved());
        assertEquals(0, snapshot.maintenance());
        assertEquals(0.0, snapshot.occupancyPercent(), 0.001);
        assertEquals(0, snapshot.activeTicketCount());
        assertEquals(0.0, snapshot.completedRevenue(), 0.001);
        assertNotNull(snapshot.occupancyHistory());
        assertEquals(10, snapshot.occupancyHistory().size());
        assertTrue(snapshot.occupancyHistory().stream().allMatch(v -> v == 0));
    }

    @Test
    void revenueFilteredToCompletedPaymentsOnRequestedDate() throws Exception {
        Fixture f = fixtureWithSpots("S-1", "S-2", "S-3");
        LocalDateTime today = LocalDateTime.of(2026, 9, 13, 12, 0);
        LocalDateTime yesterday = today.minusDays(1);

        Vehicle v1 = vehicleFor("VEH-R1", "PLATE-R1", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v1);
        Payment todayPayment = payTicket(f, f.parking.vehicleEntry(v1), "PAY-TODAY", f.customerA.getUserId());
        todayPayment.setPaymentTime(today.minusHours(1));

        Vehicle v2 = vehicleFor("VEH-R2", "PLATE-R2", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v2);
        Payment yesterdayPayment = payTicket(f, f.parking.vehicleEntry(v2), "PAY-YEST", f.customerA.getUserId());
        yesterdayPayment.setPaymentTime(yesterday.minusHours(1));

        // A non-completed payment on the requested day must not count toward revenue.
        Payment pending = new Payment("PAY-PENDING", "T-PENDING", f.customerA.getUserId(), 50.0, "CASH");
        pending.setPaymentTime(today.minusHours(2));

        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);
        OperationsSnapshot todaySnapshot = service.snapshot(f.admin, today);
        assertEquals(todayPayment.getFinalAmount(), todaySnapshot.completedRevenue(), 0.001);

        OperationsSnapshot yesterdaySnapshot = service.snapshot(f.admin, yesterday);
        assertEquals(yesterdayPayment.getFinalAmount(), yesterdaySnapshot.completedRevenue(), 0.001);
    }

    @Test
    void refreshReflectsPersistedRealValuesInsteadOfCachedSimulation() throws Exception {
        Fixture f = fixtureWithSpots("S-1", "S-2");
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);
        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);

        OperationsSnapshot before = service.snapshot(f.admin, now);
        assertEquals(2, before.available());
        assertEquals(0, before.occupied());

        Vehicle v = vehicleFor("VEH-REF", "PLATE-REF", f.customerA.getUserId());
        f.garage.registerVehicleDetails(v);
        Ticket ticket = f.parking.vehicleEntry(v);

        OperationsSnapshot afterEntry = service.snapshot(f.admin, now);
        assertEquals(1, afterEntry.available());
        assertEquals(1, afterEntry.occupied());
        assertEquals(1, afterEntry.activeTicketCount());

        Payment payment = payTicket(f, ticket, "PAY-REF", f.customerA.getUserId());
        payment.setPaymentTime(now.minusMinutes(5));

        OperationsSnapshot afterPayment = service.snapshot(f.admin, now);
        assertEquals(0, afterPayment.activeTicketCount());
        assertEquals(payment.getFinalAmount(), afterPayment.completedRevenue(), 0.001);
        assertNotEquals(before.occupied(), afterEntry.occupied());
    }

    @Test
    void snapshotHistoryIsImmutable() throws Exception {
        Fixture f = fixtureWithSpots("S-1");
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);
        OperationsSnapshotService service = new OperationsSnapshotService(f.parking, f.tickets, f.payments);
        OperationsSnapshot snapshot = service.snapshot(f.admin, now);

        List<Integer> history = snapshot.occupancyHistory();
        assertThrows(UnsupportedOperationException.class, () -> history.add(99));
    }

    @Test
    void dashboardRefreshContractUsesLiveServicesWithoutSimulation() throws Exception {
        Path app = Path.of("src/main/java/com/parking/gui/ParkingApplication.java");
        Path dashboard = Path.of("src/main/java/com/parking/gui/DashboardView.java");
        String source = Files.readString(app) + Files.readString(dashboard);

        assertFalse(source.contains("liveRandom"), "simulation Random must be deleted");
        assertFalse(source.contains("simulatedRevenue"), "simulated revenue must be deleted");
        assertFalse(source.contains("simulatedActiveTickets"), "simulated tickets must be deleted");
        assertFalse(source.contains("simulatedOccupancy"), "simulated occupancy must be deleted");
        assertFalse(source.contains("injectLiveData"), "random inject path must be deleted");

        assertTrue(source.contains("Refresh"), "dashboard must offer Refresh");
        assertTrue(source.contains("Off"), "auto-refresh must offer Off");
        assertTrue(source.contains("5 seconds"), "auto-refresh must offer 5 seconds");
        assertTrue(source.contains("30 seconds"), "auto-refresh must offer 30 seconds");
        assertTrue(source.contains("BackgroundTaskRunner"), "refresh must use BackgroundTaskRunner");
        assertTrue(source.contains("refreshDashboard"), "refresh method must exist");
        assertTrue(source.contains("stopDashboardRefresh"), "refresh cancellation must exist");
        assertTrue(source.contains("operationsSnapshots.snapshot")
                || source.contains("operationsSnapshots .snapshot")
                || source.contains(".snapshot("), "dashboard must consume snapshot service");
    }

    @Test
    void autoRefreshCancelledOnNavigationAndShutdown() throws Exception {
        Path app = Path.of("src/main/java/com/parking/gui/ParkingApplication.java");
        String source = Files.readString(app);
        int navigateTo = source.indexOf("private void navigateTo(");
        assertTrue(navigateTo >= 0, "navigateTo must exist");
        assertTrue(source.indexOf("stopDashboardRefresh", navigateTo) >= 0,
                "navigation must stop auto-refresh");

        assertTrue(source.contains("shutdownResources"), "shutdown path must exist");
        int shutdown = source.indexOf("shutdownResources()");
        assertTrue(shutdown >= 0, "shutdownResources must be defined");
        String tail = source.substring(shutdown);
        assertTrue(tail.contains("stopDashboardRefresh"), "shutdown must stop auto-refresh");
        assertTrue(source.contains("SpotStatus.OCCUPIED") || source.contains("SpotStatus"),
                "snapshot counts must derive from real spot statuses");
    }
}
