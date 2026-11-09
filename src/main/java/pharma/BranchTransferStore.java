package pharma;

import java.io.IOException;
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

public final class BranchTransferStore {
    private static final String HEADER =
            "transferId,medicineId,branch,quantity,requestedAt,expectedArrival,status";

    private final Path dataFile;
    private List<Transfer> transfers = new ArrayList<>();

    public BranchTransferStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "branch-transfers.csv"));
    }

    BranchTransferStore(Path dataFile) throws IOException {
        this.dataFile = dataFile;
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public List<Transfer> getTransfers() {
        return List.copyOf(transfers);
    }

    public Transfer request(MedicineBatch medicine, String branch, int quantity, LocalDate expectedArrival)
            throws IOException {
        if (medicine == null) {
            throw new IllegalArgumentException("Select a medicine to request.");
        }
        if (branch == null || branch.isBlank()) {
            throw new IllegalArgumentException("Sister branch name is required.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        }
        if (expectedArrival == null || expectedArrival.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Expected arrival must be today or a future date.");
        }
        Transfer transfer = new Transfer(
                UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                medicine.medicineId(), branch.trim(), quantity, LocalDateTime.now(),
                expectedArrival, Status.REQUESTED);
        List<Transfer> updated = new ArrayList<>(transfers);
        updated.add(transfer);
        save(updated);
        transfers = updated;
        return transfer;
    }

    public void updateStatus(String transferId, Status nextStatus) throws IOException {
        List<Transfer> updated = new ArrayList<>(transfers);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).transferId().equals(transferId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Branch transfer not found: " + transferId);
        }
        Transfer current = updated.get(index);
        if (current.status() == Status.REQUESTED && nextStatus == Status.IN_TRANSIT) {
            updated.set(index, current.withStatus(nextStatus));
        } else if (current.status() == Status.IN_TRANSIT && nextStatus == Status.RECEIVED) {
            updated.set(index, current.withStatus(nextStatus));
        } else {
            throw new IllegalArgumentException(
                    "Branch transfers can only move from Requested to In transit to Received.");
        }
        save(updated);
        transfers = updated;
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(dataFile, StandardCharsets.UTF_8);
        List<Transfer> loaded = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            try {
                List<String> fields = parseCsv(lines.get(i));
                if (fields.size() != 7) {
                    throw new IllegalArgumentException("Expected seven columns.");
                }
                loaded.add(new Transfer(
                        fields.get(0), fields.get(1), fields.get(2), Integer.parseInt(fields.get(3)),
                        LocalDateTime.parse(fields.get(4)), LocalDate.parse(fields.get(5)),
                        Status.valueOf(fields.get(6))));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid branch transfer data at line " + (i + 1) + ".", exception);
            }
        }
        transfers = loaded;
    }

    private void save(List<Transfer> updated) throws IOException {
        Files.createDirectories(dataFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        updated.forEach(transfer -> lines.add(csv(List.of(
                transfer.transferId(), transfer.medicineId(), transfer.branch(),
                Integer.toString(transfer.quantity()), transfer.requestedAt().toString(),
                transfer.expectedArrival().toString(), transfer.status().name()))));
        Path temporaryFile = Files.createTempFile(dataFile.getParent(), "branch-transfers-", ".tmp");
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
        REQUESTED("Requested"),
        IN_TRANSIT("In transit"),
        RECEIVED("Received");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Transfer(
            String transferId, String medicineId, String branch, int quantity,
            LocalDateTime requestedAt, LocalDate expectedArrival, Status status) {
        public Transfer withStatus(Status newStatus) {
            return new Transfer(transferId, medicineId, branch, quantity, requestedAt, expectedArrival, newStatus);
        }
    }
}
