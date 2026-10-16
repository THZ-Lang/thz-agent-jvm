package thz.lang.agent.llm;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provedor e orquestrador automático do executável llama-server.
 * Garante que o THZ-Agent possua sua própria infraestrutura de execução local
 * em CPU sem depender de servidores externos como Ollama.
 */
public final class LlamaServerDownloader {

    private static final String VERSAO_PADRAO = "b11696";
    private static final String URL_WINDOWS_X64 =
        "https://github.com/ggml-org/llama.cpp/releases/download/" + VERSAO_PADRAO + "/llama-" + VERSAO_PADRAO + "-bin-win-cpu-x64.zip";

    private static final Path DEFAULT_DIR = Path.of(
        System.getProperty("user.home"), ".thz", "tools", "llama.cpp");

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private LlamaServerDownloader() {}

    /**
     * Retorna o diretório onde os binários do llama.cpp são armazenados.
     */
    public static Path getLlamaDir() {
        return DEFAULT_DIR;
    }

    /**
     * Retorna o caminho do executável do servidor local.
     */
    public static Path getLlamaServerPath() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exeName = isWindows ? "llama-server.exe" : "llama-server";
        return DEFAULT_DIR.resolve(exeName);
    }

    /**
     * Verifica se o llama-server já está instalado e disponível localmente.
     */
    public static boolean isInstalado() {
        Path serverPath = getLlamaServerPath();
        if (Files.isExecutable(serverPath) || Files.exists(serverPath)) {
            return true;
        }

        // Checar também no PATH do sistema operacional
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String exeName = getLlamaServerPath().getFileName().toString();
            for (String part : pathEnv.split(File.pathSeparator)) {
                if (part == null || part.isBlank()) continue;
                Path cand = Path.of(part).resolve(exeName);
                if (Files.isExecutable(cand) || Files.exists(cand)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Garante que o executável llama-server está instalado e pronto para uso.
     * Se ausente, realiza o download automático do binário mínimo de CPU do llama.cpp e o descompacta.
     *
     * @return Path do executável pronto para execução
     */
    public static Path assegurarInstalado() throws IOException, InterruptedException {
        Path serverPath = getLlamaServerPath();
        if (isInstalado()) {
            if (Files.exists(serverPath)) {
                return serverPath;
            }
            // Se estiver no PATH, localizá-lo
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                String exeName = serverPath.getFileName().toString();
                for (String part : pathEnv.split(File.pathSeparator)) {
                    if (part == null || part.isBlank()) continue;
                    Path cand = Path.of(part).resolve(exeName);
                    if (Files.isExecutable(cand) || Files.exists(cand)) {
                        return cand;
                    }
                }
            }
        }

        Files.createDirectories(DEFAULT_DIR);

        System.out.println("[THZ-Agent] Infraestrutura própria LLAMA Server ausente.");
        System.out.println("[THZ-Agent] Baixando binário oficial CPU (llama.cpp " + VERSAO_PADRAO + ")...");
        System.out.println("[THZ-Agent] URL: " + URL_WINDOWS_X64);

        Path zipTemp = DEFAULT_DIR.resolve("llama-bin-temp.zip");
        try {
            baixarArquivo(URL_WINDOWS_X64, zipTemp);
            System.out.println("[THZ-Agent] Descompactando binários em: " + DEFAULT_DIR);
            descompactarZip(zipTemp, DEFAULT_DIR);
        } finally {
            try {
                Files.deleteIfExists(zipTemp);
            } catch (Exception ignored) {}
        }

        if (!Files.exists(serverPath)) {
            throw new IOException("Falha ao preparar infraestrutura do llama-server: arquivo não encontrado em " + serverPath);
        }

        // Em POSIX, marcar como executável
        File file = serverPath.toFile();
        if (!file.canExecute()) {
            file.setExecutable(true);
        }

        System.out.println("[THZ-Agent] Infraestrutura LLAMA Server configurada com sucesso em: " + serverPath);
        return serverPath;
    }

    private static void baixarArquivo(String urlStr, Path destino) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(TIMEOUT)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlStr))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " ao baixar llama-server");
        }

        long totalEstimado = 19_575_690L; // ~19MB
        String cl = response.headers().firstValue("Content-Length").orElse(null);
        if (cl != null) {
            try { totalEstimado = Long.parseLong(cl); } catch (NumberFormatException ignored) {}
        }

        byte[] buffer = new byte[8192];
        long totalBaixado = 0;
        int read;
        long inicio = System.currentTimeMillis();

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(destino)) {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBaixado += read;
                if (System.currentTimeMillis() - inicio > 400 || read < 8192) {
                    double percentual = totalEstimado > 0 ? (double) totalBaixado / totalEstimado : 0.0;
                    System.out.printf("\r  [Llama-Server] Progresso: %.1f%% (%d / %d KB)",
                        percentual * 100, totalBaixado / 1024, totalEstimado / 1024);
                    System.out.flush();
                }
            }
        }
        System.out.println();
    }

    private static void descompactarZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path destPath = targetDir.resolve(entry.getName()).normalize();
                if (!destPath.startsWith(targetDir)) {
                    throw new IOException("Entrada zip inválida (Zip Slip): " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    if (destPath.getParent() != null) {
                        Files.createDirectories(destPath.getParent());
                    }
                    Files.copy(zis, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
