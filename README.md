# MediStock

A small JavaFX desktop pharmacy inventory and billing application for the hackathon. It uses Java collections, `LocalDate`, exception handling, and CSV file handling; no database or additional application libraries are needed.

## Requirements

- JDK 25 or newer
- Apache Maven 3.9+
- VS Code **Extension Pack for Java** (includes Java language support and Maven support)

JavaFX controls are the only application dependency and are downloaded by Maven from `pom.xml`.

## Run

From this directory:

```bash
mvn javafx:run
```

## Features

- Add medicine batches with ID, name, category, manufacturer, price, optional rack position, quantity, and expiry.
- Admins can update a medicine's current price and rack position from the inventory table; the new price applies to all of its existing batches and future billing, while completed sale prices remain unchanged.
- Restock a medicine using its existing medicine ID; create a separate batch when its expiry date differs.
- View previously stocked medicines with no sellable quantity in Inventory → Out of stock, then use Restock to add a quantity and expiry date without re-entering product details.
- Medicine names, categories, and manufacturers are matched without regard to capitalization when reusing an existing ID.
- Search by medicine name or category; filter near-expiry, expired, or low-stock batches. Batches with zero quantity are hidden from active inventory.
- Generate bills and deduct stock from the earliest-expiring non-expired batches first.
- Track incoming supplier orders from Placed to In transit to Delivered; receiving an order adds its new expiry-dated quantity to the medicine ID.
- Request out-of-stock medicines from a named sister branch and track the transfer as Requested → In transit → Received. Receiving a transfer asks for the batch expiry date and adds the quantity to inventory.
- Track multiple customer orders from checkout through delivery. Each checkout is a separate order, customers can start another purchase from their order history, and each customer sees only their own orders; admins update delivery status from the Deliveries page.
- Ask for the customer's name and 10-digit mobile at checkout; display an itemized MediStock receipt and allow it to be saved as a text file.
- Place supplier orders for existing medicines or choose Other and enter a new medicine's ID, name, category, manufacturer, and price.
- Add multiple existing or new medicine lines to one supplier-order submission.
- Save a customer's cart by account between visits; the cart is cleared after a successful checkout.
- Estimate the listed-price value of expired stock and include it in the expiry report.
- Create the first admin account on first launch; customers can register and shop without access to inventory management or reports.
- Show a one-time recovery code when an account is created. Forgot password verifies the account username and recovery code, then saves a new salted password hash and rotates the recovery code.
- Export stock, expiry, and sales CSV reports.
- Visualize inventory health with a dashboard pie chart and compare the five best-selling medicines from the last 30 days in a bar chart.
- Click a pie-chart segment or sales bar to see its exact inventory or sales value.
- Use the dashboard's smart restock planner for estimated replenishment suggestions based on recent sales and current non-expired stock; click a suggested restock quantity to open supplier orders.
- Automatically save inventory and sales to `~/.pharma-desk/pharmacy-data.csv`.
- Save sister branch transfer requests to `~/.pharma-desk/branch-transfers.csv`.
- Save supplier orders to `~/.pharma-desk/supplier-orders.csv`.
- Save customer delivery tracking to `~/.pharma-desk/customer-deliveries.csv`.
- Customer name and mobile details entered at checkout are saved with delivery orders in that local file.
- Display the cropped medical emblem from the provided reference prominently on the sign-in screen, without the phone frame or surrounding screenshot.
- Use an adaptive window size and consistent styling across sign-in, dashboards, inventory, billing, reports, forms, and dialogs.
- Save customer carts by username to `~/.pharma-desk/customer-carts.csv`.
- Store local account records in `~/.pharma-desk/users.csv`; passwords are salted and hashed with the JDK's PBKDF2 implementation.
- Create data CSV files automatically in `~/.pharma-desk/` the first time data is saved; no manual CSV setup or editing is required.

Near-expiry means expiring within 30 days. Expired stock is excluded from billing.
Restock suggestions are estimates, not guaranteed forecasts. For each medicine, the planner totals units sold in the last 30 days and estimates average daily demand as that total divided by 30. It estimates days of stock as current non-expired stock divided by average daily demand, and recommends restocking when stock is at most 10 units or the estimated runway is under 14 days. The suggested quantity brings stock up to the greater of the 14-day demand estimate or 20 units, subtracting current available stock. If there are no recorded sales, only medicines at or below 10 units are flagged and the target is 20 units.

The Reports screen exports:

- **Stock on hand:** non-empty batches with quantity, price, expiry date, and status.
- **Expiry watch:** batches with remaining stock that are expired or expire within 30 days.
- **Sales history:** completed sale line items and totals, including medicines that have since sold out.
- The expired stock loss is estimated using the listed unit price because purchase/acquisition costs are not recorded.

The Inventory page colors rows only while **All medicines** is selected: red for expired, orange for expiry within 30 days, blue for low stock (10 or fewer), and green for safe stock. Filtered lists use the normal row style.

## Sign-in

On first launch, create an administrator username and password. Sign in as the admin to add stock, view reports, and access billing. Customers can create their own account from the sign-in screen; they can search available stock and check out, but cannot manage inventory or view reports.

After customer checkout, open **My deliveries** to see that account's order status. Admins can update customer deliveries in **Deliveries**; the separate **Orders** page is for incoming supplier stock. Customer delivery records are filtered by the signed-in username.

This local account system is intended for a hackathon desktop demo, not as production authentication.

If a user forgets their password, select **Forgot password?** on sign-in and enter the username, saved recovery code, and a new password. The new password hash and salt replace the previous values in `users.csv`; the plain-text password is never written to that file. Recovery codes are also stored as salted hashes and each code can be used once. Older accounts without a recovery code receive one after their next successful sign-in.
