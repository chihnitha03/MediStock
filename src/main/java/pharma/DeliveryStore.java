package pharma;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DeliveryStore {
    private static final String HEADER =
            "deliveryId,customerUsername,saleId,items,total,placedAt,status,customerName,customerMobile";

    private final Path dataFile;
    private List<DeliveryOrder> orders = new ArrayList<>();

    public DeliveryStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "customer-deliveries.csv"));
    }

    DeliveryStore(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public List<DeliveryOrder> getOrdersForCustomer(String username) {
        String customer = normalizeUsername(username);
        return orders.stream()
                .filter(order -> order.customerUsername().equals(customer))
                .sorted((first, second) -> second.placedAt().compareTo(first.placedAt()))
                .toList();
    }

    public List<DeliveryOrder> getAllOrders() {
        return orders.stream()
                .sorted((first, second) -> second.placedAt().compareTo(first.placedAt()))
                .toList();
    }

    public DeliveryOrder createOrder(String username, List<InventoryStore.SaleItem> items) throws IOException {
        return createOrder(username, "", "", items);
    }

    public DeliveryOrder createOrder(
            String username, String customerName, String customerMobile,
            List<InventoryStore.SaleItem> items) throws IOException {
        String customer = normalizeUsername(username);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A delivery order must contain at least one item.");
        }
        if (customerName == null || customerMobile == null) {
            throw new IllegalArgumentException("Customer name and mobile number are required.");
        }
        InventoryStore.SaleItem first = items.getFirst();
        BigDecimal total = items.stream()
                .map(InventoryStore.SaleItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String itemSummary = items.stream()
                .map(item -> item.name() + " x " + item.quantity())
                .collect(Collectors.joining("; "));
        DeliveryOrder order = new DeliveryOrder(
                UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                customer, first.saleId(), itemSummary, total, LocalDateTime.now(), Status.PLACED,
                customerName.trim(), customerMobile.trim());
        List<DeliveryOrder> updated = new ArrayList<>(orders);
        updated.add(order);
        save(updated);
        orders = updated;
        return order;
    }

    public void updateStatus(String deliveryId, Status nextStatus) throws IOException {
        List<DeliveryOrder> updated = new ArrayList<>(orders);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).deliveryId().equals(deliveryId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Delivery not found: " + deliveryId);
        }
        DeliveryOrder current = updated.get(index);
        boolean validTransition = (current.status() == Status.PLACED && nextStatus == Status.IN_TRANSIT)
                || (current.status() == Status.IN_TRANSIT && nextStatus == Status.DELIVERED);
        if (!validTransition) {
            throw new IllegalArgumentException("Deliveries can only move from Placed to In transit to Delivered.");
        }
        updated.set(index, current.withStatus(nextStatus));
        save(updated);
        orders = updated;
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        List<DeliveryOrder> loaded = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                List<String> fields = parseCsv(lines.get(i));
                if (fields.size() != 7 && fields.size() != 9) {
                    throw new IllegalArgumentException("Expected seven legacy or nine current columns.");
                }
                loaded.add(new DeliveryOrder(
                        fields.get(0), normalizeUsername(fields.get(1)), fields.get(2), fields.get(3),
                        new BigDecimal(fields.get(4)), LocalDateTime.parse(fields.get(5)),
                        Status.valueOf(fields.get(6)), fields.size() == 9 ? fields.get(7) : "",
                        fields.size() == 9 ? fields.get(8) : ""));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid customer delivery data at line " + (i + 1) + ".", exception);
            }
        }
        orders = loaded;
    }

    private void save(List<DeliveryOrder> updated) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        updated.forEach(order -> lines.add(csv(List.of(
                order.deliveryId(), order.customerUsername(), order.saleId(), order.items(),
                order.total().toPlainString(), order.placedAt().toString(), order.status().name(),
                order.customerName(), order.customerMobile()))));
        Path temporaryFile = Files.createTempFile(dataFile.getParent(), "deliveries-", ".tmp");
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

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Customer username is required.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
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

    public record DeliveryOrder(
            String deliveryId,
            String customerUsername,
            String saleId,
            String items,
            BigDecimal total,
            LocalDateTime placedAt,
            Status status,
            String customerName,
            String customerMobile) {
        public DeliveryOrder withStatus(Status newStatus) {
            return new DeliveryOrder(deliveryId, customerUsername, saleId, items, total,
                    placedAt, newStatus, customerName, customerMobile);
        }
    }
}
