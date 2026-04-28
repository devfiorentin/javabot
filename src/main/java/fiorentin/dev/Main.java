package fiorentin.dev;

import fiorentin.dev.bot.MeuBot;
import fiorentin.dev.db.Database;
import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String token = dotenv.get("BOT_TOKEN");
        String username = dotenv.get("BOT_USERNAME");

        if (token == null || token.isEmpty()) {
            System.err.println("❌ BOT_TOKEN não encontrado!");
            System.exit(1);
        }

        Database.conectar();

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MeuBot(token, username));
            System.out.println("✅ Bot iniciado com sucesso!");
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}