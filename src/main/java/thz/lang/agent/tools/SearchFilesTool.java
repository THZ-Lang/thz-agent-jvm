package thz.lang.agent.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Busca por padrão (regex) nos arquivos do projeto.
 */
public final class SearchFilesTool implements Tool {

    private static final Set<String> IGNORAR_EXTENSAO = Set.of(
        ".class", ".jar", ".exe", ".dll", ".so", ".dylib",
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".woff", ".ttf",
        ".zip", ".tar", ".gz", ".7z"
    );

    private static final Set<String> IGNORAR_DIR = Set.of(
        ".git", "node_modules", "target", "dist", ".gradle", "build", ".thz"
    );

    @Override public String nome() { return "search_files"; }
    @Override public String descricao() { return "Busca por padrão/regex nos arquivos do projeto"; }
    @Override public String parametrosSchema() { return "{\"pattern\": \"string\", \"path\": \"string (opcional)\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.SEGURO; }

    @Override
    public String executar(String args) {
        String pattern = extrairPattern(args);
        String caminho = extrairPath(args);

        if (pattern == null || pattern.isBlank()) {
            return "Erro: padrão não especificado";
        }

        Path dir = caminho != null ? Paths.get(caminho) : Paths.get(".");
        if (!Files.isDirectory(dir)) {
            return "Erro: diretório não encontrado: " + dir;
        }

        try {
            List<String> resultados = new ArrayList<>();
            int maxResults = 50;

            try (var stream = Files.walk(dir)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !IGNORAR_DIR.stream().anyMatch(d ->
                        p.toString().contains("\\" + d + "\\") || p.toString().contains("/" + d + "/")))
                    .filter(p -> !IGNORAR_EXTENSAO.contains(
                        extensao(p.getFileName().toString())))
                    .filter(p -> {
                        try {
                            String conteudo = Files.readString(p);
                            return conteudo.contains(pattern);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .limit(maxResults)
                    .forEach(p -> {
                        try {
                            String conteudo = Files.readString(p);
                            String[] linhas = conteudo.split("\n");
                            for (int i = 0; i < linhas.length; i++) {
                                if (linhas[i].contains(pattern)) {
                                    resultados.add(String.format("%s:%d: %s",
                                        dir.relativize(p), i + 1, linhas[i].strip()));
                                }
                            }
                        } catch (Exception ignored) {}
                    });
            }

            if (resultados.isEmpty()) {
                return "Nenhum resultado para: " + pattern;
            }

            return String.format("%d resultados:\n%s",
                resultados.size(),
                String.join("\n", resultados));

        } catch (IOException e) {
            return "Erro na busca: " + e.getMessage();
        }
    }

    private String extrairPattern(String args) {
        String t = args.trim();
        if (t.startsWith("pattern=\"")) {
            int end = t.indexOf("\"", 9);
            return end > 0 ? t.substring(9, end) : t.substring(9);
        }
        return t;
    }

    private String extrairPath(String args) {
        int idx = args.indexOf("path=\"");
        if (idx == -1) return null;
        int start = idx + 6;
        int end = args.indexOf("\"", start);
        return end > 0 ? args.substring(start, end) : null;
    }

    private String extensao(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot) : "";
    }
}
