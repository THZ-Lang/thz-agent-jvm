package thz.lang.agent.llm;

import java.util.List;

/**
 * Interface para backends de LLM. Permite trocar entre local (llama.cpp)
 * e APIs remotas (OpenAI, Anthropic, Ollama, etc.).
 */
public interface LlmBackend {

    record MensagemChat(String role, String content) {}

    /** Nome descritivo do backend (ex: "llama.cpp (HTTP-CPU)", "OpenAI API") */
    String nome();

    /** Gera texto a partir de um prompt em texto corrido */
    String gerar(String prompt, int maxTokens, float temperature, int topK, float topP);

    /** Gera texto a partir de mensagens estruturadas no padrão ChatML (system, user, assistant) */
    default String gerarComMensagens(List<MensagemChat> mensagens, int maxTokens, float temperature, int topK, float topP) {
        StringBuilder sb = new StringBuilder();
        for (MensagemChat m : mensagens) {
            sb.append("[").append(m.role()).append("] ").append(m.content()).append("\n\n");
        }
        return gerar(sb.toString(), maxTokens, temperature, topK, topP);
    }

    /** Gera embedding para um texto (retorna vetor de floats) */
    float[] embedding(String texto);

    /** Retorna estimativa de tokens para um texto */
    int estimarTokens(String texto);

    /** Informações do modelo carregado */
    ModeloInfo infoModelo();

    /** Libera recursos do backend */
    void fechar();

    /** Informações sobre o modelo */
    record ModeloInfo(String nome, String tipo, String provider, int dimsEmbedding) {}
}
