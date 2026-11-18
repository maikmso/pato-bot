package com.patobloxfruits.bot.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Uma fruta à venda no estoque.
 */
@Entity
@Table(name = "fruits")
public class Fruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome exibido, ex: "Leopard". Único (sem diferenciar maiúsculas). */
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Quantidade disponível para venda (já descontando pedidos reservados). */
    @Column(nullable = false)
    private int stock;

    protected Fruit() {
    }

    public Fruit(String name, BigDecimal price, int stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /** Reserva 1 unidade para um pedido. */
    public void reserveOne() {
        if (stock <= 0) {
            throw new IllegalStateException("Sem estoque de " + name);
        }
        stock--;
    }

    /** Devolve 1 unidade (pedido cancelado/expirado). */
    public void releaseOne() {
        stock++;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
