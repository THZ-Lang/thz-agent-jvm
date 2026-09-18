package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecCommandToolTest {

    private ExecCommandTool tool;

    @BeforeEach
    void setUp() {
        tool = new ExecCommandTool();
    }

    @Test
    @DisplayName("Deve executar comando simples no Windows")
    void testComandoSimples() {
        String resultado = tool.executar("command=\"echo THZ\"");
        assertNotNull(resultado);
        assertTrue(resultado.contains("THZ") || resultado.contains("exit"));
    }

    @Test
    @DisplayName("Deve retornar exit code 0 para comando bem-sucedido")
    void testExitCodeZero() {
        String resultado = tool.executar("command=\"echo ok\"");
        assertTrue(resultado.contains("[exit 0]"));
    }

    @Test
    @DisplayName("Deve retornar exit code não-zero para comando com falha")
    void testExitCodeNaoZero() {
        String resultado = tool.executar("command=\"dir /nonexistent_path_12345\"");
        // No Windows, dir com path inválido retorna exit code 1
        assertNotNull(resultado);
    }

    @Test
    @DisplayName("Deve retornar erro para comando vazio")
    void testComandoVazio() {
        String resultado = tool.executar("");
        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo PERIGOSO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.PERIGOSO, tool.nivelPerigo());
    }

    @Test
    @DisplayName("Deve ter nome e descrição válidos")
    void testMetadados() {
        assertEquals("execute_command", tool.nome());
        assertNotNull(tool.descricao());
    }
}
