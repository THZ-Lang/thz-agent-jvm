package thz.lang.agent.tools;

import java.io.*;
import java.util.concurrent.*;

/**
 * Executa um comando no shell do sistema.
 */
public final class ExecCommandTool implements Tool {

    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT = 10000;

    @Override public String nome() { return "execute_command"; }
    @Override public String descricao() { return "Executa um comando no shell (terminal). Retorna stdout + stderr + exit code."; }
    @Override public String parametrosSchema() { return "{\"command\": \"string\", \"workdir\": \"string (opcional)\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.PERIGOSO; }

    @Override
    public String executar(String args) {
        String comando = extrairComando(args);
        String workdir = extrairWorkdir(args);

        if (comando == null || comando.isBlank()) {
            return "Erro: comando não especificado";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", comando);
            } else {
                pb.command("sh", "-c", comando);
            }

            if (workdir != null) {
                pb.directory(new File(workdir));
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Ler output com timeout
            String output;
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                long start = System.currentTimeMillis();

                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (sb.length() > MAX_OUTPUT) {
                        sb.append("... (output truncado)\n");
                        break;
                    }
                    if (System.currentTimeMillis() - start > TIMEOUT_SECONDS * 1000L) {
                        process.destroyForcibly();
                        sb.append("... (timeout após ").append(TIMEOUT_SECONDS).append("s)\n");
                        break;
                    }
                }
                output = sb.toString().strip();
            }

            process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            return String.format("[exit %d]\n%s", exitCode, output.isEmpty() ? "(sem output)" : output);

        } catch (Exception e) {
            return "Erro ao executar comando: " + e.getMessage();
        }
    }

    private String extrairComando(String args) {
        String t = args.trim();
        if (t.startsWith("command=\"")) {
            int end = t.indexOf("\"", 9);
            return end > 0 ? t.substring(9, end) : t.substring(9);
        }
        // Fallback: todo o args é o comando
        return t;
    }

    private String extrairWorkdir(String args) {
        int idx = args.indexOf("workdir=\"");
        if (idx == -1) return null;
        int start = idx + 9;
        int end = args.indexOf("\"", start);
        return end > 0 ? args.substring(start, end) : null;
    }
}
