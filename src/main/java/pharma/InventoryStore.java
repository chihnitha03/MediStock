package pharma;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InventoryStore {
    private static final String HEADER =
            "type,medicineId,name,category,manufacturer,price,quantity,expiryDate,saleId,soldAt,rackLocation";

    private final Path dataFile;
    private List<MedicineBatch> batches = new ArrayList<>();
    private List<SaleItem> sales = new ArrayList<>();
    private Set<String> receivedOrderIds = new HashSet<>();

    public InventoryStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "pharmacy-data.csv"));
    }

    InventoryStore(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public List<MedicineBatch> getBatches() {
        return List.copyOf(batches);
    }

    public List<MedicineBatch> getOutOfStockMedicines() {
        Map<String, MedicineBatch> medicines = new LinkedHashMap<>();
        for (MedicineBatch batch : batches) {
            medicines.putIfAbsent(batch.medicineId(), batch);
        }
        return medicines.values().stream()
                .filter(batch -> availableQuantity(batch.medicineId()) == 0)
                .sorted(Comparator.comparing(MedicineBatch::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<SaleItem> getSales() {
        return List.copyOf(sales);
    }

    public int availableQuantity(String medicineId) {
        LocalDate today = LocalDate.now();
        return batches.stream()
                .filter(batch -> batch.medicineId().equals(medicineId))
                .filter(batch -> !batch.expiryDate().isBefore(today))
                .mapToInt(MedicineBatch::quantity)
                .sum();
    }

    public MedicineBatch medicineDetails(String medicineId) {
        return batches.stream()
                .filter(batch -> batch.medicineId().equalsIgnoreCase(medicineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Medicine not found: " + medicineId));
    }

    public BigDecimal expiredStockValue() {
        LocalDate today = LocalDate.now();
        return batches.stream()
                .filter(batch -> batch.quantity() > 0 && batch.expiryDate().isBefore(today))
                .map(batch -> batch.price().multiply(BigDecimal.valueOf(batch.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addBatch(MedicineBatch newBatch) throws IOException {
        List<MedicineBatch> updated = new ArrayList<>(batches);
        addToBatches(updated, newBatch);
        save(updated, sales, receivedOrderIds);
        batches = updated;
    }

    public void updateMedicineDetails(String medicineId, BigDecimal price, String rackLocation) throws IOException {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero.");
        }
        if (medicineId == null || medicineId.isBlank()
                || batches.stream().noneMatch(batch -> batch.medicineId().equalsIgnoreCase(medicineId))) {
            throw new IllegalArgumentException("Medicine not found: " + medicineId);
        }
        List<MedicineBatch> updated = batches.stream()
                .map(batch -> batch.medicineId().equalsIgnoreCase(medicineId)
                        ? batch.withDetails(price, rackLocation)
                        : batch)
                .toList();
        save(updated, sales, receivedOrderIds);
        batches = new ArrayList<>(updated);
    }

    public boolean receiveOrderBatch(String orderId, MedicineBatch newBatch) throws IOException {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID is required.");
        }
        if (receivedOrderIds.contains(orderId)) {
            return false;
        }
        List<MedicineBatch> updatedBatches = new ArrayList<>(batches);
        addToBatches(updatedBatches, newBatch);
        Set<String> updatedReceivedOrders = new HashSet<>(receivedOrderIds);
        updatedReceivedOrders.add(orderId);
        save(updatedBatches, sales, updatedReceivedOrders);
        batches = updatedBatches;
        receivedOrderIds = updatedReceivedOrders;
        return true;
    }

    public List<SaleItem> checkout(Map<String, Integer> requestedQuantities) throws IOException {
        if (requestedQuantities.isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the bill.");
        }

        LocalDate today = LocalDate.now();
        List<MedicineBatch> updatedBatches = new ArrayList<>(batches);
        List<SaleItem> newSales = new ArrayList<>(sales);
        String saleId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime soldAt = LocalDateTime.now();
        List<SaleItem> receipt = new ArrayList<>();

        for (Map.Entry<String, Integer> requested : requestedQuantities.entrySet()) {
            int quantity = requested.getValue();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Purchase quantities must be greater than zero.");
            }
            int available = availableQuantity(requested.getKey());
            if (quantity > available) {
                throw new IllegalArgumentException(
                        requested.getKey() + " has only " + available + " non-expired units available.");
            }

            MedicineBatch product = batches.stream()
                    .filter(batch -> batch.medicineId().equals(requested.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Medicine not found: " + requested.getKey()));
            int remaining = quantity;
            List<Integer> batchIndexes = new ArrayList<>();
            for (int i = 0; i < updatedBatches.size(); i++) {
                MedicineBatch batch = updatedBatches.get(i);
                if (batch.medicineId().equals(requested.getKey())
                        && !batch.expiryDate().isBefore(today)
                        && batch.quantity() > 0) {
                    batchIndexes.add(i);
                }
            }
            batchIndexes.sort(Comparator.comparing(index -> updatedBatches.get(index).expiryDate()));

            for (int index : batchIndexes) {
                if (remaining == 0) {
                    break;
                }
                MedicineBatch batch = updatedBatches.get(index);
                int deducted = Math.min(remaining, batch.quantity());
                updatedBatches.set(index, batch.withQuantity(batch.quantity() - deducted));
                remaining -= deducted;
            }

            SaleItem item = new SaleItem(
                    saleId, soldAt, product.medicineId(), product.name(), product.price(), quantity);
            receipt.add(item);
            newSales.add(item);
        }

        save(updatedBatches, newSales, receivedOrderIds);
        batches = updatedBatches;
        sales = newSales;
        return List.copyOf(receipt);
    }

    public void exportStock(Path destination) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("medicineId,name,category,manufacturer,price,quantity,expiryDate,rackLocation,status");
        for (MedicineBatch batch : batches) {
            if (batch.quantity() == 0) {
                continue;
            }
            rows.add(csv(List.of(
                    batch.medicineId(), batch.name(), batch.category(), batch.manufacturer(),
                    batch.price().toPlainString(), Integer.toString(batch.quantity()),
                    batch.expiryDate().toString(), batch.rackLocation(),
                    expiryStatus(batch.expiryDate()))));
        }
        Files.write(destination, rows, StandardCharsets.UTF_8);
    }

    public void exportExpiry(Path destination) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("medicineId,name,category,quantity,expiryDate,status,estimatedValue");
        for (MedicineBatch batch : batches) {
            if (batch.quantity() > 0 && !batch.expiryDate().isAfter(LocalDate.now().plusDays(30))) {
                BigDecimal estimatedValue = batch.expiryDate().isBefore(LocalDate.now())
                        ? batch.price().multiply(BigDecimal.valueOf(batch.quantity()))
                        : BigDecimal.ZERO;
                rows.add(csv(List.of(
                        batch.medicineId(), batch.name(), batch.category(),
                        Integer.toString(batch.quantity()), batch.expiryDate().toString(),
                        expiryStatus(batch.expiryDate()), estimatedValue.toPlainString())));
            }
        }
        Files.write(destination, rows, StandardCharsets.UTF_8);
    }

    public void exportSales(Path destination) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("saleId,soldAt,medicineId,name,unitPrice,quantity,lineTotal");
        for (SaleItem sale : sales) {
            rows.add(csv(List.of(
                    sale.saleId(), sale.soldAt().toString(), sale.medicineId(), sale.name(),
                    sale.unitPrice().toPlainString(), Integer.toString(sale.quantity()),
                    sale.lineTotal().toPlainString())));
        }
        Files.write(destination, rows, StandardCharsets.UTF_8);
    }

    public static String expiryStatus(LocalDate expiryDate) {
        if (expiryDate.isBefore(LocalDate.now())) {
            return "Expired";
        }
        if (!expiryDate.isAfter(LocalDate.now().plusDays(30))) {
            return "Near expiry";
        }
        return "Good";
    }

    private static boolean sameMedicine(MedicineBatch first, MedicineBatch second) {
        return sameText(first.name(), second.name())
                && sameText(first.category(), second.category())
                && sameText(first.manufacturer(), second.manufacturer());
    }

    private static boolean sameText(String first, String second) {
        return first.strip().equalsIgnoreCase(second.strip());
    }

    private static void addToBatches(List<MedicineBatch> updated, MedicineBatch newBatch) {
        MedicineBatch batchToAdd = newBatch;
        for (int i = 0; i < updated.size(); i++) {
            MedicineBatch existing = updated.get(i);
            if (existing.medicineId().equalsIgnoreCase(newBatch.medicineId())) {
                if (!sameMedicine(existing, newBatch)) {
                    throw new IllegalArgumentException(
                            "This medicine ID already belongs to a medicine with different details.");
                }
                batchToAdd = newBatch.withDetails(existing.price(), existing.rackLocation());
                if (existing.expiryDate().equals(batchToAdd.expiryDate())) {
                    updated.set(i, existing.withQuantity(existing.quantity() + batchToAdd.quantity()));
                    return;
                }
            }
        }
        updated.add(batchToAdd);
    }

    private void load() throws IOException {
        List<MedicineBatch> loadedBatches = new ArrayList<>();
        List<SaleItem> loadedSales = new ArrayList<>();
        Set<String> loadedReceivedOrders = new HashSet<>();
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.isBlank()) {
                continue;
            }
            try {
                List<String> fields = parseCsv(line);
                if ("B".equals(fields.get(0))) {
                    loadedBatches.add(new MedicineBatch(
                            fields.get(1), fields.get(2), fields.get(3), fields.get(4),
                            new BigDecimal(fields.get(5)), Integer.parseInt(fields.get(6)),
                            LocalDate.parse(fields.get(7)), fields.size() == 11 ? fields.get(10) : ""));
                } else if ("S".equals(fields.get(0))) {
                    loadedSales.add(new SaleItem(
                            fields.get(8), LocalDateTime.parse(fields.get(9)), fields.get(1),
                            fields.get(2), new BigDecimal(fields.get(5)), Integer.parseInt(fields.get(6))));
                } else if ("R".equals(fields.get(0))) {
                    loadedReceivedOrders.add(fields.get(8));
                } else {
                    throw new IllegalArgumentException("Unknown record type.");
                }
            } catch (RuntimeException exception) {
                throw new IOException("Invalid pharmacy data at line " + (lineNumber + 1) + ".", exception);
            }
        }
        batches = loadedBatches;
        sales = loadedSales;
        receivedOrderIds = loadedReceivedOrders;
    }

    private void save(
            List<MedicineBatch> newBatches, List<SaleItem> newSales, Set<String> newReceivedOrderIds)
            throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> rows = new ArrayList<>();
        rows.add(HEADER);
        for (MedicineBatch batch : newBatches) {
            rows.add(csv(List.of(
                    "B", batch.medicineId(), batch.name(), batch.category(), batch.manufacturer(),
                    batch.price().toPlainString(), Integer.toString(batch.quantity()),
                    batch.expiryDate().toString(), "", "", batch.rackLocation())));
        }
        for (SaleItem sale : newSales) {
            rows.add(csv(List.of(
                    "S", sale.medicineId(), sale.name(), "", "", sale.unitPrice().toPlainString(),
                    Integer.toString(sale.quantity()), "", sale.saleId(), sale.soldAt().toString(), "")));
        }
        for (String orderId : newReceivedOrderIds) {
            rows.add(csv(List.of("R", "", "", "", "", "", "", "", orderId, "", "")));
        }
        Path temporaryFile = Files.createTempFile(dataFile.getParent(), "pharmacy-", ".tmp");
        try {
            Files.write(temporaryFile, rows, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, dataFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String csv(List<String> fields) {
        return fields.stream()
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .reduce((first, second) -> first + "," + second)
                .orElse("");
    }

    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unclosed quoted value.");
        }
        fields.add(field.toString());
        if (fields.size() != 10 && fields.size() != 11) {
            throw new IllegalArgumentException("Expected 10 legacy or 11 current columns.");
        }
        return fields;
    }

    public record SaleItem(
            String saleId,
            LocalDateTime soldAt,
            String medicineId,
            String name,
            BigDecimal unitPrice,
            int quantity) {
        public BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}