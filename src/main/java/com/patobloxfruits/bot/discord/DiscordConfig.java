package com.patobloxfruits.bot.discord;

import com.patobloxfruits.bot.config.BotProperties;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfig {

    /** Conecta o bot no Discord quando a aplicação sobe. */
    @Bean(destroyMethod = "shutdown")
    public JDA jda(BotProperties props) throws InterruptedException {
        if (props.token() == null || props.token().isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente DISCORD_TOKEN");
        }
        return JDABuilder.createLight(props.token(), GatewayIntent.GUILDS)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .setActivity(Activity.watching("a loja 🍎"))
                .build()
                .awaitReady();
    }
}
