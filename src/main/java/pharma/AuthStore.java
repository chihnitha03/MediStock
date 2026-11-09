package pharma;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public final class AuthStore {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final String HEADER =
            "username,role,salt,passwordHash,recoverySalt,recoveryHash";

    private final Path usersFile;
    private final SecureRandom secureRandom = new SecureRandom();
    private List<User> users = new ArrayList<>();

    public AuthStore() throws IOException {
        this(Path.of(System.getProperty("user.home"), ".pharma-desk", "users.csv"));
    }

    AuthStore(Path usersFile) throws IOException {
        this.usersFile = usersFile;
        if (Files.exists(usersFile)) {
            load();
        }
    }

    public boolean needsAdminSetup() {
        return users.stream().noneMatch(user -> user.role() == Role.ADMIN);
    }

    public User login(String username, char[] password) {
        String normalized = normalizeUsername(username);
        User user = users.stream()
                .filter(account -> account.username().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(AuthStore::invalidCredentials);
        byte[] candidate = hash(password, Base64.getDecoder().decode(user.salt()));
        byte[] expected = Base64.getDecoder().decode(user.passwordHash());
        if (!MessageDigest.isEqual(candidate, expected)) {
            throw invalidCredentials();
        }
        return user;
    }

    public User createAdmin(String username, char[] password) throws IOException {
        return createAdminWithRecovery(username, password).user();
    }

    public AccountSetup createAdminWithRecovery(String username, char[] password) throws IOException {
        if (!needsAdminSetup()) {
            throw new IllegalStateException("An administrator account has already been created.");
        }
        return createUser(username, password, Role.ADMIN);
    }

    public User registerCustomer(String username, char[] password) throws IOException {
        return registerCustomerWithRecovery(username, password).user();
    }

    public AccountSetup registerCustomerWithRecovery(String username, char[] password) throws IOException {
        return createUser(username, password, Role.CUSTOMER);
    }

    public boolean hasRecoveryCode(String username) {
        User user = findUser(username);
        return !user.recoverySalt().isEmpty() && !user.recoveryHash().isEmpty();
    }

    public String createRecoveryCode(String username, char[] currentPassword) throws IOException {
        User user = findUser(username);
        byte[] currentPasswordHash = hash(currentPassword, Base64.getDecoder().decode(user.salt()));
        if (!MessageDigest.isEqual(currentPasswordHash, Base64.getDecoder().decode(user.passwordHash()))) {
            throw invalidCredentials();
        }
        String recoveryCode = newRecoveryCode();
        byte[] recoverySalt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(recoverySalt);
        char[] recoveryCodeChars = recoveryCode.toCharArray();
        User updatedUser;
        try {
            updatedUser = new User(user.username(), user.role(), user.salt(), user.passwordHash(),
                    Base64.getEncoder().encodeToString(recoverySalt),
                    Base64.getEncoder().encodeToString(hash(recoveryCodeChars, recoverySalt)));
        } finally {
            java.util.Arrays.fill(recoveryCodeChars, '\0');
        }
        replaceUser(updatedUser);
        return recoveryCode;
    }

    public AccountSetup resetPassword(String username, char[] recoveryCode, char[] newPassword) throws IOException {
        User user = findUser(username);
        if (user.recoverySalt().isEmpty() || user.recoveryHash().isEmpty()) {
            throw new IllegalArgumentException(
                    "This account has no recovery code yet. Sign in once to set one up.");
        }
        char[] normalizedRecoveryCode = normalizeRecoveryCode(recoveryCode);
        byte[] candidateRecoveryHash;
        try {
            candidateRecoveryHash =
                    hash(normalizedRecoveryCode, Base64.getDecoder().decode(user.recoverySalt()));
        } finally {
            java.util.Arrays.fill(normalizedRecoveryCode, '\0');
        }
        if (!MessageDigest.isEqual(candidateRecoveryHash, Base64.getDecoder().decode(user.recoveryHash()))) {
            throw new IllegalArgumentException("Username or recovery code is incorrect.");
        }
        validatePassword(newPassword);
        byte[] newSalt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(newSalt);
        String newRecoveryCode = newRecoveryCode();
        byte[] newRecoverySalt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(newRecoverySalt);
        char[] newRecoveryCodeChars = newRecoveryCode.toCharArray();
        User updatedUser = new User(user.username(), user.role(),
                Base64.getEncoder().encodeToString(newSalt),
                Base64.getEncoder().encodeToString(hash(newPassword, newSalt)),
                Base64.getEncoder().encodeToString(newRecoverySalt),
                Base64.getEncoder().encodeToString(hash(newRecoveryCodeChars, newRecoverySalt)));
        java.util.Arrays.fill(newRecoveryCodeChars, '\0');
        replaceUser(updatedUser);
        return new AccountSetup(updatedUser, newRecoveryCode);
    }

    private AccountSetup createUser(String username, char[] password, Role role) throws IOException {
        String normalized = normalizeUsername(username);
        validatePassword(password);
        if (users.stream().anyMatch(user -> user.username().equalsIgnoreCase(normalized))) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        String recoveryCode = newRecoveryCode();
        byte[] recoverySalt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(recoverySalt);
        char[] recoveryCodeChars = recoveryCode.toCharArray();
        User user = new User(normalized, role, Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash(password, salt)),
                Base64.getEncoder().encodeToString(recoverySalt),
                Base64.getEncoder().encodeToString(hash(recoveryCodeChars, recoverySalt)));
        java.util.Arrays.fill(recoveryCodeChars, '\0');
        List<User> updated = new ArrayList<>(users);
        updated.add(user);
        save(updated);
        users = updated;
        return new AccountSetup(user, recoveryCode);
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(usersFile, StandardCharsets.UTF_8);
        List<User> loaded = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            String[] fields = lines.get(index).split(",", -1);
            try {
                if (fields.length != 4 && fields.length != 6) {
                    throw new IllegalArgumentException("Expected four legacy or six current fields.");
                }
                Role role = Role.valueOf(fields[1]);
                if (role != Role.ADMIN && role != Role.CUSTOMER) {
                    throw new IllegalArgumentException("Unsupported account role.");
                }
                loaded.add(new User(fields[0], role, fields[2], fields[3],
                        fields.length == 6 ? fields[4] : "",
                        fields.length == 6 ? fields[5] : ""));
            } catch (RuntimeException exception) {
                throw new IOException("Invalid user account data at line " + (index + 1) + ".", exception);
            }
        }
        users = loaded;
    }

    private void save(List<User> updated) throws IOException {
        Files.createDirectories(usersFile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        updated.forEach(user -> lines.add(String.join(",",
                user.username(), user.role().name(), user.salt(), user.passwordHash(),
                user.recoverySalt(), user.recoveryHash())));
        Path temporaryFile = Files.createTempFile(usersFile.getParent(), "users-", ".tmp");
        try {
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, usersFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, usersFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Enter a username.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,31}")) {
            throw new IllegalArgumentException("Username must be 3–32 characters using letters, numbers, dot, _ or -.");
        }
        return normalized;
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }
    }

    private static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private String newRecoveryCode() {
        byte[] bytes = new byte[9];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase(Locale.ROOT);
    }

    private static char[] normalizeRecoveryCode(char[] recoveryCode) {
        if (recoveryCode == null) {
            return new char[0];
        }
        return new String(recoveryCode).trim().toUpperCase(Locale.ROOT).toCharArray();
    }

    private User findUser(String username) {
        String normalized = normalizeUsername(username);
        return users.stream()
                .filter(account -> account.username().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(AuthStore::invalidCredentials);
    }

    private void replaceUser(User replacement) throws IOException {
        List<User> updated = new ArrayList<>(users);
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).username().equalsIgnoreCase(replacement.username())) {
                updated.set(index, replacement);
                save(updated);
                users = updated;
                return;
            }
        }
        throw new IllegalArgumentException("Account not found.");
    }

    private static IllegalArgumentException invalidCredentials() {
        return new IllegalArgumentException("Username or password is incorrect.");
    }

    public enum Role {
        ADMIN,
        CUSTOMER
    }

    public record AccountSetup(User user, String recoveryCode) {}

    public record User(
            String username, Role role, String salt, String passwordHash,
            String recoverySalt, String recoveryHash) {}
}
