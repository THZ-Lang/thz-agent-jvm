package thz.lang.agent.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThzEvalToolTest {

    private final ThzEvalTool tool = new ThzEvalTool();

    @Test
    @DisplayName("Deve ter metadados e nível de perigo SEGURO")
    void testMetadados() {
        assertEquals("thz_eval", tool.nome());
        assertNotNull(tool.descricao());
        assertEquals(Tool.NivelPerigo.SEGURO, tool.nivelPerigo());
    }

    @Test
    @DisplayName("Deve compilar e executar sintaxe moderna com Top-Level Statements")
    void testExecutarSintaxeModerna() {
        String codigo = """
            val x = 42
            print(x)
            """;
        String resultado = tool.executar(codigo);
        assertNotNull(resultado);
        assertTrue(resultado.contains("sucesso"), "Deveria indicar sucesso na execução: " + resultado);
    }

    @Test
    @DisplayName("Deve compilar e executar sintaxe foundational no estilo corporativo")
    void testExecutarSintaxeFoundational() {
        String codigo = """
            PROGRAMA TesteFoundational
            PROCEDIMENTO Principal()
            INICIO
                VARIAVEL x : INTEIRO <- 42
                EXIBA x
            FIM
            FIM_PROGRAMA
            """;
        String resultado = tool.executar(codigo);
        assertNotNull(resultado);
        assertTrue(resultado.contains("sucesso"), "Deveria indicar sucesso: " + resultado);
    }

    @Test
    @DisplayName("Deve reportar erros sintáticos ou semânticos com clareza")
    void testReportarErros() {
        String codigoInvalido = "SE 1 == 2 EXIBIR sem_entao";
        String resultado = tool.executar(codigoInvalido);
        assertNotNull(resultado);
        assertTrue(resultado.contains("❌") || resultado.contains("Falha") || resultado.contains("Erro"),
            "Deveria reportar erro: " + resultado);
    }

    @Test
    @DisplayName("Deve extrair código fornecido em formato JSON ou texto puro")
    void testExtrairCodigo() {
        assertEquals("EXIBIR 1", ThzEvalTool.extrairCodigo("EXIBIR 1"));
        assertEquals("EXIBIR 2", ThzEvalTool.extrairCodigo("{\"codigo\": \"EXIBIR 2\"}"));
        assertEquals("EXIBIR 3", ThzEvalTool.extrairCodigo("\"EXIBIR 3\""));
    }
}
