package fiorentin.dev.db;

import fiorentin.dev.model.Pergunta;

import java.sql.*;

public class PerguntaDAO {

    /**
     * Busca uma pergunta aleatória da lição informada.
     */
    public static Pergunta perguntaDaLicao(int licaoId) {
        String sql = """
            SELECT * FROM perguntas
            WHERE licao_id = ?
            ORDER BY RAND()
            LIMIT 1
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, licaoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Pergunta(
                        rs.getInt("id"),
                        rs.getInt("licao_id"),
                        rs.getString("enunciado"),
                        rs.getString("opcao_a"),
                        rs.getString("opcao_b"),
                        rs.getString("opcao_c"),
                        rs.getString("opcao_d"),
                        rs.getString("resposta_correta"),
                        rs.getInt("xp_recompensa")
                );
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar pergunta: " + e.getMessage());
        }

        return null;
    }

    /**
     * Salva a resposta do usuário no histórico.
     */
    public static void salvarResposta(long usuarioId, int perguntaId, String resposta, boolean correta) {
        String sql = """
            INSERT INTO respostas_quiz (usuario_id, pergunta_id, resposta_dada, correta)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ps.setInt(2, perguntaId);
            ps.setString(3, resposta);
            ps.setBoolean(4, correta);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Erro ao salvar resposta: " + e.getMessage());
        }
    }

    public static Pergunta perguntaAleatoria() {
        String sql = "SELECT * FROM perguntas ORDER BY RAND() LIMIT 1";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar pergunta aleatória: " + e.getMessage());
        }
        return null;
    }

    public static Pergunta buscarPorId(int id) {
        String sql = "SELECT * FROM perguntas WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar pergunta: " + e.getMessage());
        }
        return null;
    }

    private static Pergunta mapear(ResultSet rs) throws SQLException {
        return new Pergunta(
                rs.getInt("id"),
                rs.getInt("licao_id"),
                rs.getString("enunciado"),
                rs.getString("opcao_a"),
                rs.getString("opcao_b"),
                rs.getString("opcao_c"),
                rs.getString("opcao_d"),
                rs.getString("resposta_correta"),
                rs.getInt("xp_recompensa")
        );
    }
}