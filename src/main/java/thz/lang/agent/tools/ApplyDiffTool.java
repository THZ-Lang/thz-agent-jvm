package thz.lang.agent.tools;

import java.nio.file.*;

/**
 * Edição cirúrgica via search/replace (diff).
 * Mais eficiente em tokens que reescrever o arquivo inteiro.
 */
public final class ApplyDiffTool implements Tool {

    @Override public String nome() { return "apply_diff"; }
    @Override public String descricao() { return "Edita um arquivo substituindo uma parte específica (search/replace)"; }
    @Override public String parametrosSchema() { return "{\"path\": \"string\", \"search\": \"string\", \"replace\": \"string\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.MODERADO; }

    @Override
    public String executar(String args) {
        String[] parsed = parseTres(args);
        if (parsed == null) return "Erro: use apply_diff(path=\"...\", search=\"...\", replace=\"...\")";

        String caminho = parsed[0];
        String search = parsed[1];
        String replace = parsed[2];

        try {
            Path path = Paths.get(caminho);
            if (!Files.exists(path)) {
                return "Erro: arquivo não encontrado: " + caminho;
            }

            String conteudo = Files.readString(path);
            int idx = conteudo.indexOf(search);

            if (idx == -1) {
                return "Erro: texto não encontrado no arquivo. Verifique o 'search' exato.";
            }

            // Verificar ocorrências múltiplas
            int count = 0;
            int searchFrom = 0;
            while ((idx = conteudo.indexOf(search, searchFrom)) != -1) {
                count++;
                searchFrom = idx + search.length();
            }

            if (count > 1) {
                return "Erro: texto encontrado " + count + " vezes. Seja mais específico no 'search'.";
            }

            String novoConteudo = conteudo.replace(search, replace);
            Files.writeString(path, novoConteudo);

            int linhasNovas = novoConteudo.split("\n").length;
            int linhasAntigas = conteudo.split("\n").length;

            return String.format(
                "Editado %s: %d linhas -> %d linhas (%+d)",
                caminho, linhasAntigas, linhasNovas, linhasNovas - linhasAntigas
            );

        } catch (Exception e) {
            return "Erro ao editar arquivo: " + e.getMessage();
        }
    }

    private String[] parseTres(String args) {
        try {
            String t = args.trim();
            int idxPath = t.indexOf("path=\"");
            int idxSearch = t.indexOf("search=\"");
            int idxReplace = t.indexOf("replace=\"");

            if (idxPath == -1 || idxSearch == -1 || idxReplace == -1) return null;

            int startP = idxPath + 6;
            int endP = t.indexOf("\"", startP);
            int startS = idxSearch + 8;
            int endS = t.indexOf("\"", startS);
            int startR = idxReplace + 9;
            int endR = t.indexOf("\"", startR);

            if (endP == -1 || endS == -1 || endR == -1) return null;

            return new String[]{
                t.substring(startP, endP),
                t.substring(startS, endS),
                t.substring(startR, endR)
            };
        } catch (Exception e) {
            return null;
        }
    }
}
