# Stock Management System (Spring Boot)

A web-based stock / inventory management system built with **Spring Boot 4**, **Spring Data JPA**,
**Spring Security**, **Thymeleaf** and **Bootstrap 5**. It is a Java rewrite of the original PHP project.

## Features

- **Login** with roles: `ADMIN` (everything, including user management) and `STAFF`
- **Dashboard**: today's and this month's sales, purchases, stock value, receivables/payables,
  low-stock alerts and recent sales
- **Products**: code, category, unit, supplier, cost/selling price, reorder level, expiry date, search, low-stock filter
- **Categories, Units (UOM), Suppliers, Customers**: full add/edit/delete
- **Purchases**: multi-item bills that add stock and record what is owed to the supplier
- **Sales**: multi-item invoices that check and reduce stock and record what the customer owes; printable invoice
- **Payments**: receive from customers / pay suppliers to settle balances
- **Reports**: sales and purchase reports for any date range, and a stock valuation report (all printable)
- Deleting a purchase or sale reverses its stock and balance changes
- **GST**: HSN code and GST rate per product; GSTIN and state per customer/supplier; CGST + SGST
  for same-state bills and IGST for other states; GST tax invoices; GST report with net payable
  and an HSN-wise summary. Prices are entered before GST. Set your own GSTIN and state under
  **Company Settings** (admin).

## Requirements

- JDK 17 or newer (JDK 21 recommended)
- Nothing else. Maven is downloaded automatically by the included wrapper (`mvnw`), and the database
  is an embedded H2 file stored in `./data`.

## Run

Double-click `run.bat`, or from a terminal in this folder:

```
mvnw spring-boot:run
```

Then open http://localhost:8080 and log in with:

| Username | Password |
|----------|----------|
| admin    | admin    |

Change this password under **Users** after the first login.

Sample categories, units, a supplier, customers and products are created on first start.

### Build a runnable JAR

```
mvnw clean package
java -jar target/stock-management-0.0.1-SNAPSHOT.jar
```

### Run the tests

```
mvnw test
```

## Using MySQL instead of H2

1. Create a MySQL user, or use `root`. The `stockdb` database is created automatically.
2. Edit `src/main/resources/application-mysql.properties` with your username and password.
3. Run with the `mysql` profile:

```
mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

Tables are created automatically on startup (`spring.jpa.hibernate.ddl-auto=update`).

## Deploying to Railway

The `Dockerfile` builds the app and starts it with the `prod` profile, which uses MySQL and turns off
the H2 console.

1. In Railway, create a project from this GitHub repo, then add a **MySQL** database to the project.
2. On the app service, set these variables (`MySQL` is the name of the database service):

   | Variable | Value |
   |---|---|
   | `MYSQLHOST` | `${{MySQL.MYSQLHOST}}` |
   | `MYSQLPORT` | `${{MySQL.MYSQLPORT}}` |
   | `MYSQLDATABASE` | `${{MySQL.MYSQLDATABASE}}` |
   | `MYSQLUSER` | `${{MySQL.MYSQLUSER}}` |
   | `MYSQLPASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
   | `ADMIN_PASSWORD` | A long, random password for the `admin` login |

3. Under **Settings > Networking**, generate a public domain for the app service.

`ADMIN_PASSWORD` only sets the password when the `admin` user is first created. To change it later,
use the **Users** page. The app will not start under the `prod` profile if this variable is missing.

## H2 console

While running on H2, admins can browse the database at http://localhost:8080/h2-console
with JDBC URL `jdbc:h2:file:./data/stockdb;AUTO_SERVER=TRUE`, user `sa` and an empty password.

## Project structure

```
src/main/java/com/stockms
├── config/        Security, form converters, display formatting, sample data
├── entity/        JPA entities (Product, Purchase, Sale, Payment, Supplier, Customer, ...)
├── repository/    Spring Data repositories
├── service/       InventoryService: business rules for stock, balances and payments
└── web/           MVC controllers
src/main/resources
├── templates/     Thymeleaf pages (layout.html is the shared shell)
└── static/        CSS and the JavaScript for multi-item bill entry
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `PORT` variable, or `8080` | HTTP port |
| `app.admin-password` | `admin` | Password for the first `admin` user (required under the `prod` profile) |
| `app.currency-symbol` | `₹` | Currency shown on screens and invoices |
