package thz.lang.agent.llm;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

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
            this.serverManager = new LlamaServerManager(modeloPath);
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
        String modo = nativoDisponivel ? "FFI" : (serverManager != null ? "HTTP-CPU" : "indisponível");
        return "llama.cpp (" + modo + ") [" + nome + "]";
    }

    @Override
    public String gerar(String prompt, int maxTokens, float temperature, int topK, float topP) {
        // 1. Tentar Rust FFI
        if (nativoDisponivel) {
            return gerarViaFfi(prompt, maxTokens, temperature, topK, topP);
        }

        // 2. Tentar HTTP (llama-server orquestrado pelo próprio agente)
        if (serverManager != null) {
            try {
                serverManager.assegurarPronto();
                for (int tentativa = 0; tentativa < 30; tentativa++) {
                    try {
                        return gerarViaHttp(prompt, maxTokens, temperature, topK, topP);
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("503")) {
                            Thread.sleep(1500); // aguardar modelo carregar
                            continue;
                        }
                        throw e;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Falha ao executar modelo local via llama-server: " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException("Nenhum backend LLM local disponível.");
    }

    @Override
    public String gerarComMensagens(java.util.List<MensagemChat> mensagens, int maxTokens, float temperature, int topK, float topP) {
        if (nativoDisponivel) {
            return gerarViaFfi(mensagens.get(mensagens.size() - 1).content(), maxTokens, temperature, topK, topP);
        }

        if (serverManager != null) {
            try {
                serverManager.assegurarPronto();
                for (int tentativa = 0; tentativa < 30; tentativa++) {
                    try {
                        return gerarViaHttpMensagens(mensagens, maxTokens, temperature, topK, topP);
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("503")) {
                            Thread.sleep(1500); // aguardar modelo carregar
                            continue;
                        }
                        throw e;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Falha ao executar modelo local via llama-server: " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException("Nenhum backend LLM local disponível.");
    }

    private String gerarViaFfi(String prompt, int maxTokens, float temperature, int topK, float topP) {
        // TODO: Chamar thz_llm_gerar via Panama FFI quando o runtime estiver compilado
        return "[THZ-LLM] Resposta FFI para prompt de " + prompt.length() + " chars";
    }

    private String gerarViaHttp(String prompt, int maxTokens, float temperature, int topK, float topP)
            throws IOException, InterruptedException {
        return gerarViaHttpMensagens(java.util.List.of(new MensagemChat("user", prompt)), maxTokens, temperature, topK, topP);
    }

    private String gerarViaHttpMensagens(java.util.List<MensagemChat> mensagens, int maxTokens, float temperature, int topK, float topP)
            throws IOException, InterruptedException {

        StringBuilder json = new StringBuilder();
        json.append("{\"model\":\"local\",\"messages\":[");
        for (int i = 0; i < mensagens.size(); i++) {
            if (i > 0) json.append(",");
            MensagemChat m = mensagens.get(i);
            json.append("{\"role\":").append(escapeJson(m.role()))
                .append(",\"content\":").append(escapeJson(m.content()))
                .append("}");
        }
        json.append("],\"max_tokens\":").append(maxTokens)
            .append(",\"temperature\":").append(String.format(java.util.Locale.US, "%.2f", Math.max(0.2f, temperature)))
            .append(",\"repeat_penalty\":1.25")
            .append(",\"presence_penalty\":0.5")
            .append(",\"frequency_penalty\":0.5")
            .append(",\"stop\":[\"<|im_end|>\",\"<|im_start|>\",\"\\n[usuario]\",\"\\n[user]\",\"## Ferramentas\",\"### Instruções\",\"\\n## Instruções\",\"<|endoftext|>\"]")
            .append(",\"stream\":false}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverManager.getBaseUrl() + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(300))
            .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        return truncarRepeticoes(extrairConteudoResposta(body));
    }

    /**
     * Remove tokens especiais e trunca degenerações de repetição consecutivas comuns em modelos compactos (0.5B).
     */
    public static String truncarRepeticoes(String texto) {
        if (texto == null || texto.isBlank()) return "";

        // 1. Remover marcadores de fim residuais ou vazamentos de cabeçalhos de sistema
        String[] stops = new String[]{
            "<|im_end|>", "<|im_start|>", "<|endoftext|>",
            "\n[usuario]", "\n[user]", "\n[assistente]", "\n[assistant]",
            "\n## Instruções", "\n### Instruções", "\n## Ferramentas", "\n### Ferramentas",
            "\n## SINTAXE", "\n## COMPATIBILIDADE", "\n## OBJETIVO", "\n## Padrão ReAct",
            "\nUsuário:", "\nUsuario:", "\nUser:"
        };
        for (String stop : stops) {
            int stopIdx = texto.indexOf(stop);
            if (stopIdx != -1) {
                texto = texto.substring(0, stopIdx);
            }
        }

        texto = texto.strip();

        // 2. Detecção de repetição de parágrafos consecutivos (inclusive listas numeradas)
        String[] paragrafos = texto.split("\\n\\s*\\n");
        if (paragrafos.length > 1) {
            List<String> unicos = new ArrayList<>();
            Set<String> normalizadosVistos = new HashSet<>();
            for (String p : paragrafos) {
                String pLimpo = p.strip();
                if (pLimpo.isBlank()) continue;
                // Normaliza removendo numeração de início (ex: "1. ", "10) ")
                String pNorm = pLimpo.replaceAll("^\\d+[\\.\\)]\\s*", "").strip();
                if (pNorm.length() > 20 && normalizadosVistos.contains(pNorm)) {
                    break;
                }
                normalizadosVistos.add(pNorm);
                unicos.add(pLimpo);
            }
            texto = String.join("\n\n", unicos);
        }

        // 3. Detecção de ciclo repetitivo em texto contínuo (frases ou blocos repetidos imediatamente)
        // Procura padrão X + X onde length(X) >= 30
        for (int tam = 40; tam <= 250; tam++) {
            for (int i = 0; i + (tam * 2) <= texto.length(); i++) {
                String pedaco1 = texto.substring(i, i + tam);
                String pedaco2 = texto.substring(i + tam, i + (tam * 2));
                if (pedaco1.equals(pedaco2)) {
                    // Trunca a partir da primeira repetição
                    return texto.substring(0, i + tam).strip();
                }
            }
        }

        return texto;
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
