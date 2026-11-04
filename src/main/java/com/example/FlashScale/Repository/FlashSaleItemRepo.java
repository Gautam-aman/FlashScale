package com.example.FlashScale.Repository;

import com.example.FlashScale.Models.FlashSaleItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlashSaleItemRepo extends JpaRepository<FlashSaleItem , Long>{

    @EntityGraph(attributePaths = "product")
    List<FlashSaleItem> findByFlashSale_IdOrderByIdAsc(Long flashSaleId);

}
