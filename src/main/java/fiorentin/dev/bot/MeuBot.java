package fiorentin.dev.bot;

import fiorentin.dev.db.Database;
import fiorentin.dev.db.UsuarioDAO;
import fiorentin.dev.model.Pergunta;
import fiorentin.dev.model.Usuario;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;

public class MeuBot extends TelegramLongPollingBot {

    private final String username;
    private final CommandHandler handler;
    private final Map<Long, Pergunta> aguardandoResposta = new HashMap<>();

    public MeuBot(String token, String username) {
        super(token);
        this.username = username != null ? username : "MeuBot";
        this.handler = new CommandHandler(aguardandoResposta);
    }

    @Override
    public String getBotUsername() { return username; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message msg = update.getMessage();
        String texto = msg.getText().trim();
        long chatId = msg.getChatId();

        Usuario usuario = UsuarioDAO.buscarOuCriar(
                msg.getFrom().getId(),
                msg.getFrom().getUserName(),
                msg.getFrom().getFirstName()
        );

        UsuarioDAO.adicionarXP(usuario.id(), 5);
        UsuarioDAO.adicionarMoedas(usuario.id(), 1);

        System.out.println("📩 Mensagem de " + usuario.nome() + ": " + texto);

        String resposta = switch (texto.split(" ")[0].toLowerCase()) {
            case "/start"    -> handler.start(usuario);
            case "/ajuda",
                 "/help"     -> handler.ajuda();
            case "/sobre"    -> handler.sobre(username);
            case "/perfil"   -> handler.perfil(usuario);
            case "/aprender" -> handler.aprender(usuario);
            case "/ranking"  -> handler.ranking();
            case "/eco"      -> handler.eco(texto);
            default -> {
                String tentativa = handler.resposta(usuario, texto);
                yield tentativa != null ? tentativa : handler.desconhecido(texto);
            }
        };

        enviarMensagem(chatId, resposta);
    }

    private void enviarMensagem(long chatId, String texto) {
        SendMessage mensagem = SendMessage.builder()
                .chatId(chatId)
                .text(texto)
                .parseMode("HTML")
                .build();

        try {
            execute(mensagem);
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}