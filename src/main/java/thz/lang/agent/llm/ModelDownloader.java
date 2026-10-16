package thz.lang.agent.llm;

import thz.lang.agent.AgentConfig;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/**
 * Download automático de modelos GGUF do HuggingFace.
 * Barra de progresso no terminal, verificação de tamanho, retry.
 */
public final class ModelDownloader {

    private static final int BUFFER_SIZE = 8192;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private ModelDownloader() {}

    /**
     * Verifica se o modelo existe e está completo.
     * Se não existe, baixa automaticamente do HuggingFace.
     * @return caminho absoluto do modelo
     */
    public static Path baixarSeNecessario(String modeloPath) throws IOException, InterruptedException {
        Path path = Path.of(modeloPath);
        long tamanhoEsperado = AgentConfig.getTamanhoEsperado();
        long margemTolerancia = Math.min(1_000_000L, (long) (tamanhoEsperado * 0.02));

        // Se arquivo existe e tamanho bate, ok
        if (Files.exists(path)) {
            long tamanho = Files.size(path);
            if (tamanho >= tamanhoEsperado - margemTolerancia) {
                return path;
            }
            // Arquivo incompleto — remover e baixar novamente
            System.out.println("[THZ-Agent] Modelo incompleto (" + formatarBytes(tamanho) + "), baixando novamente...");
            Files.delete(path);
        }

        // Criar diretório pai se necessário
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Baixar
        String nomeModelo = path.getFileName().toString();
        System.out.println("[THZ-Agent] Baixando modelo: " + nomeModelo);
        System.out.println("[THZ-Agent] Tamanho: ~" + formatarBytes(tamanhoEsperado));
        System.out.println("[THZ-Agent] Fonte: HuggingFace (" + AgentConfig.getNomeModeloPadrao() + ")");
        System.out.println();

        baixarComProgresso(AgentConfig.getUrlDownload(), path, tamanhoEsperado);

        // Verificar tamanho final
        long tamanhoFinal = Files.size(path);
        if (tamanhoFinal < tamanhoEsperado - margemTolerancia) {
            Files.delete(path);
            throw new IOException("Download incompleto: esperado ~" + formatarBytes(tamanhoEsperado)
                + ", recebido " + formatarBytes(tamanhoFinal));
        }

        System.out.println();
        System.out.println("[THZ-Agent] Modelo baixado com sucesso: " + formatarBytes(tamanhoFinal));
        return path;
    }

    private static void baixarComProgresso(String urlStr, Path destino, long tamanhoPadrao) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(TIMEOUT)
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlStr))
            .timeout(Duration.ofMinutes(30))
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " ao baixar modelo");
        }

        long totalEstimado = tamanhoPadrao;
        String contentLength = response.headers().firstValue("Content-Length").orElse(null);
        if (contentLength != null) {
            try { totalEstimado = Long.parseLong(contentLength); } catch (NumberFormatException ignored) {}
        }

        Path temp = destino.resolveSibling(destino.getFileName() + ".tmp");

        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(temp)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBaixado = 0;
            int read;
            long inicio = System.currentTimeMillis();
            int ultimaBarra = -1;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBaixado += read;

                // Atualizar progresso a cada 500ms
                long agora = System.currentTimeMillis();
                if (agora - inicio > 500 || read < BUFFER_SIZE) {
                    double percentual = (double) totalBaixado / totalEstimado;
                    int barras = (int) (percentual * 30);
                    if (barras != ultimaBarra) {
                        exibirProgresso(totalBaixado, totalEstimado, percentual, inicio);
                        ultimaBarra = barras;
                    }
                }
            }
        }

        // Renomear temp para o nome final
        Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void exibirProgresso(long baixado, long total, double percentual, long inicio) {
        int barras = (int) (percentual * 30);
        StringBuilder barra = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            barra.append(i < barras ? '=' : ' ');
        }
        barra.append(']');

        long elapsed = System.currentTimeMillis() - inicio;
        double velocidade = (double) baixado / (elapsed / 1000.0);
        long etaSegundos = velocidade > 0 ? (long) ((total - baixado) / velocidade) : 0;

        System.out.printf("\r  %s %3.0f%% (%s / %s) %s",
            barra,
            percentual * 100,
            formatarBytes(baixado),
            formatarBytes(total),
            etaSegundos > 0 ? "ETA: " + formatarTempo(etaSegundos) : ""
        );
        System.out.flush();
    }

    private static String formatarBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static String formatarTempo(long segundos) {
        if (segundos < 60) return segundos + "s";
        if (segundos < 3600) return (segundos / 60) + "m" + (segundos % 60) + "s";
        return (segundos / 3600) + "h" + ((segundos % 3600) / 60) + "m";
    }
}
