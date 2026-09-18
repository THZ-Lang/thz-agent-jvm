package thz.lang.agent.tools;

import java.nio.file.*;

/**
 * Escreve conteúdo em um arquivo (sobrescreve ou cria).
 */
public final class WriteFileTool implements Tool {

    @Override public String nome() { return "write_file"; }
    @Override public String descricao() { return "Escreve conteúdo em um arquivo (cria ou sobrescreve)"; }
    @Override public String parametrosSchema() { return "{\"path\": \"string\", \"content\": \"string\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.MODERADO; }

    @Override
    public String executar(String args) {
        String[] parsed = parseDois(args, "path", "content");
        if (parsed == null) return "Erro: use write_file(path=\"...\", content=\"...\")";

        String caminho = parsed[0];
        String conteudo = parsed[1];

        try {
            Path path = Paths.get(caminho);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            boolean existe = Files.exists(path);
            Files.writeString(path, conteudo);

            return String.format(
                "%s arquivo %s (%d chars)",
                existe ? "Atualizado" : "Criado",
                caminho,
                conteudo.length()
            );
        } catch (Exception e) {
            return "Erro ao escrever arquivo: " + e.getMessage();
        }
    }

    static String[] parseDois(String args, String key1, String key2) {
        // Parse: key1="value1", key2="value2"
        try {
            String t = args.trim();
            int idx1 = t.indexOf(key1 + "=\"");
            int idx2 = t.indexOf(key2 + "=\"");
            if (idx1 == -1 || idx2 == -1) return null;

            int start1 = idx1 + key1.length() + 2;
            int end1 = t.indexOf("\"", start1);
            int start2 = idx2 + key2.length() + 2;
            int end2 = t.indexOf("\"", start2);

            if (end1 == -1 || end2 == -1) return null;

            return new String[]{
                t.substring(start1, end1),
                t.substring(start2, end2)
            };
        } catch (Exception e) {
            return null;
        }
    }
}
