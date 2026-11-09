package pharma;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

public record MedicineBatch(
        String medicineId,
        String name,
        String category,
        String manufacturer,
        BigDecimal price,
        int quantity,
        LocalDate expiryDate,
        String rackLocation) {

    public MedicineBatch {
        medicineId = requireText(medicineId, "Medicine ID").toUpperCase(Locale.ROOT);
        name = requireText(name, "Medicine name");
        category = requireText(category, "Category");
        manufacturer = requireText(manufacturer, "Manufacturer");
        Objects.requireNonNull(price, "Price is required");
        Objects.requireNonNull(expiryDate, "Expiry date is required");
        rackLocation = rackLocation == null ? "" : rackLocation.trim();
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
    }

    public MedicineBatch(
            String medicineId, String name, String category, String manufacturer,
            BigDecimal price, int quantity, LocalDate expiryDate) {
        this(medicineId, name, category, manufacturer, price, quantity, expiryDate, "");
    }

    public MedicineBatch withQuantity(int newQuantity) {
        return new MedicineBatch(
                medicineId, name, category, manufacturer, price, newQuantity, expiryDate, rackLocation);
    }

    public MedicineBatch withDetails(BigDecimal newPrice, String newRackLocation) {
        return new MedicineBatch(
                medicineId, name, category, manufacturer, newPrice, quantity, expiryDate, newRackLocation);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}