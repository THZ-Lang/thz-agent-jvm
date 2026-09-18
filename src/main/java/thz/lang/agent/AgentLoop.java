package thz.lang.agent;

import thz.lang.agent.llm.LlmBackend;
import thz.lang.agent.tools.Tool;

import java.util.*;
import java.util.regex.*;

/**
 * Loop principal do agente (padrão ReAct).
 * Think → Act → Observe → Repeat até atingir o objetivo.
 */
public final class AgentLoop {

    private static final int MAX_ITERACOES = 15;

    private final LlmBackend llm;
    private final ContextManager context;
    private final ToolRegistry tools;
    private final ApprovalGate approval;
    private final TerminalUI ui;
    private final SessionMemory memory;
    private final String sessaoId;

    private int turnoIdx = 0;

    public AgentLoop(LlmBackend llm, ContextManager context, ToolRegistry tools,
                     ApprovalGate approval, TerminalUI ui, SessionMemory memory,
                     String sessaoId) {
        this.llm = llm;
        this.context = context;
        this.tools = tools;
        this.approval = approval;
        this.ui = ui;
        this.memory = memory;
        this.sessaoId = sessaoId;
    }

    /**
     * Executa o loop ReAct para um objetivo do usuário.
     */
    public String executar(String objetivo) {
        context.adicionarMensagem("usuario", objetivo);
        memory.salvarTurno(sessaoId, turnoIdx++, "usuario", objetivo);

        for (int i = 0; i < MAX_ITERACOES; i++) {
            // Verificar necessidade de compactação
            if (context.precisaCompactar()) {
                ui.exibirCompacting();
                context.compactar();
            }

            // 1. Montar prompt completo
            String prompt = montarPromptComFerramentas();

            // 2. Chamar LLM
            String resposta = llm.gerar(prompt, 2048, 0.7f, 40, 0.95f);

            // 3. Verificar se é resposta final
            if (contemAnswer(resposta)) {
                String respostaFinal = extrairAnswer(resposta);
                ui.exibirResposta(respostaFinal);
                context.adicionarMensagem("assistente", respostaFinal);
                memory.salvarTurno(sessaoId, turnoIdx++, "assistente", respostaFinal);
                return respostaFinal;
            }

            // 4. Extrair Thought
            String thought = extrairThought(resposta);
            if (thought != null) {
                ui.exibirPensamento(thought);
            }

            // 5. Extrair Action
            ToolCall toolCall = extrairAction(resposta);
            if (toolCall == null) {
                // LLM não gerou action — tratar como resposta direta
                String respostaLimpa = resposta.trim();
                ui.exibirResposta(respostaLimpa);
                context.adicionarMensagem("assistente", respostaLimpa);
                memory.salvarTurno(sessaoId, turnoIdx++, "assistente", respostaLimpa);
                return respostaLimpa;
            }

            // 6. Verificar se a ferramenta existe
            Optional<Tool> toolOpt = tools.obter(toolCall.nome);
            if (toolOpt.isEmpty()) {
                String erro = "Ferramenta desconhecida: " + toolCall.nome;
                ui.exibirErro(erro);
                context.adicionarMensagem("ferramenta", erro);
                continue;
            }

            Tool tool = toolOpt.get();

            // 7. Pedir aprovação se necessário
            if (approval.precisaAprovacao(tool)) {
                boolean aprovado = approval.pedirAprovacao(
                    tool.nome(),
                    toolCall.toString()
                );
                if (!aprovado) {
                    String rejeitado = "Ação rejeitada pelo usuário.";
                    ui.exibirInfo(rejeitado);
                    context.adicionarMensagem("ferramenta", rejeitado);
                    continue;
                }
            }

            // 8. Executar ferramenta
            String resultado;
            try {
                resultado = tool.executar(toolCall.args);
            } catch (Exception e) {
                resultado = "Erro ao executar " + tool.nome() + ": " + e.getMessage();
            }

            ui.exibirObservacao(resultado);

            // 9. Alimentar resultado ao contexto
            String obsMsg = "Observation: " + resultado;
            context.adicionarMensagem("ferramenta", obsMsg);
            memory.salvarTurno(sessaoId, turnoIdx++, "ferramenta", obsMsg);
        }

        String timeout = "Número máximo de iterações atingido (" + MAX_ITERACOES + ").";
        ui.exibirErro(timeout);
        return timeout;
    }

    // --- Parsing da resposta do LLM ---

    private String montarPromptComFerramentas() {
        String base = context.montarPrompt();
        return base + tools.gerarDescricoes();
    }

    private boolean contemAnswer(String resposta) {
        return resposta.contains("Answer:");
    }

    private String extrairAnswer(String resposta) {
        int idx = resposta.indexOf("Answer:");
        if (idx == -1) return resposta;
        return resposta.substring(idx + 7).trim();
    }

    private String extrairThought(String resposta) {
        Pattern p = Pattern.compile("Thought:\\s*(.+?)(?=\\n|Action:)", Pattern.DOTALL);
        Matcher m = p.matcher(resposta);
        return m.find() ? m.group(1).trim() : null;
    }

    private ToolCall extrairAction(String resposta) {
        // Formato: Action: nome(args)
        Pattern p = Pattern.compile("Action:\\s*(\\w+)\\s*\\(([^)]*)\\)");
        Matcher m = p.matcher(resposta);
        if (m.find()) {
            return new ToolCall(m.group(1), m.group(2));
        }

        // Fallback: Action: nome
        p = Pattern.compile("Action:\\s*(\\w+)");
        m = p.matcher(resposta);
        if (m.find()) {
            return new ToolCall(m.group(1), "");
        }

        return null;
    }

    /** Representa uma chamada de ferramenta extraída da resposta do LLM */
    public record ToolCall(String nome, String args) {
        @Override
        public String toString() {
            return nome + "(" + args + ")";
        }
    }
}
