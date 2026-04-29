package fiorentin.dev.db;

import java.sql.*;
import java.time.LocalDateTime;

public class LojaDAO {

    /**
     * Verifica se o usuário tem XP duplo ativo.
     */
    public static boolean temXpDuplo(long usuarioId) {
        return temEfeito(usuarioId, "xp_duplo");
    }

    /**
     * Verifica se o usuário tem proteção de duelo ativa.
     */
    public static boolean temProtecaoDuelo(long usuarioId) {
        return temEfeito(usuarioId, "protecao_duelo");
    }

    /**
     * Remove a proteção de duelo após ser usada.
     */
    public static void consumirProtecao(long usuarioId) {
        String sql = """
            UPDATE usuario_itens SET ativo = FALSE
            WHERE usuario_id = ? AND tipo = 'protecao_duelo' AND ativo = TRUE
            LIMIT 1
            """;
        executarUpdate(sql, usuarioId);
    }

    /**
     * Retorna o título ativo do usuário, ou null se não tiver.
     */
    public static String tituloAtivo(long usuarioId) {
        String sql = """
            SELECT valor FROM usuario_itens
            WHERE usuario_id = ? AND tipo = 'titulo' AND ativo = TRUE
            ORDER BY obtido_em DESC LIMIT 1
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("valor");

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar título: " + e.getMessage());
        }
        return null;
    }

    /**
     * Registra compra e aplica o efeito do item.
     * Retorna a descrição do resultado (ex: o que saiu na caixa).
     */
    public static String comprar(long usuarioId, String tipo) {
        // Verifica saldo
        int preco = precoDe(tipo);
        int moedas = moedasDo(usuarioId);

        if (moedas < preco) {
            return "sem_saldo";
        }

        // Debita as moedas
        UsuarioDAO.adicionarMoedas(usuarioId, -preco);

        // Aplica o efeito
        String resultado = aplicarEfeito(usuarioId, tipo);

        // Registra no histórico
        registrarCompra(usuarioId, tipo, preco, resultado);

        return resultado;
    }

    // ── Efeitos ──────────────────────────────────────────────────────────────

    private static String aplicarEfeito(long usuarioId, String tipo) {
        return switch (tipo) {
            case "caixa_comum"    -> abrirCaixaComum(usuarioId);
            case "caixa_premium"  -> abrirCaixaPremium(usuarioId);
            case "xp_duplo"       -> ativarXpDuplo(usuarioId);
            case "protecao_duelo" -> ativarProtecao(usuarioId);
            default -> "❓ Item desconhecido.";
        };
    }

    private static String abrirCaixaComum(long usuarioId) {
        int roll = (int) (Math.random() * 100);

        if (roll < 60) {
            // 60% → XP
            int xp = 10 + (int) (Math.random() * 21); // 10~30
            UsuarioDAO.adicionarXP(usuarioId, xp);
            return "✨ +%d XP!".formatted(xp);

        } else if (roll < 85) {
            // 25% → Moedas
            int moedas = 15 + (int) (Math.random() * 26); // 15~40
            UsuarioDAO.adicionarMoedas(usuarioId, moedas);
            return "🪙 +%d moedas!".formatted(moedas);

        } else {
            // 15% → Título bronze
            String titulo = sortearTitulo("bronze");
            salvarTitulo(usuarioId, titulo);
            return "🥉 Título desbloqueado: <b>%s</b>".formatted(titulo);
        }
    }

    private static String abrirCaixaPremium(long usuarioId) {
        int roll = (int) (Math.random() * 100);

        if (roll < 40) {
            // 40% → XP alto
            int xp = 50 + (int) (Math.random() * 51); // 50~100
            UsuarioDAO.adicionarXP(usuarioId, xp);
            return "✨ +%d XP!".formatted(xp);

        } else if (roll < 65) {
            // 25% → Moedas
            int moedas = 60 + (int) (Math.random() * 61); // 60~120
            UsuarioDAO.adicionarMoedas(usuarioId, moedas);
            return "🪙 +%d moedas!".formatted(moedas);

        } else if (roll < 90) {
            // 25% → Título prata
            String titulo = sortearTitulo("prata");
            salvarTitulo(usuarioId, titulo);
            return "🥈 Título desbloqueado: <b>%s</b>".formatted(titulo);

        } else {
            // 10% → Título ouro (raro!)
            String titulo = sortearTitulo("ouro");
            salvarTitulo(usuarioId, titulo);
            return "🥇 RARO! Título desbloqueado: <b>%s</b>".formatted(titulo);
        }
    }

    private static String ativarXpDuplo(long usuarioId) {
        // Expira em 24h
        String sql = """
            INSERT INTO usuario_itens (usuario_id, tipo, expira_em)
            VALUES (?, 'xp_duplo', ?)
            """;
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusHours(24)));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Erro ao ativar XP duplo: " + e.getMessage());
        }
        return "⚡ XP Duplo ativado por 24 horas!";
    }

    private static String ativarProtecao(long usuarioId) {
        String sql = """
            INSERT INTO usuario_itens (usuario_id, tipo)
            VALUES (?, 'protecao_duelo')
            """;
        executarUpdate(sql, usuarioId);
        return "🛡️ Proteção de Duelo ativada para o próximo duelo!";
    }

    // ── Títulos ──────────────────────────────────────────────────────────────

    private static final String[] TITULOS_BRONZE = {
            "☕ Aprendiz de Java", "🔰 Iniciante Dedicado",
            "📘 Leitor de Docs", "🐛 Caçador de Bugs"
    };

    private static final String[] TITULOS_PRATA = {
            "⚙️ Dev em Formação", "🧪 Testador Curioso",
            "🔧 Ajustador de Código", "📦 Empacotador Maven"
    };

    private static final String[] TITULOS_OURO = {
            "🏆 Mestre do Java", "💡 Arquiteto de Soluções",
            "🚀 Dev Sênior", "👑 Lenda do Código"
    };

    private static String sortearTitulo(String raridade) {
        String[] lista = switch (raridade) {
            case "prata" -> TITULOS_PRATA;
            case "ouro"  -> TITULOS_OURO;
            default      -> TITULOS_BRONZE;
        };
        return lista[(int) (Math.random() * lista.length)];
    }

    private static void salvarTitulo(long usuarioId, String titulo) {
        String sql = """
            INSERT INTO usuario_itens (usuario_id, tipo, valor)
            VALUES (?, 'titulo', ?)
            """;
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, titulo);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Erro ao salvar título: " + e.getMessage());
        }
    }

    // ── Utilitários ──────────────────────────────────────────────────────────

    private static boolean temEfeito(long usuarioId, String tipo) {
        String sql = """
            SELECT id FROM usuario_itens
            WHERE usuario_id = ? AND tipo = ? AND ativo = TRUE
            AND (expira_em IS NULL OR expira_em > NOW())
            """;
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, tipo);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ Erro ao verificar efeito: " + e.getMessage());
        }
        return false;
    }

    private static int precoDe(String tipo) {
        return switch (tipo) {
            case "caixa_comum"    -> 30;
            case "caixa_premium"  -> 100;
            case "xp_duplo"       -> 80;
            case "protecao_duelo" -> 50;
            default -> Integer.MAX_VALUE;
        };
    }

    private static int moedasDo(long usuarioId) {
        String sql = "SELECT moedas FROM usuarios WHERE id = ?";
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("moedas");
        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar moedas: " + e.getMessage());
        }
        return 0;
    }

    private static void registrarCompra(long usuarioId, String tipo, int preco, String resultado) {
        String sql = """
            INSERT INTO compras (usuario_id, item_tipo, preco_pago, resultado)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ps.setString(2, tipo);
            ps.setInt(3, preco);
            ps.setString(4, resultado);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Erro ao registrar compra: " + e.getMessage());
        }
    }

    private static void executarUpdate(String sql, Object... params) {
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++)
                ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Erro no update: " + e.getMessage());
        }
    }
}