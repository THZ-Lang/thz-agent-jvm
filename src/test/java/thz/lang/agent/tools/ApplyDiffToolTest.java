package thz.lang.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ApplyDiffToolTest {

    private ApplyDiffTool tool;

    @BeforeEach
    void setUp() {
        tool = new ApplyDiffTool();
    }

    @Test
    @DisplayName("Deve substituir texto corretamente")
    void testSubstituirTexto(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("codigo.txt");
        Files.writeString(arquivo, "Hello World");

        String args = "path=\"" + arquivo + "\" search=\"World\" replace=\"THZ\"";
        String resultado = tool.executar(args);

        assertTrue(resultado.contains("Editado"));
        assertEquals("Hello THZ", Files.readString(arquivo));
    }

    @Test
    @DisplayName("Deve retornar erro quando texto não encontrado")
    void testTextoNaoEncontrado(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("codigo.txt");
        Files.writeString(arquivo, "Hello World");

        String args = "path=\"" + arquivo + "\" search=\"Inexistente\" replace=\"X\"";
        String resultado = tool.executar(args);

        assertTrue(resultado.contains("não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar erro para múltiplas ocorrências")
    void testMultiplasOcorrencias(@TempDir Path tempDir) throws IOException {
        Path arquivo = tempDir.resolve("codigo.txt");
        Files.writeString(arquivo, "abc abc abc");

        String args = "path=\"" + arquivo + "\" search=\"abc\" replace=\"xyz\"";
        String resultado = tool.executar(args);

        assertTrue(resultado.contains("vezes"));
    }

    @Test
    @DisplayName("Deve retornar erro para arquivo inexistente")
    void testArquivoInexistente() {
        String args = "path=\"/inexistente.txt\" search=\"a\" replace=\"b\"";
        String resultado = tool.executar(args);

        assertTrue(resultado.contains("Erro"));
    }

    @Test
    @DisplayName("Deve ter nível de perigo MODERADO")
    void testNivelPerigo() {
        assertEquals(Tool.NivelPerigo.MODERADO, tool.nivelPerigo());
    }
}
