package pharma;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AuthStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path usersFile = Files.createTempDirectory("pharma-users-").resolve("users.csv");
        AuthStore auth = new AuthStore(usersFile);
        if (!auth.needsAdminSetup()) {
            throw new AssertionError("A fresh store should require admin setup.");
        }

        AuthStore.AccountSetup adminSetup =
                auth.createAdminWithRecovery("Pharmacy.Admin", "admin-pass-123".toCharArray());
        AuthStore.User admin = adminSetup.user();
        if (admin.role() != AuthStore.Role.ADMIN || auth.needsAdminSetup()) {
            throw new AssertionError("Admin setup did not persist.");
        }
        AuthStore.AccountSetup customerSetup =
                auth.registerCustomerWithRecovery("customer01", "customer-pass-1".toCharArray());
        AuthStore.User customer = customerSetup.user();
        if (customer.role() != AuthStore.Role.CUSTOMER) {
            throw new AssertionError("Customer role was not assigned.");
        }

        AuthStore reopened = new AuthStore(usersFile);
        if (reopened.login("PHARMACY.ADMIN", "admin-pass-123".toCharArray()).role() != AuthStore.Role.ADMIN
                || reopened.login("customer01", "customer-pass-1".toCharArray()).role() != AuthStore.Role.CUSTOMER) {
            throw new AssertionError("Accounts did not survive restart.");
        }
        AuthStore.AccountSetup resetAccount = reopened.resetPassword(
                "CUSTOMER01", customerSetup.recoveryCode().toLowerCase(Locale.ROOT).toCharArray(),
                "customer-new-pass-2".toCharArray());
        AuthStore afterReset = new AuthStore(usersFile);
        if (afterReset.login("customer01", "customer-new-pass-2".toCharArray()).role()
                != AuthStore.Role.CUSTOMER
                || !afterReset.hasRecoveryCode("customer01")) {
            throw new AssertionError("Password reset was not saved to the account file.");
        }
        try {
            afterReset.login("customer01", "customer-pass-1".toCharArray());
            throw new AssertionError("Old password still works after reset.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("incorrect")) {
                throw expected;
            }
        }
        try {
            afterReset.resetPassword("customer01", customerSetup.recoveryCode().toCharArray(),
                    "another-new-pass-3".toCharArray());
            throw new AssertionError("A recovery code was reusable after password reset.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("incorrect")) {
                throw expected;
            }
        }
        afterReset.resetPassword("customer01", resetAccount.recoveryCode().toCharArray(),
                "customer-final-pass-4".toCharArray());
        try {
            reopened.login("customer01", "wrong-password".toCharArray());
            throw new AssertionError("Incorrect password was accepted.");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("incorrect")) {
                throw expected;
            }
        }
        try {
            reopened.createAdmin("another-admin", "another-pass".toCharArray());
            throw new AssertionError("A second initial administrator was allowed.");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("already")) {
                throw expected;
            }
        }
        String storedAccounts = Files.readString(usersFile);
        if (storedAccounts.contains("admin-pass-123") || storedAccounts.contains("customer-pass-1")
                || storedAccounts.contains(customerSetup.recoveryCode())
                || storedAccounts.contains(resetAccount.recoveryCode())) {
            throw new AssertionError("A password was stored as plain text.");
        }
        Path legacyFile = usersFile.resolveSibling("legacy-users.csv");
        Files.writeString(legacyFile, "username,role,salt,passwordHash\n"
                + admin.username() + "," + admin.role() + "," + admin.salt() + "," + admin.passwordHash() + "\n");
        AuthStore legacy = new AuthStore(legacyFile);
        if (!legacy.login(admin.username(), "admin-pass-123".toCharArray()).username().equals(admin.username())
                || legacy.hasRecoveryCode(admin.username())) {
            throw new AssertionError("Legacy account records did not load compatibly.");
        }
        String legacyRecoveryCode = legacy.createRecoveryCode(
                admin.username(), "admin-pass-123".toCharArray());
        if (!legacy.hasRecoveryCode(admin.username())) {
            throw new AssertionError("Legacy account recovery setup failed.");
        }
        legacy.resetPassword(admin.username(), legacyRecoveryCode.toCharArray(),
                "legacy-admin-new-pass".toCharArray());
        if (!new AuthStore(legacyFile).login(admin.username(),
                "legacy-admin-new-pass".toCharArray()).username().equals(admin.username())) {
            throw new AssertionError("Legacy account password recovery did not persist.");
        }
        System.out.println("Authentication smoke test passed.");
    }
}
