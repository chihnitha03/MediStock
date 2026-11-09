package pharma;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PharmacyApp extends Application {
    private static final int LOW_STOCK_LIMIT = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private InventoryStore store;
    private AuthStore authStore;
    private OrderStore orderStore;
    private BranchTransferStore branchTransferStore;
    private DeliveryStore deliveryStore;
    private CartStore cartStore;
    private AuthStore.User currentUser;
    private Stage stage;
    private BorderPane root;
    private TableView<MedicineBatch> inventoryTable;
    private TableView<MedicineBatch> outOfStockTable;
    private TableView<BillingRow> billingTable;
    private TableView<CartRow> cartTable;
    private ObservableList<CartRow> cart;
    private TextField searchField;
    private String inventoryFilter = "All medicines";
    private Label subtotalLabel;
    private final Map<String, Button> navigationButtons = new HashMap<>();
    private String billingSearch = "";

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        try {
            store = new InventoryStore();
            authStore = new AuthStore();
            orderStore = new OrderStore();
            branchTransferStore = new BranchTransferStore();
            deliveryStore = new DeliveryStore();
            cartStore = new CartStore();
        } catch (IOException exception) {
            showStartupError("Could not open pharmacy data", exception.getMessage());
            return;
        }

        root = new BorderPane();
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double windowWidth = Math.max(1024, Math.min(1280, screenBounds.getWidth() * 0.94));
        double windowHeight = Math.max(640, Math.min(800, screenBounds.getHeight() * 0.92));
        Scene scene = new Scene(root, windowWidth, windowHeight);
        scene.getStylesheets().add(getClass().getResource("/pharma.css").toExternalForm());
        stage.setTitle("MediStock — Pharmacy Management");
        stage.setMinWidth(1000);
        stage.setMinHeight(620);
        stage.setScene(scene);
        if (authStore.needsAdminSetup()) {
            showAuthScreen(AuthScreen.ADMIN_SETUP);
        } else {
            showAuthScreen(AuthScreen.LOGIN);
        }
        stage.show();
    }

    private HBox createHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 30, 18, 30));
        header.getStyleClass().add("workspace-header");

        VBox welcome = new VBox(3,
                new Label("Welcome back, " + currentUser.username()),
                new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy"))));
        welcome.getStyleClass().add("header-welcome");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label role = new Label(currentUser.role() == AuthStore.Role.ADMIN ? "ADMIN" : "CUSTOMER");
        role.getStyleClass().add("role-pill");
        Button logout = new Button("Sign out");
        logout.getStyleClass().add("signout-button");
        logout.setOnAction(event -> {
            currentUser = null;
            showAuthScreen(AuthScreen.LOGIN);
        });
        header.getChildren().addAll(welcome, spacer, role, logout);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);
        sidebar.setMinWidth(220);
        sidebar.setPadding(new Insets(28, 17, 22, 17));

        HBox identity = new HBox(11);
        identity.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("+");
        logo.getStyleClass().add("sidebar-logo");
        VBox brand = new VBox(2, new Label("MediStock"), new Label("PHARMACY DESK"));
        brand.getStyleClass().add("sidebar-brand");
        identity.getChildren().addAll(logo, brand);

        Label section = new Label("WORKSPACE");
        section.getStyleClass().add("sidebar-section");
        VBox nav = new VBox(8);
        navigationButtons.clear();
        if (currentUser.role() == AuthStore.Role.ADMIN) {
            nav.getChildren().addAll(
                    navButton("Overview", "⌂", () -> showPage("Overview", createDashboard())),
                    navButton("Inventory", "▤", () -> showPage("Inventory", createInventory())),
                    navButton("Orders", "⇄", () -> showPage("Orders", createOrders())),
                    navButton("Sister branches", "⇆", () -> showPage("Sister branches", createBranchTransfers())),
                    navButton("Deliveries", "➜", () -> showPage("Deliveries", createAdminDeliveries())),
                    navButton("Billing", "＋", () -> showPage("Billing", createBilling())),
                    navButton("Reports", "▥", () -> showPage("Reports", createReports())));
        } else {
            nav.getChildren().addAll(
                    navButton("Find medicine", "⌕", () -> showPage("Find medicine", createBilling())),
                    navButton("My deliveries", "➜", () -> showPage("My deliveries", createCustomerDeliveries())));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox account = new VBox(5, new Label(currentUser.username()), new Label(
                currentUser.role() == AuthStore.Role.ADMIN ? "Pharmacy administrator" : "Customer account"));
        account.getStyleClass().add("account-card");
        sidebar.getChildren().addAll(identity, section, nav, spacer, account);
        return sidebar;
    }

    private Button navButton(String text, String icon, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        navigationButtons.put(text, button);
        Label symbol = new Label(icon);
        symbol.getStyleClass().add("nav-icon");
        button.setGraphic(symbol);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showPage(String activePage, VBox content) {
        if (currentUser == null) {
            return;
        }
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("page-scroll");
        root.setCenter(scrollPane);
        navigationButtons.forEach((name, button) ->
                button.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), name.equals(activePage)));
    }

    private void showAuthScreen(AuthScreen screen) {
        root.setTop(null);
        root.setLeft(null);
        root.setRight(null);
        root.setBottom(null);
        root.getStyleClass().setAll("auth-root");

        VBox showcase = new VBox(8);
        showcase.getStyleClass().add("auth-showcase");
        showcase.setPrefWidth(500);
        showcase.setMinWidth(400);
        showcase.setPadding(new Insets(18, 38, 16, 38));
        HBox brand = new HBox(12);
        brand.setAlignment(Pos.CENTER_LEFT);
        VBox brandName = new VBox(2, new Label("MediStock"), new Label("PHARMACY MANAGEMENT"));
        brandName.getStyleClass().add("auth-brand");
        brand.getChildren().add(brandName);

        Label eyebrow = new Label("SMARTER PHARMACY. SAFER CARE.");
        eyebrow.getStyleClass().add("auth-eyebrow");
        Label headline = new Label("Care starts with\norganized stock.");
        headline.getStyleClass().add("auth-headline");
        headline.setWrapText(true);
        headline.setMaxWidth(Double.MAX_VALUE);
        Label description = new Label("Manage medicine stock, expiry dates, and purchases in one place.");
        description.getStyleClass().add("auth-description");
        description.setWrapText(true);
        description.setMaxWidth(Double.MAX_VALUE);
        Image logoImage = new Image(getClass().getResourceAsStream("/pharmacy-logo.png"));
        ImageView logoArtwork = new ImageView(logoImage);
        logoArtwork.setFitWidth(170);
        logoArtwork.setFitHeight(228);
        logoArtwork.setPreserveRatio(true);
        logoArtwork.setSmooth(true);
        logoArtwork.getStyleClass().add("auth-logo-art");
        StackPane artworkFrame = new StackPane(logoArtwork);
        artworkFrame.getStyleClass().add("auth-art-frame");
        artworkFrame.setMaxWidth(210);
        artworkFrame.setMaxHeight(246);
        VBox features = new VBox(7,
                featureLine("01", "Live stock visibility"),
                featureLine("02", "Expiry date monitoring"),
                featureLine("03", "Quick, clear billing"));
        Label footer = new Label("YOUR LOCAL PHARMACY, IN GOOD HANDS");
        footer.getStyleClass().add("auth-footer");
        footer.setWrapText(true);
        showcase.getChildren().addAll(brand, artworkFrame, eyebrow, headline, description, features, footer);

        VBox form = new VBox(16);
        form.getStyleClass().add("auth-form");
        form.setMaxWidth(420);
        form.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label(switch (screen) {
            case ADMIN_SETUP -> "Set up your pharmacy";
            case REGISTER -> "Create your account";
            case LOGIN -> "Welcome back";
        });
        heading.getStyleClass().add("auth-form-title");
        Label subtitle = new Label(switch (screen) {
            case ADMIN_SETUP -> "Create the first administrator account to get started.";
            case REGISTER -> "Create a customer account to search and buy medicines.";
            case LOGIN -> "Sign in to continue to your MediStock workspace.";
        });
        subtitle.getStyleClass().add("auth-form-subtitle");
        subtitle.setWrapText(true);

        TextField username = new TextField();
        username.setPromptText("Username");
        username.getStyleClass().add("auth-input");
        PasswordField password = new PasswordField();
        password.setPromptText("Password (at least 8 characters)");
        password.getStyleClass().add("auth-input");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm password");
        confirmPassword.getStyleClass().add("auth-input");
        Label error = new Label();
        error.getStyleClass().add("auth-error");
        error.setWrapText(true);
        Button submit = new Button(switch (screen) {
            case ADMIN_SETUP -> "Create administrator";
            case REGISTER -> "Create customer account";
            case LOGIN -> "Sign in";
        });
        submit.getStyleClass().add("auth-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);

        submit.setOnAction(event -> {
            char[] passwordChars = password.getText().toCharArray();
            char[] confirmationChars = confirmPassword.getText().toCharArray();
            try {
                if (screen != AuthScreen.LOGIN && !password.getText().equals(confirmPassword.getText())) {
                    throw new IllegalArgumentException("The passwords do not match.");
                }
                AuthStore.User user;
                String recoveryCode = null;
                switch (screen) {
                    case ADMIN_SETUP -> {
                        AuthStore.AccountSetup account =
                                authStore.createAdminWithRecovery(username.getText(), passwordChars);
                        user = account.user();
                        recoveryCode = account.recoveryCode();
                    }
                    case REGISTER -> {
                        AuthStore.AccountSetup account =
                                authStore.registerCustomerWithRecovery(username.getText(), passwordChars);
                        user = account.user();
                        recoveryCode = account.recoveryCode();
                    }
                    case LOGIN -> {
                        user = authStore.login(username.getText(), passwordChars);
                        if (!authStore.hasRecoveryCode(user.username())) {
                            recoveryCode = authStore.createRecoveryCode(user.username(), passwordChars);
                        }
                    }
                    default -> throw new IllegalStateException("Unsupported sign-in screen.");
                }
                error.setText("");
                showWorkspace(user);
                if (recoveryCode != null) {
                    showRecoveryCode(recoveryCode);
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                error.setText(exception.getMessage());
            } catch (IOException exception) {
                error.setText("Could not save your account: " + exception.getMessage());
            } finally {
                java.util.Arrays.fill(passwordChars, '\0');
                java.util.Arrays.fill(confirmationChars, '\0');
                password.clear();
                confirmPassword.clear();
            }
        });

        form.getChildren().addAll(heading, subtitle, authField("USERNAME", username),
                authField("PASSWORD", password));
        if (screen != AuthScreen.LOGIN) {
            form.getChildren().add(authField("CONFIRM PASSWORD", confirmPassword));
        }
        form.getChildren().addAll(error, submit);
        if (screen == AuthScreen.LOGIN) {
            Button forgot = new Button("Forgot password?");
            forgot.getStyleClass().add("auth-link");
            forgot.setOnAction(event -> showForgotPasswordDialog());
            Button register = new Button("New customer?  Create an account");
            register.getStyleClass().add("auth-link");
            register.setOnAction(event -> showAuthScreen(AuthScreen.REGISTER));
            form.getChildren().addAll(forgot, register);
        } else if (screen == AuthScreen.REGISTER) {
            Button back = new Button("Already have an account?  Sign in");
            back.getStyleClass().add("auth-link");
            back.setOnAction(event -> showAuthScreen(AuthScreen.LOGIN));
            form.getChildren().add(back);
        }

        StackPane formArea = new StackPane(form);
        formArea.getStyleClass().add("auth-form-area");
        HBox layout = new HBox(showcase, formArea);
        HBox.setHgrow(formArea, Priority.ALWAYS);
        root.setCenter(layout);
    }

    private void showForgotPasswordDialog() {
        Dialog<PasswordReset> dialog = new Dialog<>();
        dialog.setTitle("Reset password");
        dialog.setHeaderText("Recover access with your recovery code");
        ButtonType reset = new ButtonType("Reset password", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(reset, ButtonType.CANCEL);

        TextField username = new TextField();
        username.setPromptText("Your account username");
        TextField recoveryCode = new TextField();
        recoveryCode.setPromptText("Recovery code");
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("New password (at least 8 characters)");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm new password");
        Label hint = new Label("Use the recovery code shown when your account was created.");
        hint.setWrapText(true);
        hint.getStyleClass().add("checkout-hint");

        VBox form = new VBox(13,
                formField("USERNAME", username),
                formField("RECOVERY CODE", recoveryCode),
                formField("NEW PASSWORD", newPassword),
                formField("CONFIRM PASSWORD", confirmPassword),
                hint);
        form.getStyleClass().add("checkout-form");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(470);

        Button resetButton = (Button) dialog.getDialogPane().lookupButton(reset);
        Runnable validate = () -> {
            boolean valid = !username.getText().isBlank()
                    && !recoveryCode.getText().isBlank()
                    && newPassword.getText().length() >= 8
                    && newPassword.getText().equals(confirmPassword.getText());
            resetButton.setDisable(!valid);
        };
        username.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        recoveryCode.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        newPassword.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        confirmPassword.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        validate.run();
        dialog.setResultConverter(button -> button == reset
                ? new PasswordReset(username.getText(), recoveryCode.getText().toCharArray(),
                        newPassword.getText().toCharArray())
                : null);

        dialog.showAndWait().ifPresent(request -> {
            try {
                AuthStore.AccountSetup account = authStore.resetPassword(
                        request.username(), request.recoveryCode(), request.newPassword());
                showAuthScreen(AuthScreen.LOGIN);
                showInfo("Password reset", "Your new password is saved. Sign in with it and save your new recovery code.");
                showRecoveryCode(account.recoveryCode());
            } catch (IllegalArgumentException | IOException exception) {
                showError("Could not reset password", exception.getMessage());
            } finally {
                java.util.Arrays.fill(request.recoveryCode(), '\0');
                java.util.Arrays.fill(request.newPassword(), '\0');
            }
        });
    }

    private void showRecoveryCode(String recoveryCode) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Save your recovery code");
        dialog.setHeaderText("Keep this code somewhere safe");
        ButtonType copy = new ButtonType("Copy code", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(copy, ButtonType.CLOSE);

        Label instructions = new Label(
                "You will need this code if you forget your password. It is shown only once and cannot be retrieved later.");
        instructions.setWrapText(true);
        instructions.getStyleClass().add("recovery-instructions");
        TextField code = new TextField(recoveryCode);
        code.setEditable(false);
        code.setFocusTraversable(false);
        code.getStyleClass().add("recovery-code");
        VBox content = new VBox(13, instructions, code);
        content.getStyleClass().add("checkout-form");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(460);

        Button copyButton = (Button) dialog.getDialogPane().lookupButton(copy);
        copyButton.getStyleClass().add("secondary-button");
        copyButton.addEventFilter(ActionEvent.ACTION, event -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(recoveryCode);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            copyButton.setText("Copied");
            event.consume();
        });
        dialog.showAndWait();
    }

    private record PasswordReset(String username, char[] recoveryCode, char[] newPassword) {}

    private HBox featureLine(String number, String text) {
        Label badge = new Label(number);
        badge.getStyleClass().add("feature-number");
        Label label = new Label(text);
        label.getStyleClass().add("feature-text");
        return new HBox(12, badge, label);
    }

    private VBox authField(String label, Control field) {
        Label caption = new Label(label);
        caption.getStyleClass().add("auth-field-label");
        return new VBox(7, caption, field);
    }

    private void showWorkspace(AuthStore.User user) {
        currentUser = user;
        root.getStyleClass().setAll("workspace-root");
        root.setLeft(createSidebar());
        root.setTop(createHeader());
        if (user.role() == AuthStore.Role.ADMIN) {
            showPage("Overview", createDashboard());
        } else {
            showPage("Find medicine", createBilling());
        }
    }

    private VBox createDashboard() {
        LocalDate today = LocalDate.now();
        List<MedicineBatch> batches = store.getBatches();
        List<MedicineBatch> inStock = batches.stream().filter(batch -> batch.quantity() > 0).toList();
        int units = inStock.stream().mapToInt(MedicineBatch::quantity).sum();
        long medicineCount = inStock.stream().map(MedicineBatch::medicineId).distinct().count();
        long nearExpiry = batches.stream()
                .filter(batch -> batch.quantity() > 0)
                .filter(batch -> !batch.expiryDate().isBefore(today))
                .filter(batch -> !batch.expiryDate().isAfter(today.plusDays(30)))
                .count();
        long expired = batches.stream()
                .filter(batch -> batch.quantity() > 0)
                .filter(batch -> batch.expiryDate().isBefore(today))
                .count();
        long lowStock = batches.stream()
                .filter(batch -> !batch.expiryDate().isBefore(today))
                .collect(Collectors.groupingBy(MedicineBatch::medicineId, Collectors.summingInt(MedicineBatch::quantity)))
                .values().stream().filter(quantity -> quantity > 0 && quantity <= LOW_STOCK_LIMIT).count();
        BigDecimal expiredValue = store.expiredStockValue();

        VBox page = pageHeading("Overview", "A clear view of what's in stock and what needs attention.");
        HBox welcomeBanner = new HBox(20);
        welcomeBanner.getStyleClass().add("dashboard-banner");
        welcomeBanner.setAlignment(Pos.CENTER_LEFT);
        VBox welcomeCopy = new VBox(7,
                new Label("Your pharmacy, at a glance."),
                new Label("Track inventory, stay on top of expiry dates, and keep the counter moving."));
        welcomeCopy.getStyleClass().add("banner-copy");
        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);
        Button createBill = primaryButton("Create a bill  →");
        createBill.getStyleClass().add("banner-button");
        createBill.setOnAction(event -> showPage("Billing", createBilling()));
        welcomeBanner.getChildren().addAll(welcomeCopy, bannerSpacer, createBill);
        HBox cards = new HBox(16,
                metricCard("Medicines in stock", Long.toString(medicineCount), "Unique medicines with stock", "blue"),
                metricCard("Units in stock", Integer.toString(units), "Across non-empty batches", "green"),
                metricCard("Near expiry", Long.toString(nearExpiry), "In-stock batches · next 30 days", "amber"),
                metricCard("Expired batches", Long.toString(expired), "In-stock batches · do not sell", "red"),
                metricCard("Expired stock value", money(expiredValue), "Estimated at listed price", "purple"));
        cards.setMaxWidth(Double.MAX_VALUE);

        VBox stockHealthPanel = panel("Stock health", "Inventory units grouped by expiry and stock status.");
        Node stockHealthChart = createStockHealthChart(batches, today);
        stockHealthPanel.getChildren().add(stockHealthChart);
        VBox salesTrendPanel = panel("Popular medicines", "Units sold in the last 30 days.");
        Node salesChart = createSalesChart(store.getSales(), LocalDateTime.now());
        salesTrendPanel.getChildren().add(salesChart);
        HBox analytics = new HBox(16, stockHealthPanel, salesTrendPanel);
        HBox.setHgrow(stockHealthPanel, Priority.ALWAYS);
        HBox.setHgrow(salesTrendPanel, Priority.ALWAYS);
        stockHealthPanel.setMinWidth(0);
        salesTrendPanel.setMinWidth(0);

        VBox reorderPanel = panel("Smart restock planner",
                "Estimates use 30-day sales and a 14-day demand target, with a 20-unit minimum.");
        List<RestockRecommendation> recommendations =
                buildRestockRecommendations(batches, store.getSales(), LocalDateTime.now());
        Button openSupplierOrders = secondaryButton("Review supplier orders");
        openSupplierOrders.setOnAction(event -> showPage("Orders", createOrders()));
        HBox reorderHeader = new HBox(10,
                new Label(recommendations.isEmpty()
                        ? "Stock levels look good."
                        : recommendations.size() + " medicine(s) may need attention"),
                spacer(), openSupplierOrders);
        reorderHeader.setAlignment(Pos.CENTER_LEFT);
        reorderHeader.getStyleClass().add("insight-toolbar");
        reorderPanel.getChildren().add(reorderHeader);
        if (recommendations.isEmpty()) {
            reorderPanel.getChildren().add(emptyState(
                    "No restock recommendations", "Low-stock and fast-selling medicines will appear here."));
        } else {
            for (RestockRecommendation recommendation : recommendations.stream().limit(5).toList()) {
                reorderPanel.getChildren().add(restockRecommendationRow(recommendation, openSupplierOrders));
            }
        }

        VBox attention = panel("Needs attention", "Expiry and low-stock items");
        List<MedicineBatch> attentionBatches = batches.stream()
                .filter(batch -> batch.quantity() > 0)
                .filter(batch -> batch.expiryDate().isBefore(today.plusDays(31))
                        || batch.quantity() <= LOW_STOCK_LIMIT)
                .sorted(Comparator.comparing(MedicineBatch::expiryDate))
                .limit(6)
                .toList();
        TableView<MedicineBatch> attentionTable = createMedicineTable(false);
        attentionTable.getItems().setAll(attentionBatches);
        Label lowStockSummary = new Label(lowStock + " medicine(s) at or below " + LOW_STOCK_LIMIT + " units");
        lowStockSummary.getStyleClass().add("muted");
        attention.getChildren().addAll(attentionTable, lowStockSummary);

        VBox salesPanel = panel("Recent sales", "Latest completed bills");
        List<InventoryStore.SaleItem> recentSales = store.getSales().stream()
                .sorted(Comparator.comparing(InventoryStore.SaleItem::soldAt).reversed())
                .limit(5)
                .toList();
        if (recentSales.isEmpty()) {
            salesPanel.getChildren().add(emptyState("No bills yet", "Completed purchases will appear here."));
        } else {
            for (InventoryStore.SaleItem sale : recentSales) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(sale.name() + "  ·  " + sale.quantity() + " units");
                name.getStyleClass().add("sale-name");
                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                Label total = new Label(money(sale.lineTotal()));
                total.getStyleClass().add("sale-total");
                row.getChildren().addAll(name, gap, total);
                salesPanel.getChildren().add(row);
            }
        }

        page.getChildren().addAll(welcomeBanner, cards, analytics, reorderPanel, attention, salesPanel);
        return page;
    }

    private Node createStockHealthChart(List<MedicineBatch> batches, LocalDate today) {
        Map<String, Integer> availableByMedicine = new HashMap<>();
        for (MedicineBatch batch : batches) {
            if (batch.quantity() > 0 && !batch.expiryDate().isBefore(today)) {
                availableByMedicine.merge(batch.medicineId(), batch.quantity(), Integer::sum);
            }
        }
        int expiredUnits = 0;
        int nearExpiryUnits = 0;
        int lowStockUnits = 0;
        int safeUnits = 0;
        for (MedicineBatch batch : batches) {
            if (batch.quantity() <= 0) {
                continue;
            }
            if (batch.expiryDate().isBefore(today)) {
                expiredUnits += batch.quantity();
            } else if (!batch.expiryDate().isAfter(today.plusDays(30))) {
                nearExpiryUnits += batch.quantity();
            } else if (availableByMedicine.getOrDefault(batch.medicineId(), 0) <= LOW_STOCK_LIMIT) {
                lowStockUnits += batch.quantity();
            } else {
                safeUnits += batch.quantity();
            }
        }
        if (expiredUnits + nearExpiryUnits + lowStockUnits + safeUnits == 0) {
            return chartEmptyState("No stock to visualize", "Inventory health appears after stock is added.");
        }
        PieChart.Data safe = pieSlice("Safe", safeUnits);
        PieChart.Data low = pieSlice("Low stock", lowStockUnits);
        PieChart.Data nearExpiry = pieSlice("Near expiry", nearExpiryUnits);
        PieChart.Data expired = pieSlice("Expired", expiredUnits);
        PieChart chart = new PieChart(FXCollections.observableArrayList(safe, low, nearExpiry, expired));
        chart.setTitle("Inventory units");
        chart.setLegendVisible(true);
        chart.setLabelsVisible(false);
        chart.setAnimated(false);
        chart.setMinHeight(250);
        chart.setPrefHeight(270);
        chart.setMaxHeight(300);
        for (PieChart.Data slice : chart.getData()) {
            slice.nodeProperty().addListener((observable, oldNode, newNode) ->
                    makeChartNodeInteractive(newNode, slice.getName(),
                            String.format(Locale.ROOT, "%,.0f units in this category.", slice.getPieValue())));
            makeChartNodeInteractive(slice.getNode(), slice.getName(),
                    String.format(Locale.ROOT, "%,.0f units in this category.", slice.getPieValue()));
        }
        return chart;
    }

    private PieChart.Data pieSlice(String name, int units) {
        return new PieChart.Data(name + " · " + units, units);
    }

    private Node createSalesChart(
            List<InventoryStore.SaleItem> sales, LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(30);
        Map<String, Integer> quantityByMedicine = new HashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        for (InventoryStore.SaleItem sale : sales) {
            if (!sale.soldAt().isBefore(cutoff) && !sale.soldAt().isAfter(now)) {
                quantityByMedicine.merge(sale.medicineId(), sale.quantity(), Integer::sum);
                displayNames.putIfAbsent(sale.medicineId(), sale.name());
            }
        }
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        quantityByMedicine.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(entry -> series.getData().add(new XYChart.Data<>(
                        displayNames.get(entry.getKey()) + " (" + entry.getKey() + ")", entry.getValue())));

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Medicine");
        xAxis.setTickLabelRotation(-18);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Units sold");
        yAxis.setLowerBound(0);
        yAxis.setTickUnit(1);
        yAxis.setMinorTickVisible(false);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Top sellers · 30 days");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setBarGap(8);
        chart.setCategoryGap(24);
        chart.setMinHeight(250);
        chart.setPrefHeight(270);
        chart.setMaxHeight(300);
        if (series.getData().isEmpty()) {
            return chartEmptyState("No recent sales", "Completed sales appear here for 30 days.");
        }
        chart.getData().add(series);
        for (XYChart.Data<String, Number> data : series.getData()) {
            data.nodeProperty().addListener((observable, oldNode, newNode) ->
                    makeChartNodeInteractive(newNode, data.getXValue(),
                            String.format(Locale.ROOT, "%,.0f units sold in the last 30 days.",
                                    data.getYValue().doubleValue())));
            makeChartNodeInteractive(data.getNode(), data.getXValue(),
                    String.format(Locale.ROOT, "%,.0f units sold in the last 30 days.",
                            data.getYValue().doubleValue()));
        }
        return chart;
    }

    private void makeChartNodeInteractive(Node node, String title, String detail) {
        if (node == null) {
            return;
        }
        node.setCursor(Cursor.HAND);
        node.setOnMouseClicked(event -> showInfo(title, detail));
    }

    private Label chartEmptyState(String title, String subtitle) {
        Label state = emptyState(title, subtitle);
        state.setMinHeight(250);
        state.setPrefHeight(270);
        state.setMaxHeight(300);
        return state;
    }

    private List<RestockRecommendation> buildRestockRecommendations(
            List<MedicineBatch> batches, List<InventoryStore.SaleItem> sales,
            LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(30);
        Map<String, Integer> recentDemand = new HashMap<>();
        Map<String, MedicineBatch> products = new HashMap<>();
        for (MedicineBatch batch : batches) {
            products.putIfAbsent(batch.medicineId(), batch);
        }
        for (InventoryStore.SaleItem sale : sales) {
            if (!sale.soldAt().isBefore(cutoff) && !sale.soldAt().isAfter(now)) {
                recentDemand.merge(sale.medicineId(), sale.quantity(), Integer::sum);
            }
        }
        return products.values().stream()
                .map(product -> {
                    int available = store.availableQuantity(product.medicineId());
                    int soldLast30Days = recentDemand.getOrDefault(product.medicineId(), 0);
                    double daysOfStock = soldLast30Days == 0
                            ? (available == 0 ? 0 : Double.POSITIVE_INFINITY)
                            : available * 30.0 / soldLast30Days;
                    int targetStock = Math.max(LOW_STOCK_LIMIT * 2,
                            (int) Math.ceil(soldLast30Days * 14.0 / 30.0));
                    int suggestedQuantity = Math.max(0, targetStock - available);
                    boolean lowStock = available <= LOW_STOCK_LIMIT;
                    boolean demandRisk = soldLast30Days > 0 && daysOfStock < 14;
                    return new RestockRecommendation(product, soldLast30Days,
                            daysOfStock, suggestedQuantity, lowStock || demandRisk);
                })
                .filter(RestockRecommendation::needsAttention)
                .sorted(Comparator.comparingDouble(RestockRecommendation::daysOfStock)
                        .thenComparing(recommendation -> recommendation.medicine().name(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private HBox restockRecommendationRow(
            RestockRecommendation recommendation, Button openSupplierOrders) {
        VBox details = new VBox(4);
        Label medicine = new Label(recommendation.medicine().name()
                + " · " + recommendation.medicine().medicineId());
        medicine.getStyleClass().add("insight-medicine");
        Label reason = new Label(recommendation.daysOfStock() == 0
                ? "No sellable stock"
                : Double.isInfinite(recommendation.daysOfStock())
                ? "Low stock · no recent sales recorded"
                : String.format(Locale.ROOT, "%.0f days of stock · %d sold in 30 days",
                        recommendation.daysOfStock(), recommendation.soldLast30Days()));
        reason.getStyleClass().add("insight-reason");
        details.getChildren().addAll(medicine, reason);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Button suggested = secondaryButton("Restock +" + recommendation.suggestedQuantity());
        suggested.getStyleClass().add("insight-quantity");
        suggested.setOnAction(event -> openSupplierOrders.fire());
        HBox row = new HBox(12, details, gap, suggested);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("insight-row");
        return row;
    }

    private record RestockRecommendation(
            MedicineBatch medicine, int soldLast30Days,
            double daysOfStock, int suggestedQuantity, boolean needsAttention) {}

    private VBox createInventory() {
        requireAdmin();
        VBox page = pageHeading("Medicine inventory", "Manage products, batches and expiry dates.");
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        searchField = new TextField();
        searchField.setPromptText("Search by medicine name or category...");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        inventoryFilter = "All medicines";
        HBox filters = createInventoryFilters();
        searchField.textProperty().addListener((observable, oldValue, newValue) -> refreshInventory());
        Button add = primaryButton("+  Add medicine");
        add.setOnAction(event -> showAddMedicineDialog());
        toolbar.getChildren().addAll(searchField, add);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        inventoryTable = createMedicineTable(true);
        inventoryTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(MedicineBatch batch, boolean empty) {
                super.updateItem(batch, empty);
                boolean colorAll = "All medicines".equals(inventoryFilter) && !empty && batch != null;
                PseudoClass expired = PseudoClass.getPseudoClass("expired");
                PseudoClass nearExpiry = PseudoClass.getPseudoClass("near-expiry");
                PseudoClass lowStock = PseudoClass.getPseudoClass("low-stock");
                PseudoClass safe = PseudoClass.getPseudoClass("safe-stock");
                pseudoClassStateChanged(expired, colorAll && batch.expiryDate().isBefore(LocalDate.now()));
                pseudoClassStateChanged(nearExpiry, colorAll && !batch.expiryDate().isBefore(LocalDate.now())
                        && !batch.expiryDate().isAfter(LocalDate.now().plusDays(30)));
                pseudoClassStateChanged(lowStock, colorAll && !batch.expiryDate().isBefore(LocalDate.now())
                        && batch.expiryDate().isAfter(LocalDate.now().plusDays(30))
                        && batch.quantity() <= LOW_STOCK_LIMIT);
                pseudoClassStateChanged(safe, colorAll && !batch.expiryDate().isBefore(LocalDate.now())
                        && batch.expiryDate().isAfter(LocalDate.now().plusDays(30))
                        && batch.quantity() > LOW_STOCK_LIMIT);
            }
        });
        VBox content = panel("Stock list", "Each row represents one expiry-dated stock batch");
        Label legend = new Label("RED  Expired     ORANGE  Expires within 30 days     BLUE  Low stock (10 or fewer)     GREEN  Safe stock");
        legend.getStyleClass().add("inventory-legend");
        VBox.setMargin(filters, new Insets(3, 0, 0, 0));
        content.getChildren().addAll(toolbar, filters, legend, inventoryTable);
        VBox.setVgrow(inventoryTable, Priority.ALWAYS);
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox outOfStockPanel = panel("Out of stock", "Your saved medicines with no sellable quantity. Restock them without re-entering their details.");
        outOfStockTable = createOutOfStockTable();
        VBox.setVgrow(outOfStockTable, Priority.ALWAYS);
        outOfStockPanel.getChildren().add(outOfStockTable);
        page.getChildren().addAll(content, outOfStockPanel);
        refreshInventory();
        refreshOutOfStock();
        return page;
    }

    private HBox createInventoryFilters() {
        ToggleGroup group = new ToggleGroup();
        HBox filters = new HBox(7);
        filters.getStyleClass().add("filter-bar");
        for (String filter : List.of("All medicines", "Near expiry", "Expired", "Low stock")) {
            ToggleButton button = new ToggleButton(filter);
            button.setToggleGroup(group);
            button.getStyleClass().add("filter-chip");
            button.setSelected(filter.equals(inventoryFilter));
            button.setOnAction(event -> {
                group.selectToggle(button);
                inventoryFilter = filter;
                refreshInventory();
            });
            filters.getChildren().add(button);
        }
        return filters;
    }

    private VBox createCustomerDeliveries() {
        requireCustomer();
        return createDeliveryPage(true);
    }

    private VBox createAdminDeliveries() {
        requireAdmin();
        return createDeliveryPage(false);
    }

    private VBox createDeliveryPage(boolean customerView) {
        List<DeliveryStore.DeliveryOrder> orders = customerView
                ? deliveryStore.getOrdersForCustomer(currentUser.username())
                : deliveryStore.getAllOrders();
        String title = customerView ? "My deliveries" : "Customer deliveries";
        VBox page = pageHeading(title, customerView
                ? "Track the status of orders placed from your account."
                : "Update delivery status so each customer can track their own order.");
        long placed = orders.stream().filter(order -> order.status() == DeliveryStore.Status.PLACED).count();
        long inTransit = orders.stream().filter(order -> order.status() == DeliveryStore.Status.IN_TRANSIT).count();
        long delivered = orders.stream().filter(order -> order.status() == DeliveryStore.Status.DELIVERED).count();
        HBox summaries = new HBox(14,
                metricCard("Just placed", Long.toString(placed), "Order received", "blue"),
                metricCard("In transit", Long.toString(inTransit), "On its way", "amber"),
                metricCard("Delivered", Long.toString(delivered), "Completed orders", "green"));
        VBox panel = panel(customerView ? "Your order history" : "All customer orders",
                customerView ? "Every checkout creates a separate order. Only orders placed by "
                        + currentUser.username() + " are shown here."
                        : "Customers see only their own orders; use the action to advance delivery.");
        if (customerView) {
            Button shopAgain = primaryButton("+  Place another order");
            shopAgain.setOnAction(event -> showPage("Find medicine", createBilling()));
            HBox orderToolbar = new HBox(shopAgain);
            orderToolbar.setAlignment(Pos.CENTER_RIGHT);
            panel.getChildren().add(orderToolbar);
        }
        TableView<DeliveryStore.DeliveryOrder> table = new TableView<>();
        table.getColumns().addAll(
                deliveryColumn("Order", order -> "#" + order.deliveryId(), 75),
                customerView ? deliveryColumn("Bill", order -> "#" + order.saleId(), 80)
                        : deliveryColumn("Customer", DeliveryStore.DeliveryOrder::customerUsername, 115),
                deliveryColumn("Items", DeliveryStore.DeliveryOrder::items, 200),
                deliveryColumn("Total", order -> money(order.total()), 90),
                deliveryColumn("Placed", order -> order.placedAt().format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), 145));
        table.getColumns().add(deliveryStatusColumn());
        if (customerView) {
            table.getColumns().add(deliveryColumn("Tracking", order -> deliveryTracking(order.status()), 210));
        } else {
            table.getColumns().add(deliveryActionColumn(table));
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyState(customerView ? "No deliveries yet" : "No customer deliveries yet",
                customerView ? "Your completed purchases will appear here with their delivery status."
                        : "Customer checkouts appear here for staff to update."));
        table.getItems().setAll(orders);
        if (customerView) {
            table.setFixedCellSize(46);
            sizeTableToRows(table, orders.size(), 420, 132);
        } else {
            VBox.setVgrow(table, Priority.ALWAYS);
        }
        panel.getChildren().add(table);
        page.getChildren().addAll(summaries, panel);
        return page;
    }

    private String deliveryTracking(DeliveryStore.Status status) {
        return switch (status) {
            case PLACED -> "✓ Order placed   ·   ○ In transit   ·   ○ Delivered";
            case IN_TRANSIT -> "✓ Order placed   ·   ✓ In transit   ·   ○ Delivered";
            case DELIVERED -> "✓ Order placed   ·   ✓ In transit   ·   ✓ Delivered";
        };
    }

    private TableColumn<DeliveryStore.DeliveryOrder, String> deliveryColumn(
            String title, java.util.function.Function<DeliveryStore.DeliveryOrder, String> value, double width) {
        TableColumn<DeliveryStore.DeliveryOrder, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<DeliveryStore.DeliveryOrder, String> deliveryStatusColumn() {
        TableColumn<DeliveryStore.DeliveryOrder, String> column =
                deliveryColumn("Status", order -> order.status().label(), 100);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("order-placed", "order-transit", "order-delivered");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status);
                    getStyleClass().add(switch (status) {
                        case "Placed" -> "order-placed";
                        case "In transit" -> "order-transit";
                        default -> "order-delivered";
                    });
                }
            }
        });
        return column;
    }

    private TableColumn<DeliveryStore.DeliveryOrder, Void> deliveryActionColumn(
            TableView<DeliveryStore.DeliveryOrder> table) {
        TableColumn<DeliveryStore.DeliveryOrder, Void> column = new TableColumn<>("Update status");
        column.setPrefWidth(140);
        column.setCellValueFactory(cell -> new SimpleObjectProperty<>(null));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button action = secondaryButton("");

            {
                action.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        advanceDelivery(getTableView().getItems().get(getIndex()), table);
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                DeliveryStore.DeliveryOrder order = getTableView().getItems().get(getIndex());
                action.setText(switch (order.status()) {
                    case PLACED -> "Mark in transit";
                    case IN_TRANSIT -> "Mark delivered";
                    case DELIVERED -> "Delivered";
                });
                action.setDisable(order.status() == DeliveryStore.Status.DELIVERED);
                setGraphic(action);
            }
        });
        return column;
    }

    private void advanceDelivery(DeliveryStore.DeliveryOrder order, TableView<DeliveryStore.DeliveryOrder> table) {
        requireAdmin();
        try {
            DeliveryStore.Status nextStatus = switch (order.status()) {
                case PLACED -> DeliveryStore.Status.IN_TRANSIT;
                case IN_TRANSIT -> DeliveryStore.Status.DELIVERED;
                case DELIVERED -> DeliveryStore.Status.DELIVERED;
            };
            if (nextStatus != order.status()) {
                deliveryStore.updateStatus(order.deliveryId(), nextStatus);
                table.getItems().setAll(deliveryStore.getAllOrders());
            }
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not update delivery", exception.getMessage());
        }
    }

    private VBox createOrders() {
        requireAdmin();
        VBox page = pageHeading("Supplier orders", "Track incoming stock from order placement through delivery.");
        List<OrderStore.SupplierOrder> orders = orderStore.getOrders();
        long placed = orders.stream().filter(order -> order.status() == OrderStore.Status.PLACED).count();
        long inTransit = orders.stream().filter(order -> order.status() == OrderStore.Status.IN_TRANSIT).count();
        long delivered = orders.stream().filter(order -> order.status() == OrderStore.Status.DELIVERED).count();
        HBox summaries = new HBox(14,
                metricCard("Placed", Long.toString(placed), "Waiting for supplier", "blue"),
                metricCard("In transit", Long.toString(inTransit), "On the way to pharmacy", "amber"),
                metricCard("Delivered", Long.toString(delivered), "Orders received", "green"));
        VBox orderPanel = panel("Order history", "Mark orders in transit, then receive them when delivered.");
        Button placeOrder = primaryButton("+  Place supplier order");
        placeOrder.setOnAction(event -> showPlaceOrderDialog());
        HBox orderToolbar = new HBox(placeOrder);
        orderToolbar.setAlignment(Pos.CENTER_RIGHT);

        TableView<OrderStore.SupplierOrder> table = new TableView<>();
        table.getColumns().addAll(
                orderColumn("Order", order -> "#" + order.orderId(), 65),
                orderColumn("Medicine", order -> order.medicineName() + " · " + order.medicineId(), 145),
                orderColumn("Supplier", OrderStore.SupplierOrder::supplier, 110),
                orderColumn("Qty", order -> Integer.toString(order.quantity()), 45),
                orderColumn("Placed", order -> order.placedAt().format(DateTimeFormatter.ofPattern("dd MMM")), 68),
                orderColumn("Expected", order -> order.expectedDelivery().format(DateTimeFormatter.ofPattern("dd MMM")), 68),
                orderStatusColumn());
        table.getColumns().add(orderActionColumn(table));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyState("No supplier orders yet", "Place an order to start tracking its delivery."));
        refreshOrders(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        orderPanel.getChildren().addAll(orderToolbar, table);
        page.getChildren().addAll(summaries, orderPanel);
        return page;
    }

    private TableColumn<OrderStore.SupplierOrder, String> orderColumn(
            String title, java.util.function.Function<OrderStore.SupplierOrder, String> value, double width) {
        TableColumn<OrderStore.SupplierOrder, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<OrderStore.SupplierOrder, String> orderStatusColumn() {
        TableColumn<OrderStore.SupplierOrder, String> column =
                orderColumn("Status", order -> order.status().label(), 100);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("order-placed", "order-transit", "order-delivered");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status);
                    getStyleClass().add(switch (status) {
                        case "Placed" -> "order-placed";
                        case "In transit" -> "order-transit";
                        default -> "order-delivered";
                    });
                }
            }
        });
        return column;
    }

    private TableColumn<OrderStore.SupplierOrder, Void> orderActionColumn(
            TableView<OrderStore.SupplierOrder> table) {
        TableColumn<OrderStore.SupplierOrder, Void> column = new TableColumn<>("Next step");
        column.setPrefWidth(115);
        column.setCellValueFactory(cell -> new SimpleObjectProperty<>(null));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button action = secondaryButton("");

            {
                action.setOnAction(event -> {
                    OrderStore.SupplierOrder order = getTableView().getItems().get(getIndex());
                    advanceOrder(order, table);
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                OrderStore.SupplierOrder order = getTableView().getItems().get(getIndex());
                action.setText(switch (order.status()) {
                    case PLACED -> "Mark in transit";
                    case IN_TRANSIT -> "Receive order";
                    case DELIVERED -> "Delivered";
                });
                action.setDisable(order.status() == OrderStore.Status.DELIVERED);
                setGraphic(action);
            }
        });
        return column;
    }

    private void refreshOrders(TableView<OrderStore.SupplierOrder> table) {
        table.getItems().setAll(orderStore.getOrders().stream()
                .sorted(Comparator.comparing(OrderStore.SupplierOrder::placedAt).reversed())
                .toList());
    }

    private void advanceOrder(OrderStore.SupplierOrder order, TableView<OrderStore.SupplierOrder> table) {
        requireAdmin();
        try {
            if (order.status() == OrderStore.Status.PLACED) {
                orderStore.updateStatus(order.orderId(), OrderStore.Status.IN_TRANSIT);
            } else if (order.status() == OrderStore.Status.IN_TRANSIT) {
                showDeliveredBatchDialog(order, table);
            }
            refreshOrders(table);
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not update order", exception.getMessage());
        }
    }

    private void showDeliveredBatchDialog(OrderStore.SupplierOrder order, TableView<OrderStore.SupplierOrder> table) {
        requireAdmin();
        DatePicker expiry = new DatePicker(order.expectedDelivery().plusYears(1));
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Receive supplier order");
        dialog.setHeaderText("Enter the expiry date printed on the delivered batch.");
        ButtonType receive = new ButtonType("Receive and add stock", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(receive, ButtonType.CANCEL);
        VBox content = new VBox(10,
                new Label(order.quantity() + " × " + order.medicineName() + " from " + order.supplier()),
                formField("Batch expiry date", expiry));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == receive ? expiry.getValue() : null);
        dialog.showAndWait().ifPresent(expiryDate -> {
            try {
                if (expiryDate.isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("The received batch expiry must be in the future.");
                }
                MedicineBatch product = store.getBatches().stream()
                        .filter(batch -> batch.medicineId().equalsIgnoreCase(order.medicineId()))
                        .findFirst()
                        .orElseGet(() -> new MedicineBatch(order.medicineId(), order.medicineName(),
                                order.category(), order.manufacturer(), order.unitPrice(),
                                0, expiryDate));
                store.receiveOrderBatch(order.orderId(), new MedicineBatch(product.medicineId(), product.name(),
                        product.category(), product.manufacturer(), product.price(),
                        order.quantity(), expiryDate));
                orderStore.updateStatus(order.orderId(), OrderStore.Status.DELIVERED);
                refreshOrders(table);
                showInfo("Order delivered", "Received " + order.quantity() + " × "
                        + order.medicineName() + ". Stock was updated using medicine ID "
                        + order.medicineId() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not receive order", exception.getMessage());
            }
        });
    }

    private VBox createBranchTransfers() {
        requireAdmin();
        VBox page = pageHeading("Sister branch transfers",
                "Request unavailable medicines from another branch and track the transfer until received.");
        List<BranchTransferStore.Transfer> transfers = branchTransferStore.getTransfers();
        long requested = transfers.stream()
                .filter(transfer -> transfer.status() == BranchTransferStore.Status.REQUESTED).count();
        long inTransit = transfers.stream()
                .filter(transfer -> transfer.status() == BranchTransferStore.Status.IN_TRANSIT).count();
        long received = transfers.stream()
                .filter(transfer -> transfer.status() == BranchTransferStore.Status.RECEIVED).count();
        HBox summaries = new HBox(14,
                metricCard("Requested", Long.toString(requested), "Awaiting branch confirmation", "blue"),
                metricCard("In transit", Long.toString(inTransit), "On the way to this pharmacy", "amber"),
                metricCard("Received", Long.toString(received), "Added to inventory", "green"));
        VBox transferPanel = panel("Transfer history", "Receiving a transfer adds its quantity and expiry-dated batch to stock.");
        Button newRequest = primaryButton("+  Request medicine");
        newRequest.setOnAction(event -> showBranchRequestDialog(null));
        HBox toolbar = new HBox(newRequest);
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        TableView<BranchTransferStore.Transfer> table = new TableView<>();
        table.getColumns().addAll(
                branchTransferColumn("Request", transfer -> "#" + transfer.transferId(), 75),
                branchTransferColumn("Medicine", transfer -> {
                    MedicineBatch medicine = store.medicineDetails(transfer.medicineId());
                    return medicine.name() + " · " + medicine.medicineId();
                }, 160),
                branchTransferColumn("Branch", BranchTransferStore.Transfer::branch, 130),
                branchTransferColumn("Qty", transfer -> Integer.toString(transfer.quantity()), 55),
                branchTransferColumn("Requested", transfer -> transfer.requestedAt()
                        .format(DateTimeFormatter.ofPattern("dd MMM")), 85),
                branchTransferColumn("Expected", transfer -> transfer.expectedArrival()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 105),
                branchTransferStatusColumn());
        table.getColumns().add(branchTransferActionColumn(table));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyState("No branch requests yet",
                "Use Inventory → Out of stock to request a medicine from a sister branch."));
        refreshBranchTransfers(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        transferPanel.getChildren().addAll(toolbar, table);
        page.getChildren().addAll(summaries, transferPanel);
        return page;
    }

    private TableColumn<BranchTransferStore.Transfer, String> branchTransferColumn(
            String title, java.util.function.Function<BranchTransferStore.Transfer, String> value, double width) {
        TableColumn<BranchTransferStore.Transfer, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<BranchTransferStore.Transfer, String> branchTransferStatusColumn() {
        TableColumn<BranchTransferStore.Transfer, String> column =
                branchTransferColumn("Status", transfer -> transfer.status().label(), 100);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("order-placed", "order-transit", "order-delivered");
                if (empty || status == null) {
                    setText(null);
                } else {
                    setText(status);
                    getStyleClass().add(switch (status) {
                        case "Requested" -> "order-placed";
                        case "In transit" -> "order-transit";
                        default -> "order-delivered";
                    });
                }
            }
        });
        return column;
    }

    private TableColumn<BranchTransferStore.Transfer, Void> branchTransferActionColumn(
            TableView<BranchTransferStore.Transfer> table) {
        TableColumn<BranchTransferStore.Transfer, Void> column = new TableColumn<>("Next step");
        column.setPrefWidth(140);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button action = secondaryButton("");

            {
                action.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        advanceBranchTransfer(getTableView().getItems().get(getIndex()), table);
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                BranchTransferStore.Transfer transfer = getTableView().getItems().get(getIndex());
                action.setText(switch (transfer.status()) {
                    case REQUESTED -> "Mark in transit";
                    case IN_TRANSIT -> "Receive transfer";
                    case RECEIVED -> "Received";
                });
                action.setDisable(transfer.status() == BranchTransferStore.Status.RECEIVED);
                setGraphic(action);
            }
        });
        return column;
    }

    private void refreshBranchTransfers(TableView<BranchTransferStore.Transfer> table) {
        table.getItems().setAll(branchTransferStore.getTransfers().stream()
                .sorted(Comparator.comparing(BranchTransferStore.Transfer::requestedAt).reversed())
                .toList());
    }

    private void advanceBranchTransfer(
            BranchTransferStore.Transfer transfer, TableView<BranchTransferStore.Transfer> table) {
        requireAdmin();
        try {
            if (transfer.status() == BranchTransferStore.Status.REQUESTED) {
                branchTransferStore.updateStatus(transfer.transferId(), BranchTransferStore.Status.IN_TRANSIT);
                refreshBranchTransfers(table);
                return;
            }
            if (transfer.status() == BranchTransferStore.Status.IN_TRANSIT) {
                showBranchTransferReceiptDialog(transfer, table);
            }
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not update branch transfer", exception.getMessage());
        }
    }

    private void showBranchTransferReceiptDialog(
            BranchTransferStore.Transfer transfer, TableView<BranchTransferStore.Transfer> table) {
        MedicineBatch medicine = store.medicineDetails(transfer.medicineId());
        DatePicker expiry = new DatePicker(LocalDate.now().plusYears(1));
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Receive branch transfer");
        dialog.setHeaderText("Enter the expiry date from the received medicine batch.");
        ButtonType receive = new ButtonType("Receive and add stock", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(receive, ButtonType.CANCEL);
        VBox content = new VBox(10,
                new Label(transfer.quantity() + " × " + medicine.name() + " from " + transfer.branch()),
                formField("Batch expiry date", expiry));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == receive ? expiry.getValue() : null);
        dialog.showAndWait().ifPresent(expiryDate -> {
            try {
                if (expiryDate == null || expiryDate.isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("The received batch expiry must be today or in the future.");
                }
                MedicineBatch receivedBatch = new MedicineBatch(
                        medicine.medicineId(), medicine.name(), medicine.category(), medicine.manufacturer(),
                        medicine.price(), transfer.quantity(), expiryDate, medicine.rackLocation());
                store.receiveOrderBatch(transfer.transferId(), receivedBatch);
                branchTransferStore.updateStatus(transfer.transferId(), BranchTransferStore.Status.RECEIVED);
                refreshBranchTransfers(table);
                refreshInventory();
                refreshOutOfStock();
                showInfo("Transfer received", transfer.quantity() + " units of " + medicine.name()
                        + " were added to inventory.");
            } catch (IOException | IllegalArgumentException exception) {
                refreshBranchTransfers(table);
                showError("Could not receive branch transfer", exception.getMessage());
            }
        });
    }

    private void showBranchRequestDialog(MedicineBatch medicine) {
        requireAdmin();
        if (medicine == null) {
            List<MedicineBatch> availableMedicines = store.getBatches().stream()
                    .collect(Collectors.toMap(MedicineBatch::medicineId, batch -> batch,
                            (first, ignored) -> first, java.util.TreeMap::new))
                    .values().stream()
                    .sorted(Comparator.comparing(MedicineBatch::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (availableMedicines.isEmpty()) {
                showError("No medicines available", "Add a medicine to inventory before requesting a branch transfer.");
                return;
            }
            ComboBox<MedicineBatch> selector = new ComboBox<>(FXCollections.observableArrayList(availableMedicines));
            selector.setValue(availableMedicines.getFirst());
            selector.setCellFactory(list -> medicineChoiceCell());
            selector.setButtonCell(medicineChoiceCell());
            Dialog<MedicineBatch> choice = new Dialog<>();
            choice.setTitle("Request from sister branch");
            choice.setHeaderText("Select the medicine to request.");
            ButtonType select = new ButtonType("Continue", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            choice.getDialogPane().getButtonTypes().addAll(select, ButtonType.CANCEL);
            choice.getDialogPane().setContent(formField("Medicine", selector));
            choice.setResultConverter(button -> button == select ? selector.getValue() : null);
            medicine = choice.showAndWait().orElse(null);
            if (medicine == null) {
                return;
            }
        }
        TextField branch = new TextField();
        branch.setPromptText("e.g. North Street Branch");
        Spinner<Integer> quantity = new Spinner<>();
        quantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 20));
        quantity.setEditable(true);
        DatePicker expected = new DatePicker(LocalDate.now().plusDays(3));
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Request from sister branch");
        dialog.setHeaderText(medicine.name() + " · " + medicine.medicineId());
        ButtonType request = new ButtonType("Create request", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(request, ButtonType.CANCEL);
        VBox form = new VBox(10, formField("Sister branch", branch), formField("Quantity", quantity),
                formField("Expected arrival", expected));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.setResultConverter(button -> button == request);
        MedicineBatch selectedMedicine = medicine;
        dialog.showAndWait().filter(Boolean::booleanValue).ifPresent(ignored -> {
            try {
                branchTransferStore.request(selectedMedicine, branch.getText(), quantity.getValue(), expected.getValue());
                showPage("Sister branches", createBranchTransfers());
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not create branch request", exception.getMessage());
            }
        });
    }

    private javafx.scene.control.ListCell<MedicineBatch> medicineChoiceCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(MedicineBatch item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " · " + item.medicineId());
            }
        };
    }

    private void showPlaceOrderDialog() {
        requireAdmin();
        Map<String, MedicineBatch> products = new HashMap<>();
        store.getBatches().forEach(batch -> products.putIfAbsent(batch.medicineId(), batch));

        ComboBox<MedicineChoice> medicine = new ComboBox<>();
        medicine.setMaxWidth(Double.MAX_VALUE);
        products.values().stream().sorted(Comparator.comparing(MedicineBatch::name))
                .map(batch -> new MedicineChoice(batch.medicineId(), batch.name()))
                .forEach(medicine.getItems()::add);
        MedicineChoice otherMedicine = new MedicineChoice("__NEW__", "Other / add a new medicine...");
        medicine.getItems().add(otherMedicine);
        if (!medicine.getItems().isEmpty()) {
            medicine.getSelectionModel().selectFirst();
        }
        TextField supplier = new TextField();
        supplier.setPromptText("Supplier or distributor name");
        Spinner<Integer> quantity = new Spinner<>();
        quantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 20));
        quantity.setEditable(true);
        DatePicker expected = new DatePicker(LocalDate.now().plusDays(7));
        TextField newId = new TextField();
        newId.setPromptText("Unique medicine ID");
        TextField newName = new TextField();
        newName.setPromptText("Medicine name");
        TextField newCategory = new TextField();
        newCategory.setPromptText("Category");
        TextField newManufacturer = new TextField();
        newManufacturer.setPromptText("Manufacturer");
        TextField newPrice = new TextField();
        newPrice.setPromptText("Selling price per unit");
        VBox newMedicineFields = new VBox(9,
                formField("Medicine ID", newId), formField("Medicine name", newName),
                formField("Category", newCategory), formField("Manufacturer", newManufacturer),
                formField("Selling price per unit (₹)", newPrice));
        newMedicineFields.setVisible(false);
        newMedicineFields.setManaged(false);
        medicine.valueProperty().addListener((observable, oldValue, newValue) -> {
            boolean isNewMedicine = newValue != null && newValue.medicineId().equals("__NEW__");
            newMedicineFields.setVisible(isNewMedicine);
            newMedicineFields.setManaged(isNewMedicine);
        });
        boolean initiallyNew = medicine.getValue() != null
                && medicine.getValue().medicineId().equals("__NEW__");
        newMedicineFields.setVisible(initiallyNew);
        newMedicineFields.setManaged(initiallyNew);

        ObservableList<SupplierDraft> draftItems = FXCollections.observableArrayList();
        TableView<SupplierDraft> draftTable = new TableView<>(draftItems);
        TableColumn<SupplierDraft, String> draftMedicine = new TableColumn<>("Medicine");
        draftMedicine.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().medicineName()));
        TableColumn<SupplierDraft, String> draftQuantity = new TableColumn<>("Qty");
        draftQuantity.setCellValueFactory(cell ->
                new SimpleStringProperty(Integer.toString(cell.getValue().quantity())));
        TableColumn<SupplierDraft, String> draftPrice = new TableColumn<>("Unit price");
        draftPrice.setCellValueFactory(cell -> new SimpleStringProperty(money(cell.getValue().unitPrice())));
        draftTable.getColumns().addAll(draftMedicine, draftQuantity, draftPrice);
        draftTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        draftTable.setPrefHeight(170);
        draftTable.setPlaceholder(emptyState("No medicines added", "Add one or more medicines to this order."));
        Button addLine = secondaryButton("Add medicine to order");
        addLine.setOnAction(event -> {
            try {
                MedicineChoice choice = medicine.getValue();
                if (choice == null) {
                    throw new IllegalArgumentException("Select a medicine.");
                }
                OrderStore.OrderRequest request;
                if (choice.medicineId().equals("__NEW__")) {
                    if (newId.getText().isBlank() || newName.getText().isBlank()
                            || newCategory.getText().isBlank() || newManufacturer.getText().isBlank()) {
                        throw new IllegalArgumentException(
                                "Enter the new medicine ID, name, category, and manufacturer.");
                    }
                    BigDecimal price = new BigDecimal(newPrice.getText().trim());
                    if (price.signum() <= 0) {
                        throw new IllegalArgumentException("Selling price must be greater than zero.");
                    }
                    request = new OrderStore.OrderRequest(newId.getText(), newName.getText(),
                            newCategory.getText(), newManufacturer.getText(),
                            price, quantity.getValue());
                } else {
                    MedicineBatch selected = store.medicineDetails(choice.medicineId());
                    request = new OrderStore.OrderRequest(selected.medicineId(), selected.name(),
                            selected.category(), selected.manufacturer(), selected.price(), quantity.getValue());
                }
                draftItems.add(new SupplierDraft(request));
                draftTable.refresh();
                newId.clear();
                newName.clear();
                newCategory.clear();
                newManufacturer.clear();
                newPrice.clear();
                quantity.getValueFactory().setValue(20);
                medicine.getSelectionModel().selectFirst();
            } catch (IllegalArgumentException exception) {
                showError("Check medicine details", exception.getMessage());
            }
        });
        Button removeLine = secondaryButton("Remove selected line");
        removeLine.setOnAction(event -> draftItems.remove(draftTable.getSelectionModel().getSelectedItem()));

        Dialog<List<OrderStore.OrderRequest>> dialog = new Dialog<>();
        dialog.setTitle("Place supplier order");
        dialog.setHeaderText("Add multiple medicines, then place them with one supplier.");
        ButtonType place = new ButtonType("Place order", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(place, ButtonType.CANCEL);
        VBox form = new VBox(10,
                formField("Supplier", supplier),
                new HBox(10, formField("Medicine", medicine), formField("Quantity", quantity)),
                newMedicineFields, addLine, draftTable, removeLine,
                formField("Expected delivery", expected));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(680);
        Button placeButton = (Button) dialog.getDialogPane().lookupButton(place);
        Runnable updatePlaceAvailability = () -> placeButton.setDisable(draftItems.isEmpty()
                || supplier.getText().isBlank() || expected.getValue() == null
                || expected.getValue().isBefore(LocalDate.now()));
        supplier.textProperty().addListener((observable, oldValue, newValue) -> updatePlaceAvailability.run());
        expected.valueProperty().addListener((observable, oldValue, newValue) -> updatePlaceAvailability.run());
        draftItems.addListener((javafx.collections.ListChangeListener<SupplierDraft>) change ->
                updatePlaceAvailability.run());
        updatePlaceAvailability.run();
        dialog.setResultConverter(button ->
                button == place ? draftItems.stream().map(SupplierDraft::request).toList() : null);
        dialog.showAndWait().ifPresent(requests -> {
            try {
                List<OrderStore.SupplierOrder> orders = orderStore.placeOrders(
                        requests, supplier.getText(), expected.getValue());
                showInfo("Order placed", orders.size() + " medicine line(s) placed. "
                        + "The orders are now marked Placed.");
                showPage("Orders", createOrders());
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not place order", exception.getMessage());
            }
        });
    }

    private VBox createBilling() {
        cart = FXCollections.observableArrayList();
        billingSearch = "";
        boolean customerView = currentUser.role() == AuthStore.Role.CUSTOMER;
        if (customerView) {
            cartStore.getCart(currentUser.username()).forEach((medicineId, quantity) -> {
                try {
                    MedicineBatch medicine = store.medicineDetails(medicineId);
                    cart.add(new CartRow(medicineId, medicine.name(), medicine.price(), quantity));
                } catch (IllegalArgumentException exception) {
                    showError("Saved cart item unavailable", exception.getMessage());
                }
            });
        }
        VBox page = pageHeading(customerView ? "Find your medicine" : "Customer billing",
                customerView ? "Search available medicines, add what you need, and review your bill."
                        : "Select medicines and quantities to create a customer bill.");

        VBox products = panel(customerView ? "Available medicines" : "Available stock",
                "Only non-expired medicines with stock can be purchased.");
        TextField productSearch = new TextField();
        productSearch.setPromptText("Search by medicine name or category...");
        productSearch.getStyleClass().add("search-field");
        productSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            billingSearch = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            refreshBillingProducts();
        });
        billingTable = new TableView<>();
        TableColumn<BillingRow, String> productColumn = stringColumn("Medicine", "name", 190);
        TableColumn<BillingRow, String> categoryColumn = stringColumn("Category", "category", 115);
        TableColumn<BillingRow, String> stockColumn = stringColumn("Available", "available", 85);
        TableColumn<BillingRow, String> priceColumn = stringColumn("Price", "price", 95);
        billingTable.getColumns().addAll(productColumn, categoryColumn, stockColumn, priceColumn);
        billingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        billingTable.setFixedCellSize(46);
        billingTable.setPlaceholder(emptyState("No medicines found", "Try another search or ask the pharmacy to restock."));
        refreshBillingProducts();

        HBox addRow = new HBox(10);
        addRow.setAlignment(Pos.CENTER_LEFT);
        Spinner<Integer> quantitySpinner = new Spinner<>();
        quantitySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, 1));
        quantitySpinner.setEditable(true);
        Button addToBill = primaryButton(customerView ? "Add to cart" : "Add selected");
        addToBill.setOnAction(event -> {
            BillingRow selected = billingTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Select a medicine", "Choose a medicine from the list before adding it.");
                return;
            }
            int quantity = quantitySpinner.getValue();
            CartRow existing = cart.stream()
                    .filter(item -> item.medicineId().equals(selected.medicineId()))
                    .findFirst().orElse(null);
            int total = quantity + (existing == null ? 0 : existing.quantity());
            if (total > selected.availableCount()) {
                showError("Not enough stock", "Only " + selected.availableCount() + " non-expired units are available.");
                return;
            }
            if (existing == null) {
                cart.add(new CartRow(selected.medicineId(), selected.getName(), selected.priceValue(), quantity));
            } else {
                existing.setQuantity(total);
                cartTable.refresh();
            }
            if (customerView && !persistCustomerCart()) {
                return;
            }
            refreshCartTotal();
        });
        addRow.getChildren().addAll(new Label("Quantity"), quantitySpinner, addToBill);
        products.getChildren().addAll(productSearch, billingTable, addRow);

        VBox billPanel = panel(customerView ? "Your shopping cart" : "Current bill",
                customerView ? "Your cart is saved to this account so you can come back later."
                        : "Review items before completing payment");
        cartTable = new TableView<>(cart);
        cartTable.getColumns().addAll(
                stringColumn("Medicine", "name", 150),
                stringColumn("Qty", "quantity", 55),
                stringColumn("Unit price", "price", 90),
                stringColumn("Total", "total", 90));
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cartTable.setFixedCellSize(46);
        cartTable.setPlaceholder(emptyState("Bill is empty", "Add a medicine to get started."));
        sizeTableToRows(cartTable, cart.size(), 320, 132);
        cart.addListener((javafx.collections.ListChangeListener<CartRow>) change ->
                sizeTableToRows(cartTable, cart.size(), 320, 132));
        subtotalLabel = new Label(money(BigDecimal.ZERO));
        subtotalLabel.getStyleClass().add("bill-total");
        refreshCartTotal();
        HBox totalRow = new HBox(10, new Label("Subtotal"), spacer(), subtotalLabel);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        Button remove = secondaryButton("Remove selected");
        remove.setOnAction(event -> {
            CartRow selected = cartTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cart.remove(selected);
                if (customerView && !persistCustomerCart()) {
                    return;
                }
                refreshCartTotal();
            }
        });
        Button checkout = primaryButton(customerView ? "Checkout & place order" : "Complete sale");
        checkout.setOnAction(event -> completeSale());
        HBox actions = new HBox(8, remove, checkout);
        actions.setAlignment(Pos.CENTER_RIGHT);
        billPanel.getChildren().addAll(cartTable, totalRow, actions);

        HBox columns = new HBox(18, products, billPanel);
        HBox.setHgrow(products, Priority.ALWAYS);
        HBox.setHgrow(billPanel, Priority.ALWAYS);
        products.setPrefWidth(0);
        products.setMinWidth(0);
        billPanel.setPrefWidth(0);
        billPanel.setMinWidth(0);
        VBox.setVgrow(columns, Priority.ALWAYS);
        page.getChildren().add(columns);
        return page;
    }

    private VBox createReports() {
        requireAdmin();
        VBox page = pageHeading("Reports & exports", "Download simple CSV files you can open in Excel or Google Sheets.");
        VBox guide = panel("Which report should I use?", "Pick a report based on the question you need to answer.");
        Label explanation = new Label(
                "Stock = what remains in inventory.   Expiry = what needs to be removed or checked soon.   Sales = what has already been sold.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("report-explanation");
        guide.getChildren().add(explanation);

        VBox stockPanel = reportCard(
                "01  ·  Stock on hand",
                "Current inventory: medicine, category, manufacturer, remaining quantity, price and expiry status. Empty batches are left out.",
                "Export stock CSV", "stock-report.csv", store::exportStock);
        VBox expiryPanel = reportCard(
                "02  ·  Expiry watch",
                "Medicines that still have stock and are expired or will expire within 30 days. Expired batches include an estimated value at listed price.",
                "Export expiry CSV", "expiry-report.csv", store::exportExpiry);
        VBox salesPanel = reportCard(
                "03  ·  Sales history",
                "Completed purchases: bill ID, sale time, medicine, quantity, unit price and line total.",
                "Export sales CSV", "sales-report.csv", store::exportSales);
        GridPane reportCards = new GridPane();
        reportCards.setHgap(16);
        reportCards.setMaxWidth(Double.MAX_VALUE);
        reportCards.getColumnConstraints().setAll(
                reportColumn(), reportColumn(), reportColumn());
        reportCards.addRow(0, stockPanel, expiryPanel, salesPanel);
        for (VBox card : List.of(stockPanel, expiryPanel, salesPanel)) {
            card.setMaxWidth(Double.MAX_VALUE);
            card.setMaxHeight(Double.MAX_VALUE);
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            GridPane.setFillHeight(card, true);
        }
        page.getChildren().addAll(guide, reportCards);
        return page;
    }

    private ColumnConstraints reportColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(33.333);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private VBox reportCard(String title, String description, String buttonText, String fileName, ExportAction action) {
        VBox card = panel(title, description);
        card.getStyleClass().add("report-card");
        Label format = new Label("CSV  ·  Opens in spreadsheet apps");
        format.getStyleClass().add("report-format");
        format.setMaxWidth(Double.MAX_VALUE);
        Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);
        Button export = primaryButton(buttonText);
        export.setMaxWidth(Double.MAX_VALUE);
        export.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save " + title.toLowerCase());
            chooser.setInitialFileName(fileName);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File destination = chooser.showSaveDialog(stage);
            if (destination != null) {
                try {
                    action.export(destination.toPath());
                    showInfo("Report exported", "Saved to " + destination.getAbsolutePath());
                } catch (IOException exception) {
                    showError("Could not export report", exception.getMessage());
                }
            }
        });
        card.getChildren().addAll(format, gap, export);
        return card;
    }

    private VBox pageHeading(String title, String subtitle) {
        VBox page = new VBox(18);
        page.setPadding(new Insets(26, 30, 30, 30));
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");
        Label subheading = new Label(subtitle);
        subheading.getStyleClass().add("page-subtitle");
        subheading.setWrapText(true);
        subheading.setMaxWidth(Double.MAX_VALUE);
        page.getChildren().addAll(new VBox(4, heading, subheading));
        return page;
    }

    private VBox metricCard(String title, String value, String description, String tone) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("metric-card", "metric-" + tone);
        Label label = new Label(title);
        label.getStyleClass().add("metric-label");
        Label number = new Label(value);
        number.getStyleClass().add("metric-value");
        Label note = new Label(description);
        note.getStyleClass().add("metric-note");
        card.getChildren().addAll(label, number, note);
        card.setMinWidth(0);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        note.setWrapText(true);
        note.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox panel(String title, String subtitle) {
        VBox panel = new VBox(14);
        panel.getStyleClass().add("panel");
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        Label caption = new Label(subtitle);
        caption.getStyleClass().add("panel-subtitle");
        caption.setWrapText(true);
        caption.setMaxWidth(Double.MAX_VALUE);
        panel.getChildren().addAll(new VBox(4, heading, caption));
        return panel;
    }

    private TableView<MedicineBatch> createMedicineTable(boolean inventoryControls) {
        TableView<MedicineBatch> table = new TableView<>();
        table.getStyleClass().add("medicine-list-table");
        table.getColumns().addAll(
                medicineColumn("Medicine", batch -> batch.name() + "  ·  " + batch.medicineId(), 175),
                medicineColumn("Category", MedicineBatch::category, 95),
                medicineColumn("Manufacturer", MedicineBatch::manufacturer, 130),
                medicineColumn("Qty", batch -> Integer.toString(batch.quantity()), 55),
                medicineColumn("Price", batch -> money(batch.price()), 80),
                medicineColumn("Expiry", batch -> batch.expiryDate().format(DATE_FORMAT), 95),
                medicineColumn("Status", batch -> inventoryStatus(batch), 90));
        if (inventoryControls) {
            table.getColumns().addAll(
                    medicineColumn("Rack", batch -> batch.rackLocation().isBlank() ? "—" : batch.rackLocation(), 80),
                    medicineEditColumn(table));
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyState("No stock to display", "Zero-quantity batches are hidden. Add more stock to see it here."));
        return table;
    }

    private TableView<MedicineBatch> createOutOfStockTable() {
        TableView<MedicineBatch> table = new TableView<>();
        table.getStyleClass().add("medicine-list-table");
        table.getColumns().addAll(
                medicineColumn("Medicine", batch -> batch.name() + "  ·  " + batch.medicineId(), 205),
                medicineColumn("Category", MedicineBatch::category, 145),
                medicineColumn("Manufacturer", MedicineBatch::manufacturer, 190),
                medicineColumn("Last listed price", batch -> money(batch.price()), 130),
                medicineColumn("Rack", batch -> batch.rackLocation().isBlank() ? "—" : batch.rackLocation(), 80),
                outOfStockActionColumn(table));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyState("Nothing is out of stock", "Previously stocked medicines will appear here when their sellable quantity reaches zero."));
        return table;
    }

    private TableColumn<MedicineBatch, Void> medicineEditColumn(TableView<MedicineBatch> table) {
        TableColumn<MedicineBatch, Void> column = new TableColumn<>("Manage");
        column.setPrefWidth(145);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button edit = secondaryButton("Edit price / rack");

            {
                edit.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        showMedicineDetailsDialog(getTableView().getItems().get(getIndex()));
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : edit);
            }
        });
        return column;
    }

    private TableColumn<MedicineBatch, Void> outOfStockActionColumn(TableView<MedicineBatch> table) {
        TableColumn<MedicineBatch, Void> column = new TableColumn<>("Action");
        column.setPrefWidth(235);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button restock = primaryButton("Restock");
            private final Button request = secondaryButton("Request from branch");
            private final HBox actions = new HBox(6, restock, request);

            {
                restock.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        showRestockDialog(getTableView().getItems().get(getIndex()));
                    }
                });
                request.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        showBranchRequestDialog(getTableView().getItems().get(getIndex()));
                    }
                });
            }

            @Override
            protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : actions);
            }
        });
        return column;
    }

    private void refreshOutOfStock() {
        if (outOfStockTable != null) {
            outOfStockTable.getItems().setAll(store.getOutOfStockMedicines());
        }
    }

    private void showRestockDialog(MedicineBatch medicine) {
        requireAdmin();
        Spinner<Integer> quantity = new Spinner<>();
        quantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 20));
        quantity.setEditable(true);
        DatePicker expiry = new DatePicker(LocalDate.now().plusYears(1));

        Dialog<MedicineBatch> dialog = new Dialog<>();
        dialog.setTitle("Restock " + medicine.name());
        dialog.setHeaderText("Medicine ID " + medicine.medicineId() + " · " + medicine.category());
        ButtonType save = new ButtonType("Add stock", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        VBox form = new VBox(11,
                new Label("The existing medicine details and ID will be reused."),
                formField("Quantity to add", quantity),
                formField("New batch expiry date", expiry));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.setResultConverter(button -> {
            if (button != save) {
                return null;
            }
            if (expiry.getValue() == null || expiry.getValue().isBefore(LocalDate.now())) {
                showError("Check expiry date", "Choose today or a future expiry date.");
                return null;
            }
            return new MedicineBatch(medicine.medicineId(), medicine.name(), medicine.category(),
                    medicine.manufacturer(), medicine.price(), quantity.getValue(), expiry.getValue());
        });
        dialog.showAndWait().ifPresent(batch -> {
            try {
                store.addBatch(batch);
                refreshInventory();
                refreshOutOfStock();
                showInfo("Stock replenished",
                        batch.quantity() + " units of " + batch.name() + " were added under the existing ID "
                                + batch.medicineId() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not restock medicine", exception.getMessage());
            }
        });
    }

    private TableColumn<MedicineBatch, String> medicineColumn(
            String title, java.util.function.Function<MedicineBatch, String> value, double width) {
        TableColumn<MedicineBatch, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, String> stringColumn(String title, String property, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private void refreshInventory() {
        if (inventoryTable == null || inventoryFilter == null || searchField == null) {
            return;
        }
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = inventoryFilter;
        List<MedicineBatch> filtered = store.getBatches().stream()
                .filter(batch -> batch.quantity() > 0)
                .filter(batch -> query.isEmpty()
                        || batch.name().toLowerCase(Locale.ROOT).contains(query)
                        || batch.category().toLowerCase(Locale.ROOT).contains(query))
                .filter(batch -> switch (filter) {
                    case "Near expiry" -> !batch.expiryDate().isBefore(LocalDate.now())
                            && !batch.expiryDate().isAfter(LocalDate.now().plusDays(30));
                    case "Expired" -> batch.expiryDate().isBefore(LocalDate.now());
                    case "Low stock" -> !batch.expiryDate().isBefore(LocalDate.now())
                            && batch.quantity() > 0 && batch.quantity() <= LOW_STOCK_LIMIT;
                    default -> true;
                })
                .sorted(Comparator.comparing(MedicineBatch::expiryDate))
                .toList();
        inventoryTable.getItems().setAll(filtered);
        inventoryTable.refresh();
        refreshOutOfStock();
    }

    private String inventoryStatus(MedicineBatch batch) {
        String expiryStatus = InventoryStore.expiryStatus(batch.expiryDate());
        if ("Expired".equals(expiryStatus) || "Near expiry".equals(expiryStatus)) {
            return expiryStatus;
        }
        return batch.quantity() <= LOW_STOCK_LIMIT ? "Low stock" : "Safe";
    }

    private void refreshBillingProducts() {
        if (billingTable == null) {
            return;
        }
        Map<String, MedicineBatch> products = new HashMap<>();
        for (MedicineBatch batch : store.getBatches()) {
            if (store.availableQuantity(batch.medicineId()) > 0) {
                products.putIfAbsent(batch.medicineId(), batch);
            }
        }
        List<BillingRow> rows = products.values().stream()
                .filter(batch -> billingSearch.isBlank()
                        || batch.name().toLowerCase(Locale.ROOT).contains(billingSearch)
                        || batch.category().toLowerCase(Locale.ROOT).contains(billingSearch))
                .sorted(Comparator.comparing(MedicineBatch::name))
                .map(batch -> new BillingRow(batch.medicineId(), batch.name(), batch.category(),
                        store.availableQuantity(batch.medicineId()), batch.price()))
                .toList();
        billingTable.getItems().setAll(rows);
        sizeTableToRows(billingTable, rows.size(), 420, 156);
    }

    private void sizeTableToRows(TableView<?> table, int rowCount, double maxHeight, double emptyHeight) {
        double height = rowCount == 0
                ? emptyHeight
                : Math.min(maxHeight, 48 + (long) rowCount * 46);
        table.setPrefHeight(height);
        table.setMaxHeight(height);
    }

    private void refreshCartTotal() {
        if (cart == null || subtotalLabel == null) {
            return;
        }
        BigDecimal subtotal = cart.stream()
                .map(row -> row.priceValue().multiply(BigDecimal.valueOf(row.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotalLabel.setText(money(subtotal));
    }

    private boolean persistCustomerCart() {
        try {
            Map<String, Integer> quantities = new HashMap<>();
            cart.forEach(item -> quantities.put(item.medicineId(), item.quantity()));
            cartStore.replaceCart(currentUser.username(), quantities);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not save cart", exception.getMessage());
            cart.clear();
            cartStore.getCart(currentUser.username()).forEach((medicineId, quantity) -> {
                try {
                    MedicineBatch medicine = store.medicineDetails(medicineId);
                    cart.add(new CartRow(medicineId, medicine.name(), medicine.price(), quantity));
                } catch (IllegalArgumentException missingMedicine) {
                    showError("Saved cart item unavailable", missingMedicine.getMessage());
                }
            });
            cartTable.refresh();
            refreshCartTotal();
            return false;
        }
    }

    private void showAddMedicineDialog() {
        requireAdmin();
        Dialog<MedicineBatch> dialog = new Dialog<>();
        dialog.setTitle("Add medicine batch");
        dialog.setHeaderText("Enter the medicine details and batch expiry.");
        ButtonType saveButton = new ButtonType("Add to stock", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField id = new TextField();
        id.setPromptText("e.g. MED-001");
        TextField name = new TextField();
        name.setPromptText("Medicine name");
        TextField category = new TextField();
        category.setPromptText("e.g. Pain relief");
        TextField manufacturer = new TextField();
        manufacturer.setPromptText("Manufacturer");
        TextField price = new TextField();
        price.setPromptText("e.g. 25.50");
        TextField rackLocation = new TextField();
        rackLocation.setPromptText("e.g. Aisle 2 · Rack B · Shelf 3");
        Spinner<Integer> quantity = new Spinner<>();
        quantity.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, 1));
        quantity.setEditable(true);
        DatePicker expiry = new DatePicker(LocalDate.now().plusYears(1));

        VBox form = new VBox(9,
                formField("Medicine ID", id), formField("Name", name),
                formField("Category", category), formField("Manufacturer", manufacturer),
                formField("Unit price (₹)", price), formField("Rack position (optional)", rackLocation),
                formField("Quantity", quantity),
                formField("Expiry date", expiry));
        form.setPadding(new Insets(10, 4, 4, 4));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.setResultConverter(button -> {
            if (button != saveButton) {
                return null;
            }
            try {
                return new MedicineBatch(id.getText(), name.getText(), category.getText(),
                        manufacturer.getText(), new BigDecimal(price.getText().trim()),
                        quantity.getValue(), expiry.getValue(), rackLocation.getText());
            } catch (RuntimeException exception) {
                showError("Check medicine details", exception.getMessage());
                return null;
            }
        });
        dialog.showAndWait().ifPresent(batch -> {
            try {
                store.addBatch(batch);
                refreshInventory();
                refreshOutOfStock();
                showInfo("Medicine added", batch.name() + " was added to stock.");
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not add medicine", exception.getMessage());
            }
        });
    }

    private void showMedicineDetailsDialog(MedicineBatch medicine) {
        requireAdmin();
        TextField price = new TextField(medicine.price().toPlainString());
        TextField rackLocation = new TextField(medicine.rackLocation());
        rackLocation.setPromptText("e.g. Aisle 2 · Rack B · Shelf 3");
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Update medicine details");
        dialog.setHeaderText(medicine.name() + " · " + medicine.medicineId()
                + "\nThe price change applies to all current batches; past receipts remain unchanged.");
        ButtonType save = new ButtonType("Save changes", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        VBox form = new VBox(10,
                formField("New unit price (₹)", price),
                formField("Rack position", rackLocation));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.setResultConverter(button -> button == save);
        dialog.showAndWait().filter(Boolean::booleanValue).ifPresent(ignored -> {
            try {
                store.updateMedicineDetails(
                        medicine.medicineId(), new BigDecimal(price.getText().trim()), rackLocation.getText());
                refreshInventory();
                refreshOutOfStock();
                refreshBillingProducts();
                showInfo("Medicine details updated",
                        "The current unit price and rack position were updated for " + medicine.name() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                showError("Could not update medicine", exception.getMessage());
            }
        });
    }

    private VBox formField(String label, Control input) {
        Label caption = new Label(label);
        caption.getStyleClass().add("form-label");
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, caption, input);
    }

    private void completeSale() {
        if (cart == null || cart.isEmpty()) {
            showError("Bill is empty", "Add at least one medicine before completing the sale.");
            return;
        }
        boolean customerCheckout = currentUser.role() == AuthStore.Role.CUSTOMER;
        CustomerInfo customer = customerCheckout ? requestCustomerDetails() : new CustomerInfo("", "");
        if (customer == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle(customerCheckout ? "Place your order" : "Complete sale");
        confirmation.setHeaderText(customerCheckout ? "Ready to place this order?" : "Confirm this purchase?");
        confirmation.setContentText((customerCheckout
                ? "Customer: " + customer.name() + "\nMobile: " + customer.mobile() + "\n"
                : "") + "Total: " + subtotalLabel.getText());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        Map<String, Integer> quantities = new HashMap<>();
        for (CartRow row : cart) {
            quantities.put(row.medicineId(), row.quantity());
        }
        try {
            List<InventoryStore.SaleItem> receipt = store.checkout(quantities);
            DeliveryStore.DeliveryOrder delivery = null;
            IOException trackingException = null;
            if (customerCheckout) {
                try {
                    delivery = deliveryStore.createOrder(
                            currentUser.username(), customer.name(), customer.mobile(), receipt);
                } catch (IOException exception) {
                    trackingException = exception;
                }
                try {
                    cartStore.clearCart(currentUser.username());
                } catch (IOException exception) {
                    showError("Could not clear saved cart",
                            "The purchase completed, but the saved cart could not be cleared: " + exception.getMessage());
                }
            }
            cart.clear();
            refreshCartTotal();
            refreshBillingProducts();
            String receiptId = delivery == null ? receipt.getFirst().saleId() : delivery.deliveryId();
            String trackingNote = delivery == null && customerCheckout
                    ? "Purchase completed, but delivery tracking could not be saved: "
                    + (trackingException == null ? "unknown error" : trackingException.getMessage())
                    : delivery == null ? "" : "Track this order in My deliveries. Status: Placed.";
            showReceipt(receiptId, customer, receipt, trackingNote);
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not complete sale", exception.getMessage());
            refreshBillingProducts();
        }
    }

    private CustomerInfo requestCustomerDetails() {
        Dialog<CustomerInfo> dialog = new Dialog<>();
        dialog.setTitle("Customer details");
        dialog.setHeaderText("Add customer details for the receipt");
        ButtonType placeOrder = new ButtonType(
                "Continue to order", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(placeOrder, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Customer full name");
        name.getStyleClass().add("checkout-input");
        TextField mobile = new TextField();
        mobile.setPromptText("10-digit mobile number");
        mobile.getStyleClass().add("checkout-input");
        Label validation = new Label("Enter a name and a valid 10-digit Indian mobile number.");
        validation.getStyleClass().add("checkout-hint");
        Label orderTotal = new Label("Order total  " + subtotalLabel.getText());
        orderTotal.getStyleClass().add("checkout-total");
        VBox form = new VBox(13,
                new Label("These details will be saved with your order and printed on the receipt."),
                formField("CUSTOMER NAME", name),
                formField("MOBILE NUMBER", mobile),
                validation, orderTotal);
        form.getStyleClass().add("checkout-form");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(470);

        Button continueButton = (Button) dialog.getDialogPane().lookupButton(placeOrder);
        Runnable validate = () -> {
            boolean valid = !name.getText().isBlank()
                    && mobile.getText().trim().matches("[6-9][0-9]{9}");
            continueButton.setDisable(!valid);
            validation.setText(valid
                    ? "Your receipt will include these details."
                    : "Enter a name and a valid 10-digit Indian mobile number.");
            validation.getStyleClass().removeAll("checkout-hint", "checkout-error");
            validation.getStyleClass().add(valid ? "checkout-hint" : "checkout-error");
        };
        name.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        mobile.textProperty().addListener((observable, oldValue, newValue) -> validate.run());
        validate.run();
        dialog.setResultConverter(button -> button == placeOrder
                ? new CustomerInfo(name.getText().trim(), mobile.getText().trim())
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private void showReceipt(
            String receiptId, CustomerInfo customer, List<InventoryStore.SaleItem> items, String trackingNote) {
        BigDecimal total = items.stream().map(InventoryStore.SaleItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String receiptText = formatReceipt(receiptId, customer, items, total, trackingNote);

        VBox receipt = new VBox(16);
        receipt.getStyleClass().add("receipt-paper");
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("+");
        logo.getStyleClass().add("receipt-logo");
        VBox brandText = new VBox(2,
                new Label("MediStock Pharmacy"),
                new Label("PURCHASE RECEIPT"));
        brandText.getStyleClass().add("receipt-brand");
        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        Label paid = new Label("PAID");
        paid.getStyleClass().add("receipt-paid");
        brand.getChildren().addAll(logo, brandText, brandSpacer, paid);

        GridPane orderInfo = new GridPane();
        orderInfo.setHgap(20);
        orderInfo.setVgap(8);
        orderInfo.add(receiptCell("RECEIPT / ORDER", "receipt-info-label"), 0, 0);
        orderInfo.add(receiptCell("#" + receiptId, "receipt-info-value"), 1, 0);
        orderInfo.add(receiptCell("DATE", "receipt-info-label"), 0, 1);
        orderInfo.add(receiptCell(LocalDate.now().format(DATE_FORMAT)
                + " · " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a")),
                "receipt-info-value"), 1, 1);
        orderInfo.add(receiptCell("CUSTOMER", "receipt-info-label"), 0, 2);
        orderInfo.add(receiptCell(customer.name().isBlank() ? "Walk-in customer" : customer.name(),
                "receipt-info-value"), 1, 2);
        if (!customer.mobile().isBlank()) {
            orderInfo.add(receiptCell("MOBILE", "receipt-info-label"), 0, 3);
            orderInfo.add(receiptCell(customer.mobile(), "receipt-info-value"), 1, 3);
        }
        orderInfo.getStyleClass().add("receipt-info");

        GridPane lineItems = new GridPane();
        lineItems.getStyleClass().add("receipt-items");
        lineItems.setHgap(10);
        lineItems.setVgap(9);
        lineItems.add(receiptCell("MEDICINE", "receipt-column-heading"), 0, 0);
        lineItems.add(receiptCell("QTY", "receipt-column-heading"), 1, 0);
        lineItems.add(receiptCell("PRICE", "receipt-column-heading"), 2, 0);
        lineItems.add(receiptCell("AMOUNT", "receipt-column-heading"), 3, 0);
        for (int i = 0; i < items.size(); i++) {
            InventoryStore.SaleItem item = items.get(i);
            int row = i + 1;
            lineItems.add(receiptCell(item.name(), "receipt-medicine"), 0, row);
            lineItems.add(receiptCell(Integer.toString(item.quantity()), "receipt-value"), 1, row);
            lineItems.add(receiptCell(money(item.unitPrice()), "receipt-value"), 2, row);
            lineItems.add(receiptCell(money(item.lineTotal()), "receipt-value"), 3, row);
        }
        ColumnConstraints medicineColumn = new ColumnConstraints();
        medicineColumn.setPercentWidth(42);
        ColumnConstraints quantityColumn = new ColumnConstraints();
        quantityColumn.setPercentWidth(12);
        ColumnConstraints priceColumn = new ColumnConstraints();
        priceColumn.setPercentWidth(21);
        ColumnConstraints amountColumn = new ColumnConstraints();
        amountColumn.setPercentWidth(25);
        lineItems.getColumnConstraints().setAll(
                medicineColumn, quantityColumn, priceColumn, amountColumn);

        HBox totalRow = new HBox(10, new Label("TOTAL PAID"), spacer(), new Label(money(total)));
        totalRow.getStyleClass().add("receipt-total");
        if (!trackingNote.isBlank()) {
            Label note = new Label(trackingNote);
            note.setWrapText(true);
            note.getStyleClass().add("receipt-note");
            receipt.getChildren().addAll(brand, orderInfo, lineItems, totalRow, note);
        } else {
            receipt.getChildren().addAll(brand, orderInfo, lineItems, totalRow);
        }

        ScrollPane scroll = new ScrollPane(receipt);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(610);
        scroll.setPrefViewportHeight(430);
        scroll.getStyleClass().add("receipt-scroll");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Purchase receipt");
        ButtonType save = new ButtonType("Save receipt", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(670);
        dialog.getDialogPane().setPrefHeight(540);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().add("primary-button");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            saveReceipt(receiptText, receiptId);
        });
        dialog.showAndWait();
    }

    private Label receiptCell(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        return label;
    }

    private String formatReceipt(
            String receiptId, CustomerInfo customer, List<InventoryStore.SaleItem> items,
            BigDecimal total, String trackingNote) {
        StringBuilder text = new StringBuilder();
        text.append("MEDISTOCK PHARMACY\n")
                .append("PURCHASE RECEIPT\n")
                .append("------------------------------------------------------------\n")
                .append("Receipt / Order: #").append(receiptId).append('\n')
                .append("Date: ").append(LocalDate.now().format(DATE_FORMAT)).append(" ")
                .append(java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))).append('\n')
                .append("Customer: ").append(customer.name().isBlank() ? "Walk-in customer" : customer.name()).append('\n');
        if (!customer.mobile().isBlank()) {
            text.append("Mobile: ").append(customer.mobile()).append('\n');
        }
        text.append("------------------------------------------------------------\n")
                .append(String.format(Locale.ROOT, "%-25s %5s %11s %12s%n", "MEDICINE", "QTY", "PRICE", "AMOUNT"))
                .append("------------------------------------------------------------\n");
        for (InventoryStore.SaleItem item : items) {
            text.append(String.format(Locale.ROOT, "%-25.25s %5d %11s %12s%n",
                    item.name(), item.quantity(), money(item.unitPrice()), money(item.lineTotal())));
        }
        text.append("------------------------------------------------------------\n")
                .append(String.format(Locale.ROOT, "%-43s %12s%n", "TOTAL PAID", money(total)))
                .append("------------------------------------------------------------\n")
                .append("Thank you for choosing MediStock Pharmacy.\n");
        if (!trackingNote.isBlank()) {
            text.append(trackingNote).append('\n');
        }
        return text.toString();
    }

    private void saveReceipt(String receiptText, String receiptId) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save purchase receipt");
        chooser.setInitialFileName("receipt-" + receiptId + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File destination = chooser.showSaveDialog(stage);
        if (destination == null) {
            return;
        }
        try {
            Files.writeString(destination.toPath(), receiptText, java.nio.charset.StandardCharsets.UTF_8);
            showInfo("Receipt saved", "Receipt saved to " + destination.getAbsolutePath());
        } catch (IOException exception) {
            showError("Could not save receipt", exception.getMessage());
        }
    }

    private record CustomerInfo(String name, String mobile) {}

    private Label emptyState(String title, String subtitle) {
        Label label = new Label(title + "\n" + subtitle);
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        return label;
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private static String money(BigDecimal amount) {
        return CURRENCY.format(amount);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }

    private void showStartupError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null || message.isBlank() ? "An unexpected error occurred." : message);
        alert.showAndWait();
        Platform.exit();
    }

    private void requireAdmin() {
        if (currentUser == null || currentUser.role() != AuthStore.Role.ADMIN) {
            throw new SecurityException("Only a signed-in administrator can manage pharmacy inventory.");
        }
    }

    private void requireCustomer() {
        if (currentUser == null || currentUser.role() != AuthStore.Role.CUSTOMER) {
            throw new SecurityException("Only a signed-in customer can view customer delivery tracking.");
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private interface ExportAction {
        void export(java.nio.file.Path path) throws IOException;
    }

    private record MedicineChoice(String medicineId, String name) {
        @Override
        public String toString() {
            return name + " · " + medicineId;
        }
    }

    private record SupplierDraft(OrderStore.OrderRequest request) {
        String medicineName() { return request.medicineName(); }
        BigDecimal unitPrice() { return request.unitPrice(); }
        int quantity() { return request.quantity(); }
    }

    private enum AuthScreen {
        ADMIN_SETUP,
        LOGIN,
        REGISTER
    }

    public static final class BillingRow {
        private final String medicineId;
        private final String name;
        private final String category;
        private final int availableCount;
        private final BigDecimal priceValue;

        BillingRow(String medicineId, String name, String category, int availableCount, BigDecimal priceValue) {
            this.medicineId = medicineId;
            this.name = name;
            this.category = category;
            this.availableCount = availableCount;
            this.priceValue = priceValue;
        }

        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getAvailable() { return Integer.toString(availableCount); }
        public String getPrice() { return money(priceValue); }
        public String medicineId() { return medicineId; }
        public int availableCount() { return availableCount; }
        public BigDecimal priceValue() { return priceValue; }
    }

    public static final class CartRow {
        private final String medicineId;
        private final String name;
        private final BigDecimal priceValue;
        private int quantity;

        CartRow(String medicineId, String name, BigDecimal priceValue, int quantity) {
            this.medicineId = medicineId;
            this.name = name;
            this.priceValue = priceValue;
            this.quantity = quantity;
        }

        public String getName() { return name; }
        public String getQuantity() { return Integer.toString(quantity); }
        public String getPrice() { return money(priceValue); }
        public String getTotal() { return money(priceValue.multiply(BigDecimal.valueOf(quantity))); }
        public String medicineId() { return medicineId; }
        public int quantity() { return quantity; }
        public BigDecimal priceValue() { return priceValue; }
        void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
