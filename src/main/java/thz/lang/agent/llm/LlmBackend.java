package thz.lang.agent.llm;

/**
 * Interface para backends de LLM. Permite trocar entre local (llama.cpp)
 * e APIs remotas (OpenAI, Anthropic, Ollama, etc.).
 */
public interface LlmBackend {

    /** Nome descritivo do backend (ex: "llama.cpp (local)", "OpenAI API") */
    String nome();

    /** Gera texto a partir de um prompt */
    String gerar(String prompt, int maxTokens, float temperature, int topK, float topP);

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
