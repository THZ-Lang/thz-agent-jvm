package thz.lang.agent.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Lista arquivos e diretórios de um caminho.
 */
public final class ListFilesTool implements Tool {

    @Override public String nome() { return "list_files"; }
    @Override public String descricao() { return "Lista arquivos e diretórios em um caminho"; }
    @Override public String parametrosSchema() { return "{\"path\": \"string (opcional, default=.)\"}"; }
    @Override public NivelPerigo nivelPerigo() { return NivelPerigo.SEGURO; }

    @Override
    public String executar(String args) {
        String caminho = extrairCaminho(args);
        Path dir = (caminho == null || caminho.isBlank()) ? Paths.get(".") : Paths.get(caminho);

        if (!Files.exists(dir)) {
            return "Erro: caminho não encontrado: " + dir;
        }

        try {
            if (Files.isRegularFile(dir)) {
                return formatarArquivo(dir, Paths.get("."));
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Diretório: ").append(dir).append("\n\n");

            // Diretórios primeiro
            List<Path> dirs = new ArrayList<>();
            List<Path> files = new ArrayList<>();

            try (var stream = Files.list(dir)) {
                stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).forEach(p -> {
                    if (Files.isDirectory(p)) dirs.add(p);
                    else files.add(p);
                });
            }

            for (Path d : dirs) {
                sb.append("  [DIR]  ").append(d.getFileName()).append("/\n");
            }
            for (Path f : files) {
                long size = Files.size(f);
                sb.append("  [FIL]  ").append(f.getFileName())
                  .append(" (").append(formatarTamanho(size)).append(")\n");
            }

            sb.append("\n").append(dirs.size()).append(" diretórios, ")
              .append(files.size()).append(" arquivos");

            return sb.toString();

        } catch (Exception e) {
            return "Erro ao listar: " + e.getMessage();
        }
    }

    private String extrairCaminho(String args) {
        String t = args.trim();
        if (t.startsWith("path=\"")) {
            int end = t.indexOf("\"", 6);
            return end > 0 ? t.substring(6, end) : t.substring(6);
        }
        if (t.isBlank()) return null;
        return t;
    }

    private String formatarArquivo(Path file, Path base) {
        try {
            return String.format("%s (%s)", base.relativize(file), formatarTamanho(Files.size(file)));
        } catch (IOException e) {
            return file.toString();
        }
    }

    private String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
