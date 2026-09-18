package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {

    private ReadFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool();
    }

    @Test
    @DisplayName("Deve ler conteúdo de um arquivo existente")
    void testLerArquivoExistente(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("teste.txt");
        Files.writeString(arquivo, "Linha 1\nLinha 2\nLinha 3");

        String resultado = tool.executar("\"" + arquivo + "\"");
        assertTrue(resultado.contains("Linha 1"));
        assertTrue(resultado.contains("Linha 2"));
        assertTrue(resultado.contains("Linha 3"));
        assertTrue(resultado.contains("3 chars") || resultado.contains("24 chars"));
    }

    @Test
    @DisplayName("Deve retornar erro para arquivo inexistente")
    void testArquivoInexistente() {
        String resultado = tool.executar("\"/caminho/inexistente.txt\"");
        assertTrue(resultado.contains("Erro"));
        assertTrue(resultado.contains("não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar erro para caminho nulo/vazio")
    void testCaminhoVazio() {
        String resultado = tool.executar("");
        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo SEGURO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.SEGURO, tool.nivelPerigo());
    }

    @Test
    @DisplayName("Deve ter nome e descrição válidos")
    void testMetadados() {
        assertEquals("read_file", tool.nome());
        assertNotNull(tool.descricao());
        assertNotNull(tool.parametrosSchema());
    }
}
