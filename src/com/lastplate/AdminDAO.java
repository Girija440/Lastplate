package com.lastplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for Admin actions, approvals, dashboard statistics,
 * and first-run bootstrap.
 */
public class AdminDAO {

    /**
     * Safe first-run bootstrap mechanism.
     * If no admin user exists in the database, generates and hashes a default admin account.
     */
    public boolean bootstrapAdmin(String email, String password, String name) {
        String countSql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next() && rs.getInt(1) == 0) {
                String adminEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : "admin@lastplate.com";
                String adminPass = (password != null && !password.trim().isEmpty()) ? password.trim() : "admin123";
                String adminName = (name != null && !name.trim().isEmpty()) ? name.trim() : "System Administrator";

                String hash = PasswordUtils.hashPassword(adminPass);

                String insertSql = "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, 'ADMIN')";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, adminName);
                    insertStmt.setString(2, adminEmail.toLowerCase());
                    insertStmt.setString(3, hash);
                    insertStmt.executeUpdate();
                    System.out.println("[AdminDAO] [BOOTSTRAP] Default admin initialized: " + adminEmail);
                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("[AdminDAO] Error during admin bootstrap: " + e.getMessage());
        }
        return false;
    }

    /**
     * Approves a PENDING reservation.
     * Uses database transaction with row locks to ensure atomic stock decrement.
     * If stock is insufficient, transaction is rolled back and an error is thrown.
     */
    public boolean approveReservation(int reservationId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Lock reservation row and verify status
            String resSql = "SELECT status FROM reservations WHERE reservation_id = ? FOR UPDATE";
            String currentStatus;
            try (PreparedStatement resStmt = conn.prepareStatement(resSql)) {
                resStmt.setInt(1, reservationId);
                try (ResultSet rs = resStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Reservation #" + reservationId + " not found.");
                    }
                    currentStatus = rs.getString("status");
                }
            }

            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Only PENDING reservations can be approved. Current status is " + currentStatus + ".");
            }

            // 2. Fetch reservation items
            String itemsSql = "SELECT ri.food_id, ri.quantity, f.food_name " +
                              "FROM reservation_items ri " +
                              "JOIN food_items f ON ri.food_id = f.food_id " +
                              "WHERE ri.reservation_id = ?";
            
            List<int[]> itemData = new ArrayList<>(); // [foodId, quantity]
            List<String> foodNames = new ArrayList<>();

            try (PreparedStatement itemsStmt = conn.prepareStatement(itemsSql)) {
                itemsStmt.setInt(1, reservationId);
                try (ResultSet rs = itemsStmt.executeQuery()) {
                    while (rs.next()) {
                        itemData.add(new int[]{rs.getInt("food_id"), rs.getInt("quantity")});
                        foodNames.add(rs.getString("food_name"));
                    }
                }
            }

            if (itemData.isEmpty()) {
                throw new IllegalStateException("Reservation has no items associated with it.");
            }

            // 3. For each item: lock food row, verify stock, and decrement
            for (int i = 0; i < itemData.size(); i++) {
                int foodId = itemData.get(i)[0];
                int requestedQty = itemData.get(i)[1];
                String foodName = foodNames.get(i);

                String lockFoodSql = "SELECT quantity FROM food_items WHERE food_id = ? FOR UPDATE";
                int availableQty;
                try (PreparedStatement lockStmt = conn.prepareStatement(lockFoodSql)) {
                    lockStmt.setInt(1, foodId);
                    try (ResultSet rs = lockStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Food item '" + foodName + "' (ID " + foodId + ") no longer exists.");
                        }
                        availableQty = rs.getInt("quantity");
                    }
                }

                if (availableQty < requestedQty) {
                    throw new IllegalStateException("Cannot approve: Insufficient stock for '" + foodName + 
                                                   "'. Available: " + availableQty + ", Requested: " + requestedQty + ".");
                }

                // Decrement stock atomically and set status to SOLD_OUT if zero
                String updateStockSql = "UPDATE food_items SET quantity = quantity - ?, " +
                                        "status = CASE WHEN quantity - ? <= 0 THEN 'SOLD_OUT' ELSE 'AVAILABLE' END " +
                                        "WHERE food_id = ?";
                try (PreparedStatement stockStmt = conn.prepareStatement(updateStockSql)) {
                    stockStmt.setInt(1, requestedQty);
                    stockStmt.setInt(2, requestedQty);
                    stockStmt.setInt(3, foodId);
                    stockStmt.executeUpdate();
                }
            }

            // 4. Mark reservation APPROVED
            String updateResSql = "UPDATE reservations SET status = 'APPROVED' WHERE reservation_id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateResSql)) {
                updateStmt.setInt(1, reservationId);
                updateStmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new SQLException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Rejects a PENDING reservation. Does not alter stock.
     */
    public boolean rejectReservation(int reservationId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            String checkSql = "SELECT status FROM reservations WHERE reservation_id = ? FOR UPDATE";
            String currentStatus;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, reservationId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Reservation #" + reservationId + " not found.");
                    }
                    currentStatus = rs.getString("status");
                }
            }

            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Only PENDING reservations can be rejected. Current status is " + currentStatus + ".");
            }

            String updateSql = "UPDATE reservations SET status = 'REJECTED' WHERE reservation_id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, reservationId);
                updateStmt.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {}
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new SQLException(e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Retrieves all reservations across all students with full details.
     */
    public List<Reservation> getAllReservations(String statusFilter) throws SQLException {
        List<Reservation> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT r.reservation_id, r.user_id, r.reservation_date, r.total_amount, r.status, " +
                "u.name AS student_name, u.email AS student_email " +
                "FROM reservations r " +
                "JOIN users u ON r.user_id = u.user_id "
        );

        if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append("WHERE r.status = ? ");
        }

        sql.append("ORDER BY r.reservation_date DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            if (statusFilter != null && !statusFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                stmt.setString(1, statusFilter.trim().toUpperCase());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Reservation res = new Reservation(
                            rs.getInt("reservation_id"),
                            rs.getInt("user_id"),
                            rs.getTimestamp("reservation_date"),
                            rs.getDouble("total_amount"),
                            rs.getString("status")
                    );
                    res.setStudentName(rs.getString("student_name"));
                    res.setStudentEmail(rs.getString("student_email"));

                    // Load items
                    loadReservationItems(conn, res);
                    list.add(res);
                }
            }
        }
        return list;
    }

    /**
     * Computes dashboard statistics for the admin dashboard.
     */
    public Map<String, Object> getDashboardStats() throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = DBConnection.getConnection()) {
            // Food counts
            String foodSql = "SELECT " +
                             "COUNT(*) AS total_foods, " +
                             "SUM(CASE WHEN status = 'AVAILABLE' AND quantity > 0 THEN 1 ELSE 0 END) AS available_foods, " +
                             "SUM(CASE WHEN status = 'SOLD_OUT' OR quantity = 0 THEN 1 ELSE 0 END) AS sold_out_foods " +
                             "FROM food_items";
            try (PreparedStatement stmt = conn.prepareStatement(foodSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalFoods", rs.getInt("total_foods"));
                    stats.put("availableFoods", rs.getInt("available_foods"));
                    stats.put("soldOutFoods", rs.getInt("sold_out_foods"));
                }
            }

            // Reservation counts & Revenue
            String resSql = "SELECT " +
                            "COUNT(*) AS total_reservations, " +
                            "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_reservations, " +
                            "SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_reservations, " +
                            "SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_reservations, " +
                            "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_reservations, " +
                            "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN total_amount ELSE 0 END), 0) AS total_revenue " +
                            "FROM reservations";
            try (PreparedStatement stmt = conn.prepareStatement(resSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalReservations", rs.getInt("total_reservations"));
                    stats.put("pendingReservations", rs.getInt("pending_reservations"));
                    stats.put("approvedReservations", rs.getInt("approved_reservations"));
                    stats.put("rejectedReservations", rs.getInt("rejected_reservations"));
                    stats.put("cancelledReservations", rs.getInt("cancelled_reservations"));
                    stats.put("totalRevenue", rs.getDouble("total_revenue"));
                }
            }

            // Students count
            String studentSql = "SELECT COUNT(*) FROM users WHERE role = 'STUDENT'";
            try (PreparedStatement stmt = conn.prepareStatement(studentSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalStudents", rs.getInt(1));
                }
            }
        }

        return stats;
    }

    private void loadReservationItems(Connection conn, Reservation res) throws SQLException {
        String sql = "SELECT ri.reservation_item_id, ri.reservation_id, ri.food_id, ri.quantity, ri.price_at_booking, " +
                     "f.food_name, f.image_url " +
                     "FROM reservation_items ri " +
                     "JOIN food_items f ON ri.food_id = f.food_id " +
                     "WHERE ri.reservation_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, res.getReservationId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReservationItem item = new ReservationItem(
                            rs.getInt("reservation_item_id"),
                            rs.getInt("reservation_id"),
                            rs.getInt("food_id"),
                            rs.getInt("quantity"),
                            rs.getDouble("price_at_booking")
                    );
                    item.setFoodName(rs.getString("food_name"));
                    item.setImageUrl(rs.getString("image_url"));
                    res.addItem(item);
                }
            }
        }
    }
}

