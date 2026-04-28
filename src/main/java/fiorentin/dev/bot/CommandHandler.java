package fiorentin.dev.bot;

import fiorentin.dev.db.LicaoDAO;
import fiorentin.dev.db.PerguntaDAO;
import fiorentin.dev.db.UsuarioDAO;
import fiorentin.dev.model.*;

import java.util.List;
import java.util.Map;

public class CommandHandler {

    private final Map<Long, Pergunta> aguardandoResposta;

    public CommandHandler(Map<Long, Pergunta> aguardandoResposta) {
        this.aguardandoResposta = aguardandoResposta;
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

            /start      — Boas-vindas
            /aprender   — Próxima lição de Java
            /perfil     — Seu nível, XP e progresso
            /ranking    — Top 10 jogadores
            /sobre      — Informações do bot
            /ajuda      — Esta lista
            """;
    }

    public String sobre(String botUsername) {
        return """
            🤖 <b>@%s v1.0</b>

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

        return """
            👤 <b>Seu Perfil</b>

            🏅 Nível: <b>%d</b>
            ⭐ XP: <b>%d</b>
            🪙 Moedas: <b>%d</b>

            📚 <b>Progresso das Lições</b>
            %s
            [%s] %d/%d concluídas
            """.formatted(
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
            sb.append("%s <b>%s</b> — Nível %d | %d XP\n"
                    .formatted(pos, escapar(u.nome()), u.nivel(), u.xp()));
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

    private String formatarPergunta(Pergunta p) {
        return """
            ❓ <b>Quiz:</b> %s

            A) %s
            B) %s
            C) %s
            D) %s

            <i>Responda com a letra: a, b, c ou d</i>
            """.formatted(escapar(p.enunciado()), escapar(p.opcaoA()),
                escapar(p.opcaoB()), escapar(p.opcaoC()), escapar(p.opcaoD()));
    }

    private String escapar(String texto) {
        if (texto == null) return "";
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}