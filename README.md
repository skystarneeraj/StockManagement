# Stock Management System (Spring Boot)

A web-based stock / inventory management system built with **Spring Boot 4**, **Spring Data JPA**,
**Spring Security**, **Thymeleaf** and **Bootstrap 5**. It is a Java rewrite of the original PHP project.

## Features

- **Multiple companies and branches**: each company's data is completely separate; customers, suppliers,
  products, categories and units are shared by the branches of one company, while stock, sales, purchases
  and payments are kept per branch
- **Login** with roles: `SUPER_ADMIN` (manages companies), `COMPANY_ADMIN` (all branches of their company,
  branches, users and company profile), `BRANCH_MANAGER` and `STAFF` (only their assigned branches).
  A branch switcher in the top bar selects the working branch; company admins can also view "All branches"
- **Stock transfers** between branches: draft, dispatch (stock leaves the source), receive (stock arrives)
- **Dashboard**: today's and this month's sales, purchases, stock value, receivables/payables,
  low-stock alerts and recent sales
- **Products**: code, category, unit, supplier, cost/selling price, reorder level, expiry date, search, low-stock filter
- **Categories, Units (UOM), Suppliers, Customers**: full add/edit/delete
- **Purchases**: multi-item bills that add stock and record what is owed to the supplier
- **Sales**: multi-item invoices that check and reduce stock and record what the customer owes; printable invoice
- **Payments**: receive from customers / pay suppliers to settle balances
- **Reports**: sales, purchase, stock and GST reports for one branch or all branches, with branch-wise totals (all printable)
- **Invoice numbers** per branch and financial year, e.g. `KT-DEL-2026-27/0001` (company code, branch code)
- Deleting a purchase or sale reverses its stock and balance changes
- **GST**: HSN code and GST rate per product; GSTIN and state per customer/supplier; CGST + SGST
  when the customer/supplier is in the same state as the **branch**, IGST otherwise; GST tax invoices
  showing the branch address and GSTIN (the branch's own, or the company's); GST report with net payable
  and an HSN-wise summary. Prices are entered before GST. Set company details under **Company Profile**
  and each branch's state and GSTIN under **Branches** (admin).

## Requirements

- JDK 17 or newer (JDK 21 recommended)
- PostgreSQL 15 or newer
- Maven is downloaded automatically by the included wrapper (`mvnw`).

## Database setup

The app uses its own `stockapp` login and `stockdb` database. The tests use a separate `stockdb_test`
database, which they wipe on every run. Create them once, using your PostgreSQL admin login:

```sql
CREATE ROLE stockapp LOGIN PASSWORD 'choose-a-password';
CREATE DATABASE stockdb OWNER stockapp ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE stockdb_test OWNER stockapp ENCODING 'UTF8' TEMPLATE template0;
```

Then create a file named `.env` in this folder (git ignores it):

```
DB_PASSWORD=choose-a-password
```

Tables are created and upgraded on startup by **Flyway** migrations in `src/main/resources/db/migration`.

### Upgrading a single-company database

The first start of this version runs `V2__multi_company.sql`, which moves all existing data into one company
(from Company Settings) with a **Head Office** branch: current stock becomes Head Office stock and every user
gets access to Head Office. Existing `ADMIN` users become `COMPANY_ADMIN`. **Back up the database first**:

```
pg_dump -h localhost -U stockapp -d stockdb -Fc -f stockdb_backup.dump
```

The migration runs in one transaction, so if it fails nothing is changed. The old `products.quantity`
column is kept as `legacy_quantity` and the `company_settings` table is kept, as a safety copy.

## Run

Double-click `run.bat`, or from a terminal in this folder:

```
mvnw spring-boot:run
```

Then open http://localhost:8080 and log in with:

| Username   | Password   | Can |
|------------|------------|-----|
| admin      | admin      | Use the sample company "My Company" (company admin) |
| superadmin | superadmin | Create and manage companies |

Change these passwords after the first login.

On an empty database, the company "My Company" with a Head Office branch and sample categories, units,
a supplier, customers and products are created on first start.

### Build a runnable JAR

```
mvnw clean package
java -jar target/stock-management-0.0.1-SNAPSHOT.jar
```

### Run the tests

```
mvnw test
```

## Deploying to Railway

The `Dockerfile` builds the app and starts it with the `prod` profile, which reads its PostgreSQL
connection from environment variables.

1. In Railway, create a project from this GitHub repo, then add a **PostgreSQL** database to the project.
2. On the app service, set these variables (`Postgres` is the name of the database service):

   | Variable | Value |
   |---|---|
   | `PGHOST` | `${{Postgres.PGHOST}}` |
   | `PGPORT` | `${{Postgres.PGPORT}}` |
   | `PGDATABASE` | `${{Postgres.PGDATABASE}}` |
   | `PGUSER` | `${{Postgres.PGUSER}}` |
   | `PGPASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `ADMIN_PASSWORD` | A long, random password for the `admin` login |
   | `SUPERADMIN_PASSWORD` | A long, random password for the `superadmin` login |

3. Under **Settings > Networking**, generate a public domain for the app service.

`ADMIN_PASSWORD` and `SUPERADMIN_PASSWORD` only set the passwords when those users are first created.
To change `admin` later, use the **Users** page. The app will not start under the `prod` profile if either
variable is missing.

## Project structure

```
src/main/java/com/stockms
├── config/        Security, form converters, display formatting, sample data
├── entity/        JPA entities (Product, Purchase, Sale, Payment, Supplier, Customer, ...)
├── repository/    Spring Data repositories
├── service/       Business rules: InventoryService (bills, payments), StockService (branch stock),
│                  StockTransferService, DocNumberService (invoice numbers), CompanyService
├── tenant/        Current company/branch per request (TenantContext, TenantFilter, TenantResolver)
└── web/           MVC controllers
src/main/resources
├── db/migration/  Flyway SQL migrations
├── templates/     Thymeleaf pages (layout.html is the shared shell)
└── static/        CSS and the JavaScript for multi-item bill entry
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `PORT` variable, or `8080` | HTTP port |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `5432`, `stockdb` | PostgreSQL location (not used under the `prod` profile) |
| `DB_USERNAME`, `DB_PASSWORD` | `stockapp`, none | PostgreSQL login; `DB_PASSWORD` is required (not used under the `prod` profile) |
| `app.admin-password` | `admin` | Password for the first `admin` user (required under the `prod` profile) |
| `app.superadmin-password` | `SUPERADMIN_PASSWORD` variable, or `superadmin` | Password for the `superadmin` user (required under the `prod` profile) |
| `app.currency-symbol` | `₹` | Currency shown on screens and invoices |
