package thz.lang.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.lang.agent.tools.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("Deve registrar todas as ferramentas padrão")
    void testFerramentasPadrao() {
        assertEquals(6, registry.todas().size());
        assertTrue(registry.obter("read_file").isPresent());
        assertTrue(registry.obter("write_file").isPresent());
        assertTrue(registry.obter("apply_diff").isPresent());
        assertTrue(registry.obter("execute_command").isPresent());
        assertTrue(registry.obter("search_files").isPresent());
        assertTrue(registry.obter("list_files").isPresent());
    }

    @Test
    @DisplayName("Deve retornar vazio para ferramenta inexistente")
    void testFerramentaInexistente() {
        Optional<Tool> tool = registry.obter("ferramenta_fantasma");
        assertTrue(tool.isEmpty());
    }

    @Test
    @DisplayName("Deve registrar ferramenta customizada")
    void testRegistrarCustomizada() {
        Tool custom = new Tool() {
            @Override public String nome() { return "custom_tool"; }
            @Override public String descricao() { return "Ferramenta de teste"; }
            @Override public String parametrosSchema() { return "{}"; }
            @Override public String executar(String args) { return "ok"; }
            @Override public NivelPerigo nivelPerigo() { return NivelPerigo.SEGURO; }
        };

        registry.registrar(custom);
        assertTrue(registry.obter("custom_tool").isPresent());
        assertEquals(7, registry.todas().size());
    }

    @Test
    @DisplayName("Deve gerar descrições formatadas para o system prompt")
    void testGerarDescricoes() {
        String desc = registry.gerarDescricoes();
        assertNotNull(desc);
        assertTrue(desc.contains("read_file"));
        assertTrue(desc.contains("write_file"));
        assertTrue(desc.contains("SEGURO") || desc.contains("MODERADO") || desc.contains("PERIGOSO"));
    }
}
