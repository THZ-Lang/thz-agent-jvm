package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SearchFilesToolTest {

    private SearchFilesTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchFilesTool();
    }

    @Test
    @DisplayName("Deve encontrar texto em arquivos")
    void testBuscarTexto(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("arquivo1.txt"), "Hello World\nLine 2\nTHZ-LANG rocks");
        Files.writeString(tempDir.resolve("arquivo2.txt"), "Nothing here");

        String resultado = tool.executar("pattern=\"THZ-LANG\" path=\"" + tempDir + "\"");
        assertTrue(resultado.contains("THZ-LANG"));
        assertTrue(resultado.contains("resultados"));
    }

    @Test
    @DisplayName("Deve retornar mensagem quando nenhum resultado")
    void testNenhumResultado(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("arquivo.txt"), "conteudo qualquer");

        String resultado = tool.executar("pattern=\"XYZNONEXISTENT\" path=\"" + tempDir + "\"");
        assertTrue(resultado.contains("Nenhum resultado"));
    }

    @Test
    @DisplayName("Deve retornar erro para padrão vazio")
    void testPadraoVazio() {
        String resultado = tool.executar("");
        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo SEGURO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.SEGURO, tool.nivelPerigo());
    }
}
