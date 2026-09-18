package thz.lang.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thz.lang.agent.tools.*;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalGateTest {

    private TerminalUI ui;
    private ApprovalGate gateAuto;
    private ApprovalGate gateManual;

    @BeforeEach
    void setUp() {
        ui = new TerminalUI();
        gateAuto = new ApprovalGate(true, ui);
        gateManual = new ApprovalGate(false, ui);
    }

    @Test
    @DisplayName("Modo automático nunca precisa de aprovação")
    void testModoAutomatico() {
        Tool readTool = new ReadFileTool();
        assertFalse(gateAuto.precisaAprovacao(readTool));
    }

    @Test
    @DisplayName("Ferramenta SEGURO nunca precisa de aprovação")
    void testFerramentaSegura() {
        Tool readTool = new ReadFileTool();
        assertFalse(gateManual.precisaAprovacao(readTool));
    }

    @Test
    @DisplayName("Ferramenta MODERADO precisa de aprovação no modo manual")
    void testFerramentaModerada() {
        Tool writeTool = new WriteFileTool();
        assertTrue(gateManual.precisaAprovacao(writeTool));
    }

    @Test
    @DisplayName("Ferramenta PERIGOSO precisa de aprovação no modo manual")
    void testFerramentaPerigosa() {
        Tool execTool = new ExecCommandTool();
        assertTrue(gateManual.precisaAprovacao(execTool));
    }

    @Test
    @DisplayName("Pode alternar modo automático")
    void testAlternarModo() {
        gateManual.setModoAutomatico(true);
        Tool writeTool = new WriteFileTool();
        assertFalse(gateManual.precisaAprovacao(writeTool));

        gateManual.setModoAutomatico(false);
        assertTrue(gateManual.precisaAprovacao(writeTool));
    }

    @Test
    @DisplayName("Deve retornar estado do modo automático")
    void testIsModoAutomatico() {
        assertTrue(gateAuto.isModoAutomatico());
        assertFalse(gateManual.isModoAutomatico());
    }
}
