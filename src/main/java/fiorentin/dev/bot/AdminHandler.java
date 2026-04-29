package fiorentin.dev.bot;

import fiorentin.dev.db.UsuarioDAO;
import fiorentin.dev.model.Usuario;

import java.util.List;

public class AdminHandler {

    private final long adminId;

    public AdminHandler(long adminId) {
        this.adminId = adminId;
    }

    public boolean isAdmin(long userId) {
        return userId == adminId;
    }

    // /admin — painel principal
    public String painel() {
        List<Usuario> usuarios = UsuarioDAO.todos();
        StringBuilder sb = new StringBuilder("🛠️ <b>Painel Admin</b>\n\n");
        sb.append("👥 Total de usuários: <b>%d</b>\n\n".formatted(usuarios.size()));
        sb.append("<b>Comandos disponíveis:</b>\n");
        sb.append("/admin lista — Lista todos os usuários\n");
        sb.append("/admin info @username — Info detalhada\n");
        sb.append("/admin setxp @username 100 — Seta XP\n");
        sb.append("/admin setmoedas @username 100 — Seta moedas\n");
        sb.append("/admin setnivel @username 5 — Seta nível\n");
        sb.append("/admin addxp @username 50 — Adiciona XP\n");
        sb.append("/admin addmoedas @username 50 — Adiciona moedas\n");
        return sb.toString();
    }

    // /admin lista
    public String lista() {
        List<Usuario> usuarios = UsuarioDAO.todos();
        if (usuarios.isEmpty()) return "😅 Nenhum usuário cadastrado.";

        StringBuilder sb = new StringBuilder("👥 <b>Usuários (%d)</b>\n\n".formatted(usuarios.size()));
        for (Usuario u : usuarios) {
            String username = u.username() != null ? "@" + u.username() : "sem username";
            sb.append("• <b>%s</b> (%s)\n  ID: <code>%d</code> | Nível %d | %d XP | %d 🪙\n\n"
                    .formatted(escapar(u.nome()), username, u.id(), u.nivel(), u.xp(), u.moedas()));
        }
        return sb.toString();
    }

    // /admin info @username
    public String info(String usernameAlvo) {
        Usuario u = UsuarioDAO.buscarPorUsername(usernameAlvo.replace("@", ""));
        if (u == null) return "❌ Usuário não encontrado.";

        String username = u.username() != null ? "@" + u.username() : "sem username";
        return """
            👤 <b>Info do Usuário</b>

            Nome: <b>%s</b>
            Username: %s
            ID: <code>%d</code>

            🏅 Nível: <b>%d</b>
            ⭐ XP: <b>%d</b>
            🪙 Moedas: <b>%d</b>
            """.formatted(escapar(u.nome()), username, u.id(), u.nivel(), u.xp(), u.moedas());
    }

    // Processa qualquer subcomando /admin xyz
    public String processar(String texto) {
        String[] partes = texto.trim().split("\\s+");
        // partes[0] = /admin, partes[1] = subcomando, partes[2] = @username, partes[3] = valor

        if (partes.length == 1) return painel();

        String sub = partes[1].toLowerCase();

        if (sub.equals("lista")) return lista();

        if (sub.equals("info") && partes.length >= 3) return info(partes[2]);

        if (partes.length >= 4) {
            String usernameAlvo = partes[2].replace("@", "");
            Usuario alvo = UsuarioDAO.buscarPorUsername(usernameAlvo);
            if (alvo == null) return "❌ Usuário @%s não encontrado.".formatted(usernameAlvo);

            int valor;
            try {
                valor = Integer.parseInt(partes[3]);
            } catch (NumberFormatException e) {
                return "❗ Valor inválido: " + partes[3];
            }

            return switch (sub) {
                case "setxp" -> {
                    UsuarioDAO.setarXP(alvo.id(), valor);
                    yield "✅ XP de <b>%s</b> definido para <b>%d</b>.".formatted(escapar(alvo.nome()), valor);
                }
                case "setmoedas" -> {
                    UsuarioDAO.setarMoedas(alvo.id(), valor);
                    yield "✅ Moedas de <b>%s</b> definidas para <b>%d</b>.".formatted(escapar(alvo.nome()), valor);
                }
                case "setnivel" -> {
                    UsuarioDAO.setarNivel(alvo.id(), valor);
                    yield "✅ Nível de <b>%s</b> definido para <b>%d</b>.".formatted(escapar(alvo.nome()), valor);
                }
                case "addxp" -> {
                    UsuarioDAO.adicionarXP(alvo.id(), valor);
                    yield "✅ +%d XP adicionado pra <b>%s</b>.".formatted(valor, escapar(alvo.nome()));
                }
                case "addmoedas" -> {
                    UsuarioDAO.adicionarMoedas(alvo.id(), valor);
                    yield "✅ +%d moedas adicionadas pra <b>%s</b>.".formatted(valor, escapar(alvo.nome()));
                }
                default -> "❓ Subcomando desconhecido. Use /admin pra ver os comandos.";
            };
        }

        return "❓ Comando incompleto. Use /admin pra ver os comandos.";
    }

    private String escapar(String texto) {
        if (texto == null) return "";
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}