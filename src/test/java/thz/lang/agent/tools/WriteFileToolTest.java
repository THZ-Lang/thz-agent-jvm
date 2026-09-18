package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WriteFileToolTest {

    private WriteFileTool tool;

    @BeforeEach
    void setUp() {
        tool = new WriteFileTool();
    }

    @Test
    @DisplayName("Deve criar um novo arquivo")
    void testCriarArquivo(@TempDir Path tempDir) {
        Path arquivo = tempDir.resolve("novo.txt");
        String args = "path=\"" + arquivo + "\" content=\"Olá Mundo\"";

        String resultado = tool.executar(args);
        assertTrue(resultado.contains("Criado"));
        assertTrue(Files.exists(arquivo));
    }

    @Test
    @DisplayName("Deve sobrescrever arquivo existente")
    void testSobrescreverArquivo(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("existente.txt");
        Files.writeString(arquivo, "Original");

        String args = "path=\"" + arquivo + "\" content=\"Atualizado\"";
        String resultado = tool.executar(args);
        assertTrue(resultado.contains("Atualizado"));

        String conteudo = Files.readString(arquivo);
        assertEquals("Atualizado", conteudo);
    }

    @Test
    @DisplayName("Deve criar diretórios pais automaticamente")
    void testCriarDiretoriosPais(@TempDir Path tempDir) {
        Path arquivo = tempDir.resolve("sub/pasta/arquivo.txt");
        String args = "path=\"" + arquivo + "\" content=\"test\"";

        tool.executar(args);
        assertTrue(Files.exists(arquivo));
    }

    @Test
    @DisplayName("Deve retornar erro para argumentos inválidos")
    void testArgumentosInvalidos() {
        String resultado = tool.executar("sem formato valido");
        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo MODERADO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.MODERADO, tool.nivelPerigo());
    }
}
