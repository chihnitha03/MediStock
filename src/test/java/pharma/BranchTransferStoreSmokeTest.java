package pharma;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class BranchTransferStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pharma-branch-transfer-test-");
        Path transferFile = directory.resolve("branch-transfers.csv");
        BranchTransferStore transfers = new BranchTransferStore(transferFile);
        MedicineBatch medicine = new MedicineBatch(
                "MED-9", "Medicine Nine", "General", "Maker", new BigDecimal("8.75"),
                0, LocalDate.now().plusDays(180), "Aisle 2 / Rack B");
        BranchTransferStore.Transfer request =
                transfers.request(medicine, "North, Street Branch", 15, LocalDate.now().plusDays(4));
        transfers.updateStatus(request.transferId(), BranchTransferStore.Status.IN_TRANSIT);
        transfers.updateStatus(request.transferId(), BranchTransferStore.Status.RECEIVED);

        BranchTransferStore reopened = new BranchTransferStore(transferFile);
        BranchTransferStore.Transfer saved = reopened.getTransfers().getFirst();
        if (saved.status() != BranchTransferStore.Status.RECEIVED
                || !saved.branch().equals("North, Street Branch")
                || saved.quantity() != 15
                || !saved.medicineId().equals("MED-9")) {
            throw new AssertionError("Branch transfer status and CSV persistence failed.");
        }
        try {
            reopened.updateStatus(saved.transferId(), BranchTransferStore.Status.IN_TRANSIT);
            throw new AssertionError("A received transfer must not move backwards.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Requested")) {
                throw expected;
            }
        }
        System.out.println("Sister branch transfer smoke test passed.");
    }
}
