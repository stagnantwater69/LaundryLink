# Database Changes

Pulling from GitHub updates the code, **not your database**. Your MySQL database lives on your own PC, so you have to update it yourself, once.

If you skip this, the app shows errors like **"Unknown column 'shop_id'"** when you log in or open a tab.

---

## What changed

| Change | Why |
|---|---|
| New `shops` table | Each owner now registers their own shop from the **Register** screen. |
| `shop_id` added to `users`, `customers`, `services`, `orders` | Every shop only sees its own data. |
| Service names are unique **per shop** | Two shops can both have "Wash & Fold". |
| `v_order_balances` now includes `shop_id` | So balances can be filtered by shop. |
| `customers.sex` column (optional) | Picks the customer's avatar picture. |
| Owner Setup screen removed | Replaced by **Register Your Shop**. |

---

## Which one are you?

### A. I want to keep my current data

Open **MySQL Workbench** as **root** and run these, in order:

1. **`add-shops.sql`**: adds shops and `shop_id`, and moves all your existing data into one shop.
   - Want a different shop name? Change `'My Laundry Shop'` on line 16 before running.
   - Safe to run again if it stops halfway.
2. **`add-customer-sex.sql`**: adds the customer sex column.
   - If you run it twice you'll see "Duplicate column name 'sex'". That's harmless and means it's already done.

Then open the app and log in like before.

### B. My data is just test data (start fresh)

1. In Workbench (as root): `DROP DATABASE laundrylink;`
2. Run **`schema.sql`**. It already has every change above.
3. Run **`create-app-user.sql`** (change `change-me` to your password first).
4. Open the app → **Register Your Shop** → create your shop and owner account.
5. Optional: run **`sample-data.sql`** for demo services and customers. It must run **after** step 4, because the demo data goes into the first registered shop.

Make sure `config/database.properties` has the same password you set in step 3.

### C. New to the project

Just follow option **B**. You don't need `add-shops.sql` or `add-customer-sex.sql`.

---

## Quick check

Run this in Workbench. If it returns a row, your database is up to date:

```sql
SELECT column_name FROM information_schema.columns
WHERE table_schema = 'laundrylink' AND table_name = 'customers' AND column_name = 'shop_id';
```
