package thz.lang.agent.rag;

import thz.lang.ia.ThzIaEngine;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

/**
 * Indexador de projeto para RAG (Retrieval-Augmented Generation).
 * Varrer os arquivos do projeto, gera embeddings e armazena para busca semântica.
 */
public final class ProjectIndexer {

    private static final Set<String> EXTENSOES_TEXTO = Set.of(
        ".thz", ".java", ".rs", ".ts", ".js", ".py", ".md", ".txt",
        ".json", ".yaml", ".yml", ".toml", ".kts", ".gradle"
    );

    private static final Set<String> IGNORAR_DIR = Set.of(
        ".git", "node_modules", "target", "dist", ".gradle", "build", ".thz"
    );

    private static final int CHUNK_SIZE = 512;
    private static final int DIM_EMBEDDING = 128;

    private static final List<ChunkIndexado> indice = new ArrayList<>();

    /**
     * Indexa todos os arquivos de texto do projeto.
     */
    public static int indexar(String caminhoProjeto) {
        indice.clear();
        Path dir = Paths.get(caminhoProjeto);
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Diretório não encontrado: " + caminhoProjeto);
        }

        AtomicInteger arquivosIndexados = new AtomicInteger(0);

        try (var stream = Files.walk(dir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> EXTENSOES_TEXTO.contains(extensao(p.getFileName().toString())))
                .filter(p -> !IGNORAR_DIR.stream().anyMatch(d ->
                    p.toString().contains("\\" + d + "\\") || p.toString().contains("/" + d + "/")))
                .forEach(p -> {
                    try {
                        indexarArquivo(p, dir);
                        arquivosIndexados.incrementAndGet();
                    } catch (Exception ignored) {}
                });
        } catch (IOException e) {
            throw new RuntimeException("Erro ao indexar projeto: " + e.getMessage(), e);
        }

        return arquivosIndexados.get();
    }

    private static void indexarArquivo(Path arquivo, Path baseDir) throws IOException {
        String conteudo = Files.readString(arquivo);
        if (conteudo.isBlank()) return;

        String relativo = baseDir.relativize(arquivo).toString();
        List<String> chunks = dividirEmChunks(conteudo, CHUNK_SIZE);

        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = ThzIaEngine.gerarEmbedding(chunks.get(i), DIM_EMBEDDING);
            indice.add(new ChunkIndexado(relativo, i, chunks.get(i), embedding));
        }
    }

    /**
     * Busca semântica por query.
     */
    public static List<ResultadoBusca> buscar(String query, int topK) {
        float[] embQuery = ThzIaEngine.gerarEmbedding(query, DIM_EMBEDDING);

        // Calcular similaridade cosseno para cada chunk
        List<ResultadoBusca> resultados = new ArrayList<>();
        for (ChunkIndexado chunk : indice) {
            double sim = similaridadeCosseno(embQuery, chunk.embedding());
            resultados.add(new ResultadoBusca(
                chunk.caminho(), chunk.idx(), chunk.conteudo(), sim
            ));
        }

        // Ordenar por similaridade (decrescente)
        resultados.sort((a, b) -> Double.compare(b.similaridade(), a.similaridade()));

        return resultados.subList(0, Math.min(topK, resultados.size()));
    }

    /**
     * Busca e retorna formato pronto para o agente.
     */
    public static String buscarFormatado(String query, int topK) {
        List<ResultadoBusca> resultados = buscar(query, topK);
        if (resultados.isEmpty()) {
            return "Nenhum resultado encontrado para: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Resultados da busca (%d encontrados):\n\n", resultados.size()));
        for (ResultadoBusca r : resultados) {
            sb.append(String.format("--- %s (chunk %d, sim=%.3f) ---\n",
                r.caminho(), r.idx(), r.similaridade()));
            sb.append(r.conteudo()).append("\n\n");
        }
        return sb.toString();
    }

    public static int totalChunks() {
        return indice.size();
    }

    // --- Helpers ---

    private static List<String> dividirEmChunks(String texto, int tamanhoMax) {
        List<String> chunks = new ArrayList<>();
        String[] linhas = texto.split("\n");
        StringBuilder atual = new StringBuilder();

        for (String linha : linhas) {
            if (atual.length() + linha.length() > tamanhoMax && !atual.isEmpty()) {
                chunks.add(atual.toString());
                atual = new StringBuilder();
            }
            atual.append(linha).append("\n");
        }

        if (!atual.isEmpty()) {
            chunks.add(atual.toString());
        }

        return chunks.isEmpty() ? List.of(texto) : chunks;
    }

    private static double similaridadeCosseno(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;

        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static String extensao(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot) : "";
    }

    // --- Records ---

    private record ChunkIndexado(String caminho, int idx, String conteudo, float[] embedding) {}

    public record ResultadoBusca(String caminho, int idx, String conteudo, double similaridade) {}
}
