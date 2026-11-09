package pharma;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class OrderStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pharma-order-test-");
        InventoryStore inventory = new InventoryStore(directory.resolve("inventory.csv"));
        LocalDate today = LocalDate.now();
        MedicineBatch paracetamol = new MedicineBatch(
                "1", "Paracetamol", "Pain relief", "Maker", new BigDecimal("20.00"), 0, today.plusDays(3));
        inventory.addBatch(paracetamol);
        if (inventory.getOutOfStockMedicines().stream().noneMatch(batch -> batch.medicineId().equals("1"))) {
            throw new AssertionError("A zero-stock medicine should appear in the restock list.");
        }
        inventory.addBatch(paracetamol.withQuantity(5));

        MedicineBatch caseVariant = new MedicineBatch(
                "1", "PARACETAMOL", "PAIN RELIEF", "MAKER", new BigDecimal("20.00"), 2, today.plusDays(3));
        inventory.addBatch(caseVariant);
        if (inventory.getBatches().size() != 1 || inventory.getBatches().getFirst().quantity() != 7) {
            throw new AssertionError("Case-only medicine detail changes should restock the same medicine batch.");
        }
        if (inventory.getOutOfStockMedicines().stream().anyMatch(batch -> batch.medicineId().equals("1"))) {
            throw new AssertionError("A restocked medicine should leave the out-of-stock list.");
        }
        MedicineBatch newExpiry = new MedicineBatch(
                "1", "Paracetamol", "Pain relief", "Maker", new BigDecimal("20.00"), 8, today.plusDays(90));
        inventory.addBatch(newExpiry);
        if (inventory.getBatches().size() != 2 || !inventory.getBatches().getLast().medicineId().equals("1")) {
            throw new AssertionError("A different expiry should create another batch under the same medicine ID.");
        }
        MedicineBatch expired = new MedicineBatch(
                "EX-1", "Old stock", "Pain relief", "Maker", new BigDecimal("5.50"), 2, today.minusDays(1));
        inventory.addBatch(expired);
        if (inventory.expiredStockValue().compareTo(new BigDecimal("11.00")) != 0) {
            throw new AssertionError("Expired stock value estimate is incorrect.");
        }

        Path ordersFile = directory.resolve("orders.csv");
        OrderStore orders = new OrderStore(ordersFile);
        OrderStore.SupplierOrder order = orders.placeOrder(
                paracetamol, "Health, Care Supplies", 25, today.plusDays(7));
        OrderStore.SupplierOrder newMedicineOrder = orders.placeOrder(
                "MED-NEW", "New Medicine", "Cold & flu", "New Manufacturer",
                new BigDecimal("37.25"), "Other Supplier", 12, today.plusDays(9));
        List<OrderStore.SupplierOrder> multiMedicineOrder = orders.placeOrders(List.of(
                        new OrderStore.OrderRequest("MED-A", "Medicine A", "General", "Maker A",
                                new BigDecimal("10.00"), 4),
                        new OrderStore.OrderRequest("MED-B", "Medicine B", "General", "Maker B",
                                new BigDecimal("15.00"), 6)),
                "Bulk Supplier", today.plusDays(12));
        if (multiMedicineOrder.size() != 2
                || multiMedicineOrder.stream().anyMatch(line -> !line.supplier().equals("Bulk Supplier"))) {
            throw new AssertionError("A multi-medicine supplier order did not create all requested lines.");
        }
        orders.updateStatus(order.orderId(), OrderStore.Status.IN_TRANSIT);
        orders.updateStatus(newMedicineOrder.orderId(), OrderStore.Status.IN_TRANSIT);
        MedicineBatch receivedBatch = new MedicineBatch(
                "1", "Paracetamol", "Pain relief", "Maker", new BigDecimal("20.00"), 25, today.plusDays(365));
        if (!inventory.receiveOrderBatch(order.orderId(), receivedBatch)
                || inventory.receiveOrderBatch(order.orderId(), receivedBatch)) {
            throw new AssertionError("A supplier delivery must only be received once.");
        }
        InventoryStore reopenedInventory = new InventoryStore(directory.resolve("inventory.csv"));
        int stockedUnits = reopenedInventory.getBatches().stream()
                .filter(batch -> batch.medicineId().equals("1"))
                .mapToInt(MedicineBatch::quantity)
                .sum();
        if (stockedUnits != 40) {
            throw new AssertionError("Received supplier stock was duplicated or not persisted.");
        }
        orders.updateStatus(order.orderId(), OrderStore.Status.DELIVERED);
        MedicineBatch newMedicineBatch = new MedicineBatch(
                newMedicineOrder.medicineId(), newMedicineOrder.medicineName(),
                newMedicineOrder.category(), newMedicineOrder.manufacturer(),
                newMedicineOrder.unitPrice(), newMedicineOrder.quantity(), today.plusDays(200));
        inventory.receiveOrderBatch(newMedicineOrder.orderId(), newMedicineBatch);
        orders.updateStatus(newMedicineOrder.orderId(), OrderStore.Status.DELIVERED);
        OrderStore reloaded = new OrderStore(ordersFile);
        OrderStore.SupplierOrder saved = reloaded.getOrders().getFirst();
        if (saved.status() != OrderStore.Status.DELIVERED
                || !saved.supplier().equals("Health, Care Supplies")
                || saved.quantity() != 25) {
            throw new AssertionError("Order status or CSV persistence failed.");
        }
        OrderStore.SupplierOrder savedNewMedicine = reloaded.getOrders().stream()
                .filter(savedOrder -> savedOrder.orderId().equals(newMedicineOrder.orderId()))
                .findFirst().orElseThrow();
        if (!savedNewMedicine.category().equals("Cold & flu")
                || !savedNewMedicine.manufacturer().equals("New Manufacturer")
                || savedNewMedicine.unitPrice().compareTo(new BigDecimal("37.25")) != 0
                || inventory.getBatches().stream().noneMatch(batch -> batch.medicineId().equals("MED-NEW"))) {
            throw new AssertionError("New medicine details were not saved or stocked when received.");
        }
        if (reloaded.getOrders().stream().filter(line -> line.supplier().equals("Bulk Supplier")).count() != 2) {
            throw new AssertionError("Multi-medicine supplier order lines were not persisted.");
        }
        try {
            reloaded.updateStatus(order.orderId(), OrderStore.Status.IN_TRANSIT);
            throw new AssertionError("A delivered order was moved backwards.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Placed")) {
                throw expected;
            }
        }
        System.out.println("Supplier order and expired stock smoke test passed.");
    }
}
