package pharma;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

public final class InventoryDetailsSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pharma-details-test-");
        Path inventoryFile = directory.resolve("inventory.csv");
        InventoryStore inventory = new InventoryStore(inventoryFile);
        LocalDate today = LocalDate.now();
        MedicineBatch firstBatch = new MedicineBatch(
                "MED-1", "Medicine One", "General", "Maker", new BigDecimal("10.00"),
                5, today.plusDays(120), "Aisle 1 / Rack A");
        inventory.addBatch(firstBatch);
        inventory.addBatch(new MedicineBatch(
                "MED-1", "Medicine One", "General", "Maker", new BigDecimal("10.00"),
                7, today.plusDays(240), "Aisle 1 / Rack A"));
        inventory.checkout(Map.of("MED-1", 1));

        inventory.updateMedicineDetails("MED-1", new BigDecimal("12.50"), "Aisle 3 / Rack C");
        if (inventory.getBatches().stream().anyMatch(batch ->
                batch.price().compareTo(new BigDecimal("12.50")) != 0
                        || !batch.rackLocation().equals("Aisle 3 / Rack C"))) {
            throw new AssertionError("Price and rack updates must apply to every batch.");
        }
        if (inventory.getSales().getFirst().unitPrice().compareTo(new BigDecimal("10.00")) != 0) {
            throw new AssertionError("Changing the current price must not rewrite a completed sale.");
        }

        inventory.addBatch(new MedicineBatch(
                "MED-1", "Medicine One", "General", "Maker", new BigDecimal("10.00"),
                3, today.plusDays(360)));
        InventoryStore reopened = new InventoryStore(inventoryFile);
        MedicineBatch latestBatch = reopened.getBatches().getLast();
        if (latestBatch.price().compareTo(new BigDecimal("12.50")) != 0
                || !latestBatch.rackLocation().equals("Aisle 3 / Rack C")) {
            throw new AssertionError("Updated details must persist and be inherited by new batches.");
        }

        Path legacyFile = directory.resolve("legacy.csv");
        Files.writeString(legacyFile, String.join(System.lineSeparator(),
                "type,medicineId,name,category,manufacturer,price,quantity,expiryDate,saleId,soldAt",
                "\"B\",\"OLD-1\",\"Legacy Medicine\",\"General\",\"Maker\",\"4.25\",\"6\","
                        + "\"" + today.plusDays(90) + "\",\"\",\"\""));
        MedicineBatch legacyMedicine = new InventoryStore(legacyFile).getBatches().getFirst();
        if (!legacyMedicine.rackLocation().isEmpty()) {
            throw new AssertionError("Legacy inventory records should load with an empty rack location.");
        }
        System.out.println("Inventory price and rack details smoke test passed.");
    }
}
