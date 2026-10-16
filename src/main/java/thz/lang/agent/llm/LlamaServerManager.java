package thz.lang.agent.llm;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * Gerencia o ciclo de vida do processo local do llama-server.
 * Garante inicialização em CPU, healthcheck e encerramento limpo.
 */
public final class LlamaServerManager {

    private static final int PORTA_PADRAO = 8080;
    private static final int TIMEOUT_STARTUP_MS = 60_000;

    private Process processo;
    private int porta;
    private final String modeloPath;
    private final HttpClient httpClient;
    private Thread shutdownHook;

    public LlamaServerManager(String modeloPath) {
        this.modeloPath = modeloPath;
        this.porta = PORTA_PADRAO;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    }

    /**
     * Verifica se o executável do llama-server está instalado.
     */
    public static boolean disponivel() {
        return LlamaServerDownloader.isInstalado();
    }

    /**
     * Garante que o servidor esteja instalado, inicializado e pronto para responder.
     */
    public synchronized void assegurarPronto() throws IOException, InterruptedException {
        if (isPronto()) {
            return;
        }

        if (!disponivel()) {
            LlamaServerDownloader.assegurarInstalado();
        }

        iniciar();
    }

    /**
     * Inicia o processo do llama-server otimizado para CPU.
     */
    public synchronized void iniciar() throws IOException, InterruptedException {
        if (processo != null && processo.isAlive()) {
            return;
        }

        Path serverExe = LlamaServerDownloader.assegurarInstalado();
        Path serverDir = serverExe.getParent() != null ? serverExe.getParent() : LlamaServerDownloader.getLlamaDir();

        // Se porta padrão em uso, checar se já é uma instância do llama-server respondendo ao modelo
        if (checarHealth(porta)) {
            System.out.println("[THZ-Agent] llama-server já rodando e pronto na porta " + porta);
            return;
        }

        // Se porta em uso por outra aplicação, encontrar porta livre
        if (portaEmUso(porta)) {
            this.porta = encontrarPortaLivre(8081, 8099);
        }

        System.out.println("[THZ-Agent] Iniciando llama-server local em CPU (porta " + porta + ")...");

        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        ProcessBuilder pb = new ProcessBuilder(
            serverExe.toString(),
            "--model", modeloPath,
            "--port", String.valueOf(porta),
            "--host", "127.0.0.1",
            "--ctx-size", "4096",
            "--batch-size", "512",
            "--ubatch-size", "256",
            "--n-predict", "1024",
            "--threads", String.valueOf(cores)
        );
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);

        Path toolsDir = Path.of(System.getProperty("user.home"), ".thz", "tools");
        Files.createDirectories(toolsDir);
        Path logFile = toolsDir.resolve("llama-server.log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        processo = pb.start();

        // Registrar shutdown hook
        if (shutdownHook == null) {
            shutdownHook = new Thread(this::parar, "thz-llama-server-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        // Aguardar o modelo carregar e o endpoint /health responder HTTP 200
        long inicio = System.currentTimeMillis();
        while (System.currentTimeMillis() - inicio < TIMEOUT_STARTUP_MS) {
            if (!processo.isAlive()) {
                throw new IOException("llama-server encerrou inesperadamente. Verifique o log em: " + logFile);
            }

            if (checarHealth(porta)) {
                System.out.println("[THZ-Agent] llama-server pronto e operacional em CPU!");
                return;
            }

            Thread.sleep(500);
        }

        parar();
        throw new IOException("llama-server não respondeu com sucesso ao healthcheck em " + TIMEOUT_STARTUP_MS + "ms");
    }

    /**
     * Verifica se o llama-server está respondendo 200 OK no endpoint /health.
     */
    public boolean isPronto() {
        return checarHealth(porta);
    }

    private boolean checarHealth(int portaAlvo) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + portaAlvo + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encerra o processo local do llama-server.
     */
    public synchronized void parar() {
        if (processo != null && processo.isAlive()) {
            System.out.println("[THZ-Agent] Encerrando llama-server local...");
            processo.destroy();
            try {
                if (!processo.waitFor(Duration.ofSeconds(5))) {
                    processo.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                processo.destroyForcibly();
            }
            processo = null;
        }
    }

    public int getPorta() {
        return porta;
    }

    public String getBaseUrl() {
        return "http://127.0.0.1:" + porta;
    }

    public boolean isRodando() {
        return (processo != null && processo.isAlive()) || isPronto();
    }

    private static boolean portaEmUso(int porta) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", porta), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static int encontrarPortaLivre(int inicio, int fim) {
        for (int p = inicio; p <= fim; p++) {
            if (!portaEmUso(p)) {
                return p;
            }
        }
        return inicio;
    }
}
