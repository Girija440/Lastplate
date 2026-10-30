# LastPlate – Smart Campus Food Availability & Reservation System

> **Tagline:** *Good food. Zero uncertainty.*  
> **Target Audience:** College OOPs & DBMS Project Demonstration (Second-Year IT standard)

LastPlate is a full-stack campus dining management system engineered with **Java 25**, **Java JDBC**, Java's built-in **`com.sun.net.httpserver.HttpServer`**, and **MySQL 8**. The frontend is built purely with **HTML5, CSS3, and Vanilla JavaScript**, featuring a violet-themed design.

---

## 1. System Architecture & Technology Stack

| Layer | Technology | Details |
| :--- | :--- | :--- |
| **Frontend** | HTML5, CSS3, Vanilla JavaScript | Responsive violet theme (`#24143D`, `#321B52`, `#8052C8`, `#B69AE8`, `#F4F0FA`), asynchronous `fetch` calls, modals, toasts |
| **Backend** | Java 25 (Standard Edition) | Lightweight, non-framework architecture using Java's built-in `HttpServer` and virtual threads |
| **Data Access** | Java JDBC (Connector/J 9.7.0) | `PreparedStatement`, connection pooling principles, ACID transactions (`setAutoCommit(false)`, row locking, `commit()`, `rollback()`) |
| **Serialization** | Google Gson 2.10.1 | JSON REST request parsing and payload serialization |
| **Security** | `PBKDF2WithHmacSHA256` | Secure password hashing (65,536 iterations, 128-bit random salt, constant-time verification) |
| **Database** | MySQL 8.0 (`lastplate_db`) | 5 normalized relational tables with foreign key constraints |

---

## 2. Relational Database Schema

The system integrates directly with the existing `lastplate_db` schema:

```
users (user_id PK, name, email UNIQUE, password_hash, role ENUM('STUDENT','ADMIN'), created_at)
categories (category_id PK, category_name UNIQUE)
food_items (food_id PK, food_name, description, category_id FK, price, quantity, image_url, status ENUM('AVAILABLE','SOLD_OUT'), created_at)
reservations (reservation_id PK, user_id FK, reservation_date, total_amount, status ENUM('PENDING','APPROVED','REJECTED','CANCELLED'))
reservation_items (reservation_item_id PK, reservation_id FK, food_id FK, quantity, price_at_booking)
```

---

## 3. Reservation & Stock Lifecycle (ACID Transactions)

```
[Student creates reservation]
          │
          ▼
   Status: PENDING
   (Stock remains UNCHANGED in food_items)
          │
          ├───► [Admin Rejects] ───► Status: REJECTED (Stock untouched)
          │
          └───► [Admin Approves]
                     │
                     ▼
             Start Transaction
                     │
             Lock Food Row (`FOR UPDATE`)
                     │
             Verify: Available Stock >= Requested
                     │
            ┌────────┴────────┐
         [Enough]         [Insufficient]
            │                   │
      Deduct Stock        Rollback Transaction
      Mark: APPROVED      Reject with Error
      Commit Transaction
            │
            ▼
    [Cancellation?]
            │
      Start Transaction
      Restore Stock (`quantity = quantity + ?`)
      Mark: CANCELLED
      Commit Transaction
```

---

## 4. Project Directory Structure

```
Lastplate/
├── .gitignore
├── pom.xml                                   # Optional Maven configuration
├── config.properties                         # Active local DB configuration
├── config.properties.example                 # Template for repository
├── build.bat                                 # Windows 1-click compiler
├── run.bat                                   # Windows 1-click runner
├── lib/                                      # Standalone dependencies
│   ├── mysql-connector-j.jar                 # MySQL JDBC Driver
│   └── gson.jar                              # Google Gson library
├── src/main/java/com/lastplate/
│   ├── Main.java                             # Application entry point & HTTP router
│   ├── DBConnection.java                     # JDBC connection manager
│   ├── User.java                             # User model
│   ├── UserDAO.java                          # User DB access & student queries
│   ├── Category.java                         # Category model
│   ├── FoodItem.java                         # Food item model
│   ├── FoodDAO.java                          # Food catalog & stock queries
│   ├── Reservation.java                      # Reservation model
│   ├── ReservationItem.java                  # Reservation items model
│   ├── ReservationDAO.java                   # Student reservations & cancel logic
│   ├── AdminDAO.java                         # Approvals, stock transactions & stats
│   ├── PasswordUtils.java                    # PBKDF2WithHmacSHA256 security
│   ├── SessionManager.java                   # In-memory session tokens & role guards
│   └── JsonUtil.java                         # JSON parser & HTTP response helpers
└── public/                                   # Frontend Static Web Assets
    ├── index.html                            # Landing page
    ├── login.html                            # Login page (student & admin)
    ├── register.html                         # Student registration
    ├── student-dashboard.html                # Student metrics & recent reservations
    ├── menu.html                             # Food menu, search, filter, reserve modal
    ├── reservations.html                     # Personal reservations & cancellation
    ├── admin-dashboard.html                  # Admin metrics, pending queue, students
    ├── admin-foods.html                      # Food catalog & inventory manager
    ├── admin-reservations.html               # All reservations approval console
    ├── css/
    │   └── style.css                         # Violet theme stylesheet
    └── js/
        ├── app.js                            # Global utilities, fetch client, toasts
        ├── auth.js                           # Login & registration handlers
        ├── student.js                        # Student views & reservation flow
        └── admin.js                          # Admin approvals & inventory operations
```

---

## 5. Configuration & Credentials

### `config.properties`
The application loads connection details on startup from `config.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/lastplate_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=root

# Default Admin Bootstrap (Generated securely on first run)
admin.default.email=admin@lastplate.com
admin.default.password=admin123
admin.default.name=System Administrator

# Server Port
server.port=8080
```

### Safe Admin Bootstrap
On first startup, the server automatically checks if an admin exists in `users`. If not found, it generates a random salt, computes the `PBKDF2WithHmacSHA256` hash of `admin.default.password`, and safely creates the account.

---

## 6. How to Build & Run

### Method A: 1-Click Scripts (Recommended for Windows)

1. **Compile the Project:**
   Double-click `build.bat` or run:
   ```cmd
   build.bat
   ```

2. **Start the Server:**
   Double-click `run.bat` or run:
   ```cmd
   run.bat
   ```

3. **Open in Browser:**
   ```
   http://localhost:8080/
   ```

---

### Method B: Manual Command Line

1. **Compile with `javac`:**
   ```cmd
   javac -encoding UTF-8 -cp "lib/*" -d bin src\main\java\com\lastplate\*.java
   ```

2. **Run with `java`:**
   ```cmd
   java -cp "bin;lib/*" com.lastplate.Main
   ```

---

### Method C: With Maven (if installed)

```cmd
mvn compile exec:java
```

---

## 7. Demo Accounts & Walkthrough

| Role | Email | Password | Access Link |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin@lastplate.com` | `admin123` | [Sign In](http://localhost:8080/login.html) *(Use "Autofill Admin Login" button)* |
| **Student** | Any registered student | (Chosen at sign-up) | [Register Student](http://localhost:8080/register.html) |

---

## 8. REST API Endpoints

### Authentication & Profiles
- `POST /api/register` – Student registration (validates email uniqueness, hashes password)
- `POST /api/login` – Authenticates user, creates session token, sets cookie
- `POST /api/logout` – Revokes session token
- `GET /api/me` – Returns active session profile

### Food Catalog & Inventory
- `GET /api/categories` – Returns all food categories
- `GET /api/foods` – Returns food items (supports `?search=` and `?category=`)
- `POST /api/admin/foods` – [Admin] Adds a new food item to catalog
- `PUT /api/admin/foods/{id}` – [Admin] Updates food item details, price, or stock
- `DELETE /api/admin/foods/{id}` – [Admin] Safely deletes food item (with constraint check)

### Reservations
- `POST /api/reservations` – [Student] Submits a `PENDING` reservation (stock unchanged)
- `GET /api/my-reservations` – [Student] Lists student's personal reservations
- `PUT /api/reservations/{id}/cancel` – Cancels reservation (restores stock if was `APPROVED`)
- `GET /api/admin/reservations` – [Admin] Lists all campus reservations (supports `?status=`)
- `PUT /api/admin/reservations/{id}/approve` – [Admin] Atomically decrements stock and approves
- `PUT /api/admin/reservations/{id}/reject` – [Admin] Rejects reservation without touching stock

### Dashboards
- `GET /api/student/dashboard` – Student metrics (total, pending, approved, spent)
- `GET /api/admin/dashboard` – Admin metrics (foods, orders, pending, revenue, students)
- `GET /api/admin/students` – [Admin] Detailed student diner directory

---

## 9. Verification & Test Plan

1. **Database Connectivity:** Tested and verified with MySQL 8.
2. **Student Registration:** Created student account with unique email check and PBKDF2 hash.
3. **Student Login:** Tested valid authentication and rejected invalid passwords.
4. **Food Catalog:** Verified search by name and category filtering.
5. **Pending Reservation:** Confirmed reservation creates with status `PENDING` and **zero stock deduction**.
6. **Admin Approval:** Verified transaction locking, stock decrement from initial quantity, and status `APPROVED`.
7. **Admin Rejection:** Verified rejection leaves inventory stock unchanged.
8. **Cancellation & Restoration:** Verified cancelling an approved reservation restores stock to inventory.
9. **Role-Based Protection:** Verified `/api/admin/*` endpoints strictly require `ADMIN` role.
10. **Overbooking Rejection:** Verified reservations exceeding available inventory are safely blocked.

