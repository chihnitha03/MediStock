package pharma;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CartStoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pharma-cart-test-");
        Path cartFile = directory.resolve("carts.csv");
        CartStore carts = new CartStore(cartFile);
        carts.replaceCart("customer-one", Map.of("MED-1", 2, "MED-2", 1));
        carts.replaceCart("customer-two", Map.of("MED-1", 5));

        CartStore reopened = new CartStore(cartFile);
        if (!reopened.getCart("CUSTOMER-ONE").equals(Map.of("MED-1", 2, "MED-2", 1))
                || !reopened.getCart("customer-two").equals(Map.of("MED-1", 5))) {
            throw new AssertionError("Cart contents did not persist independently for each customer.");
        }

        reopened.setQuantity("customer-one", "MED-1", 0);
        reopened.clearCart("customer-two");
        CartStore updated = new CartStore(cartFile);
        if (!updated.getCart("customer-one").equals(Map.of("MED-2", 1))
                || !updated.getCart("customer-two").isEmpty()) {
            throw new AssertionError("Cart item removal or clearing failed.");
        }
        System.out.println("Customer cart persistence smoke test passed.");
    }
}
