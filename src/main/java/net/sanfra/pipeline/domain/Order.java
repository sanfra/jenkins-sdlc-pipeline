package net.sanfra.pipeline.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String product;
    private int quantity;
    private String status;

    public Order() {}

    public Order(String product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public String getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }

    public void confirm() { this.status = "CONFIRMED"; }
    public void cancel() { this.status = "CANCELLED"; }
}
