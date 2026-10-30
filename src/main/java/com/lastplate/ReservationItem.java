package com.lastplate;

/**
 * Model representing an item line in a reservation.
 * Matches MySQL 'reservation_items' table.
 */
public class ReservationItem {
    private int reservationItemId;
    private int reservationId;
    private int foodId;
    private String foodName; // Helper for UI display
    private String imageUrl; // Helper for UI display
    private int quantity;
    private double priceAtBooking;

    public ReservationItem() {
    }

    public ReservationItem(int reservationItemId, int reservationId, int foodId, 
                           int quantity, double priceAtBooking) {
        this.reservationItemId = reservationItemId;
        this.reservationId = reservationId;
        this.foodId = foodId;
        this.quantity = quantity;
        this.priceAtBooking = priceAtBooking;
    }

    public int getReservationItemId() {
        return reservationItemId;
    }

    public void setReservationItemId(int reservationItemId) {
        this.reservationItemId = reservationItemId;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPriceAtBooking() {
        return priceAtBooking;
    }

    public void setPriceAtBooking(double priceAtBooking) {
        this.priceAtBooking = priceAtBooking;
    }

    public double getSubtotal() {
        return quantity * priceAtBooking;
    }
}

