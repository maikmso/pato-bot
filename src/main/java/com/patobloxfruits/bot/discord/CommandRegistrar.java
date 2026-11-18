package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.config.BotProperties;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registra os comandos de barra (/) no servidor. Só administradores enxergam esses comandos.
 */
@Component
public class CommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);

    private final JDA jda;
    private final BotProperties props;

    public CommandRegistrar(JDA jda, BotProperties props) {
        this.jda = jda;
        this.props = props;
    }

    @PostConstruct
    void register() {
        Guild guild = jda.getGuildById(props.guildId());
        if (guild == null) {
            log.error("O bot não está no servidor {}. Adicione o bot pelo link de convite.", props.guildId());
            return;
        }

        var adminOnly = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR);

        guild.updateCommands().addCommands(
                Commands.slash("fruta", "Gerenciar frutas e estoque")
                        .setDefaultPermissions(adminOnly)
                        .addSubcommands(
                                new SubcommandData("adicionar", "Cadastrar uma fruta nova")
                                        .addOption(OptionType.STRING, "nome", "Nome da fruta (ex: Leopard)", true)
                                        .addOption(OptionType.NUMBER, "preco", "Preço em reais (ex: 15.90)", true)
                                        .addOption(OptionType.INTEGER, "estoque", "Quantidade disponível", true),
                                new SubcommandData("preco", "Alterar o preço de uma fruta")
                                        .addOption(OptionType.STRING, "nome", "Nome da fruta", true, true)
                                        .addOption(OptionType.NUMBER, "preco", "Novo preço em reais", true),
                                new SubcommandData("estoque", "Alterar a quantidade em estoque")
                                        .addOption(OptionType.STRING, "nome", "Nome da fruta", true, true)
                                        .addOption(OptionType.INTEGER, "quantidade", "Nova quantidade", true),
                                new SubcommandData("remover", "Tirar a fruta da loja (zera o estoque)")
                                        .addOption(OptionType.STRING, "nome", "Nome da fruta", true, true),
                                new SubcommandData("listar", "Ver todas as frutas cadastradas")),
                Commands.slash("painel", "Postar os painéis da loja neste canal")
                        .setDefaultPermissions(adminOnly)
                        .addSubcommands(
                                new SubcommandData("loja", "Mensagem com o botão Comprar (use no #comprar)"),
                                new SubcommandData("estoque", "Lista de frutas e preços (use no #estoque)"))
        ).queue(
                ok -> log.info("Comandos registrados no servidor {}", guild.getName()),
                err -> log.error("Falha ao registrar comandos", err));
    }
}
