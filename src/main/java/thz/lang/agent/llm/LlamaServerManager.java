package thz.lang.agent.llm;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * Gerencia o processo do llama-server.exe.
 * Inicia automaticamente o servidor local quando necessário,
 * aguarda ficar pronto, e para ao encerrar o agente.
 */
public final class LlamaServerManager {

    private static final Path LLAMA_DIR = Path.of(
        System.getProperty("user.home"), ".thz", "tools", "llama.cpp");
    private static final Path LLAMA_SERVER = LLAMA_DIR.resolve("llama-server.exe");
    private static final int PORTA_PADRAO = 8080;
    private static final int TIMEOUT_STARTUP_MS = 60_000;

    private Process processo;
    private int porta;
    private final String modeloPath;

    public LlamaServerManager(String modeloPath) {
        this.modeloPath = modeloPath;
        this.porta = PORTA_PADRAO;
    }

    /**
     * Verifica se o llama-server.exe está disponível.
     */
    public static boolean disponivel() {
        return Files.exists(LLAMA_SERVER);
    }

    /**
     * Inicia o llama-server se não estiver rodando.
     * Aguarda até 30s ficar pronto.
     */
    public void iniciar() throws IOException, InterruptedException {
        if (processo != null && processo.isAlive()) {
            return; // Já rodando
        }

        if (!disponivel()) {
            throw new IOException("llama-server.exe não encontrado em: " + LLAMA_SERVER);
        }

        // Verificar se porta já está em uso (outro servidor rodando)
        if (portaEmUso(porta)) {
            System.out.println("[THZ-Agent] llama-server já rodando na porta " + porta);
            return;
        }

        System.out.println("[THZ-Agent] Iniciando llama-server na porta " + porta + "...");

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        ProcessBuilder pb = new ProcessBuilder(
            LLAMA_SERVER.toString(),
            "--model", modeloPath,
            "--port", String.valueOf(porta),
            "--host", "127.0.0.1",
            "--ctx-size", "4096",
            "--threads", String.valueOf(threads)
        );
        pb.directory(LLAMA_DIR.toFile());
        pb.redirectErrorStream(true);

        // Redirecionar stdout para log
        Path logFile = Path.of(System.getProperty("user.home"),
            ".thz", "tools", "llama-server.log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        processo = pb.start();

        // Aguardar ficar pronto
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < TIMEOUT_STARTUP_MS) {
            if (!processo.isAlive()) {
                throw new IOException("llama-server encerrou inesperadamente");
            }
            if (portaEmUso(porta)) {
                // Aguardar mais 1s para estabilizar
                Thread.sleep(1000);
                System.out.println("[THZ-Agent] llama-server pronto!");
                return;
            }
            Thread.sleep(500);
        }

        throw new IOException("llama-server não ficou pronto em " + TIMEOUT_STARTUP_MS + "ms");
    }

    /**
     * Para o servidor se estiver rodando.
     */
    public void parar() {
        if (processo != null && processo.isAlive()) {
            System.out.println("[THZ-Agent] Parando llama-server...");
            processo.destroy();
            try {
                if (!processo.waitFor(Duration.ofSeconds(5))) {
                    processo.destroyForcibly();
                }
            } catch (InterruptedException ignored) {}
            processo = null;
        }
    }

    public int getPorta() { return porta; }
    public String getBaseUrl() { return "http://127.0.0.1:" + porta; }
    public boolean isRodando() { return processo != null && processo.isAlive(); }

    private static boolean portaEmUso(int porta) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", porta), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
