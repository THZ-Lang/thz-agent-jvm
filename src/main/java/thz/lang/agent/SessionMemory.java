package thz.lang.agent;

import java.sql.*;
import java.util.*;

/**
 * Memória persistente do agente via SQLite.
 * Armazena histórico de sessões e dados de treinamento.
 */
public final class SessionMemory {

    private static final String DB_PATH_DEFAULT = "dados/agent/sessions.db";
    private final String dbPath;
    private Connection conn;

    public SessionMemory() {
        this(System.getProperty("thz.agent.db.dir") != null
            ? System.getProperty("thz.agent.db.dir") + "/sessions.db"
            : DB_PATH_DEFAULT);
    }

    public SessionMemory(String dbPath) {
        this.dbPath = dbPath;
        try {
            java.nio.file.Files.createDirectories(
                java.nio.file.Paths.get(dbPath).getParent());
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            inicializar();
        } catch (Exception e) {
            System.err.println("[THZ-Agent] Aviso: SQLite indisponível, memória em modo degradado (sem persistência).");
            this.conn = null;
        }
    }

    private void inicializar() throws SQLException {
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS sessao (
                id TEXT PRIMARY KEY,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                modelo TEXT,
                diretorio TEXT,
                resumo TEXT
            )
        """);

        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS turno (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessao_id TEXT,
                idx INTEGER,
                papel TEXT,
                conteudo TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (sessao_id) REFERENCES sessao(id)
            )
        """);

        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS treino_dados (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prompt TEXT,
                resposta_desejada TEXT,
                feedback TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    public void criarSessao(String id, String modelo, String diretorio) {
        if (conn == null) return;
        try {
            var stmt = conn.prepareStatement(
                "INSERT INTO sessao (id, modelo, diretorio) VALUES (?, ?, ?)");
            stmt.setString(1, id);
            stmt.setString(2, modelo);
            stmt.setString(3, diretorio);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Ignorar duplicatas
        }
    }

    public void salvarTurno(String sessaoId, int idx, String papel, String conteudo) {
        if (conn == null) return;
        try {
            var stmt = conn.prepareStatement(
                "INSERT INTO turno (sessao_id, idx, papel, conteudo) VALUES (?, ?, ?, ?)");
            stmt.setString(1, sessaoId);
            stmt.setInt(2, idx);
            stmt.setString(3, papel);
            stmt.setString(4, conteudo);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Ignorar erros de inserção
        }
    }

    public List<String[]> listarSessoes() {
        List<String[]> sessoes = new ArrayList<>();
        if (conn == null) return sessoes;
        try {
            var rs = conn.createStatement().executeQuery(
                "SELECT id, timestamp, modelo, diretorio FROM sessao ORDER BY timestamp DESC LIMIT 20");
            while (rs.next()) {
                sessoes.add(new String[]{
                    rs.getString("id"),
                    rs.getString("timestamp"),
                    rs.getString("modelo"),
                    rs.getString("diretorio")
                });
            }
        } catch (SQLException e) {
            // Ignorar
        }
        return sessoes;
    }

    public void salvarDadoTreino(String prompt, String resposta, String feedback) {
        if (conn == null) return;
        try {
            var stmt = conn.prepareStatement(
                "INSERT INTO treino_dados (prompt, resposta_desejada, feedback) VALUES (?, ?, ?)");
            stmt.setString(1, prompt);
            stmt.setString(2, resposta);
            stmt.setString(3, feedback);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Ignorar
        }
    }

    public int contarDadosTreino() {
        if (conn == null) return 0;
        try {
            var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM treino_dados");
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            // Ignorar
        }
        return 0;
    }

    public void fechar() {
        if (conn == null) return;
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            // Ignorar
        }
    }
}
