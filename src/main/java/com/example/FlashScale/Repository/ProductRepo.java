package com.example.FlashScale.Repository;

import com.example.FlashScale.Models.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepo extends JpaRepository<Product , Long> {
    Optional<Product> findBySku(String sku);
}
