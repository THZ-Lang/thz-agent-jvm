package thz.lang.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextManagerTest {

    private ContextManager ctx;

    @BeforeEach
    void setUp() {
        ctx = new ContextManager(1000); // budget pequeno para testes
    }

    @Test
    @DisplayName("Deve criar contexto com system prompt padrão")
    void testSystemPromptPadrao() {
        String prompt = ctx.montarPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("THZ-Agent"));
    }

    @Test
    @DisplayName("Deve adicionar mensagens ao histórico")
    void testAdicionarMensagem() {
        ctx.adicionarMensagem("usuario", "Olá");
        ctx.adicionarMensagem("assistente", "Olá! Como posso ajudar?");

        List<ContextManager.Mensagem> historico = ctx.getHistorico();
        assertEquals(2, historico.size());
        assertEquals("usuario", historico.get(0).papel());
        assertEquals("Olá", historico.get(0).conteudo());
        assertEquals("assistente", historico.get(1).papel());
    }

    @Test
    @DisplayName("Deve estimar tokens corretamente (~4 chars por token)")
    void testEstimacaoTokens() {
        ctx.adicionarMensagem("usuario", "12345678"); // 8 chars = ~2 tokens
        assertTrue(ctx.getTokensEstimados() > 0);
        assertTrue(ctx.getTokensEstimados() < 10);
    }

    @Test
    @DisplayName("Deve detectar necessidade de compactação")
    void testPrecisaCompactar() {
        assertFalse(ctx.precisaCompactar());

        // Preencher até ~80% do budget
        for (int i = 0; i < 50; i++) {
            ctx.adicionarMensagem("usuario", "Mensagem de teste longa para ocupar espaço no contexto ".repeat(2));
        }

        assertTrue(ctx.precisaCompactar());
    }

    @Test
    @DisplayName("Deve compactar contexto preservando turnos recentes")
    void testCompactar() {
        // Adicionar muitas mensagens
        for (int i = 0; i < 20; i++) {
            ctx.adicionarMensagem("usuario", "Pergunta " + i);
            ctx.adicionarMensagem("assistente", "Resposta " + i);
        }

        int antes = ctx.getHistorico().size();
        ctx.compactar();
        int depois = ctx.getHistorico().size();

        assertTrue(depois < antes, "Deve reduzir o número de mensagens");
        assertTrue(depois >= 2, "Deve manter pelo menos os turnos recentes");
    }

    @Test
    @DisplayName("Deve limpar histórico completamente")
    void testLimparHistorico() {
        ctx.adicionarMensagem("usuario", "teste");
        ctx.limparHistorico();

        assertTrue(ctx.getHistorico().isEmpty());
        assertEquals(0, ctx.getTokensEstimados());
    }

    @Test
    @DisplayName("Deve incluir instruções do projeto no prompt")
    void testInstrucoesProjeto() {
        ctx.setInstrucoesProjeto("Use sempre indentação de 4 espaços.");
        String prompt = ctx.montarPrompt();

        assertTrue(prompt.contains("Use sempre indentação de 4 espaços."));
        assertTrue(prompt.contains("Instruções do Projeto"));
    }

    @Test
    @DisplayName("Deve incluir system prompt customizado")
    void testSystemPromptCustomizado() {
        ctx.setSystemPrompt("Você é um especialista em Rust.");
        String prompt = ctx.montarPrompt();

        assertTrue(prompt.contains("especialista em Rust"));
    }
}
