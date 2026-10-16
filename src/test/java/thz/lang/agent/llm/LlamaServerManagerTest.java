package thz.lang.agent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlamaServerManagerTest {

    @Test
    @DisplayName("Deve instanciar com porta padrão e URL base corretas")
    void testInstanciacao() {
        LlamaServerManager manager = new LlamaServerManager("modelo.gguf");
        assertEquals(8080, manager.getPorta());
        assertEquals("http://127.0.0.1:8080", manager.getBaseUrl());
        assertFalse(manager.isRodando());
    }

    @Test
    @DisplayName("Parar quando não está rodando não deve lançar erro")
    void testPararSeguro() {
        LlamaServerManager manager = new LlamaServerManager("modelo.gguf");
        assertDoesNotThrow(manager::parar);
        assertFalse(manager.isRodando());
    }
}
