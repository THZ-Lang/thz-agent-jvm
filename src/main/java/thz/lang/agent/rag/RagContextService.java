package thz.lang.agent.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Serviço de RAG (Retrieval-Augmented Generation) para o THZ-Agent.
 * Conecta a indexação vetorial de arquivos do workspace ao contexto do agente.
 */
public final class RagContextService {

    private final String caminhoProjeto;
    private boolean indexado;
    private int totalArquivosIndexados;

    public RagContextService(String caminhoProjeto) {
        this.caminhoProjeto = caminhoProjeto != null && !caminhoProjeto.isBlank()
            ? caminhoProjeto
            : System.getProperty("user.dir");
        this.indexado = false;
        this.totalArquivosIndexados = 0;
    }

    /**
     * Garante que o projeto está indexado em memória para busca semântica.
     */
    public synchronized void garantirIndexacao() {
        if (indexado) return;
        try {
            Path dir = Paths.get(caminhoProjeto);
            if (Files.isDirectory(dir)) {
                totalArquivosIndexados = ProjectIndexer.indexar(caminhoProjeto);
                indexado = true;
            }
        } catch (Exception e) {
            // Em caso de falha de leitura em arquivos protegidos, não impede o agente de operar
            indexado = false;
        }
    }

    /**
     * Força a re-indexação dos arquivos do projeto.
     */
    public synchronized int reindexar() {
        totalArquivosIndexados = ProjectIndexer.indexar(caminhoProjeto);
        indexado = true;
        return totalArquivosIndexados;
    }

    /**
     * Recupera os trechos mais relevantes do projeto para o objetivo atual.
     * Retorna vazio se a similaridade não atingir o limiar mínimo.
     */
    public String obterContextoRelevante(String query, int topK, double minSimilaridade) {
        if (query == null || query.isBlank()) return "";

        garantirIndexacao();
        if (!indexado || ProjectIndexer.totalChunks() == 0) {
            return "";
        }

        List<ProjectIndexer.ResultadoBusca> resultados = ProjectIndexer.buscar(query, topK);
        if (resultados.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean temConteudo = false;

        for (ProjectIndexer.ResultadoBusca r : resultados) {
            if (r.similaridade() >= minSimilaridade) {
                if (!temConteudo) {
                    sb.append("## Contexto do Workspace Relevante (RAG):\n");
                    temConteudo = true;
                }
                sb.append(String.format("### Arquivo: %s (similaridade: %.2f)\n", r.caminho(), r.similaridade()));
                sb.append("```\n").append(r.conteudo().trim()).append("\n```\n\n");
            }
        }

        return temConteudo ? sb.toString().trim() : "";
    }

    public boolean isIndexado() {
        return indexado;
    }

    public int getTotalArquivosIndexados() {
        return totalArquivosIndexados;
    }

    public String getCaminhoProjeto() {
        return caminhoProjeto;
    }
}
