package thz.lang.agent;

import thz.lang.agent.llm.*;

import java.nio.file.*;
import java.util.*;

/**
 * THZ-Agent — Assistente de Código Autônomo
 *
 * Standalone tool similar a Claude Code / Cursor / Aider.
 * Roda no terminal, tem memória, historico de conversação,
 * e pode ler/escrever/editar arquivos do projeto.
 *
 * Uso:
 *   thz agent --modelo model.gguf
 *   thz agent --api https://api.openai.com/v1 --api-key sk-...
 *   thz agent --yes (auto-approve)
 */
public final class ThzAgent {

    private static final String VERSAO = "1.0.0";

    public static void main(String[] args) {
        TerminalUI ui = new TerminalUI();
        Map<String, String> argMap = parseArgs(args);

        // Modo --help
        if (argMap.containsKey("help") || argMap.containsKey("h")) {
            exibirAjuda();
            return;
        }

        // Modo --sessoes
        if (argMap.containsKey("sessoes")) {
            listarSessoes();
            return;
        }

        ui.exibirBanner();

        // 1. Criar backend LLM
        LlmBackend backend;
        try {
            backend = criarBackend(argMap, ui);
        } catch (Exception e) {
            ui.exibirErro("Erro ao inicializar LLM: " + e.getMessage());
            return;
        }

        ui.exibirInfo("Modelo: " + backend.nome());
        ui.exibirInfo("Projeto: " + Paths.get("").toAbsolutePath());
        ui.exibirInfo("Digite 'sair' para encerrar.\n");

        // 2. Inicializar componentes
        ContextManager context = new ContextManager();
        ToolRegistry tools = new ToolRegistry();
        boolean autoApprove = argMap.containsKey("yes") || argMap.containsKey("y");
        ApprovalGate approval = new ApprovalGate(autoApprove, ui);
        SessionMemory memory = new SessionMemory();
        String sessaoId = UUID.randomUUID().toString();

        // 3. Carregar THZ.md do projeto (se existir)
        carregarInstrucoesProjeto(context);

        // 4. Criar sessão
        String modelo = backend.infoModelo().nome();
        String dir = Paths.get("").toAbsolutePath().toString();
        memory.criarSessao(sessaoId, modelo, dir);

        // 5. Criar loop do agente
        AgentLoop loop = new AgentLoop(backend, context, tools, approval, ui, memory, sessaoId);

        // 6. REPL do agente
        try {
            while (true) {
                ui.exibirPrompt();
                String input = ui.lerLinha();

                if (input == null || input.trim().equalsIgnoreCase("sair")) {
                    break;
                }

                String trimmed = input.trim();

                // Comandos especiais
                if (trimmed.equalsIgnoreCase("limpar")) {
                    context.limparHistorico();
                    ui.exibirInfo("Histórico limpo.");
                    continue;
                }

                if (trimmed.equalsIgnoreCase("compactar")) {
                    ui.exibirCompacting();
                    context.compactar();
                    ui.exibirInfo("Contexto compactado. Tokens estimados: " + context.getTokensEstimados());
                    continue;
                }

                if (trimmed.equalsIgnoreCase("tokens")) {
                    ui.exibirInfo("Tokens estimados: " + context.getTokensEstimados() + "/" + context.getTokenBudget());
                    continue;
                }

                if (trimmed.equalsIgnoreCase("ferramentas")) {
                    ui.exibirInfo("Ferramentas disponíveis:");
                    ui.exibirInfo(tools.gerarDescricoes());
                    continue;
                }

                if (trimmed.startsWith("auto ")) {
                    boolean on = trimmed.substring(5).trim().toLowerCase().startsWith("on");
                    approval.setModoAutomatico(on);
                    ui.exibirInfo("Auto-approve: " + (on ? "ATIVADO" : "DESATIVADO"));
                    continue;
                }

                if (trimmed.isEmpty()) continue;

                // Executar loop do agente
                try {
                    loop.executar(trimmed);
                } catch (ApprovalGate.SairException e) {
                    break;
                }
            }
        } finally {
            backend.fechar();
            memory.fechar();
            ui.exibirInfo("Sessão encerrada.");
        }
    }

    // --- Helpers ---

    private static LlmBackend criarBackend(Map<String, String> argMap, TerminalUI ui) {
        // Backend API
        if (argMap.containsKey("api")) {
            String url = argMap.get("api");
            String key = argMap.getOrDefault("api-key", "");
            String modelo = argMap.getOrDefault("modelo", "gpt-4o-mini");
            return new ApiLlmBackend(url, key, modelo);
        }

        // Backend local — modelo explícito via CLI
        if (argMap.containsKey("modelo")) {
            int gpu = Integer.parseInt(argMap.getOrDefault("gpu", "0"));
            String path = argMap.get("modelo");
            try {
                path = ModelDownloader.baixarSeNecessario(path).toString();
            } catch (Exception e) {
                throw new RuntimeException("Falha ao preparar modelo: " + e.getMessage(), e);
            }
            return new LocalLlmBackend(path, gpu);
        }

        // Default: carregar config e auto-baixar modelo padrão
        AgentConfig config = AgentConfig.ler();
        int gpu = Integer.parseInt(argMap.getOrDefault("gpu",
            String.valueOf(config.getGpuLayers())));

        ui.exibirInfo("Modelo padrão: " + AgentConfig.getNomeModeloPadrao());
        try {
            String path = ModelDownloader.baixarSeNecessario(config.getModeloPath()).toString();
            return new LocalLlmBackend(path, gpu);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao baixar modelo padrão: " + e.getMessage(), e);
        }
    }

    private static void carregarInstrucoesProjeto(ContextManager context) {
        String[] nomes = {"THZ.md", "CLAUDE.md", "AGENTS.md", ".thz-agent.md"};
        for (String nome : nomes) {
            Path path = Paths.get(nome);
            if (Files.exists(path)) {
                try {
                    String conteudo = Files.readString(path);
                    context.setInstrucoesProjeto(conteudo);
                    break;
                } catch (Exception ignored) {}
            }
        }
    }

    private static void listarSessoes() {
        SessionMemory memory = new SessionMemory();
        List<String[]> sessoes = memory.listarSessoes();

        if (sessoes.isEmpty()) {
            System.out.println("Nenhuma sessão anterior encontrada.");
        } else {
            System.out.println("Sessões anteriores:");
            for (String[] s : sessoes) {
                System.out.printf("  %s | %s | %s | %s%n", s[0].substring(0, 8), s[1], s[2], s[3]);
            }
        }

        memory.fechar();
    }

    private static void exibirAjuda() {
        System.out.println("""
            THZ-Agent v%s — Assistente de Código Inteligente

            Uso:
              thz agent [opções]

            Opções:
              --modelo <path>       Modelo GGUF local
              --gpu <n>             Nº de GPU layers (default: 0)
              --api <url>           URL da API (OpenAI-compatible)
              --api-key <key>       Chave da API
              --yes, -y             Auto-approve todas as ações
              --sessoes             Lista sessões anteriores
              --help, -h            Exibe esta ajuda

            Exemplos:
              thz agent --modelo phi-3-mini.Q4_K_M.gguf
              thz agent --api https://api.openai.com/v1 --api-key sk-...
              thz agent --modelo llama.gguf --yes

            Comandos no REPL:
              sair                  Encerra a sessão
              limpar                Limpa o histórico
              compactar             Compacta o contexto
              tokens                Mostra uso de tokens
              ferramentas           Lista ferramentas disponíveis
              auto on/off           Liga/desliga auto-approve
            """.formatted(VERSAO));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "true");
                }
            } else if (arg.startsWith("-")) {
                map.put(arg.substring(1), "true");
            }
        }
        return map;
    }
}
