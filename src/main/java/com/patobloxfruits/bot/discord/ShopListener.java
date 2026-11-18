package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.config.BotProperties;
import com.patobloxfruits.bot.domain.Fruit;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.service.FruitService;
import com.patobloxfruits.bot.service.OrderService;
import com.patobloxfruits.bot.service.UserFacingException;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Recebe todas as interações do Discord (comandos, botões, menus e formulários).
 */
@Component
public class ShopListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ShopListener.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JDA jda;
    private final BotProperties props;
    private final FruitService fruits;
    private final OrderService orders;
    private final ShopService shop;

    public ShopListener(JDA jda, BotProperties props, FruitService fruits, OrderService orders, ShopService shop) {
        this.jda = jda;
        this.props = props;
        this.fruits = fruits;
        this.orders = orders;
        this.shop = shop;
    }

    @PostConstruct
    void listen() {
        jda.addEventListener(this);
    }

    // ------------------------------------------------------------- comandos /

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!isStaff(event.getMember())) {
            event.reply("Só a staff pode usar esse comando.").setEphemeral(true).queue();
            return;
        }
        try {
            switch (event.getFullCommandName()) {
                case "fruta adicionar" -> {
                    Fruit f = fruits.add(event.getOption("nome").getAsString(),
                            event.getOption("preco").getAsDouble(),
                            event.getOption("estoque").getAsInt());
                    replyOk(event, "Fruta **" + f.getName() + "** adicionada: " + Ui.money(f.getPrice())
                            + ", estoque " + f.getStock() + ".");
                }
                case "fruta preco" -> {
                    Fruit f = fruits.setPrice(event.getOption("nome").getAsString(), event.getOption("preco").getAsDouble());
                    replyOk(event, "Preço de **" + f.getName() + "** agora é " + Ui.money(f.getPrice()) + ".");
                }
                case "fruta estoque" -> {
                    Fruit f = fruits.setStock(event.getOption("nome").getAsString(), event.getOption("quantidade").getAsInt());
                    replyOk(event, "Estoque de **" + f.getName() + "** agora é " + f.getStock() + ".");
                }
                case "fruta remover" -> {
                    fruits.remove(event.getOption("nome").getAsString());
                    replyOk(event, "Fruta removida da loja (estoque zerado).");
                }
                case "fruta listar" -> event.replyEmbeds(Ui.stockList(fruits.listAll())).setEphemeral(true).queue();
                case "painel loja" -> {
                    event.getChannel().sendMessageEmbeds(Ui.shopPanel())
                            .setActionRow(Button.success(Ui.BTN_BUY, "Comprar").withEmoji(Emoji.fromUnicode("🛒")))
                            .queue();
                    replyOk(event, "Painel da loja postado.");
                }
                case "painel estoque" -> {
                    event.getChannel().sendMessageEmbeds(Ui.stockList(fruits.listAll())).queue();
                    replyOk(event, "Lista de estoque postada. Quando mudar o estoque, apague a antiga e poste de novo.");
                }
                default -> event.reply("Comando desconhecido.").setEphemeral(true).queue();
            }
        } catch (UserFacingException e) {
            event.reply("⚠️ " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!"nome".equals(event.getFocusedOption().getName())) {
            return;
        }
        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = fruits.listAll().stream()
                .map(Fruit::getName)
                .filter(n -> n.toLowerCase().contains(typed))
                .limit(25)
                .map(n -> new Command.Choice(n, n))
                .toList();
        event.replyChoices(choices).queue();
    }

    // ---------------------------------------------------------------- botões

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        try {
            if (id.equals(Ui.BTN_BUY)) {
                showFruitMenu(event);
            } else if (id.startsWith(Ui.BTN_CANCEL)) {
                Long orderId = Long.valueOf(id.substring(Ui.BTN_CANCEL.length()));
                PurchaseOrder order = orders.get(orderId);
                if (!event.getUser().getId().equals(order.getDiscordUserId()) && !isStaff(event.getMember())) {
                    throw new UserFacingException("Só quem fez o pedido pode cancelar.");
                }
                event.deferEdit().queue();
                shop.cancelByUser(orderId, event.getUser());
            } else if (id.startsWith(Ui.BTN_DELIVERED)) {
                if (!isStaff(event.getMember())) {
                    throw new UserFacingException("Só a staff pode marcar como entregue.");
                }
                event.deferEdit().queue();
                shop.onDelivered(Long.valueOf(id.substring(Ui.BTN_DELIVERED.length())), event.getUser());
                event.getHook().editOriginalComponents().queue(); // tira o botão
            } else if (id.equals(Ui.BTN_CLOSE)) {
                event.deferEdit().queue();
                if (event.getChannel() instanceof TextChannel channel) {
                    shop.closeTicket(channel);
                }
            }
        } catch (UserFacingException e) {
            replyError(event, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erro no botão {}", id, e);
            replyError(event, "Algo deu errado. Tente de novo.");
        }
    }

    private void showFruitMenu(ButtonInteractionEvent event) {
        List<Fruit> available = fruits.listInStock();
        if (available.isEmpty()) {
            event.reply("Estamos sem frutas no momento 😢 Volte mais tarde!").setEphemeral(true).queue();
            return;
        }
        StringSelectMenu.Builder menu = StringSelectMenu.create(Ui.MENU_FRUIT).setPlaceholder("Escolha sua fruta");
        available.stream().limit(25).forEach(f -> menu.addOption(
                f.getName() + " — " + Ui.money(f.getPrice()),
                String.valueOf(f.getId()),
                f.getStock() + " disponível(is)",
                Emoji.fromUnicode("🍎")));
        event.reply("Qual fruta você quer?").addActionRow(menu.build()).setEphemeral(true).queue();
    }

    // ------------------------------------------------------- menu de seleção

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals(Ui.MENU_FRUIT)) {
            return;
        }
        String fruitId = event.getValues().get(0);
        Modal modal = Modal.create(Ui.MODAL_CHECKOUT + fruitId, "Finalizar compra")
                .addComponents(
                        ActionRow.of(TextInput.create(Ui.FIELD_NICK, "Seu nick no Roblox", TextInputStyle.SHORT)
                                .setRequired(true).setMinLength(3).setMaxLength(20).build()),
                        ActionRow.of(TextInput.create(Ui.FIELD_NAME, "Nome completo (para o PIX)", TextInputStyle.SHORT)
                                .setRequired(true).setMinLength(3).setMaxLength(80).build()),
                        ActionRow.of(TextInput.create(Ui.FIELD_EMAIL, "E-mail", TextInputStyle.SHORT)
                                .setRequired(true).setMaxLength(100).build()),
                        ActionRow.of(TextInput.create(Ui.FIELD_CPF, "CPF (opcional)", TextInputStyle.SHORT)
                                .setRequired(false).setMaxLength(14).build()),
                        ActionRow.of(TextInput.create(Ui.FIELD_PHONE, "Celular com DDD (opcional)", TextInputStyle.SHORT)
                                .setRequired(false).setMaxLength(20).build()))
                .build();
        event.replyModal(modal).queue();
    }

    // ------------------------------------------------------------ formulário

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(Ui.MODAL_CHECKOUT) || event.getGuild() == null) {
            return;
        }
        Long fruitId = Long.valueOf(event.getModalId().substring(Ui.MODAL_CHECKOUT.length()));
        var form = new ShopService.CheckoutForm(
                value(event, Ui.FIELD_NICK), value(event, Ui.FIELD_NAME), value(event, Ui.FIELD_EMAIL),
                digitsOnly(value(event, Ui.FIELD_CPF)), value(event, Ui.FIELD_PHONE));

        if (!EMAIL.matcher(form.email()).matches()) {
            event.reply("⚠️ E-mail inválido. Tente de novo.").setEphemeral(true).queue();
            return;
        }
        if (!form.cpf().isEmpty() && form.cpf().length() != 11) {
            event.reply("⚠️ CPF inválido (precisa ter 11 números). Deixe em branco se preferir.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();
        try {
            TextChannel ticket = shop.startPurchase(event.getGuild(), event.getUser(), fruitId, form);
            event.getHook().sendMessage("✅ Pedido criado! Pague o PIX no seu ticket: " + ticket.getAsMention()).queue();
        } catch (UserFacingException e) {
            event.getHook().sendMessage("⚠️ " + e.getMessage()).queue();
        } catch (RuntimeException e) {
            log.error("Erro ao processar compra", e);
            event.getHook().sendMessage("⚠️ Algo deu errado. Tente de novo em instantes.").queue();
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        return member.hasPermission(Permission.ADMINISTRATOR)
                || (props.hasOwner() && member.getId().equals(props.ownerId()));
    }

    private static String value(ModalInteractionEvent event, String field) {
        ModalMapping m = event.getValue(field);
        return m == null ? "" : m.getAsString().trim();
    }

    private static String digitsOnly(String s) {
        return s.replaceAll("\\D", "");
    }

    private static void replyOk(SlashCommandInteractionEvent event, String message) {
        event.reply("✅ " + message).setEphemeral(true).queue();
    }

    private static void replyError(IReplyCallback event, String message) {
        if (event.isAcknowledged()) {
            event.getHook().sendMessage("⚠️ " + message).setEphemeral(true).queue();
        } else {
            event.reply("⚠️ " + message).setEphemeral(true).queue();
        }
    }
}
