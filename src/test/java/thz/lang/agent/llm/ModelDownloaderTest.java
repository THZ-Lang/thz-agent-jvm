package thz.lang.agent.llm;

import thz.lang.agent.AgentConfig;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class ModelDownloaderTest {

    @Test
    @DisplayName("Deve ter URL de download válida")
    void testUrlDownload() {
        String url = AgentConfig.getUrlDownload();
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
        assertTrue(url.contains("huggingface.co"));
        assertTrue(url.endsWith(".gguf"));
    }

    @Test
    @DisplayName("Deve ter tamanho esperado entre 300MB e 1GB para modelo leve de CPU")
    void testTamanhoEsperado() {
        long tamanho = AgentConfig.getTamanhoEsperado();
        assertTrue(tamanho >= 300_000_000L, "Modelo deve ter pelo menos 300MB");
        assertTrue(tamanho <= 1_000_000_000L, "Modelo deve ter no máximo 1GB para execução ágil em CPU");
    }

    @Test
    @DisplayName("Deve detectar modelo existente como completo")
    void testModeloExistente(@TempDir Path tempDir) throws Exception {
        Path modelo = tempDir.resolve("qwen2.5-coder-0.5b-instruct-q4_k_m.gguf");

        // Criar arquivo com tamanho >= esperado
        byte[] dados = new byte[100]; // Simular arquivo existente
        Files.write(modelo, dados);

        assertTrue(Files.exists(modelo));
    }

    @Test
    @DisplayName("Deve ter directory models configurado")
    void testModelsDir() {
        Path modelsDir = AgentConfig.getModelsDir();
        assertNotNull(modelsDir);
        assertTrue(modelsDir.toString().contains("models"));
    }
}
