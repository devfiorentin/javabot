package fiorentin.dev.db;

import fiorentin.dev.model.Duelo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DueloDAO {

    /**
     * Cria um novo duelo e retorna o ID gerado.
     */
    public static int criar(long desafianteId, long desafiadoId, int perguntaId, int aposta, long chatId, Integer threadId) {
        String sql = """
        INSERT INTO duelos (desafiante_id, desafiado_id, pergunta_id, aposta, status, expira_em, chat_id, thread_id)
        VALUES (?, ?, ?, ?, 'aguardando', DATE_ADD(NOW(), INTERVAL 1 HOUR), ?, ?)
        """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, desafianteId);
            ps.setLong(2, desafiadoId);
            ps.setInt(3, perguntaId);
            ps.setInt(4, aposta);
            ps.setLong(5, chatId);
            if (threadId != null) ps.setInt(6, threadId);
            else ps.setNull(6, java.sql.Types.INTEGER);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao criar duelo: " + e.getMessage());
        }
        return -1;
    }
    /**
     * Busca um duelo pelo ID.
     */
    public static Duelo buscarPorId(int id) {
        String sql = "SELECT * FROM duelos WHERE id = ?";

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar duelo: " + e.getMessage());
        }

        return null;
    }

    /**
     * Busca duelo pendente onde o usuário foi desafiado.
     */
    public static Duelo buscarPendente(long desafiadoId) {
        String sql = """
            SELECT * FROM duelos
            WHERE desafiado_id = ? AND status = 'aguardando'
            ORDER BY criado_em DESC
            LIMIT 1
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, desafiadoId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) return mapear(rs);

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar duelo pendente: " + e.getMessage());
        }

        return null;
    }

    /**
     * Registra a resposta do desafiante e muda status pra em_andamento.
     */
    public static void responderDesafiante(int dueloId, String resposta) {
        String sql = """
            UPDATE duelos
            SET resposta_desafiante = ?, status = 'em_andamento'
            WHERE id = ?
            """;
        executarUpdate(sql, resposta, dueloId);
    }

    /**
     * Registra a resposta do desafiado e finaliza o duelo.
     */
    public static void responderDesafiado(int dueloId, String resposta, long vencedorId) {
        String sql = """
            UPDATE duelos
            SET resposta_desafiado = ?, status = 'finalizado', vencedor_id = ?
            WHERE id = ?
            """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, resposta);
            ps.setLong(2, vencedorId);
            ps.setInt(3, dueloId);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Erro ao finalizar duelo: " + e.getMessage());
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

    private static Duelo mapear(ResultSet rs) throws SQLException {
        return new Duelo(
                rs.getInt("id"),
                rs.getLong("desafiante_id"),
                rs.getLong("desafiado_id"),
                rs.getInt("pergunta_id"),
                rs.getInt("aposta"),
                rs.getString("resposta_desafiante"),
                rs.getString("resposta_desafiado"),
                rs.getString("status"),
                rs.getObject("vencedor_id") != null ? rs.getLong("vencedor_id") : null,
                rs.getObject("chat_id") != null ? rs.getLong("chat_id") : null,
                rs.getObject("thread_id") != null ? rs.getInt("thread_id") : null
        );
    }




    /**
     * Busca duelos expirados e devolve as moedas.
     */
    public static List<Duelo> buscarExpirados() {
        String sql = """
        SELECT * FROM duelos
        WHERE status IN ('aguardando', 'em_andamento')
        AND expira_em < NOW()
        """;

        List<Duelo> lista = new ArrayList<>();
        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) lista.add(mapear(rs));

        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar duelos expirados: " + e.getMessage());
        }
        return lista;
    }

    public static void cancelar(int dueloId) {
        executarUpdate(
                "UPDATE duelos SET status = 'finalizado', vencedor_id = NULL WHERE id = ?",
                dueloId
        );
    }

    public static Duelo buscarAguardandoPor(long desafianteId) {
        String sql = """
        SELECT * FROM duelos
        WHERE desafiante_id = ? AND status = 'aguardando'
        ORDER BY criado_em DESC LIMIT 1
        """;

        try (Connection con = Database.getConexao();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, desafianteId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapear(rs);
        } catch (SQLException e) {
            System.err.println("❌ Erro ao buscar duelo: " + e.getMessage());
        }
        return null;
    }

    public static void aceitarDuelo(int dueloId) {
        executarUpdate(
                "UPDATE duelos SET status = 'em_andamento' WHERE id = ?",
                dueloId
        );
    }

    public static void finalizarDuelo(int dueloId, long vencedorId) {
        executarUpdate(
                "UPDATE duelos SET status = 'finalizado', vencedor_id = ? WHERE id = ?",
                vencedorId == -1 ? null : vencedorId, dueloId
        );
    }

    public static void salvarRespostaDesafiado(int dueloId, String resposta) {
        executarUpdate(
                "UPDATE duelos SET resposta_desafiado = ? WHERE id = ?",
                resposta, dueloId
        );
    }

    public static void salvarRespostaDesafiante(int dueloId, String resposta) {
        executarUpdate(
                "UPDATE duelos SET resposta_desafiante = ? WHERE id = ?",
                resposta, dueloId
        );
    }
}