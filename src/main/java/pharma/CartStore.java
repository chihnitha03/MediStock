package pharma;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CartStore {
    private static final String HEADER = "username,medicineId,quantity";

    private final Path dataFile;
    private Map<String, Map<String, Integer>> carts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public CartStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "customer-carts.csv"));
    }

    CartStore(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public Map<String, Integer> getCart(String username) {
        return Map.copyOf(carts.getOrDefault(normalize(username), Map.of()));
    }

    public void setQuantity(String username, String medicineId, int quantity) throws IOException {
        if (medicineId == null || medicineId.isBlank()) {
            throw new IllegalArgumentException("Medicine ID is required.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Cart quantity cannot be negative.");
        }
        String customer = normalize(username);
        Map<String, Map<String, Integer>> updated = copyCarts();
        Map<String, Integer> customerCart = updated.computeIfAbsent(customer, ignored -> new TreeMap<>());
        if (quantity == 0) {
            customerCart.remove(medicineId);
        } else {
            customerCart.put(medicineId, quantity);
        }
        if (customerCart.isEmpty()) {
            updated.remove(customer);
        }
        save(updated);
        carts = updated;
    }

    public void replaceCart(String username, Map<String, Integer> items) throws IOException {
        String customer = normalize(username);
        Map<String, Integer> validated = new TreeMap<>();
        for (Map.Entry<String, Integer> item : items.entrySet()) {
            if (item.getKey() == null || item.getKey().isBlank()
                    || item.getValue() == null || item.getValue() <= 0) {
                throw new IllegalArgumentException("Saved cart items need a medicine ID and positive quantity.");
            }
            validated.put(item.getKey(), item.getValue());
        }
        Map<String, Map<String, Integer>> updated = copyCarts();
        if (validated.isEmpty()) {
            updated.remove(customer);
        } else {
            updated.put(customer, validated);
        }
        save(updated);
        carts = updated;
    }

    public void clearCart(String username) throws IOException {
        Map<String, Map<String, Integer>> updated = copyCarts();
        updated.remove(normalize(username));
        save(updated);
        carts = updated;
    }

    private Map<String, Map<String, Integer>> copyCarts() {
        Map<String, Map<String, Integer>> copied = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        carts.forEach((username, items) -> copied.put(username, new TreeMap<>(items)));
        return copied;
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        Map<String, Map<String, Integer>> loaded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> fields = parseCsv(lines.get(i));
            try {
                if (fields.size() != 3) {
                    throw new IllegalArgumentException("Expected three columns.");
                }
                String username = normalize(fields.get(0));
                String medicineId = fields.get(1);
                int quantity = Integer.parseInt(fields.get(2));
                if (medicineId.isBlank() || quantity <= 0) {
                    throw new IllegalArgumentException("Invalid cart item.");
                }
                loaded.computeIfAbsent(username, ignored -> new TreeMap<>())
                        .merge(medicineId, quantity, Integer::sum);
            } catch (RuntimeException exception) {
                throw new IOException("Invalid saved cart data at line " + (i + 1) + ".", exception);
            }
        }
        carts = loaded;
    }

    private void save(Map<String, Map<String, Integer>> updated) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        updated.forEach((username, items) ->
                items.forEach((medicineId, quantity) ->
                        lines.add(csv(List.of(username, medicineId, Integer.toString(quantity))))));
        Path temporaryFile = Files.createTempFile(dataFile.getParent(), "carts-", ".tmp");
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

    private static String normalize(String username) {
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
}
