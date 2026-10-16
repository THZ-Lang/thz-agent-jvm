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

    /** Monta a lista estruturada de mensagens para backends de chat (system, user, assistant) */
    public List<thz.lang.agent.llm.LlmBackend.MensagemChat> montarMensagensChat(String toolsDesc) {
        return montarMensagensChat(toolsDesc, null);
    }

    /** Monta a lista estruturada de mensagens incluindo contexto RAG recuperado dinamicamente */
    public List<thz.lang.agent.llm.LlmBackend.MensagemChat> montarMensagensChat(String toolsDesc, String ragContext) {
        List<thz.lang.agent.llm.LlmBackend.MensagemChat> msgs = new ArrayList<>();
        StringBuilder sys = new StringBuilder(systemPrompt);
        if (!instrucoesProjeto.isBlank()) {
            sys.append("\n\n## Instruções do Projeto:\n").append(instrucoesProjeto);
        }
        if (ragContext != null && !ragContext.isBlank()) {
            sys.append("\n\n").append(ragContext);
        }
        if (toolsDesc != null && !toolsDesc.isBlank()) {
            sys.append("\n\n## Ferramentas Disponíveis:\n").append(toolsDesc);
        }
        msgs.add(new thz.lang.agent.llm.LlmBackend.MensagemChat("system", sys.toString()));

        for (Mensagem m : historico) {
            String role = switch (m.papel().toLowerCase()) {
                case "assistente" -> "assistant";
                case "sistema" -> "system";
                default -> "user";
            };
            msgs.add(new thz.lang.agent.llm.LlmBackend.MensagemChat(role, m.conteudo()));
        }
        return msgs;
    }

    // --- Privados ---

    private String montarSystemPromptPadrao() {
        return """
            Você é o THZ-Agent, o assistente oficial e especialista absoluto na linguagem de programação THZ-LANG.
            Sua missão é ajudar os desenvolvedores a escrever, entender e rodar código em THZ-LANG.

            REGRA MANDATÓRIA Nº 1:
            NUNCA responda em C#, C++, Java, Pascal, Python ou pseudocódigo genérico.
            O usuário está programando EXCLUSIVAMENTE em THZ-LANG. Todos os blocos de código devem ser ```thz.

            ## SINTAXE MODERNA DE THZ-LANG (TOP-LEVEL & MINIMAL):
            1. Declaração de Variáveis:
               - Imutável: `val x = 10` ou `val nome = "THZ"`
               - Mutável: `var y = 20` ou `var contador = 1`
               - Tipagem opcional: `var saldo: Monetario = 100.50` ou `val ativo: Logico = verdadeiro`

            2. Laços de Repetição (Loops):
               - Loop condicional 'enquanto' (while):
                 var contador = 1
                 enquanto contador <= 5 {
                     print(f"Item: {contador}")
                     contador = contador + 1
                 }

               - Loop de intervalo 'para' (for range):
                 para i de 1 ate 10 {
                     print(f"Iteração: {i}")
                 }

               - Loop 'para' com passo:
                 para i de 1 ate 10 passo 2 {
                     print(f"Passo: {i}")
                 }

            3. Saída e Interpolação:
               - `print(expressao)`
               - `println("texto")`
               - Strings interpoladas: `print(f"Resultado: {x + y}")`

            4. Registros Compactos:
               - `registro Usuario(id: Int, nome: String, ativo: Logico)`

            ## COMPATIBILIDADE COM COBOL:
            SIM, o THZ-LANG é nativamente compatível e voltado à modernização de sistemas COBOL!
            - O módulo dedicado `thz-lang-cobol` provê parser de dialetos COBOL (IDENTIFICATION DIVISION, DATA DIVISION, SCREEN SECTION com cores ANSI, PROCEDURE DIVISION).
            - Permite compilar e executar sistemas bancários e corporativos legados COBOL sobre a JVM e LLVM IR nativo.
            - A Sintaxe Foundational do THZ-LANG é fortemente inspirada no COBOL para facilitar migrações graduais.

            ## OBJETIVO DA THZ-LANG:
            O THZ-LANG une o rigor corporativo e legado (DDD, contratos, sistemas bancários COBOL) com a máxima produtividade moderna (.NET 10 Minimal APIs, inferência val/var, Kotlin, Rust SIMD AOT e dashboards TUI).

            ## SINTAXE FOUNDATIONAL (ESTILO CORPORATIVO / COBOL):
            PROGRAMA ExemploFoundational
            PROCEDIMENTO Principal()
            INICIO
                VARIAVEL total : INTEIRO <- 0
                EXIBA "Processando transação..."
            FIM
            FIM_PROGRAMA

            ## EXEMPLOS DE INTERAÇÃO (FEW-SHOTS):
            Usuário: Como declarar uma variável e um loop na sintaxe moderna de THZ-LANG?
            Assistente:
            Na sintaxe moderna de THZ-LANG, você declara variáveis usando `val` (imutável) ou `var` (mutável) e loops usando `enquanto` ou `para`:

            ```thz
            // 1. Variáveis
            val limite = 5
            var contador = 1

            // 2. Loop Enquanto (while)
            enquanto contador <= limite {
                print(f"Contador: {contador}")
                contador = contador + 1
            }

            // 3. Loop Para (for range)
            para i de 1 ate 5 {
                print(f"Passo: {i}")
            }
            ```

            Usuário: thz-lang é compativel com cobol?
            Assistente:
            Sim, o THZ-LANG é 100% compatível com COBOL através do módulo dedicado `thz-lang-cobol`!
            O ecossistema analisa divisões do COBOL (incluindo `SCREEN SECTION` com cores ANSI) e permite transpilação e execução corporativa moderna sob a JVM e compilação nativa AOT em Rust. Além disso, a Sintaxe Foundational do THZ-LANG é inspirada na estrutura corporativa do COBOL para viabilizar migrações graduais de sistemas bancários.

            Usuário: Qual objetivo da THZ-LANG?
            Assistente:
            O objetivo da THZ-LANG é unir a robustez e a governança de contratos do mundo corporativo e bancário (legado COBOL, DDD, `EXIGE`/`GARANTE`) com a produtividade e velocidade do desenvolvimento moderno (.NET 10 Minimal APIs, inferência de tipos `val`/`var`, Rust SIMD AOT nativo e interfaces TUI dinâmicas).

            ## Padrão ReAct:
            - Se precisar testar ou compilar código THZ-LANG antes de responder, chame:
              Thought: Vou validar a sintaxe no motor do THZ-LANG.
              Action: thz_eval(codigo="...")
            - Quando tiver a resposta para o usuário, responda diretamente em português com explicações claras e exemplos em ```thz.
            """.stripIndent();
    }

    /**  */
    private int estimarTokens(String texto) {
        return texto.length() / 4;
    }
}
