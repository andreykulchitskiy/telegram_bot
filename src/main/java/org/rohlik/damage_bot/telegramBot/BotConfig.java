package org.rohlik.damage_bot.telegramBot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:application.properties")
public class BotConfig {

    @Value("${damageBot.name}")
    String damageBotName;
    @Value("${damageBot.token}")
    String damageBotToken;

    public String getDamageBotName() {
        return damageBotName;
    }

    public String getDamageBotToken() {
        return damageBotToken;
    }

    }

