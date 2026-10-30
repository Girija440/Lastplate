package com.lastplate;

import java.sql.Timestamp;

/**
 * Model representing a user in the LastPlate system.
 * Matches MySQL 'users' table.
 */
public class User {
    private int userId;
    private String name;
    private String email;
    private transient String passwordHash; // transient so Gson will not serialize password hash
    private String role; // 'STUDENT' or 'ADMIN'
    private Timestamp createdAt;

    public User() {
    }

    public User(int userId, String name, String email, String passwordHash, String role, Timestamp createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}

