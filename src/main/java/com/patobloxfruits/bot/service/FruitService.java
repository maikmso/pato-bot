package com.patobloxfruits.bot.service;

import com.patobloxfruits.bot.domain.Fruit;
import com.patobloxfruits.bot.repository.FruitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cadastro de frutas e estoque (usado pelos comandos /fruta).
 */
@Service
public class FruitService {

    private final FruitRepository fruits;

    public FruitService(FruitRepository fruits) {
        this.fruits = fruits;
    }

    @Transactional
    public Fruit add(String name, double price, int stock) {
        String clean = name.trim();
        validate(price, stock);
        if (fruits.findByNameIgnoreCase(clean).isPresent()) {
            throw new UserFacingException("Já existe a fruta **" + clean + "**. Use `/fruta preco` ou `/fruta estoque`.");
        }
        return fruits.save(new Fruit(clean, toMoney(price), stock));
    }

    @Transactional
    public Fruit setPrice(String name, double price) {
        validate(price, 0);
        Fruit fruit = get(name);
        fruit.setPrice(toMoney(price));
        return fruit;
    }

    @Transactional
    public Fruit setStock(String name, int stock) {
        validate(1, stock);
        Fruit fruit = get(name);
        fruit.setStock(stock);
        return fruit;
    }

    @Transactional
    public void remove(String name) {
        Fruit fruit = get(name);
        // Não apaga do banco (pedidos antigos apontam para ela): só zera o estoque.
        fruit.setStock(0);
    }

    @Transactional(readOnly = true)
    public List<Fruit> listAll() {
        return fruits.findAllByOrderByPriceDesc();
    }

    @Transactional(readOnly = true)
    public List<Fruit> listInStock() {
        return fruits.findByStockGreaterThanOrderByPriceDesc(0);
    }

    @Transactional(readOnly = true)
    public Fruit get(String name) {
        return fruits.findByNameIgnoreCase(name.trim())
                .orElseThrow(() -> new UserFacingException("Fruta **" + name + "** não encontrada."));
    }

    private static void validate(double price, int stock) {
        if (price <= 0 || price > 10_000) {
            throw new UserFacingException("Preço inválido. Use um valor entre 0,01 e 10.000.");
        }
        if (stock < 0 || stock > 10_000) {
            throw new UserFacingException("Estoque inválido.");
        }
    }

    private static BigDecimal toMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
