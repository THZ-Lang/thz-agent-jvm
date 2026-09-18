package thz.lang.agent;

import thz.lang.agent.tools.Tool;

/**
 * Portão de aprovação do usuário.
 * Controla quais ações precisam de aprovação antes de serem executadas.
 */
public final class ApprovalGate {

    private boolean modoAutomatico;
    private final TerminalUI ui;

    public ApprovalGate(boolean modoAutomatico, TerminalUI ui) {
        this.modoAutomatico = modoAutomatico;
        this.ui = ui;
    }

    /** Verifica se uma ação precisa de aprovação */
    public boolean precisaAprovacao(Tool tool) {
        if (modoAutomatico) return false;
        return tool.nivelPerigo() != Tool.NivelPerigo.SEGURO;
    }

    /** Pede aprovação ao usuário para uma ação */
    public boolean pedirAprovacao(String acao, String detalhes) {
        if (modoAutomatico) return true;

        ui.exibirAcaoProposta(acao, detalhes);
        String input = ui.lerInput("  [A]provar / [R]ejeitar / [E]ditar / [S]air: ");

        if (input == null) return false;

        return switch (input.trim().toLowerCase()) {
            case "a", "aprovar", "y", "yes", "sim" -> true;
            case "r", "rejeitar", "n", "no", "nao", "não" -> false;
            case "e", "editar" -> false; // TODO: implementar edição inline
            case "s", "sair" -> throw new SairException();
            default -> false;
        };
    }

    public void setModoAutomatico(boolean automatico) {
        this.modoAutomatico = automatico;
    }

    public boolean isModoAutomatico() {
        return modoAutomatico;
    }

    /** Exceção para sinalizar que o usuário quer sair */
    public static class SairException extends RuntimeException {
        public SairException() {
            super("Usuário solicitou saída");
        }
    }
}
