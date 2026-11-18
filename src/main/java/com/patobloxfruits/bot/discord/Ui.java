package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.domain.Fruit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * IDs dos componentes e "desenho" das mensagens (embeds).
 */
public final class Ui {

    private Ui() {
    }

    // Cor amarela do pato (#F5B700)
    public static final Color YELLOW = new Color(0xF5B700);
    public static final Color GREEN = new Color(0x2ECC71);
    public static final Color RED = new Color(0xE74C3C);

    // IDs de botões / menus / modais. O que vem depois do ":" é parâmetro.
    public static final String BTN_BUY = "loja:comprar";
    public static final String MENU_FRUIT = "loja:fruta";
    public static final String MODAL_CHECKOUT = "loja:checkout:"; // + fruitId
    public static final String BTN_CANCEL = "pedido:cancelar:";   // + orderId
    public static final String BTN_DELIVERED = "pedido:entregue:"; // + orderId
    public static final String BTN_CLOSE = "ticket:fechar";

    public static final String FIELD_NICK = "nick";
    public static final String FIELD_NAME = "nome";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_CPF = "cpf";
    public static final String FIELD_PHONE = "telefone";

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    public static String money(BigDecimal value) {
        synchronized (BRL) {
            return BRL.format(value);
        }
    }

    /** Mensagem fixa do canal #comprar, com o botão "Comprar". */
    public static MessageEmbed shopPanel() {
        return new EmbedBuilder()
                .setColor(YELLOW)
                .setTitle("🛒 Comprar fruta")
                .setDescription("""
                        Clique em **Comprar** para escolher sua fruta.

                        **1.** Escolha a fruta
                        **2.** Informe seu nick do Roblox
                        **3.** Pague o **PIX** que aparece no seu ticket
                        **4.** O pagamento é confirmado **na hora** ✅
                        **5.** Receba a fruta por **trade** no jogo 🍎""")
                .setFooter("PATO BLOX FRUITS • Pagamento via PIX")
                .build();
    }

    /** Lista de frutas para o #estoque. */
    public static MessageEmbed stockList(List<Fruit> fruits) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(YELLOW)
                .setTitle("🍎 Estoque de frutas")
                .setFooter("Para comprar, vá no canal de compras e clique em Comprar");
        if (fruits.isEmpty()) {
            eb.setDescription("Sem frutas no momento. Volte mais tarde! 🦆");
            return eb.build();
        }
        StringBuilder sb = new StringBuilder();
        for (Fruit f : fruits) {
            String qty = f.getStock() > 0 ? "`" + f.getStock() + " disp.`" : "~~esgotada~~";
            sb.append("**").append(f.getName()).append("** — ")
                    .append(money(f.getPrice())).append("  ").append(qty).append('\n');
        }
        eb.setDescription(sb.toString());
        return eb.build();
    }
}
