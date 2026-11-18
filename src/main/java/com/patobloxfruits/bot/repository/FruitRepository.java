package com.patobloxfruits.bot.repository;

import com.patobloxfruits.bot.domain.Fruit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FruitRepository extends JpaRepository<Fruit, Long> {

    Optional<Fruit> findByNameIgnoreCase(String name);

    List<Fruit> findAllByOrderByPriceDesc();

    List<Fruit> findByStockGreaterThanOrderByPriceDesc(int stock);

    /** Trava a linha enquanto reservamos o estoque, evitando vender a mesma fruta duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fruit f where f.id = :id")
    Optional<Fruit> findByIdForUpdate(Long id);
}
