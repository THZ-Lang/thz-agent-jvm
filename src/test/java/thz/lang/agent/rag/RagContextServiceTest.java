package thz.lang.agent.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes do RagContextService")
class RagContextServiceTest {

    @Test
    @DisplayName("Deve inicializar e indexar arquivos de teste com sucesso")
    void testIndexacaoWorkspace(@TempDir Path tempDir) throws IOException {
        Path thzFile = tempDir.resolve("teste.thz");
        Files.writeString(thzFile, """
            val limite = 10
            var contador = 1
            enquanto contador <= limite {
                print(contador)
                contador = contador + 1
            }
            """);

        RagContextService service = new RagContextService(tempDir.toString());
        service.garantirIndexacao();

        assertTrue(service.isIndexado());
        assertTrue(service.getTotalArquivosIndexados() >= 1);
    }

    @Test
    @DisplayName("Deve recuperar contexto semântico formatado para query relevante")
    void testRecuperarContextoRelevante(@TempDir Path tempDir) throws IOException {
        Path thzFile = tempDir.resolve("servico_bancario.thz");
        Files.writeString(thzFile, """
            // Serviço bancário de transferência em THZ-LANG
            funcao transferir(origem: Conta, destino: Conta, valor: Monetario): Logico {
                EXIGE valor > 0.0
                EXIGE origem.saldo >= valor
                origem.saldo = origem.saldo - valor
                destino.saldo = destino.saldo + valor
                GARANTE origem.saldo >= 0.0
                retorne verdadeiro
            }
            """);

        RagContextService service = new RagContextService(tempDir.toString());
        String contexto = service.obterContextoRelevante("transferir saldo conta bancaria", 1, 0.10);

        assertNotNull(contexto);
        assertFalse(contexto.isBlank());
        assertTrue(contexto.contains("servico_bancario.thz"));
        assertTrue(contexto.contains("funcao transferir"));
    }

    @Test
    @DisplayName("Deve retornar vazio para query nula ou em branco")
    void testQueryVazia(@TempDir Path tempDir) {
        RagContextService service = new RagContextService(tempDir.toString());
        assertEquals("", service.obterContextoRelevante("", 2, 0.50));
        assertEquals("", service.obterContextoRelevante(null, 2, 0.50));
    }
}
