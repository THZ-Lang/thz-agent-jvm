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

        // Modo export-dataset
        if (argMap.containsKey("export-dataset") || (args.length > 0 && "export-dataset".equalsIgnoreCase(args[0]))) {
            String destino = argMap.getOrDefault("export-dataset", "build/datasets/thz_instruct_train.jsonl");
            if (destino.isBlank() || "true".equalsIgnoreCase(destino)) {
                if (args.length > 1 && !args[1].startsWith("-")) {
                    destino = args[1];
                } else {
                    destino = "build/datasets/thz_instruct_train.jsonl";
                }
            }
            exportarDataset(destino, ui);
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

        // 5. Inicializar RAG semântico do workspace
        thz.lang.agent.rag.RagContextService rag = new thz.lang.agent.rag.RagContextService(dir);
        rag.garantirIndexacao();
        if (rag.isIndexado() && rag.getTotalArquivosIndexados() > 0) {
            ui.exibirInfo("RAG: " + rag.getTotalArquivosIndexados() + " arquivos semânticos indexados no workspace.");
        }

        // 6. Criar loop do agente
        AgentLoop loop = new AgentLoop(backend, context, tools, approval, ui, memory, sessaoId);
        loop.setRagService(rag);

        // Se invocado com prompt direto via CLI (--prompt ou -p), executa e sai
        if (argMap.containsKey("prompt")) {
            String promptDireto = argMap.get("prompt");
            try {
                ui.exibirInfo("Executando objetivo: " + promptDireto + "\n");
                loop.executar(promptDireto);
            } finally {
                backend.fechar();
                memory.fechar();
            }
            return;
        }

        // 6. REPL interativo do agente
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

                // Avaliação direta de código THZ-LANG sem passar pelo LLM
                if (trimmed.startsWith("thz ") || trimmed.startsWith("eval ")) {
                    String codigoThz = trimmed.substring(trimmed.indexOf(' ') + 1).trim();
                    ui.exibirInfo("[THZ-LANG] Compilando e executando...");
                    String res = tools.obter("thz_eval")
                        .map(t -> t.executar(codigoThz))
                        .orElse("Ferramenta thz_eval não encontrada.");
                    ui.exibirInfo(res);
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

        // Backend local — seleção por tamanho (--size 1.5b | --size 3b)
        if (argMap.containsKey("size") || argMap.containsKey("s")) {
            String sizeArg = argMap.getOrDefault("size", argMap.get("s"));
            var perfil = AgentConfig.PerfilModelo.resolver(sizeArg);
            int gpu = Integer.parseInt(argMap.getOrDefault("gpu", "0"));
            String path = AgentConfig.getModelsDir().resolve(perfil.getNomeArquivo()).toString();
            ui.exibirInfo(String.format("Modelo selecionado (%s): %s", perfil.getAlias().toUpperCase(), perfil.getNomeArquivo()));
            try {
                path = ModelDownloader.baixarSeNecessario(path).toString();
            } catch (Exception e) {
                throw new RuntimeException("Falha ao preparar modelo (" + perfil.getAlias() + "): " + e.getMessage(), e);
            }
            return new LocalLlmBackend(path, gpu);
        }

        // Backend local — modelo explícito via CLI
        if (argMap.containsKey("modelo")) {
            int gpu = Integer.parseInt(argMap.getOrDefault("gpu", "0"));
            String modeloArg = argMap.get("modelo");
            String path;
            if (modeloArg.equalsIgnoreCase("1.5b") || modeloArg.equalsIgnoreCase("3b") || modeloArg.equalsIgnoreCase("0.5b")) {
                var perfil = AgentConfig.PerfilModelo.resolver(modeloArg);
                path = AgentConfig.getModelsDir().resolve(perfil.getNomeArquivo()).toString();
            } else {
                path = modeloArg;
            }

            try {
                path = ModelDownloader.baixarSeNecessario(path).toString();
            } catch (Exception e) {
                throw new RuntimeException("Falha ao preparar modelo: " + e.getMessage(), e);
            }
            return new LocalLlmBackend(path, gpu);
        }

        // Default: carregar config e auto-baixar modelo padrão (1.5B)
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

    private static void exportarDataset(String caminho, TerminalUI ui) {
        try {
            Path destino = Paths.get(caminho).toAbsolutePath();
            ui.exibirInfo("[THZ-Agent] Gerando dataset de instrução para Fine-Tuning LoRA...");
            var exemplos = thz.lang.agent.training.ThzDatasetGenerator.gerarExemplosPadrao();
            int total = thz.lang.agent.training.ThzDatasetGenerator.exportarJsonl(exemplos, destino);
            ui.exibirSucesso(String.format("Dataset gerado com sucesso: %d exemplos em %s", total, destino));
            ui.exibirInfo("Para iniciar o treinamento LoRA:");
            ui.exibirInfo("  python scripts/train_lora.py --dataset " + destino);
        } catch (Exception e) {
            ui.exibirErro("Falha ao exportar dataset: " + e.getMessage());
        }
    }

    private static void exibirAjuda() {
        System.out.println("""
            THZ-Agent v%s — Assistente e Especialista de Código THZ-LANG

            Uso:
              thz agent [opções]
              thz agent export-dataset [caminho]

            Opções:
              --prompt, -p <texto>  Executa um objetivo direto via CLI e finaliza
              --size, -s <1.5b|3b>  Seleciona modelo Qwen-Coder (1.5B padrão CPU, 3B avançado)
              --export-dataset <arq> Exporta dataset ChatML para Fine-Tuning LoRA
              --modelo <path>       Modelo GGUF local ou alias (1.5b, 3b, 0.5b)
              --gpu <n>             Nº de GPU layers (default: 0)
              --api <url>           URL da API (OpenAI-compatible)
              --api-key <key>       Chave da API
              --yes, -y             Auto-approve todas as ações
              --sessoes             Lista sessões anteriores
              --help, -h            Exibe esta ajuda

            Exemplos:
              thz agent -p "Como declarar uma Minimal API em THZ-LANG moderna?"
              thz agent --size 1.5b
              thz agent --size 3b -p "Modernizar programa COBOL com SCREEN SECTION"
              thz agent --yes

            Comandos no REPL:
              thz <codigo>          Compila e executa código THZ-LANG imediatamente
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
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    map.put(key, args[++i]);
                } else {
                    map.put(key, "true");
                }
            } else if (arg.equals("-p") && i + 1 < args.length) {
                map.put("prompt", args[++i]);
            } else if (arg.startsWith("-")) {
                map.put(arg.substring(1), "true");
            }
        }
        return map;
    }
}
