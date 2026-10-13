# LaundryLink — Team Guide

How to get LaundryLink running on your computer, and how to build your assigned module so it fits with the rest of the app.

**Read in order:** Part A (set up and run) → Part B (how the project is organized) → Part C (your module) → Part D (team rules).

| Member | Module | Your tab / files |
|---|---|---|
| **Edmar** | Customer Management | **Customers** tab (`customers.fxml`) |
| **Reniel** | Service and Price Management | **Services & Prices** tab (`services-prices.fxml`) |
| **John Ray** | Order Status Tracking | Status panel used inside orders (`order-status.fxml`, new) |
| **Ken** | Accounts, Laundry Orders, Payments, Dashboard, Sales & Reports, shared code | Everything else |

---

# Part A — Set up and run the app

## A1. Install these (once)

| Install | Version | Notes |
|---|---|---|
| **JDK 8 *with JavaFX*** | 8 (1.8) | The app is a JavaFX 8 project. Use **Oracle JDK 8**, **Liberica JDK 8 "Full"**, or **Azul Zulu 8 "JDK FX"**. A plain OpenJDK 8 has **no JavaFX** and the project won't compile. |
| **Apache NetBeans 8.2** | 8.2 | The project is a NetBeans 8.2 "JavaFX Application" (Ant) project. Newer NetBeans versions don't open it as a JavaFX project. |
| **MySQL Community Server** | 8.0 | Remember the **root** password you choose during install. |
| **MySQL Workbench** | 8.0 | To run the SQL scripts. |
| **Git** | any | To get the code and share your work. |
| Scene Builder (optional) | Gluon Scene Builder 8.x | To edit `.fxml` screens visually. Save only JavaFX 8 features. |

Check the JDK in NetBeans: **Tools → Java Platforms** must show a JDK 1.8 that includes JavaFX.

## A2. Get the project

```
git clone https://github.com/stagnantwater69/LaundryLink.git
```

Put it in your `Documents\NetBeansProjects` folder, then in NetBeans: **File → Open Project… → select the `LaundryLink` folder**.

> The MySQL driver (`lib/mysql-connector-j-8.4.0.jar`) is already inside the project. Don't download another one.

## A3. Create your local database (once)

Each member has **their own local database**. Never connect to someone else's.

1. Make sure MySQL is running: Windows **Services → MySQL80 → Start** (or `net start MySQL80` in an *admin* terminal).
2. Open **MySQL Workbench**, connect to **Local instance** as **root**.
3. Run these files from the `database` folder **in this order**, each with **File → Open SQL Script…**, then the ⚡ **Execute** button (the plain lightning bolt that runs everything, *not* Ctrl+Enter):

| # | File | What it does |
|---|---|---|
| 1 | `schema.sql` | Creates the `laundrylink` database and all 8 tables |
| 2 | `create-app-user.sql` | Creates the app's login. **First change `change-me` on line 8 to your own password.** |
| 3 | `sample-data.sql` | Optional demo services and customers. Run it **after** registering your shop in the app. |

> Already have an older LaundryLink database? See **`database/DATABASE_CHANGES.md`** for how to update it. A fresh `schema.sql` is already up to date.

## A4. Tell the app your database password (once)

1. In the `config` folder, copy `database.properties.example` and name the copy **`database.properties`**.
2. Open it and set the password you chose in step A3.2:

```properties
db.url=jdbc:mysql://localhost:3306/laundrylink
db.user=laundrylink_app
db.password=YOUR-PASSWORD-HERE
```

`config/database.properties` is ignored by Git, so your password is never uploaded. **Never put a real password in the `.example` file.**

## A5. Run the app

1. In NetBeans, right-click **LaundryLink → Clean and Build**.
2. Press **F6** (Run Project).
3. First time only: **Staff / Owner Login** opens **Owner Setup**. Create your local owner account (any name, e.g. your own).
4. Log in. You should see the **Dashboard**.

**Optional demo orders** (to see the Dashboard filled in): after step 3, run `database/demo-orders.sql` in Workbench. To remove them later, run the two `DELETE` lines at the bottom of that file.

## A6. Something's wrong?

| Message / problem | Fix |
|---|---|
| "Could not reach the LaundryLink database" | MySQL isn't running → start **MySQL80** (step A3.1). |
| "Access denied for user 'laundrylink_app'" | Password in `config/database.properties` doesn't match step A3.2. |
| "Unknown column …" | Your database is older than the code. Re-run `schema.sql` on a fresh database, or follow `database/DATABASE_CHANGES.md`. |
| Compile errors about `javafx…` | Your JDK has no JavaFX → install a JDK from the table in A1 and select it in **Tools → Java Platforms**. |
| Old screens appear after pulling new code | **Clean and Build**, then run. |
| "Unable to delete file … dist\run…" on Clean | The app is still open. Close its window, then build again. |

---

# Part B — How the project is organized

## B1. Folders

```
LaundryLink/
├── config/                         database.properties (yours, not in Git) + .example
├── database/                       schema.sql, create-app-user.sql, sample-data.sql, demo-orders.sql, add-*.sql, DATABASE_CHANGES.md
├── lib/                            MySQL driver
├── LaundryLink_ERD.md              Every table, column and relationship
├── fixthiserd/                     ERD picture (.png / .svg / .drawio)
└── src/com/laundrylink/
    ├── LaundryLinkApp.java         Starts the app
    ├── config/                     Database connection
    ├── controller/                 One controller per screen (button clicks, show data)
    ├── service/                    Business rules + permissions
    ├── dao/                        SQL queries only
    ├── model/                      Plain data classes (User, OrderStatus, ...)
    ├── util/                       Shared helpers (session, dialogs, navigation, money)
    ├── view/                       .fxml screens
    ├── css/laundrylink.css         Shared retro style
    └── images/                     Icons
```

**The rule:** Controller → Service → DAO → database.
- **Controller**: reads the form, calls the service in the background, shows the result. No SQL.
- **Service**: checks the rules (validation, who may do it) and opens the transaction.
- **DAO**: runs the SQL with `PreparedStatement`. No rules, no UI.

## B2. Database conventions

| Topic | Rule |
|---|---|
| Primary keys | Every table's key is just `id`. Foreign keys are named after what they point to (`customer_id`, `order_id`) or the person's role (`created_by`, `received_by`, `changed_by` → `users.id`). |
| Names | People are stored as `first_name`, `middle_name` (optional, NULL when blank), `last_name`. |
| Money | `DECIMAL` in MySQL, **`BigDecimal`** in Java. Never `double`. Two decimal places. |
| Dates/times | `DATETIME`, shop time zone **Asia/Manila** (`DashboardService.SHOP_ZONE`). |
| Owner | `users.role = 'ADMIN'` means **the shop owner** (exactly one). All other accounts are `'STAFF'`. |
| Deleting | Don't delete things that orders use. Services are **deactivated** (`is_active = 0`); customers can be deleted by the owner **only if they have no orders**. |
| SQL safety | Always `PreparedStatement` with `?` parameters, always try-with-resources. |

The full column list for every table is in **`LaundryLink_ERD.md`**.

## B3. Shared helpers you should use (don't rewrite these)

| Need | Use |
|---|---|
| Database connection | `ConnectionFactory.getConnection()` (in try-with-resources) |
| Several writes that must all succeed together | `TransactionHelper.inTransaction(connection -> { ...; return result; })` |
| Who is logged in | `SessionContext.getCurrentUser()`, `SessionContext.getCurrentUserId()` |
| Owner-only action (check in the **service**) | `SessionContext.requireAdmin();` (throws if a staff member tries) |
| Is the user the owner? (to hide buttons in the UI) | `SessionContext.isAdmin()` |
| A rule was broken (message shown to the user) | `throw new ServiceException("Price must be greater than zero.");` |
| Run database work without freezing the screen | `BackgroundTask.run(() -> service.load(), result -> {...}, error -> {...})` / `BackgroundTask.runVoid(...)` |
| Show errors/success | `Dialogs.showFailure(label, "Title", error)`, `Dialogs.showSuccess(label, "Saved.")`, `Dialogs.info(...)`, `Dialogs.confirm(...)` |
| Money text | `MoneyFormat.php(amount)` → `PHP 1,234.50` |
| Order status / payment status | `OrderStatus` and `PaymentStatus` enums (have display names) |
| Message in the bottom status bar | `AppShell.setStatus("Ready.")` |
| Switch to another tab | `AppShell.openTab(View.DASHBOARD)` |

## B4. Screen style rules

- Link the stylesheet at the top of your `.fxml` (copy from `staff-accounts.fxml`):
  ```xml
  <stylesheets><URL value="@../css/laundrylink.css" /></stylesheets>
  ```
- Reuse the existing classes: `page-title`, `page-subtitle`, `card`, `card-header compact`, `card-header-title`, `card-body`, `field-label`, `note-text`, button class `primary`, `form-error`, `form-success`.
  `staff-accounts.fxml` is the best example of a "table on the left, form on the right" screen.
- If you add CSS, write sizes in **`em`** (14px = 1em), not `px`, so the app scales to full screen.
- Use layout panes (HBox / VBox / GridPane / BorderPane), not fixed x/y positions. Check your screen at normal size **and** maximized.

---

# Part C — Your module

Every module needs: the screen (`.fxml`), a **controller**, a **service**, a **DAO**, a **model** class, validation, and a quick test (Part C5).
Use the existing `StaffAccountsController` → `AccountService` → `UserDAO` → `User` as your example. It follows every rule above.

## C1. Edmar — Customer Management

**Screen:** the **Customers** tab. `src/com/laundrylink/view/customers.fxml` already exists (title only) and is already in the tab bar. Fill it in and add `fx:controller="com.laundrylink.controller.CustomerController"` to its root.

**Table:** `customers` (`id, first_name, middle_name, last_name, contact_number, address, created_at`)

**Create these files:**
| Package | File |
|---|---|
| model | `Customer.java` |
| dao | `CustomerDAO.java` |
| service | `CustomerService.java` |
| controller | `CustomerController.java` |

**Features:**
- List + **search** by name or contact number.
- **Add / edit** customer: first name and last name required, middle name optional, contact number and address optional.
- **History:** show the selected customer's orders (read `orders` + `v_order_balances` for totals and payment status).
- **Delete:** owner only (`SessionContext.requireAdmin()` in the service) and **only if the customer has no orders**. Otherwise show "This customer has orders and can't be deleted."

**Rules:**
- Keep `contact_number` as a **String** (keeps the leading 0 of 09xx numbers).
- Staff can add, view, search, and edit. Only the owner can delete.

**Methods Ken's order screen will call** (please use these exact names):
```java
Customer findCustomerById(int id)
List<Customer> searchCustomers(String text)
```

## C2. Reniel — Service and Price Management

**Screen:** the **Services & Prices** tab. `src/com/laundrylink/view/services-prices.fxml` already exists and is in the tab bar. Add `fx:controller="com.laundrylink.controller.ServiceController"`.

**Table:** `services` (`id, service_name, pricing_unit, current_price, is_active, created_at`)

**Create these files:**
| Package | File |
|---|---|
| model | `LaundryService.java` (not `Service`, which is confusing next to the `service` package) |
| dao | `ServiceDAO.java` |
| service | `ServiceCatalogService.java` |
| controller | `ServiceController.java` |

**Features:**
- List all services with unit (**KG** or **PIECE**), price, and Active/Inactive.
- **Owner:** add, edit price, activate/deactivate. **Staff:** view only. Hide the form with `SessionContext.isAdmin()` **and** call `SessionContext.requireAdmin()` in every save/deactivate method.

**Rules:**
- Price must be **> 0**, stored as `BigDecimal`.
- Service names are unique: show a friendly message on duplicates.
- **Never delete** a service; deactivate it. Changing a price must not change old orders (they keep their own price copy).

**Method Ken's order screen will call:**
```java
List<LaundryService> getActiveServices()   // only is_active = 1
```

## C3. John Ray — Order Status Tracking

**Screen:** a **status panel** that Ken places inside the order details screen.
- Create `src/com/laundrylink/view/order-status.fxml` with `fx:controller="com.laundrylink.controller.StatusController"`.
- It shows **one order's** history (a table: Status / Changed by / Date & time) and an **Advance to …** button.
- Ask Ken to add `ORDER_STATUS("order-status.fxml")` to `util/View.java`. It's a shared file (see Part D).

**Table:** `order_status_history` (`id, order_id, changed_by, status, changed_at`)
- `status` is the stage the order moved **into**. The previous stage is just the row before it.
- Also update `orders.laundry_status` (the current stage).

**Create these files:**
| Package | File |
|---|---|
| model | `StatusChange.java` |
| dao | `StatusHistoryDAO.java` |
| service | `StatusService.java` |
| controller | `StatusController.java` |

**Controller API** for Ken to embed it:
```java
public void showOrder(int orderId)     // loads that order's history and current status
```

**Rules:**
- **Order of stages:** RECEIVED → WASHING → DRYING → READY_FOR_PICKUP → RELEASED. Only one step forward at a time; no skipping, no going back.
- Each step writes **both** `orders.laundry_status` **and** a new history row (`status`, `changed_by = SessionContext.getCurrentUserId()`), in **one** `TransactionHelper.inTransaction(...)`.
- Lock the order row first (`SELECT laundry_status FROM orders WHERE id = ? FOR UPDATE`) so two clicks can't advance it twice.
- **RELEASED:** don't write it yourself. Call Ken's existing `new OrderService().releaseOrder(orderId)`. It checks *Ready for Pickup + fully paid* and records the history.
- **CANCELLED** and **RELEASED** orders can't be advanced.

## C4. Code template (copy and rename)

A minimal, working pattern using the shared helpers. Example: Reniel's "list services".

```java
// model/LaundryService.java
package com.laundrylink.model;

import java.math.BigDecimal;

public class LaundryService {
    private final int id;
    private final String serviceName;
    private final String pricingUnit;     // "KG" or "PIECE"
    private final BigDecimal currentPrice;
    private final boolean active;

    public LaundryService(int id, String serviceName, String pricingUnit, BigDecimal currentPrice, boolean active) {
        this.id = id;
        this.serviceName = serviceName;
        this.pricingUnit = pricingUnit;
        this.currentPrice = currentPrice;
        this.active = active;
    }

    public int getId() { return id; }
    public String getServiceName() { return serviceName; }
    public String getPricingUnit() { return pricingUnit; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public boolean isActive() { return active; }
}
```

```java
// dao/ServiceDAO.java: SQL only
package com.laundrylink.dao;

import com.laundrylink.model.LaundryService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceDAO {

    public List<LaundryService> findAll(Connection connection) throws SQLException {
        String sql = "SELECT id, service_name, pricing_unit, current_price, is_active FROM services"
                + " ORDER BY is_active DESC, service_name";
        List<LaundryService> list = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(new LaundryService(rs.getInt("id"), rs.getString("service_name"),
                        rs.getString("pricing_unit"), rs.getBigDecimal("current_price"), rs.getBoolean("is_active")));
            }
        }
        return list;
    }

    public void updatePrice(Connection connection, int id, java.math.BigDecimal price) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE services SET current_price = ? WHERE id = ?")) {
            statement.setBigDecimal(1, price);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }
}
```

```java
// service/ServiceCatalogService.java: rules + permissions
package com.laundrylink.service;

import com.laundrylink.config.ConnectionFactory;
import com.laundrylink.dao.ServiceDAO;
import com.laundrylink.model.LaundryService;
import com.laundrylink.util.SessionContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class ServiceCatalogService {

    private final ServiceDAO serviceDAO = new ServiceDAO();

    public List<LaundryService> listServices() throws ServiceException, SQLException {
        SessionContext.requireLoggedIn();
        try (Connection connection = ConnectionFactory.getConnection()) {
            return serviceDAO.findAll(connection);
        }
    }

    public void changePrice(int serviceId, BigDecimal newPrice) throws ServiceException, SQLException {
        SessionContext.requireAdmin();                         // owner only, checked here, not just in the UI
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new ServiceException("Price must be greater than zero.");
        }
        TransactionHelper.inTransaction(connection -> {
            serviceDAO.updatePrice(connection, serviceId, newPrice);
            return null;
        });
    }
}
```

```java
// controller/ServiceController.java: screen only (fx:id names must match the .fxml)
package com.laundrylink.controller;

import com.laundrylink.model.LaundryService;
import com.laundrylink.service.ServiceCatalogService;
import com.laundrylink.util.BackgroundTask;
import com.laundrylink.util.Dialogs;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;

public class ServiceController {

    @FXML
    private TableView<LaundryService> servicesTable;

    private final ServiceCatalogService catalogService = new ServiceCatalogService();

    @FXML
    private void initialize() {
        // ...set up table columns here...
        BackgroundTask.run(catalogService::listServices,
                (List<LaundryService> services) -> servicesTable.setItems(FXCollections.observableArrayList(services)),
                error -> Dialogs.error("Cannot load services", error));
    }
}
```

> **Java 8 reminder:** the project compiles as Java 8. Don't use `var`, `List.of(...)`, text blocks, or `switch ->`.

## C5. Test your module before sharing

Check these on your own computer:
- [ ] App starts and your tab opens with no error dialog.
- [ ] Add, edit, search, and save work, and the data is still there **after closing and reopening the app**.
- [ ] Wrong input shows a red message (blank name, zero price, …) and nothing is saved.
- [ ] Log in as a **staff** account (create one in Staff Accounts): owner-only actions are hidden **and** rejected.
- [ ] Your screen looks right at normal size **and** maximized.
- [ ] **Clean and Build** has no errors.

---

# Part D — Working together

## D1. Git workflow

```
git checkout main
git pull                                   # always start from the latest code
git checkout -b feature/edmar-customers    # or feature/reniel-services, feature/johnray-status
# ...work, then:
git add <your files>
git commit -m "Customers: add search and edit form"
git push -u origin feature/edmar-customers
```

Then open a **Pull Request** on GitHub into `main`. **Ken reviews and approves** before merging. Keep `main` runnable: don't merge something that stops the app from starting.

**Never commit:** `config/database.properties`, `build/`, `dist/`, `nbproject/private/` (already in `.gitignore`).

## D2. Shared files:  


| File | Why it's shared |
|---|---|
| `database/schema.sql` | Everyone's tables. A change needs a migration script for existing databases. |
| `util/View.java`, `view/main-shell.fxml`, `controller/MainShellController.java` | Navigation and tabs |
| `css/laundrylink.css` | Everyone's look. Prefer the existing classes. |
| `util/*`, `service/TransactionHelper.java`, `config/*` | Used by every module |
| `LaundryLink_ERD.md`, `fixthiserd/` | Must match `schema.sql` |

## D3. Getting help

- **Unsure about a column or relationship:** check `LaundryLink_ERD.md`.
- **Unsure how to structure code:** copy the Staff Accounts pattern (`StaffAccountsController` / `AccountService` / `UserDAO`).
- **Your module needs something from another module** 
