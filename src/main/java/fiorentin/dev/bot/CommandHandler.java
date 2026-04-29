package fiorentin.dev.bot;

import fiorentin.dev.db.*;
import fiorentin.dev.model.*;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class CommandHandler {

    private final Map<Long, Pergunta> aguardandoResposta;
    private final Map<Long, Integer> aguardandoDuelo;

    public CommandHandler(Map<Long, Pergunta> aguardandoResposta, Map<Long, Integer> aguardandoDuelo) {
        this.aguardandoResposta = aguardandoResposta;
        this.aguardandoDuelo = aguardandoDuelo;
    }

    public String start(Usuario u) {
        return """
            Olá, %s! 👋

            Seja bem-vindo ao <b>JavaBot</b>! ☕

            Use /ajuda para ver o que eu sei fazer.
            """.formatted(escapar(u.nome()));
    }

    public String ajuda() {
        return """
            📋 <b>Comandos disponíveis:</b>

            /start           — Boas-vindas
            /aprender        — Próxima lição de Java
            /duelar @u 50    — Desafia alguém apostando moedas
            /cancelarduelo   — Cancela duelo pendente e devolve moedas
            /indicar         — Gere seu link e ganhe XP por indicações
            /loja            — Gaste suas moedas em itens e caixas
            /perfil          — Seu nível, XP e progresso
            /ranking         — Top 10 jogadores
            /sobre           — Informações do bot
            /ajuda           — Esta lista
            """;
    }

    public String sobre(String botUsername) {
        return """
            🤖 <b>@%s v1.0 BETA</b>

            Bot de aprendizado de Java com
            gamificação — XP, moedas e ranking!

            Criado com Java + TelegramBots + MySQL 😄
            """.formatted(botUsername);
    }

    public String perfil(Usuario usuario) {
        List<LicaoProgresso> licoes = LicaoDAO.todasComProgresso(usuario.id());
        long concluidas = licoes.stream().filter(LicaoProgresso::concluida).count();
        int total = licoes.size();

        int barraTotal = 10;
        int barraCheia = total == 0 ? 0 : (int) ((concluidas * barraTotal) / total);
        String barra = "█".repeat(barraCheia) + "░".repeat(barraTotal - barraCheia);

        StringBuilder licoesSb = new StringBuilder();
        for (LicaoProgresso l : licoes) {
            String emoji = switch (l.dificuldade()) {
                case "intermediario" -> "🟡";
                case "avancado"      -> "🔴";
                default              -> "🟢";
            };
            String status = l.concluida() ? "✅" : "🔒";
            licoesSb.append("%s %s %s\n".formatted(status, emoji, escapar(l.titulo())));
        }

        String titulo = LojaDAO.tituloAtivo(usuario.id());
        String tituloStr = titulo != null ? "\n🎖️ <b>%s</b>".formatted(escapar(titulo)) : "";
        String xpDuploStr = LojaDAO.temXpDuplo(usuario.id()) ? "\n⚡ <b>XP Duplo ativo!</b>" : "";
        String protecaoStr = LojaDAO.temProtecaoDuelo(usuario.id()) ? "\n🛡️ <b>Proteção de duelo ativa!</b>" : "";

        return """
            👤 <b>Seu Perfil</b>%s%s%s

            🏅 Nível: <b>%d</b>
            ⭐ XP: <b>%d</b>
            🪙 Moedas: <b>%d</b>

            📚 <b>Progresso das Lições</b>
            %s
            [%s] %d/%d concluídas
            """.formatted(
                tituloStr, xpDuploStr, protecaoStr,
                usuario.nivel(), usuario.xp(), usuario.moedas(),
                licoesSb, barra, concluidas, total
        );
    }

    public String aprender(Usuario usuario) {
        Licao licao = LicaoDAO.proximaLicao(usuario.id());

        if (licao == null) {
            return "🏆 Parabéns! Você completou todas as lições!\n\nAguarde novas em breve.";
        }

        Pergunta pergunta = PerguntaDAO.perguntaDaLicao(licao.id());

        String emoji = switch (licao.dificuldade()) {
            case "intermediario" -> "🟡";
            case "avancado"      -> "🔴";
            default              -> "🟢";
        };

        String msg = """
            %s <b>%s</b>
            <i>Dificuldade: %s | Recompensa: +%d XP</i>

            %s

            """.formatted(emoji, escapar(licao.titulo()),
                escapar(licao.dificuldade()), licao.xpRecompensa(),
                escapar(licao.conteudo()));

        if (pergunta != null) {
            aguardandoResposta.put(usuario.id(), pergunta);
            msg += formatarPergunta(pergunta);
        } else {
            LicaoDAO.marcarConcluida(usuario.id(), licao.id());
            UsuarioDAO.adicionarXP(usuario.id(), licao.xpRecompensa());
            msg += "✅ Lição concluída! <b>+" + licao.xpRecompensa() + " XP</b>";
        }

        return msg;
    }

    public String resposta(Usuario usuario, String texto) {
        Pergunta pergunta = aguardandoResposta.get(usuario.id());
        if (pergunta == null) return null;

        String dada = texto.toLowerCase().trim();
        if (!dada.matches("[abcd]")) return null;

        aguardandoResposta.remove(usuario.id());

        boolean correta = dada.equals(pergunta.respostaCorreta());
        PerguntaDAO.salvarResposta(usuario.id(), pergunta.id(), dada, correta);

        if (correta) {
            UsuarioDAO.adicionarXP(usuario.id(), pergunta.xpRecompensa());
            UsuarioDAO.adicionarMoedas(usuario.id(), 5);
            LicaoDAO.marcarConcluida(usuario.id(), pergunta.licaoId());
            return "✅ <b>Correto!</b> +%d XP e +5 🪙\n\nUse /aprender para a próxima lição!"
                    .formatted(pergunta.xpRecompensa());
        } else {
            return "❌ <b>Errado!</b> A resposta correta era a letra <b>%s</b>.\n\nNão desanima, tente outra lição!"
                    .formatted(pergunta.respostaCorreta().toUpperCase());
        }
    }

    public String ranking() {
        List<Usuario> lista = UsuarioDAO.ranking();
        if (lista.isEmpty()) return "😅 Nenhum usuário no ranking ainda!";

        String[] medalhas = {"🥇", "🥈", "🥉"};
        StringBuilder sb = new StringBuilder("🏆 <b>Ranking Global</b>\n\n");

        for (int i = 0; i < lista.size(); i++) {
            Usuario u = lista.get(i);
            String pos = i < 3 ? medalhas[i] : (i + 1) + ".";
            String titulo = LojaDAO.tituloAtivo(u.id());
            String tituloStr = titulo != null ? " <i>%s</i>".formatted(escapar(titulo)) : "";
            sb.append("%s <b>%s</b>%s — Nível %d | %d XP\n"
                    .formatted(pos, escapar(u.nome()), tituloStr, u.nivel(), u.xp()));
        }

        return sb.toString();
    }

    public String eco(String texto) {
        String conteudo = texto.replaceFirst("(?i)/eco\\s*", "").trim();
        return conteudo.isEmpty() ? "❗ Use assim: /eco Olá mundo!" : "🔊 " + escapar(conteudo);
    }

    public String desconhecido(String texto) {
        return "🤔 Não reconheço o comando <code>" + escapar(texto) + "</code>.\n\nUse /ajuda.";
    }

    /**
     * /duelar @usuario 50
     * Recebe BiConsumer pra notificar o desafiado no PV com botões.
     */
    public String duelar(Usuario desafiante, String texto,
                         BiConsumer<Long, String> notificarDesafiado,
                         long chatId, Integer threadId) {

        String[] partes = texto.split(" ");
        if (partes.length < 3) return "❗ Use assim: /duelar @usuario 50";

        String usernameAlvo = partes[1].replace("@", "").toLowerCase();
        int aposta;

        try {
            aposta = Integer.parseInt(partes[2]);
        } catch (NumberFormatException e) {
            return "❗ A aposta precisa ser um número. Ex: /duelar @usuario 50";
        }

        if (aposta <= 0) return "❗ A aposta precisa ser maior que zero!";
        if (aposta > desafiante.moedas())
            return "❌ Você só tem <b>%d moedas</b>! Não dá pra apostar %d."
                    .formatted(desafiante.moedas(), aposta);

        Usuario desafiado = UsuarioDAO.buscarPorUsername(usernameAlvo);
        if (desafiado == null)
            return "❌ Usuário @%s não encontrado! Ele precisa ter usado o bot ao menos uma vez."
                    .formatted(usernameAlvo);

        if (desafiado.id() == desafiante.id()) return "😅 Você não pode se desafiar!";

        if (aposta > desafiado.moedas())
            return "❌ @%s só tem <b>%d moedas</b> e não consegue cobrir a aposta!"
                    .formatted(usernameAlvo, desafiado.moedas());

        Pergunta pergunta = PerguntaDAO.perguntaAleatoria();
        if (pergunta == null) return "❌ Sem perguntas disponíveis no momento!";

        int dueloId = DueloDAO.criar(desafiante.id(), desafiado.id(),
                pergunta.id(), aposta, chatId, threadId);

        // Guarda o estado do desafiante
        aguardandoDuelo.put(desafiante.id(), dueloId);

        // Notifica o desafiado no PV — MeuBot vai enviar com botões aceitar/recusar
        String msgDesafiado = """
            ⚔️ <b>Você foi desafiado!</b>

            <b>%s</b> te desafiou num duelo!
            💰 Aposta: <b>%d moedas</b>

            Aceite ou recuse abaixo 👇
            """.formatted(escapar(desafiante.nome()), aposta);

        notificarDesafiado.accept(desafiado.id(), msgDesafiado + "|DUELO_ID:" + dueloId);

        // Retorna mensagem pro grupo com a pergunta pro desafiante
        // TROCA o return final por isso:
        return """
    ⚔️ <b>Duelo criado!</b>

    %s desafiou <b>%s</b>
    💰 Aposta: <b>%d moedas</b>

    ⏳ Aguardando <b>%s</b> aceitar o duelo...
    """.formatted(
                escapar(desafiante.nome()), escapar(desafiado.nome()),
                aposta, escapar(desafiado.nome())
        );
    }

    public String respostaDuelo(Usuario usuario, String texto, BiConsumer<Long, String> enviarParaUsuario) {
        Integer dueloId = aguardandoDuelo.get(usuario.id());
        if (dueloId == null) return null;

        String resposta = texto.toLowerCase().trim();
        if (!resposta.matches("[abcd]")) return null;

        aguardandoDuelo.remove(usuario.id());
        Duelo duelo = DueloDAO.buscarPorId(dueloId);
        if (duelo == null) return "❌ Duelo não encontrado!";

        Pergunta pergunta = PerguntaDAO.buscarPorId(duelo.perguntaId());

        // Desafiante respondeu primeiro — aguarda o desafiado
        // Desafiante respondeu primeiro — aguarda o desafiado
        if (usuario.id() == duelo.desafianteId()) {
            DueloDAO.responderDesafiante(dueloId, resposta);
            aguardandoDuelo.put(duelo.desafiadoId(), dueloId);
            return "aguardando"; // ← só retorna flag, sem mensagem
        }

        // Desafiado respondeu — finaliza
        boolean desafianteAcertou = duelo.respostaDesafiante().equals(pergunta.respostaCorreta());
        boolean desafiadoAcertou  = resposta.equals(pergunta.respostaCorreta());

        long vencedorId;
        String resultadoMsg;
        Usuario desafiante = UsuarioDAO.buscarPorId(duelo.desafianteId());

        if (desafianteAcertou && !desafiadoAcertou) {
            vencedorId = duelo.desafianteId();
            UsuarioDAO.adicionarMoedas(duelo.desafianteId(),  duelo.aposta());
            UsuarioDAO.adicionarMoedas(duelo.desafiadoId(),  -duelo.aposta());
            UsuarioDAO.adicionarXP(duelo.desafianteId(), 30);
            resultadoMsg = "🏆 <b>%s venceu!</b> +%d moedas e +30 XP\n😔 <b>%s</b> perdeu %d moedas."
                    .formatted(escapar(desafiante.nome()), duelo.aposta(), escapar(usuario.nome()), duelo.aposta());

        } else if (!desafianteAcertou && desafiadoAcertou) {
            vencedorId = duelo.desafiadoId();
            UsuarioDAO.adicionarMoedas(duelo.desafiadoId(),   duelo.aposta());
            UsuarioDAO.adicionarMoedas(duelo.desafianteId(), -duelo.aposta());
            UsuarioDAO.adicionarXP(duelo.desafiadoId(), 30);
            resultadoMsg = "🏆 <b>%s venceu!</b> +%d moedas e +30 XP\n😔 <b>%s</b> perdeu %d moedas."
                    .formatted(escapar(usuario.nome()), duelo.aposta(), escapar(desafiante.nome()), duelo.aposta());

        } else {
            vencedorId = -1;
            resultadoMsg = "🤝 <b>Empate!</b> Os dois %s. Moedas devolvidas."
                    .formatted(desafianteAcertou ? "acertaram" : "erraram");
        }

        DueloDAO.responderDesafiado(dueloId, resposta, vencedorId);
        String gabarito = "📝 Resposta correta: <b>%s</b>".formatted(pergunta.respostaCorreta().toUpperCase());

        return resultadoMsg + "\n\n" + gabarito;
    }

    public String indicar(Usuario usuario, String botUsername) {
        return """
            🔗 <b>Indique amigos e ganhe recompensas!</b>

            Seu link de indicação:
            <code>https://t.me/%s?start=ref_%d</code>

            Quando alguém entrar pelo seu link:
            🎁 Você ganha <b>+50 XP</b> e <b>+10 🪙</b>
            🎁 Seu amigo ganha <b>+20 XP</b> de bônus

            👥 Total de indicações: <b>%d</b>
            """.formatted(botUsername, usuario.id(), usuario.totalIndicacoes());
    }

    public String loja(Usuario usuario) {
        String titulo = LojaDAO.tituloAtivo(usuario.id());
        String tituloStr = titulo != null ? "\n🎖️ Título atual: <b>" + escapar(titulo) + "</b>" : "";

        return """
            🏪 <b>Loja JavaBot</b>
            🪙 Seu saldo: <b>%d moedas</b>%s

            🎰 <b>Caixa Comum</b> — 30 🪙
            <i>60%% XP • 25%% Moedas • 15%% Título 🥉</i>

            💎 <b>Caixa Premium</b> — 100 🪙
            <i>40%% XP alto • 25%% Moedas • 25%% Título 🥈 • 10%% Título 🥇</i>

            ⚡ <b>XP Duplo 24h</b> — 80 🪙
            <i>Todo XP ganho é dobrado por 24 horas</i>

            🛡️ <b>Proteção de Duelo</b> — 50 🪙
            <i>Próximo duelo perdido não desconta moedas</i>
            """.formatted(usuario.moedas(), tituloStr);
    }

    public String comprar(Usuario usuario, String tipo) {
        String resultado = LojaDAO.comprar(usuario.id(), tipo);

        if (resultado.equals("sem_saldo")) {
            int preco = switch (tipo) {
                case "caixa_comum"    -> 30;
                case "caixa_premium"  -> 100;
                case "xp_duplo"       -> 80;
                case "protecao_duelo" -> 50;
                default -> 0;
            };
            return "❌ Saldo insuficiente! Você precisa de <b>%d 🪙</b> e tem <b>%d 🪙</b>."
                    .formatted(preco, usuario.moedas());
        }

        return "🎁 <b>Você comprou e recebeu:</b>\n\n" + resultado;
    }

    public String cancelarDuelo(Usuario usuario) {
        Duelo duelo = DueloDAO.buscarAguardandoPor(usuario.id());
        if (duelo == null) return "❌ Você não tem nenhum duelo aguardando resposta.";

        DueloDAO.cancelar(duelo.id());
        UsuarioDAO.adicionarMoedas(usuario.id(), duelo.aposta());
        return "✅ Duelo cancelado! <b>%d moedas</b> devolvidas.".formatted(duelo.aposta());
    }

    private String formatarPergunta(Pergunta p) {
        return """
            ❓ <b>Quiz:</b> %s

            A) %s
            B) %s
            C) %s
            D) %s

            <i>Responda com os botões abaixo</i>
            """.formatted(escapar(p.enunciado()), escapar(p.opcaoA()),
                escapar(p.opcaoB()), escapar(p.opcaoC()), escapar(p.opcaoD()));
    }

    public String escapar(String texto) {
        if (texto == null) return "";
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}