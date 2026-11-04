package com.example.FlashScale.reservation;

import com.example.FlashScale.Models.FlashSaleItem;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "Reservations")
@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flash_sale_item_id", nullable = false)
    private FlashSaleItem item;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public enum Status {
        RESERVED,
        CONFIRMED,
        CANCELLED,
        EXPIRED
    }

}
