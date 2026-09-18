package thz.lang.agent;

import java.util.*;

/**
 * Gerencia o contexto da conversa com o LLM.
 * Controla o budget de tokens e compacta automaticamente quando necessário.
 */
public final class ContextManager {

    private static final int TOKEN_BUDGET_PADRAO = 8192;
    private static final double LIMITE_COMPACTACAO = 0.80;

    private final int tokenBudget;
    private final List<Mensagem> historico;
    private String systemPrompt;
    private String instrucoesProjeto;
    private int tokensEstimados;

    public record Mensagem(String papel, String conteudo, long timestamp) {}

    public ContextManager() {
        this(TOKEN_BUDGET_PADRAO);
    }

    public ContextManager(int tokenBudget) {
        this.tokenBudget = tokenBudget;
        this.historico = new ArrayList<>();
        this.tokensEstimados = 0;
        this.systemPrompt = montarSystemPromptPadrao();
        this.instrucoesProjeto = "";
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    public void setInstrucoesProjeto(String instrucoes) {
        this.instrucoesProjeto = instrucoes;
    }

    public void adicionarMensagem(String papel, String conteudo) {
        historico.add(new Mensagem(papel, conteudo, System.currentTimeMillis()));
        tokensEstimados += estimarTokens(conteudo);
    }

    /** Monta o prompt completo para enviar ao LLM */
    public String montarPrompt() {
        StringBuilder sb = new StringBuilder();

        // System prompt
        sb.append(systemPrompt).append("\n\n");

        // Instruções do projeto (se houver)
        if (!instrucoesProjeto.isBlank()) {
            sb.append("## Instruções do Projeto\n");
            sb.append(instrucoesProjeto).append("\n\n");
        }

        // Histórico da conversa
        sb.append("## Histórico da Conversa\n");
        for (Mensagem msg : historico) {
            sb.append("[").append(msg.papel()).append("] ").append(msg.conteudo()).append("\n\n");
        }

        return sb.toString();
    }

    /** Verifica se precisa compactar o contexto */
    public boolean precisaCompactar() {
        return tokensEstimados > (tokenBudget * LIMITE_COMPACTACAO);
    }

    /** Compacta o contexto resumindo turnos antigos */
    public void compactar() {
        if (historico.size() <= 4) return;

        // Manter apenas os últimos 4 turnos completos
        List<Mensagem> recentes = new ArrayList<>(
            historico.subList(Math.max(0, historico.size() - 4), historico.size())
        );

        // Resumir turnos anteriores
        List<Mensagem> antigos = historico.subList(0, historico.size() - 4);
        StringBuilder resumo = new StringBuilder("[Contexto compactado] Turnos anteriores resumidos:\n");
        for (Mensagem m : antigos) {
            String preview = m.conteudo().length() > 100
                ? m.conteudo().substring(0, 100) + "..."
                : m.conteudo();
            resumo.append("- ").append(m.papel()).append(": ").append(preview).append("\n");
        }

        historico.clear();
        historico.add(new Mensagem("sistema", resumo.toString(), System.currentTimeMillis()));
        historico.addAll(recentes);

        tokensEstimados = estimarTokens(montarPrompt());
    }

    public void limparHistorico() {
        historico.clear();
        tokensEstimados = 0;
    }

    public List<Mensagem> getHistorico() {
        return Collections.unmodifiableList(historico);
    }

    public int getTokensEstimados() {
        return tokensEstimados;
    }

    public int getTokenBudget() {
        return tokenBudget;
    }

    // --- Privados ---

    private String montarSystemPromptPadrao() {
        return "Você é o THZ-Agent, assistente de código. Responda direto e em português.";
    }

    /**  */
    private int estimarTokens(String texto) {
        return texto.length() / 4;
    }
}
