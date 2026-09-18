package thz.lang.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Deve criar config com defaults quando não existe arquivo")
    void testCriarConfigDefault() {
        // Temporarily override home dir isn't feasible, so just test construction
        AgentConfig config = new AgentConfig();
        assertNotNull(config.getModeloPath());
        assertTrue(config.getModeloPath().contains("qwen2.5-coder-3b-instruct"));
        assertEquals(0, config.getGpuLayers());
        assertNull(config.getApiUrl());
        assertNull(config.getApiKey());
        assertFalse(config.isAutoApprove());
    }

    @Test
    @DisplayName("Deve retornar valores default corretos")
    void testValoresDefault() {
        assertEquals("qwen2.5-coder-3b-instruct-q4_k_m.gguf", AgentConfig.getNomeModeloPadrao());
        assertTrue(AgentConfig.getUrlDownload().contains("huggingface.co"));
        assertTrue(AgentConfig.getTamanhoEsperado() > 1_000_000_000L);
    }

    @Test
    @DisplayName("Deve-setar e obter valores")
    void testSetters() {
        AgentConfig config = new AgentConfig();
        config.setModeloPath("/tmp/custom.gguf");
        config.setGpuLayers(35);
        config.setApiUrl("https://api.example.com");
        config.setApiKey("sk-test");
        config.setAutoApprove(true);

        assertEquals("/tmp/custom.gguf", config.getModeloPath());
        assertEquals(35, config.getGpuLayers());
        assertEquals("https://api.example.com", config.getApiUrl());
        assertEquals("sk-test", config.getApiKey());
        assertTrue(config.isAutoApprove());
    }
}
