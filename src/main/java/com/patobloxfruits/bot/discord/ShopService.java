package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.amplopay.AmploPayClient;
import com.patobloxfruits.bot.amplopay.AmploPayDtos;
import com.patobloxfruits.bot.amplopay.AmploPayDtos.PixReceiveResponse;
import com.patobloxfruits.bot.amplopay.AmploPayException;
import com.patobloxfruits.bot.config.AmploPayProperties;
import com.patobloxfruits.bot.config.BotProperties;
import com.patobloxfruits.bot.domain.PurchaseOrder;
import com.patobloxfruits.bot.service.OrderService;
import com.patobloxfruits.bot.service.OrderService.PaymentResult;
import com.patobloxfruits.bot.service.QrCodeService;
import com.patobloxfruits.bot.service.UserFacingException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tudo que o bot faz no Discord em torno de um pedido: abrir ticket, mandar o PIX,
 * avisar pagamento, registrar entrega, fechar ticket.
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);
    private static final int CLOSE_DELAY_SECONDS = 15;

    private final JDA jda;
    private final BotProperties bot;
    private final AmploPayProperties amploPay;
    private final OrderService orders;
    private final AmploPayClient amploPayClient;
    private final QrCodeService qrCodes;

    public ShopService(JDA jda, BotProperties bot, AmploPayProperties amploPay, OrderService orders,
                       AmploPayClient amploPayClient, QrCodeService qrCodes) {
        this.jda = jda;
        this.bot = bot;
        this.amploPay = amploPay;
        this.orders = orders;
        this.amploPayClient = amploPayClient;
        this.qrCodes = qrCodes;
    }

    /** Dados que o cliente preencheu no formulário de compra. */
    public record CheckoutForm(String robloxNick, String name, String email, String cpf, String phone) {
    }

    /**
     * Fluxo completo de compra: reserva → ticket → PIX.
     *
     * @return o canal do ticket criado
     */
    public TextChannel startPurchase(Guild guild, User buyer, Long fruitId, CheckoutForm form) {
        PurchaseOrder order = orders.reserve(fruitId, buyer.getId(), form.robloxNick());
        TextChannel ticket = null;
        try {
            ticket = createTicketChannel(guild, buyer);
            orders.attachTicket(order.getId(), ticket.getId());

            PixReceiveResponse pix = amploPayClient.createPix(new AmploPayDtos.PixReceiveRequest(
                    order.getIdentifier(),
                    order.getAmount(),
                    new AmploPayDtos.Client(form.name(), form.email(), blankToNull(form.phone()), blankToNull(form.cpf())),
                    List.of(new AmploPayDtos.Product(
                            "fruit-" + order.getFruit().getId(), "Blox Fruits - " + order.getFruitName(),
                            1, order.getAmount(), false)),
                    Map.of("discordUserId", buyer.getId(), "robloxNick", order.getRobloxNick()),
                    amploPay.callbackUrl(),
                    true));
            orders.attachPayment(order.getId(), pix.transactionId(), pix.webhookToken());

            sendPaymentMessage(ticket, buyer, order, pix.pix().code());
            logToStaff("🆕 Pedido **" + order.getIdentifier() + "**: " + order.getFruitName() + " ("
                    + Ui.money(order.getAmount()) + ") para <@" + buyer.getId() + "> — nick `"
                    + order.getRobloxNick() + "` em " + ticket.getAsMention());
            return ticket;
        } catch (RuntimeException e) {
            // Deu erro no meio: devolve a fruta ao estoque e apaga o ticket.
            orders.cancel(order.getId());
            if (ticket != null) {
                ticket.delete().queue(null, err -> log.warn("Não consegui apagar o ticket", err));
            }
            if (e instanceof UserFacingException) {
                throw e;
            }
            log.error("Falha ao iniciar compra do pedido {}", order.getIdentifier(), e);
            if (e instanceof AmploPayException) {
                throw new UserFacingException("Não consegui gerar o PIX agora. Confira se o e-mail e o CPF estão certos e tente de novo.");
            }
            throw new UserFacingException("Algo deu errado ao abrir seu pedido. Tente de novo em instantes.");
        }
    }

    /** Chamado quando a AmploPay confirma o pagamento. */
    public void onPaid(Long orderId, PaymentResult result) {
        PurchaseOrder order = orders.get(orderId);
        TextChannel ticket = ticketOf(order);

        String ownerPing = bot.hasOwner() ? " <@" + bot.ownerId() + ">" : "";
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Ui.GREEN)
                .setTitle("✅ Pagamento confirmado!")
                .setDescription("Recebemos seu PIX de **" + Ui.money(order.getAmount()) + "**.\n\n"
                        + "Agora é só aguardar: a staff vai entrar no jogo e fazer o **trade** da sua **"
                        + order.getFruitName() + "** para o nick `" + order.getRobloxNick() + "`.\n"
                        + "Fique online no Blox Fruits! 🍎")
                .setFooter("Pedido " + order.getIdentifier());

        if (result == PaymentResult.PAID_AFTER_CANCEL) {
            eb.addField("⚠️ Atenção staff", "Este PIX foi pago **depois** do pedido expirar. "
                    + "Confira se ainda há estoque; se não houver, faça o reembolso.", false);
        }

        if (ticket != null) {
            ticket.sendMessage("<@" + order.getDiscordUserId() + ">" + ownerPing)
                    .setEmbeds(eb.build())
                    .setActionRow(Button.success(Ui.BTN_DELIVERED + order.getId(), "Marcar como entregue (staff)").withEmoji(
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📦")))
                    .queue();
        }
        logToStaff("💰 PAGO: **" + order.getIdentifier() + "** — " + order.getFruitName() + " ("
                + Ui.money(order.getAmount()) + ") nick `" + order.getRobloxNick() + "`"
                + (ticket != null ? " em " + ticket.getAsMention() : "")
                + (result == PaymentResult.PAID_AFTER_CANCEL ? " ⚠️ pago após expirar" : "") + ownerPing);
    }

    /** Staff clicou em "Marcar como entregue". */
    public void onDelivered(Long orderId, User staff) {
        if (!orders.markDelivered(orderId)) {
            throw new UserFacingException("Esse pedido não está com status **pago**.");
        }
        PurchaseOrder order = orders.get(orderId);
        TextChannel ticket = ticketOf(order);

        String reviews = notBlank(bot.reviewsChannelId()) ? "<#" + bot.reviewsChannelId() + ">" : "o canal de avaliações";
        if (ticket != null) {
            ticket.sendMessageEmbeds(new EmbedBuilder()
                            .setColor(Ui.YELLOW)
                            .setTitle("📦 Entregue!")
                            .setDescription("Sua **" + order.getFruitName() + "** foi entregue. Obrigado pela compra! 🦆\n\n"
                                    + "Deixa sua avaliação em " + reviews + " ⭐")
                            .build())
                    .setActionRow(Button.secondary(Ui.BTN_CLOSE, "Fechar ticket")
                            .withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔒")))
                    .queue();
        }

        if (notBlank(bot.deliveredChannelId())) {
            TextChannel delivered = jda.getTextChannelById(bot.deliveredChannelId());
            if (delivered != null) {
                delivered.sendMessageEmbeds(new EmbedBuilder()
                        .setColor(Ui.GREEN)
                        .setDescription("✅ **" + order.getFruitName() + "** entregue para <@"
                                + order.getDiscordUserId() + ">")
                        .setTimestamp(java.time.Instant.now())
                        .build()).queue();
            }
        }
        logToStaff("📦 Entregue: **" + order.getIdentifier() + "** por " + staff.getAsMention());
    }

    /** Cliente ou staff cancelou um pedido pendente. */
    public void cancelByUser(Long orderId, User actor) {
        if (!orders.cancel(orderId)) {
            throw new UserFacingException("Esse pedido não pode mais ser cancelado (já foi pago ou cancelado).");
        }
        PurchaseOrder order = orders.get(orderId);
        closeWithMessage(order, "❌ Pedido cancelado por " + actor.getAsMention() + ". Este ticket será fechado.");
        logToStaff("❌ Cancelado: **" + order.getIdentifier() + "** por " + actor.getAsMention());
    }

    /** O PIX expirou sem pagamento. */
    public void expire(Long orderId) {
        if (!orders.cancel(orderId)) {
            return;
        }
        PurchaseOrder order = orders.get(orderId);
        closeWithMessage(order, "⌛ O tempo para pagamento acabou e o pedido foi cancelado. "
                + "Se ainda quiser a fruta, é só abrir um novo pedido.");
        logToStaff("⌛ Expirado: **" + order.getIdentifier() + "**");
    }

    /** Apaga o canal do ticket depois de alguns segundos. */
    public void closeTicket(TextChannel channel) {
        channel.sendMessage("🔒 Fechando este ticket em " + CLOSE_DELAY_SECONDS + " segundos...").queue();
        channel.delete().queueAfter(CLOSE_DELAY_SECONDS, TimeUnit.SECONDS, null,
                err -> log.warn("Não consegui apagar o ticket {}", channel.getId(), err));
    }

    public Duration orderExpiration() {
        return Duration.ofMinutes(bot.orderExpirationMinutes());
    }

    // ---------------------------------------------------------------- helpers

    private TextChannel createTicketChannel(Guild guild, User buyer) {
        Category category = guild.getCategoriesByName(bot.ticketCategoryName(), true).stream().findFirst()
                .orElseGet(() -> guild.createCategory(bot.ticketCategoryName())
                        .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                        .addMemberPermissionOverride(jda.getSelfUser().getIdLong(),
                                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MANAGE_CHANNEL), null)
                        .complete());

        String name = "ticket-" + buyer.getName().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        return guild.createTextChannel(name.length() > 90 ? name.substring(0, 90) : name, category)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(buyer.getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES),
                        null)
                .addMemberPermissionOverride(jda.getSelfUser().getIdLong(),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS,
                                Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL),
                        null)
                .complete();
    }

    private void sendPaymentMessage(TextChannel ticket, User buyer, PurchaseOrder order, String pixCode) {
        byte[] png = qrCodes.toPng(pixCode);
        var embed = new EmbedBuilder()
                .setColor(Ui.YELLOW)
                .setTitle("🧾 Pedido: " + order.getFruitName())
                .setDescription("Pague o PIX abaixo. A confirmação é **automática**: assim que o pagamento cair, "
                        + "eu aviso aqui. ✅")
                .addField("Fruta", order.getFruitName(), true)
                .addField("Valor", Ui.money(order.getAmount()), true)
                .addField("Nick no Roblox", "`" + order.getRobloxNick() + "`", true)
                .addField("PIX copia e cola", "```" + pixCode + "```", false)
                .addField("⏳ Prazo", "Você tem **" + bot.orderExpirationMinutes() + " minutos** para pagar.", false)
                .setImage("attachment://pix.png")
                .setFooter("Pedido " + order.getIdentifier())
                .build();

        ticket.sendMessage(buyer.getAsMention())
                .setEmbeds(embed)
                .addFiles(FileUpload.fromData(png, "pix.png"))
                .setActionRow(Button.danger(Ui.BTN_CANCEL + order.getId(), "Cancelar pedido"))
                .complete();
    }

    private void closeWithMessage(PurchaseOrder order, String message) {
        TextChannel ticket = ticketOf(order);
        if (ticket == null) {
            return;
        }
        ticket.sendMessageEmbeds(new EmbedBuilder().setColor(Ui.RED).setDescription(message).build()).queue();
        ticket.delete().queueAfter(CLOSE_DELAY_SECONDS * 2L, TimeUnit.SECONDS, null,
                err -> log.warn("Não consegui apagar o ticket {}", ticket.getId(), err));
    }

    private TextChannel ticketOf(PurchaseOrder order) {
        return order.getTicketChannelId() == null ? null : jda.getTextChannelById(order.getTicketChannelId());
    }

    private void logToStaff(String message) {
        if (!notBlank(bot.logChannelId())) {
            return;
        }
        TextChannel channel = jda.getTextChannelById(bot.logChannelId());
        if (channel != null) {
            channel.sendMessage(message).queue(null, err -> log.warn("Falha ao logar no Discord", err));
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
