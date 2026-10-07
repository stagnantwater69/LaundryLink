# Service and Price Management

Feature specification, implementation plan, progress log, and team handoff
reference for Reniel's assigned LaundryLink module.

## 1. Feature Overview

The Service and Price Management module maintains the laundry services that
the shop offers. It lets logged-in users view available services and lets the
owner manage service names, pricing units, prices, and availability.

The module covers these assigned requirements:

1. View available services and prices.
2. Add a laundry service.
3. Set the pricing unit to kilogram or piece.
4. Update service details and prices.
5. Activate or deactivate a laundry service.

## 2. Scope

### Included

- Displaying laundry services and their current prices.
- Supporting `KG` and `PIECE` pricing units.
- Adding and editing services as the owner.
- Activating and deactivating services as the owner.
- Providing active service data to Laundry Order Management.
- Protecting management operations with the existing authorization system.

### Not included

- Deleting services.
- Changing historical order prices.
- Processing payments.
- Managing customers, accounts, or order statuses.
- Introducing a web API or a separate server.
- Adding service descriptions unless the team approves a future schema change.

## 3. Users and Permissions

LaundryLink stores two roles in `users.role`:

- `ADMIN`: the shop owner.
- `STAFF`: a laundry staff member.

| Operation | Staff | Owner (`ADMIN`) |
|---|---:|---:|
| View active services and prices | Yes | Yes |
| View inactive services | No | Yes |
| Add a service | No | Yes |
| Change a name, unit, or price | No | Yes |
| Activate or deactivate a service | No | Yes |

The UI may hide or disable management controls for staff, but that is not
sufficient authorization. Every write operation must also call the existing
`SessionContext.requireAdmin()` method in the business service layer.

## 4. Accepted Working Decisions

These decisions are the current defaults. They may be revised after team
review, but any change must also be recorded in the Decision Log.

1. Staff see active services only.
2. The owner sees active and inactive services, with All/Active/Inactive filters.
3. Java and MySQL store pricing units as `KG` and `PIECE`.
4. The UI displays those values as `Per Kilogram` and `Per Piece`.
5. The owner may change a service's pricing unit after confirmation.
6. Name, unit, and price changes affect future orders only.
7. Historical orders retain their original name, unit, and price snapshots.
8. Deactivated services are unavailable when creating new orders.
9. Deactivated services remain attached to historical orders.
10. Deactivation requires confirmation.
11. The required service fields are name, pricing unit, price, and status.
12. Service names are unique, including names that differ only by letter case.
13. Prices must be positive and have no more than two decimal places.
14. The Laundry Order module should consume the shared active-service query.

## 5. Existing Database Design

No schema change is currently required. The existing `services` table contains:

| Column | Java representation | Purpose |
|---|---|---|
| `id` | `int` | Unique service identifier |
| `service_name` | `String` | Unique display name, maximum 100 characters |
| `pricing_unit` | `PricingUnit` | `KG` or `PIECE` |
| `current_price` | `BigDecimal` | Current price using `DECIMAL(10,2)` |
| `is_active` | `boolean` | Whether new orders may select the service |
| `created_at` | `LocalDateTime` | Creation date and time |

Existing constraints already require a unique name, a positive price, and a
valid pricing unit.

The `order_items` table preserves historical values in:

- `service_name_snapshot`
- `pricing_unit_snapshot`
- `unit_price`

Therefore, editing a current service must never update old `order_items` rows.

## 6. Application Architecture

The module follows the existing desktop architecture:

```text
MySQL services table
    -> LaundryServiceDAO (SQL and row mapping)
    -> ServiceCatalogService (validation, permissions, business rules)
    -> ServicesPricesController (JavaFX behavior and background tasks)
    -> services-prices.fxml (table, form, filters, and buttons)
```

There is no REST API and no separate backend server. Database operations run
inside the desktop application through JDBC. UI-triggered database work must
use `BackgroundTask` so it does not freeze the JavaFX thread.

## 7. Domain Model

### `PricingUnit`

- Constants: `KG`, `PIECE`.
- Friendly display names: `Per Kilogram`, `Per Piece`.
- Constant names intentionally match the MySQL enum values.

### `LaundryService`

Represents one row from the `services` table. Money uses `BigDecimal`; `double`
and `float` must not be used for prices.

## 8. Validation Rules

### Service name

- Required after trimming whitespace.
- Maximum 100 characters.
- Repeated internal whitespace should be normalized before saving.
- Must be unique according to the database's case-insensitive collation.

### Pricing unit

- Required.
- Must be exactly `KG` or `PIECE` internally.
- The UI should use a controlled selector, not free text.

### Price

- Required.
- Parsed as `BigDecimal`.
- Must be greater than zero.
- Must have no more than two decimal places.
- Must fit MySQL `DECIMAL(10,2)`.

### Status

- New services are active by default.
- Services are deactivated instead of deleted.

## 9. User Flows

### Staff viewing services

1. Staff opens Services & Prices.
2. The app loads active services on a background thread.
3. The table shows the service name, friendly pricing unit, and price.
4. Management controls are unavailable.

### Owner managing services

1. Owner opens Services & Prices.
2. The app loads active and inactive services.
3. The owner may filter the list by status.
4. The owner may add or select and edit a service.
5. The owner confirms unit changes and deactivation.
6. The table refreshes after a successful operation.

## 10. Laundry Order Integration Contract

Laundry Order Management will need active services containing:

- Service ID
- Service name
- Pricing unit
- Current price
- Active status

The order module should obtain them through the shared read-only service-catalog
operation rather than duplicating SQL.

The integration entry point is:

```java
ServiceCatalogService.listActiveServices()
```

The future Laundry Orders controller should call it through `BackgroundTask`,
populate its service selector with the returned `LaundryService` objects, and
show a useful empty-state message when no active services exist. This feature
must not replace the placeholder Orders screen because that module is owned by
the Laundry Order Management developer.

When an order is created, it should copy the selected service name, unit, and
price into `order_items`. `KG` permits decimal quantities; `PIECE` requires a
whole-number quantity. Order calculations and inserts remain owned by the
Laundry Order Management module.

## 11. Implementation Roadmap

- [x] Step 1: Add `PricingUnit` and `LaundryService` domain models.
- [x] Step 2: Add read-only `LaundryServiceDAO` operations.
- [x] Step 3: Add the business service for reading, validation, and authorization.
- [x] Step 4: Build the read-only Services & Prices table.
- [x] Step 5: Add the owner-only management form.
- [x] Step 6: Add service creation.
- [x] Step 7: Add service editing and pricing-unit change confirmation.
- [x] Step 8: Add activation and deactivation.
- [ ] Step 9: Integrate the shared active-service contract with Orders.
  The contract is ready, but `laundry-orders.fxml` is still a placeholder with
  no controller. Complete the connection with the Orders module owner when its
  service selector is implemented; do not duplicate the catalog query there.
- [ ] Step 10: Complete role, validation, database, and UI testing.

Each step must be reviewed, compiled, and committed before beginning the next
step. Pushing may group one or two completed commits. The pull request will be
created only after the feature is ready for team review.

## 12. Current File Inventory

### Added by this feature

| File | Purpose |
|---|---|
| `model/PricingUnit.java` | Defines `KG` and `PIECE` and their UI labels |
| `model/LaundryService.java` | Represents one service database record |
| `dao/LaundryServiceDAO.java` | Loads, inserts, updates, and changes the active status of services |
| `service/ServiceCatalogService.java` | Applies visibility, validation, login rules, and owner-only catalog management |
| `controller/ServicesPricesController.java` | Loads and filters the catalog and handles all owner-only management actions |
| `view/services-prices.fxml` | Displays the catalog and owner-only create/management form |
| `docs/service-price-management.md` | Feature specification and progress record |

### Expected later

| File | Expected purpose |
|---|---|
| `css/laundrylink.css` | Module styling only if existing styles are insufficient |

### Shared files to avoid changing unnecessarily

- `MainShellController.java` and `View.java`: the tab is already registered.
- `OrderService.java`: belongs to Laundry Order Management.
- Dashboard, payment, customer, account, and status-tracking files.
- Generated `nbproject/build-impl.xml` and `nbproject/jfx-impl.xml`.
- `database/schema.sql`: the existing schema already supports this feature.

## 13. Testing Checklist

### Read operations

- [ ] Owner can load active and inactive services.
- [ ] Staff can load active services.
- [ ] Empty results display a useful message.
- [ ] Database failures display a friendly error without freezing the UI.

### Creation and editing

- [ ] Valid services can be created.
- [ ] Blank and overly long names are rejected.
- [ ] Duplicate names are rejected with a friendly message.
- [ ] Zero, negative, malformed, over-precision, and oversized prices are rejected.
- [ ] `KG` and `PIECE` save and reload correctly.
- [ ] Previous order snapshots remain unchanged after an edit.

### Status and authorization

- [ ] Owner can activate and deactivate a service.
- [ ] Deactivation asks for confirmation.
- [ ] Staff cannot perform management operations through either the UI or service layer.
- [ ] Inactive services do not appear when creating a new order.

## 14. Risks and Coordination Points

- The Orders developer must use the shared active-service contract to avoid
  duplicate queries and inconsistent prices.
- UI hiding is not security; all writes require service-layer authorization.
- Price calculations must use `BigDecimal`.
- Database work on the JavaFX thread would freeze the interface.
- Shared CSS changes may conflict with other developers' UI work.
- The repository does not yet have a committed automated test setup.

## 15. Decision Log

| Date | Decision | Reason |
|---|---|---|
| 2026-10-06 | Use the existing `services` schema without adding columns | It supports all five assigned requirements |
| 2026-10-06 | Use `KG` and `PIECE` internally | They match the existing MySQL enums and order constraints |
| 2026-10-06 | Staff see active services; owner sees all | Staff need available choices while the owner must reactivate inactive records |
| 2026-10-06 | Never update historical order snapshots | Past totals must remain correct after catalog changes |
| 2026-10-06 | Deactivate instead of delete | Existing orders retain valid foreign-key references |

## 16. Maintenance Instructions

After each meaningful implementation step:

1. Update the roadmap checkbox.
2. Update the file inventory if files were added or responsibilities changed.
3. Record changed requirements or architecture decisions in the Decision Log.
4. Update the testing checklist when new behavior becomes testable.
5. Commit the documentation update with the corresponding implementation.

Do not use this file as a raw chat transcript. Keep it concise, current, and
focused on decisions another developer needs to understand the module.
