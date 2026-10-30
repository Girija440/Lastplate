package com.lastplate;

import java.sql.Timestamp;

/**
 * Model representing a food item in the LastPlate system.
 * Matches MySQL 'food_items' table.
 */
public class FoodItem {
    private int foodId;
    private String foodName;
    private String description;
    private int categoryId;
    private String categoryName; // helper for UI display
    private double price;
    private int quantity;
    private String imageUrl;
    private String status; // 'AVAILABLE' or 'SOLD_OUT'
    private Timestamp createdAt;

    public FoodItem() {
    }

    public FoodItem(int foodId, String foodName, String description, int categoryId, 
                    double price, int quantity, String imageUrl, String status, Timestamp createdAt) {
        this.foodId = foodId;
        this.foodName = foodName;
        this.description = description;
        this.categoryId = categoryId;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getFoodId() {
        return foodId;
    }

    public void setFoodId(int foodId) {
        this.foodId = foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

