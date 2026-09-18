package thz.lang.agent.tools;

import java.nio.file.*;

/**
 * Lê o conteúdo de um arquivo do projeto.
 */
public final class ReadFileTool implements Tool {

    @Override public String nome() { return "read_file"; }
    @Override public String descricao() { return "Lê o conteúdo completo de um arquivo"; }
    @Override public String parametrosSchema() { return "{\"path\": \"string\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.SEGURO; }

    @Override
    public String executar(String args) {
        String caminho = extrairCaminho(args);
        if (caminho == null) return "Erro: caminho não especificado";

        try {
            Path path = Paths.get(caminho);
            if (!Files.exists(path)) {
                return "Erro: arquivo não encontrado: " + caminho;
            }
            if (!Files.isRegularFile(path)) {
                return "Erro: não é um arquivo: " + caminho;
            }
            if (Files.size(path) > 1_000_000) {
                return "Erro: arquivo muito grande (>1MB). Use read_file com offset.";
            }

            String conteudo = Files.readString(path);
            return "--- " + caminho + " (" + conteudo.length() + " chars) ---\n" + conteudo;

        } catch (Exception e) {
            return "Erro ao ler arquivo: " + e.getMessage();
        }
    }

    static String extrairCaminho(String args) {
        // Parse simples: "path" ou path=
        String trimmed = args.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.contains("=")) {
            return trimmed.split("=", 2)[1].trim();
        }
        return trimmed;
    }
}
