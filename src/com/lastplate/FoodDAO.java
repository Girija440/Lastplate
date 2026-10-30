package com.lastplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Food items and Categories.
 */
public class FoodDAO {

    /**
     * Retrieves all food items with optional search query and category filtering.
     */
    public List<FoodItem> getAllFoodItems(String search, Integer categoryId) throws SQLException {
        List<FoodItem> items = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT f.food_id, f.food_name, f.description, f.category_id, c.category_name, " +
                "f.price, f.quantity, f.image_url, f.status, f.created_at " +
                "FROM food_items f " +
                "LEFT JOIN categories c ON f.category_id = c.category_id " +
                "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND LOWER(f.food_name) LIKE ? ");
            params.add("%" + search.trim().toLowerCase() + "%");
        }

        if (categoryId != null && categoryId > 0) {
            sql.append("AND f.category_id = ? ");
            params.add(categoryId);
        }

        sql.append("ORDER BY f.food_id ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    items.add(mapFoodItem(rs));
                }
            }
        }
        return items;
    }

    /**
     * Retrieves a single food item by ID.
     */
    public FoodItem getFoodItemById(int foodId) throws SQLException {
        String sql = "SELECT f.food_id, f.food_name, f.description, f.category_id, c.category_name, " +
                     "f.price, f.quantity, f.image_url, f.status, f.created_at " +
                     "FROM food_items f " +
                     "LEFT JOIN categories c ON f.category_id = c.category_id " +
                     "WHERE f.food_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, foodId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapFoodItem(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retrieves all food categories.
     */
    public List<Category> getAllCategories() throws SQLException {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT category_id, category_name FROM categories ORDER BY category_id ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(new Category(rs.getInt("category_id"), rs.getString("category_name")));
            }
        }
        return categories;
    }

    /**
     * Adds a new food item to the inventory.
     */
    public FoodItem addFoodItem(FoodItem item) throws SQLException {
        if (item.getFoodName() == null || item.getFoodName().trim().isEmpty()) {
            throw new IllegalArgumentException("Food name is required.");
        }
        if (item.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        if (item.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        String status = (item.getQuantity() > 0) ? "AVAILABLE" : "SOLD_OUT";
        if (item.getStatus() != null && !item.getStatus().trim().isEmpty()) {
            status = item.getStatus().trim();
        }

        String sql = "INSERT INTO food_items (food_name, description, category_id, price, quantity, image_url, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, item.getFoodName().trim());
            stmt.setString(2, item.getDescription() != null ? item.getDescription().trim() : "");
            stmt.setInt(3, item.getCategoryId());
            stmt.setDouble(4, item.getPrice());
            stmt.setInt(5, item.getQuantity());
            stmt.setString(6, item.getImageUrl() != null ? item.getImageUrl().trim() : "");
            stmt.setString(7, status);

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return getFoodItemById(keys.getInt(1));
                } else {
                    throw new SQLException("Failed to add food item, no ID returned.");
                }
            }
        }
    }

    /**
     * Updates an existing food item.
     */
    public boolean updateFoodItem(FoodItem item) throws SQLException {
        if (item.getFoodName() == null || item.getFoodName().trim().isEmpty()) {
            throw new IllegalArgumentException("Food name is required.");
        }
        if (item.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        if (item.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        String status = (item.getQuantity() > 0) ? "AVAILABLE" : "SOLD_OUT";
        if (item.getStatus() != null && !item.getStatus().trim().isEmpty()) {
            status = item.getStatus().trim();
        }

        String sql = "UPDATE food_items SET food_name = ?, description = ?, category_id = ?, " +
                     "price = ?, quantity = ?, image_url = ?, status = ? WHERE food_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, item.getFoodName().trim());
            stmt.setString(2, item.getDescription() != null ? item.getDescription().trim() : "");
            stmt.setInt(3, item.getCategoryId());
            stmt.setDouble(4, item.getPrice());
            stmt.setInt(5, item.getQuantity());
            stmt.setString(6, item.getImageUrl() != null ? item.getImageUrl().trim() : "");
            stmt.setString(7, status);
            stmt.setInt(8, item.getFoodId());

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates only stock quantity and auto-updates status.
     */
    public boolean updateStock(int foodId, int newQuantity) throws SQLException {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }
        String sql = "UPDATE food_items SET quantity = ?, status = CASE WHEN ? <= 0 THEN 'SOLD_OUT' ELSE 'AVAILABLE' END WHERE food_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newQuantity);
            stmt.setInt(2, newQuantity);
            stmt.setInt(3, foodId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Updates only price.
     */
    public boolean updatePrice(int foodId, double newPrice) throws SQLException {
        if (newPrice < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        String sql = "UPDATE food_items SET price = ? WHERE food_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, newPrice);
            stmt.setInt(2, foodId);
            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a food item if not referenced in any reservations.
     * Throws an exception with clean advice if reservations exist.
     */
    public boolean deleteFoodItem(int foodId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM reservation_items WHERE food_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, foodId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new IllegalStateException("Cannot delete this food item because it is referenced in past reservations. You can mark it as SOLD_OUT or set quantity to 0 instead.");
                }
            }
        }

        String deleteSql = "DELETE FROM food_items WHERE food_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
            delStmt.setInt(1, foodId);
            return delStmt.executeUpdate() > 0;
        }
    }

    private FoodItem mapFoodItem(ResultSet rs) throws SQLException {
        FoodItem item = new FoodItem(
                rs.getInt("food_id"),
                rs.getString("food_name"),
                rs.getString("description"),
                rs.getInt("category_id"),
                rs.getDouble("price"),
                rs.getInt("quantity"),
                rs.getString("image_url"),
                rs.getString("status"),
                rs.getTimestamp("created_at")
        );
        item.setCategoryName(rs.getString("category_name"));
        return item;
    }
}

