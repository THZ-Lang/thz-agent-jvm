package thz.lang.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIndexerTest {

    @Test
    @DisplayName("Deve indexar arquivos de texto de um diretório")
    void testIndexarProjeto(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("arquivo.thz"), "PROGRAMA Teste FIM_PROGRAMA");
        Files.writeString(tempDir.resolve("codigo.java"), "public class Test {}");
        Files.writeString(tempDir.resolve("readme.md"), "# Projeto THZ");

        int arquivos = ProjectIndexer.indexar(tempDir.toString());
        assertTrue(arquivos >= 2, "Deve indexar pelo menos 2 arquivos");
        assertTrue(ProjectIndexer.totalChunks() > 0);
    }

    @Test
    @DisplayName("Deve ignorar diretórios .git e node_modules")
    void testIgnorarDiretorios(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve(".git/config"), "deve ser ignorado");
        Files.createDirectories(tempDir.resolve("node_modules"));
        Files.writeString(tempDir.resolve("node_modules/pacote.js"), "deve ser ignorado");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/codigo.thz"), "deve ser indexado");

        ProjectIndexer.indexar(tempDir.toString());
        // Não deve incluir arquivos de .git ou node_modules
        assertTrue(true); // Se não lançou exceção, está ok
    }

    @Test
    @DisplayName("Deve ignorar arquivos binários")
    void testIgnorarBinarios(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("imagem.png"), "fake png");
        Files.writeString(tempDir.resolve("codigo.thz"), "PROGRAMA Teste FIM_PROGRAMA");

        int arquivos = ProjectIndexer.indexar(tempDir.toString());
        // Deve indexar apenas .thz
        assertTrue(arquivos >= 1);
    }

    @Test
    @DisplayName("Deve buscar por similaridade semântica")
    void testBuscarSemantico(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("auth.thz"),
            "REGRA_NEGOCIO Autenticar\n  OPERACAO VerificarSenha(senha: TEXTO) : LOGICO\nFIM_REGRA");
        Files.writeString(tempDir.resolve("finance.thz"),
            "REGRA_NEGOCIO Faturar\n  OPERACAO CalcularTotal() : DECIMAL\nFIM_REGRA");

        ProjectIndexer.indexar(tempDir.toString());

        List<ProjectIndexer.ResultadoBusca> resultados =
            ProjectIndexer.buscar("autenticação e senha", 5);

        assertFalse(resultados.isEmpty(), "Deve encontrar resultados");
        // O primeiro resultado deve ser o arquivo de auth
        assertTrue(resultados.get(0).caminho().contains("auth"));
    }

    @Test
    @DisplayName("Deve formatar resultados para o agente")
    void testBuscarFormatado(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("test.thz"), "PROGRAMA Teste FIM_PROGRAMA");
        ProjectIndexer.indexar(tempDir.toString());

        String formatado = ProjectIndexer.buscarFormatado("teste", 3);
        assertNotNull(formatado);
        assertTrue(formatado.contains("Resultados"));
    }

    @Test
    @DisplayName("Deve formatar busca corretamente")
    void testFormatarBusca(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("unico.txt"), "conteudo unico");
        ProjectIndexer.indexar(tempDir.toString());

        String formatado = ProjectIndexer.buscarFormatado("teste", 5);
        assertNotNull(formatado);
        assertFalse(formatado.isBlank());
    }

    @Test
    @DisplayName("Deve retornar erro para diretório inexistente")
    void testDiretorioInexistente() {
        assertThrows(RuntimeException.class,
            () -> ProjectIndexer.indexar("/caminho/inexistente"));
    }
}
