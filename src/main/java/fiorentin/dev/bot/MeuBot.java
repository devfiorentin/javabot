package fiorentin.dev.bot;

import fiorentin.dev.db.DueloDAO;
import fiorentin.dev.db.PerguntaDAO;
import fiorentin.dev.db.UsuarioDAO;
import fiorentin.dev.model.Duelo;
import fiorentin.dev.model.Pergunta;
import fiorentin.dev.model.Usuario;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.Map;

public class MeuBot extends TelegramLongPollingBot {

    private final String username;
    private final CommandHandler handler;
    private final AdminHandler admin;
    private final DueloExpiracaoService expiracaoService;
    private final Map<Long, Pergunta> aguardandoResposta = new HashMap<>();
    private final Map<Long, Integer> aguardandoDuelo = new HashMap<>();

    public MeuBot(String token, String username, long adminId) {
        super(token);
        this.username = username != null ? username : "MeuBot";
        this.handler = new CommandHandler(aguardandoResposta, aguardandoDuelo);
        this.admin = new AdminHandler(adminId);
        this.expiracaoService = new DueloExpiracaoService(this::enviarParaUsuario);
        this.expiracaoService.iniciar();
    }

    @Override
    public String getBotUsername() { return username; }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            processarCallback(update);
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message msg = update.getMessage();
        String texto = msg.getText().trim();
        long chatId = msg.getChatId();
        Integer threadId = msg.isTopicMessage() ? msg.getMessageThreadId() : null;

        Usuario usuario = UsuarioDAO.buscarOuCriar(
                msg.getFrom().getId(),
                msg.getFrom().getUserName(),
                msg.getFrom().getFirstName()
        );

        UsuarioDAO.adicionarXP(usuario.id(), 5);
        UsuarioDAO.adicionarMoedas(usuario.id(), 1);

        System.out.println("📩 Mensagem de " + usuario.nome() + ": " + texto);

        String cmd = texto.split(" ")[0].toLowerCase();

        // /start e /menu
        if (cmd.equals("/start") || cmd.equals("/menu")) {
            String[] partes = texto.split(" ");
            if (partes.length > 1 && partes[1].startsWith("ref_")) {
                try {
                    long indicadorId = Long.parseLong(partes[1].substring(4));
                    if (indicadorId != usuario.id() && !UsuarioDAO.jaFoiIndicado(usuario.id())) {
                        UsuarioDAO.registrarIndicacao(usuario.id(), indicadorId);
                        UsuarioDAO.adicionarXP(indicadorId, 50);
                        UsuarioDAO.adicionarMoedas(indicadorId, 10);
                        UsuarioDAO.adicionarXP(usuario.id(), 20);
                        Usuario indicador = UsuarioDAO.buscarPorId(indicadorId);
                        if (indicador != null) {
                            enviarParaUsuario(indicadorId,
                                    "🎉 <b>%s</b> entrou pelo seu link! +50 XP e +10 🪙"
                                            .formatted(handler.escapar(usuario.nome())));
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
            enviarComTeclado(chatId, handler.start(usuario), Teclado.menuPrincipal(), threadId);
            return;
        }

        // /loja
        if (cmd.equals("/loja")) {
            enviarComTeclado(chatId, handler.loja(usuario), Teclado.loja(), threadId);
            return;
        }

        // /duelar
        if (cmd.equals("/duelar")) {
            String msgDuelo = handler.duelar(usuario, texto,
                    this::notificarDesafiado, chatId, threadId);
            enviarMensagem(chatId, msgDuelo, threadId); // ← sem teclado
            return;
        }

        String resposta = switch (cmd) {
            case "/ajuda", "/help" -> handler.ajuda();
            case "/sobre"          -> handler.sobre(username);
            case "/perfil"         -> handler.perfil(usuario);
            case "/indicar"        -> handler.indicar(usuario, username);
            case "/aprender"       -> {
                String msg2 = handler.aprender(usuario);
                Pergunta p = aguardandoResposta.get(usuario.id());
                if (p != null) {
                    enviarComTeclado(chatId, msg2, Teclado.quiz(p.id()), threadId);
                    yield null;
                }
                yield msg2;
            }
            case "/ranking"        -> handler.ranking();
            case "/eco"            -> handler.eco(texto);
            case "/cancelarduelo"  -> handler.cancelarDuelo(usuario);
            case "/admin" -> {
                if (!admin.isAdmin(usuario.id())) yield "⛔ Acesso negado.";
                yield admin.processar(texto);
            }
            default -> {
                String tentativaDuelo = handler.respostaDuelo(usuario, texto, this::enviarParaUsuario);
                if (tentativaDuelo != null) yield tentativaDuelo;
                String tentativaQuiz = handler.resposta(usuario, texto);
                yield tentativaQuiz != null ? tentativaQuiz : null;
            }
        };

        if (resposta != null && !resposta.isBlank()) {
            enviarMensagem(chatId, resposta, threadId);
        }
    }

    private void processarCallback(Update update) {
        var callback = update.getCallbackQuery();
        long chatId   = callback.getMessage().getChatId();
        int messageId = callback.getMessage().getMessageId();
        long userId   = callback.getFrom().getId();
        String data   = callback.getData();

        System.out.println("🔍 CALLBACK RECEBIDO: '" + data + "'"); // ← adiciona isso


        Usuario usuario = UsuarioDAO.buscarOuCriar(
                userId,
                callback.getFrom().getUserName(),
                callback.getFrom().getFirstName()
        );

        responderCallback(callback.getId());

        // ── cmd: ────────────────────────────────────────────────────────────
        if (data.startsWith("cmd:")) {
            String cmd = data.substring(4);
            switch (cmd) {
                case "menu"    -> editarMensagem(chatId, messageId, handler.start(usuario), Teclado.menuPrincipal());
                case "perfil"  -> editarMensagem(chatId, messageId, handler.perfil(usuario), Teclado.voltarMenu());
                case "ranking" -> editarMensagem(chatId, messageId, handler.ranking(), Teclado.voltarMenu());
                case "indicar" -> editarMensagem(chatId, messageId, handler.indicar(usuario, username), Teclado.voltarMenu());
                case "loja"    -> editarMensagem(chatId, messageId, handler.loja(usuario), Teclado.loja());
                case "duelos"  -> editarMensagem(chatId, messageId, "⚔️ Use /duelar @usuario 50 pra desafiar alguém!", Teclado.voltarMenu());
                case "aprender" -> {
                    String msg = handler.aprender(usuario);
                    Pergunta p = aguardandoResposta.get(usuario.id());
                    if (p != null) editarMensagem(chatId, messageId, msg, Teclado.quiz(p.id()));
                    else editarMensagem(chatId, messageId, msg, Teclado.voltarMenu());
                }
            }
            return;
        }

        // ── quiz: ────────────────────────────────────────────────────────────
        if (data.startsWith("quiz:")) {
            String[] partes = data.split(":");
            String letra = partes[2];
            String resultado = handler.resposta(usuario, letra);
            if (resultado != null) editarMensagem(chatId, messageId, resultado, Teclado.menuPrincipal());
            return;
        }

        // ── comprar: ─────────────────────────────────────────────────────────
        if (data.startsWith("comprar:")) {
            String tipo = data.substring(8);
            Usuario atualizado = UsuarioDAO.buscarPorId(usuario.id());
            String resultado = handler.comprar(atualizado, tipo);
            editarMensagem(chatId, messageId, resultado, Teclado.loja());
            return;
        }

        // ── duelo_resp: (botões A B C D do duelo) ────────────────────────────
        if (data.startsWith("duelo_resp:")) {
            String[] partes = data.split(":");
            int dueloId = Integer.parseInt(partes[1]);
            String letra = partes[2];

            Duelo duelo = DueloDAO.buscarPorId(dueloId);

            if (duelo == null) { responderCallbackComAviso(callback.getId(), "❌ Duelo não encontrado!"); return; }
            if (duelo.status().equals("finalizado")) { responderCallbackComAviso(callback.getId(), "⚠️ Duelo já encerrado!"); return; }

            // Verifica se faz parte do duelo
            boolean isDesafiante = usuario.id() == duelo.desafianteId();
            boolean isDesafiado  = usuario.id() == duelo.desafiadoId();

            if (!isDesafiante && !isDesafiado) {
                responderCallbackComAviso(callback.getId(), "⛔ Você não faz parte deste duelo!");
                return;
            }

            // Verifica se já respondeu
            if (isDesafiante && duelo.respostaDesafiante() != null) {
                responderCallbackComAviso(callback.getId(), "✅ Você já respondeu! Aguardando o adversário...");
                return;
            }
            if (isDesafiado && duelo.respostaDesafiado() != null) {
                responderCallbackComAviso(callback.getId(), "✅ Você já respondeu! Aguardando o adversário...");
                return;
            }

            // Salva a resposta
            // Salva a resposta
            if (isDesafiante) {
                DueloDAO.salvarRespostaDesafiante(dueloId, letra); // ← troca
                responderCallbackComAviso(callback.getId(), "✅ Resposta registrada!");
                String textoAtual = "";
                if (callback.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message m) {
                    textoAtual = m.getText() != null ? m.getText() : "";
                }
                editarMensagem(chatId, messageId,
                        textoAtual + "\n\n✅ <b>%s</b> já respondeu!"
                                .formatted(handler.escapar(usuario.nome())), Teclado.respostaDuelo(dueloId));
            } else {
                DueloDAO.salvarRespostaDesafiado(dueloId, letra); // ← troca
                responderCallbackComAviso(callback.getId(), "✅ Resposta registrada!");
                String textoAtual = "";
                if (callback.getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message m) {
                    textoAtual = m.getText() != null ? m.getText() : "";
                }
                editarMensagem(chatId, messageId,
                        textoAtual + "\n\n✅ <b>%s</b> já respondeu!"
                                .formatted(handler.escapar(usuario.nome())), Teclado.respostaDuelo(dueloId));
            }

            // Rebusca pra ver se os dois já responderam
            // Rebusca pra ver se os dois já responderam
            Duelo atualizado = DueloDAO.buscarPorId(dueloId);
            System.out.println("🔍 respostaDesafiante: " + atualizado.respostaDesafiante());
            System.out.println("🔍 respostaDesafiado: " + atualizado.respostaDesafiado());
            System.out.println("🔍 status: " + atualizado.status());

            if (atualizado.respostaDesafiante() != null && atualizado.respostaDesafiado() != null) {
                System.out.println("🔍 FINALIZANDO DUELO!");

                aguardandoDuelo.remove(duelo.desafianteId());
                aguardandoDuelo.remove(duelo.desafiadoId());

                Pergunta pergunta = PerguntaDAO.buscarPorId(duelo.perguntaId());
                Usuario desafiante = UsuarioDAO.buscarPorId(duelo.desafianteId());
                Usuario desafiadoU = UsuarioDAO.buscarPorId(duelo.desafiadoId());

                boolean desafianteAcertou = atualizado.respostaDesafiante().equals(pergunta.respostaCorreta());
                boolean desafiadoAcertou  = atualizado.respostaDesafiado().equals(pergunta.respostaCorreta());

                long vencedorId;
                String resultadoMsg;

                if (desafianteAcertou && !desafiadoAcertou) {
                    vencedorId = duelo.desafianteId();
                    UsuarioDAO.adicionarMoedas(duelo.desafianteId(),  duelo.aposta());
                    UsuarioDAO.adicionarMoedas(duelo.desafiadoId(),  -duelo.aposta());
                    UsuarioDAO.adicionarXP(duelo.desafianteId(), 30);
                    resultadoMsg = "🏆 <b>%s venceu!</b> +%d moedas e +30 XP\n😔 <b>%s</b> perdeu %d moedas."
                            .formatted(handler.escapar(desafiante.nome()), duelo.aposta(),
                                    handler.escapar(desafiadoU.nome()), duelo.aposta());

                } else if (!desafianteAcertou && desafiadoAcertou) {
                    vencedorId = duelo.desafiadoId();
                    UsuarioDAO.adicionarMoedas(duelo.desafiadoId(),   duelo.aposta());
                    UsuarioDAO.adicionarMoedas(duelo.desafianteId(), -duelo.aposta());
                    UsuarioDAO.adicionarXP(duelo.desafiadoId(), 30);
                    resultadoMsg = "🏆 <b>%s venceu!</b> +%d moedas e +30 XP\n😔 <b>%s</b> perdeu %d moedas."
                            .formatted(handler.escapar(desafiadoU.nome()), duelo.aposta(),
                                    handler.escapar(desafiante.nome()), duelo.aposta());

                } else {
                    vencedorId = -1;
                    resultadoMsg = "🤝 <b>Empate!</b> Os dois %s. Moedas devolvidas."
                            .formatted(desafianteAcertou ? "acertaram" : "erraram");
                }

                // Atualiza o vencedor no banco
                DueloDAO.finalizarDuelo(dueloId, vencedorId);

                String gabarito = "📝 Resposta correta: <b>%s</b>".formatted(pergunta.respostaCorreta().toUpperCase());
                String resultado = resultadoMsg + "\n\n" + gabarito;

                long destino = duelo.chatId() != null ? duelo.chatId() : chatId;
                enviarComTeclado(destino, resultado, Teclado.voltarMenu(), duelo.threadId());
            }
            return;
        }

        // ── duelo_aceitar: ────────────────────────────────────────────────────
        if (data.startsWith("duelo_aceitar:")) {
            int dueloId = Integer.parseInt(data.split(":")[1]);
            Duelo duelo = DueloDAO.buscarPorId(dueloId);

            if (duelo == null) { responderCallbackComAviso(callback.getId(), "❌ Duelo não encontrado!"); return; }
            if (usuario.id() != duelo.desafiadoId()) { responderCallbackComAviso(callback.getId(), "⛔ Só o desafiado pode aceitar!"); return; }
            if (!duelo.status().equals("aguardando")) { responderCallbackComAviso(callback.getId(), "⚠️ Duelo não está mais disponível!"); return; }

            Pergunta p = PerguntaDAO.buscarPorId(duelo.perguntaId());
            Usuario desafiante = UsuarioDAO.buscarPorId(duelo.desafianteId());

            // Muda status pra em_andamento
            DueloDAO.aceitarDuelo(dueloId);

            // Guarda estado dos dois
            aguardandoDuelo.put(duelo.desafianteId(), dueloId);
            aguardandoDuelo.put(duelo.desafiadoId(), dueloId);

            long destino = duelo.chatId() != null ? duelo.chatId() : chatId;
            Integer destThread = duelo.threadId();

            String msgPergunta = """
        ⚔️ <b>Duelo aceito!</b>

        %s vs %s
        💰 Aposta: <b>%d moedas</b>

        ❓ <b>%s</b>

        A) %s
        B) %s
        C) %s
        D) %s

        <i>Os dois respondam com os botões!</i>
        """.formatted(
                    handler.escapar(desafiante.nome()),
                    handler.escapar(usuario.nome()),
                    duelo.aposta(),
                    handler.escapar(p.enunciado()),
                    handler.escapar(p.opcaoA()), handler.escapar(p.opcaoB()),
                    handler.escapar(p.opcaoC()), handler.escapar(p.opcaoD())
            );

            editarMensagem(chatId, messageId, "✅ Duelo aceito!", null);
            enviarComTeclado(destino, msgPergunta, Teclado.respostaDuelo(dueloId), destThread);
            return;
        }
        // ── duelo_recusar: ────────────────────────────────────────────────────
        if (data.startsWith("duelo_recusar:")) {
            int dueloId = Integer.parseInt(data.split(":")[1]);
            Duelo duelo = DueloDAO.buscarPorId(dueloId);

            // Só o desafiado pode recusar
            if (duelo != null && usuario.id() != duelo.desafiadoId()) {
                responderCallbackComAviso(callback.getId(), "⛔ Só o desafiado pode recusar!");
                return;
            }

            if (duelo != null) {
                UsuarioDAO.adicionarMoedas(duelo.desafianteId(), duelo.aposta());
                DueloDAO.cancelar(dueloId);

                long destino = duelo.chatId() != null ? duelo.chatId() : chatId;
                enviarMensagem(destino,
                        "❌ <b>%s</b> recusou o duelo! Moedas devolvidas."
                                .formatted(handler.escapar(usuario.nome())),
                        duelo.threadId());
            }
            editarMensagem(chatId, messageId, "❌ Duelo recusado.", Teclado.voltarMenu());
            return;
        }
    }

    /**
     * Chamado quando o desafiado precisa ser notificado no PV com botões aceitar/recusar.
     * O CommandHandler passa a mensagem com "|DUELO_ID:X" no final.
     */
    private void notificarDesafiado(long chatIdDesafiado, String msgComId) {
        String[] partes = msgComId.split("\\|DUELO_ID:");
        String msg = partes[0];
        int dueloId = Integer.parseInt(partes[1]);

        Duelo duelo = DueloDAO.buscarPorId(dueloId);

        // Envia no PV do desafiado
        SendMessage pvMsg = SendMessage.builder()
                .chatId(chatIdDesafiado)
                .text(msg)
                .parseMode("HTML")
                .replyMarkup(Teclado.confirmarDuelo(dueloId))
                .build();
        try {
            execute(pvMsg);
        } catch (TelegramApiException e) {
            System.err.println("⚠️ Não foi possível notificar no PV: " + e.getMessage());
        }

        // Envia também no grupo onde o duelo foi criado
        if (duelo != null && duelo.chatId() != null) {
            enviarComTeclado(duelo.chatId(), msg, Teclado.confirmarDuelo(dueloId), duelo.threadId());
        }
    }
    public void enviarParaUsuario(long chatId, String texto) {
        enviarMensagem(chatId, texto, null);
    }

    private void enviarComTeclado(long chatId, String texto, InlineKeyboardMarkup teclado, Integer threadId) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId)
                .text(texto)
                .parseMode("HTML")
                .replyMarkup(teclado);

        if (threadId != null) builder.messageThreadId(threadId);

        try {
            execute(builder.build());
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    private void enviarMensagem(long chatId, String texto, Integer threadId) {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId)
                .text(texto)
                .parseMode("HTML");

        if (threadId != null) builder.messageThreadId(threadId);

        try {
            execute(builder.build());
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    private void editarMensagem(long chatId, int messageId, String texto, InlineKeyboardMarkup teclado) {
        EditMessageText.EditMessageTextBuilder builder = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(texto)
                .parseMode("HTML");

        if (teclado != null) builder.replyMarkup(teclado);

        try {
            execute(builder.build());
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao editar mensagem: " + e.getMessage());
        }
    }

    private void responderCallback(String callbackId) {
        try {
            execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .build());
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao responder callback: " + e.getMessage());
        }
    }

    private void responderCallbackComAviso(String callbackId, String aviso) {
        try {
            execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackId)
                    .text(aviso)
                    .showAlert(true) // true = popup, false = toast rápido
                    .build());
        } catch (TelegramApiException e) {
            System.err.println("❌ Erro ao responder callback: " + e.getMessage());
        }
    }
}