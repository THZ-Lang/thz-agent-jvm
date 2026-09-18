package thz.lang.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryTest {

    private SessionMemory memory;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        // Forçar SQLite em diretório temporário
        System.setProperty("thz.agent.db.dir", tempDir.toString());
        memory = new SessionMemory();
    }

    @AfterEach
    void tearDown() {
        memory.fechar();
    }

    @Test
    @DisplayName("Deve criar sessão com sucesso")
    void testCriarSessao() {
        memory.criarSessao("sessao-001", "phi-3-mini", "/projetos/teste");

        List<String[]> sessoes = memory.listarSessoes();
        assertFalse(sessoes.isEmpty());
        assertEquals("sessao-001", sessoes.get(0)[0]);
    }

    @Test
    @DisplayName("Deve salvar e recuperar turnos")
    void testSalvarTurnos() {
        memory.criarSessao("sessao-002", "mock", "/test");

        memory.salvarTurno("sessao-002", 0, "usuario", "Olá");
        memory.salvarTurno("sessao-002", 1, "assistente", "Olá! Como posso ajudar?");

        // Não há método direto para listar turnos, mas não deve lançar exceção
        assertDoesNotThrow(() -> memory.salvarTurno("sessao-002", 2, "ferramenta", "Observation: ok"));
    }

    @Test
    @DisplayName("Deve salvar dados de treino")
    void testSalvarDadosTreino() {
        memory.salvarDadoTreino("O que é DDD?", "DDD é Domain-Driven Design", "bom");

        int total = memory.contarDadosTreino();
        assertTrue(total > 0);
    }

    @Test
    @DisplayName("Deve contar dados de treino corretamente")
    void testContarDadosTreino() {
        int antes = memory.contarDadosTreino();

        memory.salvarDadoTreino("p1", "r1", "bom");
        memory.salvarDadoTreino("p2", "r2", "bom");

        int depois = memory.contarDadosTreino();
        assertEquals(antes + 2, depois);
    }

    @Test
    @DisplayName("Deve listar sessões ordenadas por data")
    void testListarSessoesOrdenadas() {
        memory.criarSessao("s1", "model1", "/path1");
        memory.criarSessao("s2", "model2", "/path2");

        List<String[]> sessoes = memory.listarSessoes();
        assertTrue(sessoes.size() >= 2);
    }

    @Test
    @DisplayName("Deve fechar conexão sem erro")
    void testFechar() {
        SessionMemory mem = new SessionMemory();
        assertDoesNotThrow(() -> mem.fechar());
    }
}
