package pharma;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public final class DeliveryStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pharma-delivery-test-");
        Path file = directory.resolve("deliveries.csv");
        DeliveryStore deliveries = new DeliveryStore(file);
        InventoryStore.SaleItem sale = new InventoryStore.SaleItem(
                "SALE-1", LocalDateTime.now(), "MED-1", "Paracetamol", new BigDecimal("12.50"), 2);
        DeliveryStore.DeliveryOrder firstOrder = deliveries.createOrder(
                "customer.one", "Asha Kumar", "9876543210", List.of(sale));
        DeliveryStore.DeliveryOrder secondOrder = deliveries.createOrder("customer.one", List.of(
                new InventoryStore.SaleItem("SALE-1B", LocalDateTime.now(), "MED-2",
                        "Vitamin C", new BigDecimal("8.00"), 3)));
        deliveries.createOrder("customer.two", List.of(
                new InventoryStore.SaleItem("SALE-2", LocalDateTime.now(), "MED-1",
                        "Paracetamol", new BigDecimal("12.50"), 1)));

        if (deliveries.getOrdersForCustomer("CUSTOMER.ONE").size() != 2
                || !deliveries.getOrdersForCustomer("customer.one").stream()
                .anyMatch(order -> order.deliveryId().equals(firstOrder.deliveryId()))
                || !deliveries.getOrdersForCustomer("customer.one").stream()
                .anyMatch(order -> order.deliveryId().equals(secondOrder.deliveryId()))
                || deliveries.getOrdersForCustomer("customer.two").size() != 1
                || !deliveries.getOrdersForCustomer("customer.one").getFirst()
                .customerUsername().equals("customer.one")) {
            throw new AssertionError("Multiple customer orders were not stored or scoped to their owner.");
        }

        deliveries.updateStatus(firstOrder.deliveryId(), DeliveryStore.Status.IN_TRANSIT);
        deliveries.updateStatus(firstOrder.deliveryId(), DeliveryStore.Status.DELIVERED);
        DeliveryStore reopened = new DeliveryStore(file);
        if (reopened.getOrdersForCustomer("customer.one").stream()
                .filter(order -> order.deliveryId().equals(firstOrder.deliveryId()))
                .findFirst().orElseThrow().status() != DeliveryStore.Status.DELIVERED
                || reopened.getOrdersForCustomer("customer.one").stream()
                .filter(order -> order.deliveryId().equals(secondOrder.deliveryId()))
                .findFirst().orElseThrow().status() != DeliveryStore.Status.PLACED
                || reopened.getOrdersForCustomer("customer.two").getFirst().status() != DeliveryStore.Status.PLACED
                || reopened.getAllOrders().size() != 3
                || !reopened.getOrdersForCustomer("customer.one").stream()
                .filter(order -> order.deliveryId().equals(firstOrder.deliveryId()))
                .findFirst().orElseThrow().customerName().equals("Asha Kumar")
                || !reopened.getOrdersForCustomer("customer.one").stream()
                .filter(order -> order.deliveryId().equals(firstOrder.deliveryId()))
                .findFirst().orElseThrow().customerMobile().equals("9876543210")) {
            throw new AssertionError("Multiple order histories, statuses, or ownership did not persist.");
        }
        try {
            reopened.updateStatus(firstOrder.deliveryId(), DeliveryStore.Status.IN_TRANSIT);
            throw new AssertionError("Delivery status was allowed to move backwards.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Placed")) {
                throw expected;
            }
        }
        String saved = Files.readString(file);
        if (!saved.contains("customer.one") || !saved.contains("customer.two")) {
            throw new AssertionError("Delivery CSV did not retain both users' orders.");
        }
        Path legacyFile = directory.resolve("legacy-deliveries.csv");
        Files.writeString(legacyFile,
                "deliveryId,customerUsername,saleId,items,total,placedAt,status\n"
                        + "\"OLD-1\",\"legacy.customer\",\"SALE-OLD\",\"Medicine x 1\",\"12.50\","
                        + "\"" + LocalDateTime.now() + "\",\"PLACED\"\n");
        DeliveryStore legacy = new DeliveryStore(legacyFile);
        if (!legacy.getAllOrders().getFirst().customerName().isEmpty()
                || !legacy.getAllOrders().getFirst().customerMobile().isEmpty()) {
            throw new AssertionError("Legacy delivery records did not load with empty customer details.");
        }
        System.out.println("Customer delivery ownership smoke test passed.");
    }
}
