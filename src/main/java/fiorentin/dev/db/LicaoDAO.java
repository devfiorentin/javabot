package fiorentin.dev.db;

import fiorentin.dev.model.Licao;
import fiorentin.dev.model.LicaoProgresso;

import java.util.ArrayList;
import java.util.List;

import java.sql.*;

public class LicaoDAO {

    /**
     * Busca a próxima lição que o usuário ainda não completou.
     * Retorna null se já completou tudo.
     */
    public static Licao proximaLicao(long usuarioId) {
        String sql = """
            SELECT l.* FROM licoes l
            WHERE l.ativa = TRUE
              AND l.id NOT IN (
                  SELECT licao_id FROM progresso
                  WHERE usuario_id = ? AND concluida = TRUE
              )
            ORDER BY l.id ASC
            LIMIT 1
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new Licao(
                        rs.getInt("id"),
                        rs.getString("titulo"),
                        rs.getString("conteudo"),
                        rs.getString("dificuldade"),
                        rs.getInt("xp_recompensa")
                );
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar lição: " + e.getMessage());
        }

        return null;
    }

    /**
     * Marca uma lição como concluída para o usuário.
     */
    public static void marcarConcluida(long usuarioId, int licaoId) {
        String sql = """
            INSERT INTO progresso (usuario_id, licao_id, concluida, concluida_em)
            VALUES (?, ?, TRUE, NOW())
            ON DUPLICATE KEY UPDATE concluida = TRUE, concluida_em = NOW()
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ps.setInt(2, licaoId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Erro ao marcar lição: " + e.getMessage());
        }
    }

    /**
     * Retorna todas as lições indicando quais o usuário já concluiu.
     */
    public static List<LicaoProgresso> todasComProgresso(long usuarioId) {
        String sql = """
        SELECT l.id, l.titulo, l.dificuldade,
               COALESCE(p.concluida, FALSE) AS concluida
        FROM licoes l
        LEFT JOIN progresso p
               ON p.licao_id = l.id AND p.usuario_id = ?
        WHERE l.ativa = TRUE
        ORDER BY l.id ASC
        """;

        List<LicaoProgresso> lista = new ArrayList<>();

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, usuarioId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                lista.add(new LicaoProgresso(
                        rs.getInt("id"),
                        rs.getString("titulo"),
                        rs.getString("dificuldade"),
                        rs.getBoolean("concluida")
                ));
            }

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar progresso: " + e.getMessage());
        }

        return lista;
    }
}