package fiorentin.dev.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.SQLException;

public class Database {

    private static HikariDataSource pool;

    // Chama isso uma vez no Main antes de iniciar o bot
    public static void conectar() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://"
                + dotenv.get("DB_HOST") + ":"
                + dotenv.get("DB_PORT") + "/"
                + dotenv.get("DB_NAME")
                + "?useSSL=false&serverTimezone=America/Sao_Paulo");
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        // Configurações do pool
        config.setMaximumPoolSize(5);       // máximo de conexões abertas ao mesmo tempo
        config.setMinimumIdle(2);           // mínimo de conexões sempre prontas
        config.setConnectionTimeout(30000); // 30s pra conseguir uma conexão

        pool = new HikariDataSource(config);
        System.out.println("✅ Banco de dados conectado!");
    }

    // Chamado sempre que precisar executar uma query
    public static Connection getConexao() throws SQLException {
        return pool.getConnection();
    }
}