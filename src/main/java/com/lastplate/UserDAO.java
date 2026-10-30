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
 * Data Access Object for User operations on the 'users' table.
 */
public class UserDAO {

    /**
     * Registers a new user. Checks for duplicate email and hashes password securely.
     */
    public User register(String name, String email, String plainPassword, String role) throws SQLException, IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("A valid email address is required.");
        }
        if (plainPassword == null || plainPassword.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }

        String userRole = ("ADMIN".equalsIgnoreCase(role)) ? "ADMIN" : "STUDENT";
        String normalizedEmail = email.trim().toLowerCase();

        if (emailExists(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        String passwordHash = PasswordUtils.hashPassword(plainPassword);

        String sql = "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name.trim());
            stmt.setString(2, normalizedEmail);
            stmt.setString(3, passwordHash);
            stmt.setString(4, userRole);

            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    return findById(newId);
                } else {
                    throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Authenticates a user with email and plain password.
     * @return User object on success, null on invalid credentials
     */
    public User login(String email, String plainPassword) throws SQLException {
        if (email == null || plainPassword == null) {
            return null;
        }

        String sql = "SELECT user_id, name, email, password_hash, role, created_at FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (PasswordUtils.verifyPassword(plainPassword, storedHash)) {
                        return mapUser(rs);
                    }
                }
            }
        }
        return null;
    }

    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, role, created_at FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT user_id, name, email, password_hash, role, created_at FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Returns list of all registered students with their reservation count and total spend.
     */
    public List<Map<String, Object>> getAllStudentsWithStats() throws SQLException {
        List<Map<String, Object>> students = new ArrayList<>();
        String sql = "SELECT u.user_id, u.name, u.email, u.created_at, " +
                     "COUNT(r.reservation_id) AS total_reservations, " +
                     "COALESCE(SUM(CASE WHEN r.status = 'APPROVED' THEN r.total_amount ELSE 0 END), 0) AS total_spent " +
                     "FROM users u " +
                     "LEFT JOIN reservations r ON u.user_id = r.user_id " +
                     "WHERE u.role = 'STUDENT' " +
                     "GROUP BY u.user_id, u.name, u.email, u.created_at " +
                     "ORDER BY u.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", rs.getInt("user_id"));
                map.put("name", rs.getString("name"));
                map.put("email", rs.getString("email"));
                map.put("createdAt", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                map.put("totalReservations", rs.getInt("total_reservations"));
                map.put("totalSpent", rs.getDouble("total_spent"));
                students.add(map);
            }
        }
        return students;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("role"),
                rs.getTimestamp("created_at")
        );
    }
}

