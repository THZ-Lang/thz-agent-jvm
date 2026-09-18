package thz.lang.agent;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/**
 * Configuração persistente do THZ-Agent.
 * Lê e grava ~/.thz/agent.json com modelo padrão, GPU layers, etc.
 */
public final class AgentConfig {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".thz");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("agent.json");
    private static final Path MODELS_DIR = CONFIG_DIR.resolve("models");

    private static final String MODELO_PADRAO = "qwen2.5-coder-3b-instruct-q4_k_m.gguf";
    private static final String HUGGINGFACE_URL =
        "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/" + MODELO_PADRAO;
    private static final long TAMANHO_ESPERADO = 2_104_932_800L;

    private String modeloPath;
    private int gpuLayers;
    private String apiUrl;
    private String apiKey;
    private boolean autoApprove;

    public AgentConfig() {
        this.modeloPath = MODELS_DIR.resolve(MODELO_PADRAO).toString();
        this.gpuLayers = 0;
        this.apiUrl = null;
        this.apiKey = null;
        this.autoApprove = false;
    }

    public static AgentConfig ler() {
        AgentConfig config = new AgentConfig();

        if (!Files.exists(CONFIG_FILE)) {
            config.salvar();
            return config;
        }

        try {
            String conteudo = Files.readString(CONFIG_FILE);
            config.parsear(conteudo);
        } catch (Exception e) {
            System.err.println("[THZ-Agent] Aviso: Erro ao ler config, usando defaults: " + e.getMessage());
        }

        return config;
    }

    public void salvar() {
        try {
            Files.createDirectories(CONFIG_DIR);
            String json = gerarJson();
            Files.writeString(CONFIG_FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[THZ-Agent] Aviso: Não foi possível salvar config: " + e.getMessage());
        }
    }

    private void parsear(String json) {
        // Parse simples sem dependências externas
        String modelo = extrairCampo(json, "modelo");
        if (modelo != null && !modelo.isBlank()) {
            this.modeloPath = modelo.replace("~", System.getProperty("user.home"));
        }

        String gpu = extrairCampo(json, "gpuLayers");
        if (gpu != null) {
            try { this.gpuLayers = Integer.parseInt(gpu); } catch (NumberFormatException ignored) {}
        }

        String api = extrairCampo(json, "apiUrl");
        if (api != null && !api.isBlank()) this.apiUrl = api;

        String key = extrairCampo(json, "apiKey");
        if (key != null && !key.isBlank()) this.apiKey = key;

        String auto = extrairCampo(json, "autoApprove");
        if (auto != null) this.autoApprove = "true".equalsIgnoreCase(auto);
    }

    private static String extrairCampo(String json, String campo) {
        String busca = "\"" + campo + "\"";
        int idx = json.indexOf(busca);
        if (idx < 0) return null;

        int doisPontos = json.indexOf(':', idx + busca.length());
        if (doisPontos < 0) return null;

        int inicio = json.indexOf('"', doisPontos + 1);
        if (inicio < 0) return null;

        int fim = json.indexOf('"', inicio + 1);
        if (fim < 0) return null;

        return json.substring(inicio + 1, fim);
    }

    private String gerarJson() {
        String modeloRelativo = Path.of(modeloPath).toString()
            .replace(System.getProperty("user.home"), "~");
        return """
            {
              "modelo": "%s",
              "gpuLayers": %d,
              "apiUrl": %s,
              "apiKey": %s,
              "autoApprove": %s
            }
            """.formatted(
                modeloRelativo,
                gpuLayers,
                apiUrl == null ? "null" : "\"" + apiUrl + "\"",
                apiKey == null ? "null" : "\"" + apiKey + "\"",
                autoApprove
            );
    }

    // --- Getters/Setters ---

    public String getModeloPath() { return modeloPath; }
    public void setModeloPath(String path) { this.modeloPath = path; }

    public int getGpuLayers() { return gpuLayers; }
    public void setGpuLayers(int n) { this.gpuLayers = n; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String url) { this.apiUrl = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String key) { this.apiKey = key; }

    public boolean isAutoApprove() { return autoApprove; }
    public void setAutoApprove(boolean auto) { this.autoApprove = auto; }

    // --- Defaults ---

    public static String getNomeModeloPadrao() { return MODELO_PADRAO; }
    public static String getUrlDownload() { return HUGGINGFACE_URL; }
    public static long getTamanhoEsperado() { return TAMANHO_ESPERADO; }
    public static Path getModelsDir() { return MODELS_DIR; }
    public static Path getConfigDir() { return CONFIG_DIR; }
}
