package thz.lang.agent.llm;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * Backend local usando llama.cpp.
 * Prioridade: Rust FFI → llama-server HTTP → stub.
 * Auto-inicia llama-server.exe se disponível.
 */
public final class LocalLlmBackend implements LlmBackend {

    private long ctxHandle;
    private final String modeloPath;
    private final int gpuLayers;
    private final boolean nativoDisponivel;
    private LlamaServerManager serverManager;
    private final HttpClient httpClient;

    public LocalLlmBackend(String modeloPath, int gpuLayers) {
        this.modeloPath = modeloPath;
        this.gpuLayers = gpuLayers;
        this.nativoDisponivel = detectarNativo();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        if (!nativoDisponivel) {
            if (LlamaServerManager.disponivel()) {
                this.serverManager = new LlamaServerManager(modeloPath);
            } else {
                System.err.println("[THZ-LLM] Aviso: llama-server.exe não encontrado. " +
                    "Modo stub ativado.");
            }
        }
    }

    private boolean detectarNativo() {
        try {
            System.loadLibrary("thz_runtime");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    @Override
    public String nome() {
        String nome = Path.of(modeloPath)
            .getFileName().toString().replaceAll("\\.[^.]+$", "");
        String modo = nativoDisponivel ? "FFI" : (serverManager != null ? "HTTP" : "stub");
        return "llama.cpp (" + modo + ") [" + nome + "]";
    }

    @Override
    public String gerar(String prompt, int maxTokens, float temperature, int topK, float topP) {
        // 1. Tentar Rust FFI
        if (nativoDisponivel) {
            return gerarViaFfi(prompt, maxTokens, temperature, topK, topP);
        }

        // 2. Tentar HTTP (llama-server)
        if (serverManager != null) {
            try {
                serverManager.iniciar();
                // Retry até o modelo carregar (503 = model loading)
                for (int tentativa = 0; tentativa < 30; tentativa++) {
                    try {
                        return gerarViaHttp(prompt, maxTokens, temperature, topK, topP);
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("503")) {
                            Thread.sleep(2000); // aguardar modelo carregar
                            continue;
                        }
                        throw e;
                    }
                }
            } catch (Exception e) {
                System.err.println("[THZ-LLM] Erro ao conectar com llama-server: " + e.getMessage());
            }
        }

        // 3. Fallback: stub
        return "Answer: O backend local não está disponível. " +
            "Configure --api para usar uma API remota.";
    }

    private String gerarViaFfi(String prompt, int maxTokens, float temperature, int topK, float topP) {
        // TODO: Chamar thz_llm_gerar via Panama FFI quando o runtime estiver compilado
        return "[THZ-LLM] Resposta FFI para prompt de " + prompt.length() + " chars";
    }

    private String gerarViaHttp(String prompt, int maxTokens, float temperature, int topK, float topP)
            throws IOException, InterruptedException {

        String json = "{\"model\":\"local\",\"messages\":[{\"role\":\"user\",\"content\":"
            + escapeJson(prompt)
            + "}],\"max_tokens\":" + maxTokens
            + ",\"temperature\":" + temperature
            + ",\"stream\":false}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverManager.getBaseUrl() + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(300))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        return extrairConteudoResposta(body);
    }

    private static String extrairConteudoResposta(String jsonResponse) {
        // Parse robusto: encontrar "content" dentro de "message" dentro de "choices"
        // Formato: {"choices":[{"message":{"content":"..."}}]}

        // 1. Encontrar "content":
        int contentIdx = jsonResponse.indexOf("\"content\"");
        if (contentIdx == -1) return jsonResponse;

        // 2. Encontrar ":" após "content"
        int colonIdx = jsonResponse.indexOf(':', contentIdx + 9);
        if (colonIdx == -1) return jsonResponse;

        // 3. Encontrar início do valor (pular espaços)
        int start = colonIdx + 1;
        while (start < jsonResponse.length() && jsonResponse.charAt(start) == ' ') start++;
        if (start >= jsonResponse.length() || jsonResponse.charAt(start) != '"') return jsonResponse;
        start++; // pular aspas de abertura

        // 4. Encontrar fim do valor (aspas não-escapadas)
        int end = start;
        while (end < jsonResponse.length()) {
            char c = jsonResponse.charAt(end);
            if (c == '\\') {
                end += 2; // pular caractere escapado
                continue;
            }
            if (c == '"') break;
            end++;
        }

        if (end >= jsonResponse.length()) return jsonResponse;

        return jsonResponse.substring(start, end)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private static int indexOfUnescaped(String s, char c, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                i++; // pular escape
            } else if (s.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    @Override
    public float[] embedding(String texto) {
        // TODO: Implementar via /v1/embeddings quando necessário
        return new float[384];
    }

    @Override
    public int estimarTokens(String texto) {
        return texto.length() / 4;
    }

    @Override
    public ModeloInfo infoModelo() {
        String nome = Path.of(modeloPath)
            .getFileName().toString().replaceAll("\\.[^.]+$", "");
        return new ModeloInfo(nome, "local", "llama.cpp", 384);
    }

    @Override
    public void fechar() {
        if (serverManager != null) {
            serverManager.parar();
        }
        ctxHandle = 0;
    }
}
