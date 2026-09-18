package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ListFilesToolTest {

    private ListFilesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListFilesTool();
    }

    @Test
    @DisplayName("Deve listar arquivos de um diretório")
    void testListarDiretorio(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("arquivo1.txt"), "conteudo");
        Files.writeString(tempDir.resolve("arquivo2.java"), "class Test {}");
        Files.createDirectories(tempDir.resolve("subpasta"));

        String resultado = tool.executar("path=\"" + tempDir + "\"");
        assertTrue(resultado.contains("arquivo1.txt"));
        assertTrue(resultado.contains("arquivo2.java"));
        assertTrue(resultado.contains("subpasta"));
        assertTrue(resultado.contains("2 arquivos"));
    }

    @Test
    @DisplayName("Deve retornar erro para caminho inexistente")
    void testCaminhoInexistente() {
        String resultado = tool.executar("path=\"C:\\caminho\\inexistente\"");
        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve listar diretório atual quando vazio")
    void testDiretorioAtual() {
        String resultado = tool.executar("");
        assertNotNull(resultado);
        assertTrue(resultado.contains("Diretório"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo SEGURO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.SEGURO, tool.nivelPerigo());
    }
}
