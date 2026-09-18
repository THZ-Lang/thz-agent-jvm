package thz.lang.agent;

import thz.lang.agent.tools.*;
import java.util.*;

/**
 * Registry de ferramentas disponíveis para o agente.
 * O LLM pode chamar qualquer ferramenta registrada durante o loop ReAct.
 */
public final class ToolRegistry {

    private final Map<String, Tool> ferramentas = new LinkedHashMap<>();

    public ToolRegistry() {
        // Registrar ferramentas padrão
        registrar(new ReadFileTool());
        registrar(new WriteFileTool());
        registrar(new ApplyDiffTool());
        registrar(new ExecCommandTool());
        registrar(new SearchFilesTool());
        registrar(new ListFilesTool());
    }

    public void registrar(Tool tool) {
        ferramentas.put(tool.nome(), tool);
    }

    public Optional<Tool> obter(String nome) {
        return Optional.ofNullable(ferramentas.get(nome));
    }

    public Collection<Tool> todas() {
        return ferramentas.values();
    }

    /** Gera descrição formatada para o system prompt do LLM */
    public String gerarDescricoes() {
        StringBuilder sb = new StringBuilder();
        for (Tool tool : ferramentas.values()) {
            sb.append("- ").append(tool.nome())
              .append(": ").append(tool.descricao())
              .append("\n  Params: ").append(tool.parametrosSchema())
              .append("\n  Risco: ").append(tool.nivelPerigo())
              .append("\n\n");
        }
        return sb.toString();
    }
}
