package fiorentin.dev.db;

import fiorentin.dev.model.Usuario;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

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

            if (rs.next()) {
                return new Usuario(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nome"),
                        rs.getInt("xp"),
                        rs.getInt("moedas"),
                        rs.getInt("nivel")
                );
            }

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
            u = new Usuario(id, username, nome, 0, 0, 1);
            salvar(u);
        }

        return u;
    }

    /**
     * Adiciona XP ao usuário e verifica se ele subiu de nível.
     */
    public static void adicionarXP(long id, int quantidade) {
        String sql = "UPDATE usuarios SET xp = xp + ? WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, quantidade);
            ps.setLong(2, id);
            ps.executeUpdate();

            verificarNivel(id); // verifica se subiu de nível após ganhar XP

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
     * Nível 1 = 0~99 XP, Nível 2 = 100~199 XP, etc.
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

            while (rs.next()) {
                lista.add(new Usuario(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("nome"),
                        rs.getInt("xp"),
                        rs.getInt("moedas"),
                        rs.getInt("nivel")
                ));
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar ranking: " + e.getMessage());
        }

        return lista;
    }
}