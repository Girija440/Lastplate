package com.lastplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a food reservation.
 * Matches MySQL 'reservations' table.
 */
public class Reservation {
    private int reservationId;
    private int userId;
    private String studentName;  // Helper for admin view
    private String studentEmail; // Helper for admin view
    private Timestamp reservationDate;
    private double totalAmount;
    private String status; // 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'
    private List<ReservationItem> items = new ArrayList<>();

    public Reservation() {
    }

    public Reservation(int reservationId, int userId, Timestamp reservationDate, 
                       double totalAmount, String status) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.reservationDate = reservationDate;
        this.totalAmount = totalAmount;
        this.status = status;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public Timestamp getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(Timestamp reservationDate) {
        this.reservationDate = reservationDate;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<ReservationItem> getItems() {
        return items;
    }

    public void setItems(List<ReservationItem> items) {
        this.items = items;
    }

    public void addItem(ReservationItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }
}

