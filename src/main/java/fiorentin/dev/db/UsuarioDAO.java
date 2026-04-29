package fiorentin.dev.db;

import fiorentin.dev.model.Usuario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    public static List<Usuario> todos() {
        String sql = "SELECT * FROM usuarios ORDER BY xp DESC";
        List<Usuario> lista = new ArrayList<>();

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) lista.add(mapear(rs));

        } catch (SQLException e) {
            System.err.println("❌ Erro ao listar usuários: " + e.getMessage());
        }
        return lista;
    }

    public static void setarXP(long id, int xp) {
        executarUpdate("UPDATE usuarios SET xp = ? WHERE id = ?", xp, id);
    }

    public static void setarMoedas(long id, int moedas) {
        executarUpdate("UPDATE usuarios SET moedas = ? WHERE id = ?", moedas, id);
    }

    public static void setarNivel(long id, int nivel) {
        executarUpdate("UPDATE usuarios SET nivel = ? WHERE id = ?", nivel, id);
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

    /**
     * Busca um usuário pelo ID do Telegram.
     * Retorna null se não existir.
     */
    public static Usuario buscarPorId(long telegramId) {
        String sql = "SELECT * FROM usuarios WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, telegramId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar usuário: " + e.getMessage());
        }

        return null;
    }

    /**
     * Salva um novo usuário no banco.
     */
    public static void salvar(Usuario u) {
        String sql = """
            INSERT INTO usuarios (id, username, nome)
            VALUES (?, ?, ?)
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, u.id());
            ps.setString(2, u.username());
            ps.setString(3, u.nome());
            ps.executeUpdate();

            System.out.println("✅ Novo usuário salvo: " + u.nome());

        } catch (SQLException e) {
            System.err.println("❌ Erro ao salvar usuário: " + e.getMessage());
        }
    }

    /**
     * Busca ou cria o usuário automaticamente.
     * Chama esse método em todo onUpdateReceived.
     */
    public static Usuario buscarOuCriar(long id, String username, String nome) {
        Usuario u = buscarPorId(id);

        if (u == null) {
            u = new Usuario(id, username, nome, 0, 0, 1, 0);
            salvar(u);
        }

        return u;
    }

    /**
     * Adiciona XP ao usuário e verifica se ele subiu de nível.
     */
    public static void adicionarXP(long id, int quantidade) {
        // Verifica se tem XP duplo ativo
        if (LojaDAO.temXpDuplo(id)) quantidade *= 2;

        String sql = "UPDATE usuarios SET xp = xp + ? WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, quantidade);
            ps.setLong(2, id);
            ps.executeUpdate();

            verificarNivel(id);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao adicionar XP: " + e.getMessage());
        }
    }

    /**
     * Adiciona moedas ao usuário.
     */
    public static void adicionarMoedas(long id, int quantidade) {
        String sql = "UPDATE usuarios SET moedas = moedas + ? WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, quantidade);
            ps.setLong(2, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Erro ao adicionar moedas: " + e.getMessage());
        }
    }

    /**
     * Calcula o nível baseado no XP e atualiza se mudou.
     * Fórmula: nível = 1 + (xp / 100)
     */
    private static void verificarNivel(long id) {
        String sqlSelect = "SELECT xp, nivel FROM usuarios WHERE id = ?";
        String sqlUpdate = "UPDATE usuarios SET nivel = ? WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sqlSelect)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int xp = rs.getInt("xp");
                int nivelAtual = rs.getInt("nivel");
                int novoNivel = 1 + (xp / 100);

                if (novoNivel > nivelAtual) {
                    PreparedStatement psUpdate = con.prepareStatement(sqlUpdate);
                    psUpdate.setInt(1, novoNivel);
                    psUpdate.setLong(2, id);
                    psUpdate.executeUpdate();
                    System.out.println("🎉 Usuário " + id + " subiu para nível " + novoNivel + "!");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro ao verificar nível: " + e.getMessage());
        }
    }

    /**
     * Retorna os top 10 usuários ordenados por XP.
     */
    public static List<Usuario> ranking() {
        String sql = "SELECT * FROM usuarios ORDER BY xp DESC LIMIT 10";
        List<Usuario> lista = new ArrayList<>();

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) lista.add(mapear(rs));

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar ranking: " + e.getMessage());
        }
        return lista;
    }

    public static Usuario buscarPorUsername(String username) {
        String sql = "SELECT * FROM usuarios WHERE LOWER(username) = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar por username: " + e.getMessage());
        }
        return null;
    }

    public static void registrarIndicacao(long novoUsuarioId, long indicadorId) {
        executarUpdate(
                "UPDATE usuarios SET indicado_por = ? WHERE id = ? AND indicado_por IS NULL",
                indicadorId, novoUsuarioId
        );
        executarUpdate(
                "UPDATE usuarios SET total_indicacoes = total_indicacoes + 1 WHERE id = ?",
                indicadorId
        );
    }

    public static boolean jaFoiIndicado(long usuarioId) {
        String sql = "SELECT indicado_por FROM usuarios WHERE id = ?";
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getObject("indicado_por") != null;
        } catch (SQLException e) {
            System.err.println("❌ Erro ao verificar indicação: " + e.getMessage());
        }
        return false;
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nome"),
                rs.getInt("xp"),
                rs.getInt("moedas"),
                rs.getInt("nivel"),
                rs.getInt("total_indicacoes")
        );
    }
}