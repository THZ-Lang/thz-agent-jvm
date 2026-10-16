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
        return """
            Você é o THZ-Agent, assistente de código inteligente e especialista supremo no ecossistema THZ-LANG.
            Sua missão é ajudar desenvolvedores a criar, refatorar, validar e executar programas em THZ-LANG com excelência.

            ## Conhecimento de THZ-LANG:
            THZ-LANG suporta duas sintaxes canônicas que convergem para a mesma Árvore Sintática Abstrata (AST):

            1. Sintaxe Foundational (Corporativa / Estilo COBOL):
               - Estruturada em divisões formais:
                 DIVISAO DE IDENTIFICACAO.
                 PROGRAMA-ID. <Nome>.
                 DIVISAO DE PROCEDIMENTOS.
                 EXIBIR "Olá Mundo".
                 FIM-PROGRAMA.
               - Suporte a operações aritméticas: SOMAR <n> A <var>, SUBTRAIR, MULTIPLICAR, DIVIDIR.
               - Controle de fluxo: SE <cond> ENTAO ... FIM-SE, REPETIR ... ATE <cond>.
               - Terminal & Cores: Cláusula SCREEN SECTION com FOREGROUND-COLOR e BACKGROUND-COLOR (0 a 7 ANSI).

            2. Sintaxe Moderna (Enxuta / Estilo .NET 10 / Kotlin / Minimal APIs):
               - Top-Level Statements nativos: escreva código direto no arquivo, sem necessidade de classes ou wrappers.
               - Variáveis com inferência: `val imutavel = 10` e `var mutavel = "texto"` ou `let`.
               - Interpolação de Strings: `f"Olá {nome}, valor total: {preco * 1.1}"`.
               - Registros compactos: `registro Ponto(x: Int, y: Int)`
               - Dashboards TUI de Terminal:
                 `TUI.janela(titulo: "Dashboard", largura: 60, altura: 20)`
                 `TUI.progresso(percentual: 75, largura: 30)`
                 `TUI.caixa(texto: "Status", estilo: "duplo")`
               - Minimal APIs: `servidor "api" { rota "/ping" { responder "pong" } }`

            ## Padrão ReAct:
            - Se precisar testar, validar sintaxe ou rodar código THZ-LANG, use a ferramenta nativa:
              Thought: Vou validar e rodar o código THZ-LANG para verificar a resposta.
              Action: thz_eval(codigo="...")
            - Para responder ao usuário, finalize com:
              Answer: <sua resposta concisa, explicativa e em português>
            """.stripIndent();
    }

    /**  */
    private int estimarTokens(String texto) {
        return texto.length() / 4;
    }
}
