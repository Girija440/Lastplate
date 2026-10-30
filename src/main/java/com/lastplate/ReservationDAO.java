package com.lastplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for Reservations and Reservation Items.
 * Enforces transaction management and stock rules.
 */
public class ReservationDAO {

    /**
     * Creates a new PENDING reservation.
     * Note: Stock is NOT reduced while the reservation is PENDING.
     */
    public Reservation createReservation(int userId, int foodId, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be at least 1.");
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Fetch food item details and verify availability
            String foodSql = "SELECT food_name, price, quantity, status FROM food_items WHERE food_id = ?";
            double priceAtBooking;
            int currentStock;
            String status;

            try (PreparedStatement foodStmt = conn.prepareStatement(foodSql)) {
                foodStmt.setInt(1, foodId);
                try (ResultSet rs = foodStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Food item with ID " + foodId + " does not exist.");
                    }
                    priceAtBooking = rs.getDouble("price");
                    currentStock = rs.getInt("quantity");
                    status = rs.getString("status");
                }
            }

            if ("SOLD_OUT".equalsIgnoreCase(status) || currentStock <= 0) {
                throw new IllegalStateException("Selected food item is currently sold out.");
            }
            if (quantity > currentStock) {
                throw new IllegalStateException("Requested quantity (" + quantity + ") exceeds current available stock (" + currentStock + ").");
            }

            double totalAmount = priceAtBooking * quantity;

            // 2. Insert into reservations (status PENDING)
            int reservationId;
            String resSql = "INSERT INTO reservations (user_id, total_amount, status) VALUES (?, ?, 'PENDING')";
            try (PreparedStatement resStmt = conn.prepareStatement(resSql, Statement.RETURN_GENERATED_KEYS)) {
                resStmt.setInt(1, userId);
                resStmt.setDouble(2, totalAmount);
                resStmt.executeUpdate();

                try (ResultSet keys = resStmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        reservationId = keys.getInt(1);
                    } else {
                        throw new SQLException("Failed to create reservation, no ID returned.");
                    }
                }
            }

            // 3. Insert into reservation_items
            String itemSql = "INSERT INTO reservation_items (reservation_id, food_id, quantity, price_at_booking) VALUES (?, ?, ?, ?)";
            try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                itemStmt.setInt(1, reservationId);
                itemStmt.setInt(2, foodId);
                itemStmt.setInt(3, quantity);
                itemStmt.setDouble(4, priceAtBooking);
                itemStmt.executeUpdate();
            }

            conn.commit();
            return getReservationById(conn, reservationId);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
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
     * Retrieves all reservations belonging to a student.
     */
    public List<Reservation> getReservationsByUserId(int userId) throws SQLException {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT r.reservation_id, r.user_id, r.reservation_date, r.total_amount, r.status " +
                     "FROM reservations r " +
                     "WHERE r.user_id = ? " +
                     "ORDER BY r.reservation_date DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Reservation res = mapReservation(rs);
                    loadReservationItems(conn, res);
                    list.add(res);
                }
            }
        }
        return list;
    }

    /**
     * Retrieves a single reservation with its items by ID.
     */
    public Reservation getReservationById(int reservationId) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            return getReservationById(conn, reservationId);
        }
    }

    private Reservation getReservationById(Connection conn, int reservationId) throws SQLException {
        String sql = "SELECT r.reservation_id, r.user_id, r.reservation_date, r.total_amount, r.status, " +
                     "u.name AS student_name, u.email AS student_email " +
                     "FROM reservations r " +
                     "JOIN users u ON r.user_id = u.user_id " +
                     "WHERE r.reservation_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reservationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Reservation res = mapReservation(rs);
                    res.setStudentName(rs.getString("student_name"));
                    res.setStudentEmail(rs.getString("student_email"));
                    loadReservationItems(conn, res);
                    return res;
                }
            }
        }
        return null;
    }

    /**
     * Cancels an eligible reservation.
     * If the reservation was APPROVED, restores stock exactly once in a transaction.
     * If PENDING, changes status to CANCELLED without touching stock.
     */
    public boolean cancelReservation(int reservationId, int userId, boolean isAdmin) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Fetch reservation with exclusive row lock
            String checkSql = "SELECT user_id, status FROM reservations WHERE reservation_id = ? FOR UPDATE";
            int ownerId;
            String currentStatus;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, reservationId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Reservation not found.");
                    }
                    ownerId = rs.getInt("user_id");
                    currentStatus = rs.getString("status");
                }
            }

            // Check authorization
            if (!isAdmin && ownerId != userId) {
                throw new IllegalStateException("You are not authorized to cancel this reservation.");
            }

            if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Reservation is already cancelled.");
            }
            if ("REJECTED".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Rejected reservations cannot be cancelled.");
            }

            // If it was APPROVED, we must restore stock
            if ("APPROVED".equalsIgnoreCase(currentStatus)) {
                String itemSql = "SELECT food_id, quantity FROM reservation_items WHERE reservation_id = ?";
                try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                    itemStmt.setInt(1, reservationId);
                    try (ResultSet rs = itemStmt.executeQuery()) {
                        while (rs.next()) {
                            int foodId = rs.getInt("food_id");
                            int qty = rs.getInt("quantity");

                            String restoreSql = "UPDATE food_items SET quantity = quantity + ?, status = 'AVAILABLE' WHERE food_id = ?";
                            try (PreparedStatement restoreStmt = conn.prepareStatement(restoreSql)) {
                                restoreStmt.setInt(1, qty);
                                restoreStmt.setInt(2, foodId);
                                restoreStmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            // Update reservation status to CANCELLED
            String updateSql = "UPDATE reservations SET status = 'CANCELLED' WHERE reservation_id = ?";
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
     * Retrieves summary stats for a student's dashboard.
     */
    public Map<String, Object> getStudentDashboardStats(int userId) throws SQLException {
        Map<String, Object> stats = new HashMap<>();

        String sql = "SELECT " +
                     "COUNT(*) AS total_reservations, " +
                     "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count, " +
                     "SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count, " +
                     "COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN total_amount ELSE 0 END), 0) AS total_spent " +
                     "FROM reservations WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalReservations", rs.getInt("total_reservations"));
                    stats.put("pendingCount", rs.getInt("pending_count"));
                    stats.put("approvedCount", rs.getInt("approved_count"));
                    stats.put("totalSpent", rs.getDouble("total_spent"));
                }
            }
        }

        // Also fetch total available food count for discovery banner
        String foodCountSql = "SELECT COUNT(*) FROM food_items WHERE status = 'AVAILABLE' AND quantity > 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(foodCountSql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                stats.put("availableFoodsCount", rs.getInt(1));
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

    private Reservation mapReservation(ResultSet rs) throws SQLException {
        return new Reservation(
                rs.getInt("reservation_id"),
                rs.getInt("user_id"),
                rs.getTimestamp("reservation_date"),
                rs.getDouble("total_amount"),
                rs.getString("status")
        );
    }
}

