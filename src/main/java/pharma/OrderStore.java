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
import java.util.List;
import java.util.UUID;

public final class OrderStore {
    private static final String HEADER =
            "orderId,medicineId,medicineName,supplier,quantity,placedAt,expectedDelivery,status,category,manufacturer,price";

    private final Path dataFile;
    private List<SupplierOrder> orders = new ArrayList<>();

    public OrderStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "supplier-orders.csv"));
    }

    OrderStore(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public List<SupplierOrder> getOrders() {
        return List.copyOf(orders);
    }

    public SupplierOrder placeOrder(
            MedicineBatch medicine, String supplier, int quantity, LocalDate expectedDelivery) throws IOException {
        return placeOrder(medicine.medicineId(), medicine.name(), medicine.category(), medicine.manufacturer(),
                medicine.price(), supplier, quantity, expectedDelivery);
    }

    public SupplierOrder placeOrder(
            String medicineId,
            String medicineName,
            String category,
            String manufacturer,
            BigDecimal price,
            String supplier,
            int quantity,
            LocalDate expectedDelivery) throws IOException {
        return placeOrders(List.of(new OrderRequest(
                medicineId, medicineName, category, manufacturer, price, quantity)), supplier, expectedDelivery)
                .getFirst();
    }

    public List<SupplierOrder> placeOrders(
            List<OrderRequest> requests, String supplier, LocalDate expectedDelivery) throws IOException {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Add at least one medicine to the supplier order.");
        }
        if (supplier == null || supplier.isBlank()) {
            throw new IllegalArgumentException("Supplier name is required.");
        }
        if (expectedDelivery == null || expectedDelivery.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Expected delivery must be today or a future date.");
        }

        List<SupplierOrder> updated = new ArrayList<>(orders);
        LocalDateTime placedAt = LocalDateTime.now();
        List<SupplierOrder> created = new ArrayList<>();
        for (OrderRequest request : requests) {
            if (request == null
                    || request.medicineId() == null || request.medicineId().isBlank()
                    || request.medicineName() == null || request.medicineName().isBlank()
                    || request.category() == null || request.category().isBlank()
                    || request.manufacturer() == null || request.manufacturer().isBlank()
                    || request.unitPrice() == null || request.unitPrice().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Each line needs a medicine ID, name, category, manufacturer, and valid price.");
            }
            if (request.quantity() <= 0) {
                throw new IllegalArgumentException("Each ordered quantity must be greater than zero.");
            }
            SupplierOrder order = new SupplierOrder(
                    UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    request.medicineId().trim().toUpperCase(java.util.Locale.ROOT),
                    request.medicineName().trim(), request.category().trim(), request.manufacturer().trim(),
                    request.unitPrice(), supplier.trim(), request.quantity(),
                    placedAt, expectedDelivery, Status.PLACED);
            updated.add(order);
            created.add(order);
        }
        save(updated);
        orders = updated;
        return List.copyOf(created);
    }

    public void updateStatus(String orderId, Status nextStatus) throws IOException {
        List<SupplierOrder> updated = new ArrayList<>(orders);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).orderId().equals(orderId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        SupplierOrder current = updated.get(index);
        boolean validTransition = (current.status() == Status.PLACED && nextStatus == Status.IN_TRANSIT)
                || (current.status() == Status.IN_TRANSIT && nextStatus == Status.DELIVERED);
        if (!validTransition) {
            throw new IllegalArgumentException("Orders can only move from Placed to In transit to Delivered.");
        }
        updated.set(index, current.withStatus(nextStatus));
        save(updated);
        orders = updated;
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        List<SupplierOrder> loaded = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                List<String> fields = parseCsv(lines.get(i));
                if (fields.size() != 8 && fields.size() != 11) {
                    throw new IllegalArgumentException("Expected eight legacy or eleven current columns.");
                }
                loaded.add(fields.size() == 8
                        ? new SupplierOrder(
                                fields.get(0), fields.get(1), fields.get(2), "", "", BigDecimal.ZERO,
                                fields.get(3), Integer.parseInt(fields.get(4)),
                                LocalDateTime.parse(fields.get(5)), LocalDate.parse(fields.get(6)),
                                Status.valueOf(fields.get(7)))
                        : new SupplierOrder(
                                fields.get(0), fields.get(1), fields.get(2), fields.get(8), fields.get(9),
                                new BigDecimal(fields.get(10)), fields.get(3), Integer.parseInt(fields.get(4)),
                                LocalDateTime.parse(fields.get(5)), LocalDate.parse(fields.get(6)),
                                Status.valueOf(fields.get(7))));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid supplier order data at line " + (i + 1) + ".", exception);
            }
        }
        orders = loaded;
    }

    private void save(List<SupplierOrder> updated) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        updated.forEach(order -> lines.add(csv(List.of(
                order.orderId(), order.medicineId(), order.medicineName(), order.supplier(),
                Integer.toString(order.quantity()), order.placedAt().toString(),
                order.expectedDelivery().toString(), order.status().name(), order.category(),
                order.manufacturer(), order.unitPrice().toPlainString()))));
        Path temporaryFile = Files.createTempFile(dataFile.getParent(), "orders-", ".tmp");
        try {
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
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
        return fields;
    }

    public enum Status {
        PLACED("Placed"),
        IN_TRANSIT("In transit"),
        DELIVERED("Delivered");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record OrderRequest(
            String medicineId,
            String medicineName,
            String category,
            String manufacturer,
            BigDecimal unitPrice,
            int quantity) {}

    public record SupplierOrder(
            String orderId,
            String medicineId,
            String medicineName,
            String category,
            String manufacturer,
            BigDecimal unitPrice,
            String supplier,
            int quantity,
            LocalDateTime placedAt,
            LocalDate expectedDelivery,
            Status status) {
        public SupplierOrder withStatus(Status newStatus) {
            return new SupplierOrder(orderId, medicineId, medicineName, category, manufacturer, unitPrice,
                    supplier, quantity, placedAt, expectedDelivery, newStatus);
        }
    }
}