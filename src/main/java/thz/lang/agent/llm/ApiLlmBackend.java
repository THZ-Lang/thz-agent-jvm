package thz.lang.agent.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Backend remoto usando APIs OpenAI-compatible.
 * Suporta OpenAI, Anthropic (via proxy), Ollama, LM Studio, etc.
 */
public final class ApiLlmBackend implements LlmBackend {

    private final String apiUrl;
    private final String apiKey;
    private final String modelo;
    private final HttpClient client;

    public ApiLlmBackend(String apiUrl, String apiKey, String modelo) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String nome() {
        return "API: " + modelo;
    }

    @Override
    public String gerar(String prompt, int maxTokens, float temperature, int topK, float topP) {
        try {
            String body = """
                {
                    "model": "%s",
                    "messages": [{"role": "user", "content": %s}],
                    "max_tokens": %d,
                    "temperature": %.2f,
                    "top_p": %.2f
                }
                """.formatted(
                modelo,
                escapeJson(prompt),
                maxTokens,
                temperature,
                topP
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(120))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("API retornou " + response.statusCode() + ": " + response.body());
            }

            // Extrair content do JSON de resposta
            return extrairContent(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erro na requisição API: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embedding(String texto) {
        try {
            String body = """
                {
                    "model": "%s",
                    "input": %s
                }
                """.formatted(modelo, escapeJson(texto));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new float[0];
            }

            return extrairEmbedding(response.body());

        } catch (Exception e) {
            return new float[0];
        }
    }

    @Override
    public int estimarTokens(String texto) {
        return texto.length() / 4;
    }

    @Override
    public ModeloInfo infoModelo() {
        return new ModeloInfo(modelo, "api", apiUrl, 1536);
    }

    @Override
    public void fechar() {
        // HttpClient não precisa de fechamento explícito
    }

    // --- Helpers ---

    private static String escapeJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /** Extração simples de "content" de um JSON de chat completion */
    private static String extrairContent(String json) {
        // Busca por "content": "..." no primeiro choice
        int idx = json.indexOf("\"content\"");
        if (idx == -1) return json;

        int start = json.indexOf("\"", idx + 9) + 1;
        if (start == 0) return json;

        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '\\') {
                end += 2;
                continue;
            }
            if (json.charAt(end) == '"') break;
            end++;
        }

        return json.substring(start, end)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /** Extração simples de embedding de um JSON de embeddings */
    private static float[] extrairEmbedding(String json) {
        int idx = json.indexOf("\"embedding\"");
        if (idx == -1) return new float[0];

        int arrStart = json.indexOf("[", idx);
        int arrEnd = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1) return new float[0];

        String[] parts = json.substring(arrStart + 1, arrEnd).split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                result[i] = 0f;
            }
        }
        return result;
    }
}
