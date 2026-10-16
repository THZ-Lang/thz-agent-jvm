package thz.lang.agent.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Higienização e Anti-Repetição do LocalLlmBackend")
class LocalLlmBackendTest {

    @Test
    @DisplayName("Deve truncar parágrafos idênticos repetidos consecutivamente")
    void testTruncarParagrafosRepetidos() {
        String texto = """
            O THZ-LANG é uma linguagem de alto nível moderna e corporativa.

            O THZ-LANG é uma linguagem de alto nível moderna e corporativa.

            O THZ-LANG é uma linguagem de alto nível moderna e corporativa.
            """;

        String resultado = LocalLlmBackend.truncarRepeticoes(texto);
        assertEquals("O THZ-LANG é uma linguagem de alto nível moderna e corporativa.", resultado);
    }

    @Test
    @DisplayName("Deve manter parágrafos diferentes intactos")
    void testManterParagrafosDiferentes() {
        String texto = """
            Primeiro ponto sobre a linguagem THZ-LANG.

            Segundo ponto sobre o módulo thz-lang-cobol.
            """;

        String resultado = LocalLlmBackend.truncarRepeticoes(texto);
        assertTrue(resultado.contains("Primeiro ponto"));
        assertTrue(resultado.contains("Segundo ponto"));
    }

    @Test
    @DisplayName("Deve remover marcadores stop residuais")
    void testRemoverTokensStop() {
        String texto = "Resposta válida.<|im_end|> lixo extra";
        String resultado = LocalLlmBackend.truncarRepeticoes(texto);
        assertEquals("Resposta válida.", resultado);
    }

    @Test
    @DisplayName("Deve truncar ciclo de frases consecutivas em texto contínuo")
    void testTruncarCicloContinuo() {
        String frase = "Esta é uma frase longa o suficiente para ser detectada pelo algoritmo anti-repeticao. ";
        String texto = frase + frase + frase;

        String resultado = LocalLlmBackend.truncarRepeticoes(texto);
        assertEquals(frase.strip(), resultado);
    }

    @Test
    @DisplayName("Deve truncar listas numeradas repetitivas mesmo com índices diferentes")
    void testTruncarListasNumeradasRepetitivas() {
        String texto = """
            1. Exemplo de loop enquanto:
            enquanto i <= 10 { print(i) }

            2. Exemplo de loop para:
            para i de 1 ate 5 { print(i) }

            3. Exemplo de loop para:
            para i de 1 ate 5 { print(i) }

            4. Exemplo de loop para:
            para i de 1 ate 5 { print(i) }
            """;

        String resultado = LocalLlmBackend.truncarRepeticoes(texto);
        assertFalse(resultado.contains("3. Exemplo de loop para:"));
        assertFalse(resultado.contains("4. Exemplo de loop para:"));
        assertTrue(resultado.contains("1. Exemplo de loop enquanto:"));
        assertTrue(resultado.contains("2. Exemplo de loop para:"));
    }
}

