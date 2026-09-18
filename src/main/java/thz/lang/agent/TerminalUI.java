package thz.lang.agent;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Interface terminal do THZ-Agent.
 * Gerencia entrada/saída colorida no terminal.
 */
public final class TerminalUI {

    // Cores ANSI
    private static final String RESET  = "\033[0m";
    private static final String RED    = "\033[31m";
    private static final String GREEN  = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE   = "\033[34m";
    private static final String CYAN   = "\033[36m";
    private static final String DIM    = "\033[2m";
    private static final String BOLD   = "\033[1m";

    private final BufferedReader input;
    private final PrintStream output;

    public TerminalUI() {
        this.input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.output = System.out;
    }

    public void exibirBanner() {
        output.println();
        output.println(CYAN + BOLD + "  ╔══════════════════════════════════════╗" + RESET);
        output.println(CYAN + BOLD + "  ║        THZ-AGENT v0.4.0              ║" + RESET);
        output.println(CYAN + BOLD + "  ║  Assistente de Código Inteligente    ║" + RESET);
        output.println(CYAN + BOLD + "  ╚══════════════════════════════════════╝" + RESET);
        output.println();
    }

    public void exibirPrompt() {
        output.print(CYAN + "thz-agent> " + RESET);
        output.flush();
    }

    public void exibirResposta(String texto) {
        output.println();
        output.println(GREEN + texto + RESET);
        output.println();
    }

    public void exibirPensamento(String thought) {
        output.println(DIM + "  Thought: " + thought + RESET);
    }

    public void exibirAcaoProposta(String acao, String detalhes) {
        output.println();
        output.println(YELLOW + "  Ação proposta: " + BOLD + acao + RESET);
        output.println(DIM + "  " + detalhes + RESET);
    }

    public void exibirObservacao(String obs) {
        output.println(DIM + "  Observation: " + obs.substring(0, Math.min(obs.length(), 200)) + RESET);
    }

    public void exibirErro(String msg) {
        output.println(RED + "  Erro: " + msg + RESET);
    }

    public void exibirInfo(String msg) {
        output.println(BLUE + "  " + msg + RESET);
    }

    public void exibirCompacting() {
        output.println(DIM + "  [Compactando contexto...]" + RESET);
    }

    public String lerInput(String prompt) {
        output.print(CYAN + prompt + RESET);
        output.flush();
        try {
            return input.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    public String lerLinha() {
        try {
            return input.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
