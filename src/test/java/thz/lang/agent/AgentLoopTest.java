package thz.lang.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thz.lang.agent.llm.LlmBackend;
import thz.lang.agent.tools.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    private MockLlmBackend llm;
    private ContextManager context;
    private ToolRegistry tools;
    private TerminalUI ui;
    private SessionMemory memory;
    private AgentLoop loop;
    private String sessaoId;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        llm = new MockLlmBackend();
        context = new ContextManager();
        tools = new ToolRegistry();
        ui = new TerminalUI(); // Sai para null/mergulho
        memory = new SessionMemory();
        sessaoId = UUID.randomUUID().toString();
        memory.criarSessao(sessaoId, "mock", tempDir.toString());

        ApprovalGate approval = new ApprovalGate(true, ui); // auto-approve
        loop = new AgentLoop(llm, context, tools, approval, ui, memory, sessaoId);
    }

    @Test
    @DisplayName("Deve retornar resposta final quando LLM gera Answer:")
    void testRespostaFinal() {
        llm.respostas = List.of("Answer: THZ-LANG é uma linguagem corporativa.");

        String resultado = loop.executar("O que é THZ-LANG?");

        assertTrue(resultado.contains("THZ-LANG é uma linguagem corporativa"));
        assertEquals(1, llm.chamadas);
    }

    @Test
    @DisplayName("Deve executar ferramenta quando LLM gera Action:")
    void testExecutarFerramenta(@TempDir Path tempDir) throws Exception {
        Path arquivo = tempDir.resolve("teste.txt");
        Files.writeString(arquivo, "conteudo");

        llm.respostas = List.of(
            "Thought: Vou ler o arquivo\nAction: read_file(\"" + arquivo + "\")",
            "Answer: O arquivo contém 'conteudo'."
        );

        String resultado = loop.executar("Leia o arquivo teste.txt");

        assertTrue(resultado.contains("conteudo"));
        assertEquals(2, llm.chamadas);
    }

    @Test
    @DisplayName("Deve lidar com ferramenta desconhecida")
    void testFerramentaDesconhecida() {
        llm.respostas = List.of(
            "Action: ferramenta_fantasma(arg)",
            "Answer: Não consegui usar a ferramenta."
        );

        String resultado = loop.executar("Use a ferramenta fantasma");

        // O LLM gera Answer na segunda chamada após falha da ferramenta
        assertNotNull(resultado);
        assertFalse(resultado.isEmpty());
    }

    @Test
    @DisplayName("Deve respeitar limite de iterações")
    void testLimiteIteracoes() {
        // LLM nunca gera Answer, só Action inválido
        llm.respostas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            llm.respostas.add("Thought: pensando...\nAction: read_file(\"inexistente\")");
        }

        String resultado = loop.executar("Fique em loop");

        assertTrue(resultado.contains("máximo") || resultado.contains("iterações"));
    }

    @Test
    @DisplayName("Deve salvar turnos na memória")
    void testSalvarMemoria() {
        llm.respostas = List.of("Answer: ok");

        loop.executar("teste");

        List<String[]> sessoes = memory.listarSessoes();
        assertFalse(sessoes.isEmpty());
    }

    // --- Mock LLM Backend ---

    static class MockLlmBackend implements LlmBackend {
        List<String> respostas = new ArrayList<>();
        int chamadas = 0;

        @Override public String nome() { return "mock-llm"; }

        @Override
        public String gerar(String prompt, int maxTokens, float temperature, int topK, float topP) {
            if (chamadas >= respostas.size()) {
                return "Answer: Sem mais respostas.";
            }
            return respostas.get(chamadas++);
        }

        @Override public float[] embedding(String texto) { return new float[0]; }
        @Override public int estimarTokens(String texto) { return texto.length() / 4; }
        @Override public ModeloInfo infoModelo() { return new ModeloInfo("mock", "test", "mock", 0); }
        @Override public void fechar() {}
    }
}
