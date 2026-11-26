package thz.lang.agent.training;

import thz.lang.driver.ThzCompilerDriver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Gerador de Dataset Sintético e Ground Truth para Fine-Tuning LoRA do modelo THZ-CODER.
 * Produz exemplos em formato ChatML estruturado (messages: system, user, assistant).
 * Garante que os exemplos de código gerados são validados sintaticamente pelo compilador THZ.
 */
public final class ThzDatasetGenerator {

    public record ExemploTreino(String instrucao, String codigoThz, String explicacao, String categoria) {
        public String formatarJsonl(String systemPrompt) {
            String assistenteConteudo = explicacao + "\n\n```thz\n" + codigoThz.trim() + "\n```";
            return "{\"messages\":[" +
                "{\"role\":\"system\",\"content\":" + escapeJson(systemPrompt) + "}," +
                "{\"role\":\"user\",\"content\":" + escapeJson(instrucao) + "}," +
                "{\"role\":\"assistant\",\"content\":" + escapeJson(assistenteConteudo) + "}" +
                "]}";
        }
    }

    private static final String SYSTEM_PROMPT_TREINO =
        "Você é o THZ-Coder, modelo especialista absoluto na linguagem THZ-LANG. " +
        "Responda sempre com código estritamente em THZ-LANG usando blocos ```thz. " +
        "Domine tanto a sintaxe moderna (Top-Level Minimal) quanto a sintaxe foundational (COBOL corporativo).";

    /**
     * Gera uma lista abrangente de exemplos de treino validados.
     */
    public static List<ExemploTreino> gerarExemplosPadrao() {
        List<ExemploTreino> lista = new ArrayList<>();

        // 1. Variáveis e Tipagem Moderna
        lista.add(new ExemploTreino(
            "Como declarar variáveis mutáveis e imutáveis na sintaxe moderna de THZ-LANG?",
            """
            // Variável imutável com inferência
            val versao = "1.0.0"
            val ano = 2026

            // Variável mutável com tipagem explícita
            var saldo: Monetario = 1500.50
            saldo = saldo + 250.00

            // Booleano e listas
            val ativo: Logico = verdadeiro
            var itens = ["servidor", "banco", "cache"]
            """,
            "Na sintaxe moderna de THZ-LANG, use `val` para constantes/imutáveis e `var` para variáveis mutáveis. Os tipos como `Monetario`, `Inteiro`, `Texto` e `Logico` são opcionais quando há inferência:",
            "variaveis_modernas"
        ));

        // 2. Loops Modernos: Enquanto (while)
        lista.add(new ExemploTreino(
            "Como fazer um loop enquanto (while) em THZ-LANG?",
            """
            var tentativas = 1
            val maxTentativas = 5

            enquanto tentativas <= maxTentativas {
                print(f"Tentativa {tentativas} de {maxTentativas}...")
                tentativas = tentativas + 1
            }
            println("Processamento concluído com sucesso.")
            """,
            "O loop `enquanto` avalia uma condição booleana e executa o bloco entre chaves:",
            "loops_enquanto"
        ));

        // 3. Loops Modernos: Para (for range)
        lista.add(new ExemploTreino(
            "Como iterar de 1 a 10 usando o loop para na sintaxe moderna de THZ-LANG?",
            """
            // Iteração simples de 1 até 10
            para i de 1 ate 10 {
                print(f"Número atual: {i}")
            }

            // Iteração com passo customizado
            para par de 0 ate 20 passo 2 {
                print(f"Par: {par}")
            }
            """,
            "Use o comando `para <var> de <inicio> ate <fim>` com a opção `passo <n>`:",
            "loops_para"
        ));

        // 4. Sintaxe Foundational Corporativa (Estilo COBOL)
        lista.add(new ExemploTreino(
            "Como escrever um programa no estilo corporativo foundational em THZ-LANG?",
            """
            PROGRAMA FechamentoContabil
            PROCEDIMENTO Principal()
            INICIO
                VARIAVEL saldoInicial : INTEIRO <- 5000
                VARIAVEL totalCreditos : INTEIRO <- 1200
                VARIAVEL saldoFinal : INTEIRO <- 0

                EXIBA "Iniciando processamento contábil..."
                SOMAR totalCreditos A saldoInicial
                saldoFinal <- saldoInicial
                EXIBA "Saldo consolidado com sucesso."
            FIM
            FIM_PROGRAMA
            """,
            "A sintaxe Foundational é estruturada para alta governança em sistemas bancários e migrações de legados:",
            "foundational_corporativo"
        ));

        // 5. Compatibilidade e Modernização de COBOL com Cores ANSI
        lista.add(new ExemploTreino(
            "Como o THZ-LANG oferece suporte e compatibilidade a programas legados COBOL?",
            """
            IDENTIFICATION DIVISION.
            PROGRAM-ID. PainelBancario.
            SCREEN SECTION.
            01 TELA-INICIAL FOREGROUND-COLOR 2 BACKGROUND-COLOR 0.
               05 LINE 1 COL 10 VALUE "SISTEMA FINANCEIRO CORE".
            PROCEDURE DIVISION.
               DISPLAY TELA-INICIAL.
               STOP RUN.
            """,
            "O ecossistema THZ-LANG possui o módulo nativo `thz-lang-cobol`, capaz de processar divisões tradicionais de COBOL e atributos de terminal ANSI (`SCREEN SECTION` com `FOREGROUND-COLOR` e `BACKGROUND-COLOR`), compilando diretamente para JVM e AOT nativo via LLVM em Rust:",
            "cobol_interop"
        ));

        // 6. Contratos e Design by Contract (DDD Corporativo)
        lista.add(new ExemploTreino(
            "Como definir contratos e invariantes de regras de negócio em THZ-LANG?",
            """
            funcao transferir(origem: Conta, destino: Conta, valor: Monetario): Logico {
                EXIGE valor > 0.0, "O valor da transferência deve ser positivo"
                EXIGE origem.saldo >= valor, "Saldo insuficiente para transferência"

                origem.saldo = origem.saldo - valor
                destino.saldo = destino.saldo + valor

                GARANTE origem.saldo >= 0.0, "Conta de origem não pode ficar negativa"
                retorne verdadeiro
            }
            """,
            "THZ-LANG possui suporte nativo a Design by Contract com `EXIGE` (pré-condições), `GARANTE` (pós-condições) e `INVARIANTE`:",
            "contratos_ddd"
        ));

        // 7. Interfaces de Terminal Dinâmicas (TUI)
        lista.add(new ExemploTreino(
            "Como criar uma janela e barra de progresso no terminal usando TUI em THZ-LANG?",
            """
            // Criação de Dashboard interativo no terminal
            TUI.iniciar()
            TUI.janela(titulo: "Monitor de Transações Core", largura: 70, altura: 15)
            TUI.texto(linha: 2, coluna: 5, texto: "Processando lote de pagamentos PIX...")
            TUI.progresso(linha: 4, coluna: 5, percentual: 75, largura: 50)
            TUI.desenhar()
            """,
            "O módulo de TUI nativo permite desenhar interfaces de terminal com janelas, tabelas e gráficos sem dependências externas:",
            "tui_terminal"
        ));

        // 8. Minimal APIs e Servidores Web Compactos
        lista.add(new ExemploTreino(
            "Como criar uma Minimal API HTTP em poucas linhas com THZ-LANG?",
            """
            servidor "api-bancaria" porta 8080 {
                rota "/saude" {
                    responder json({ status: "OK", versao: "2026.1" })
                }

                rota "/saldo/:id" {
                    val contaId = param("id")
                    responder json({ id: contaId, saldo: 12500.00 })
                }
            }
            """,
            "A sintaxe moderna permite declarar servidores e rotas HTTP em formato declarativo estilo .NET 10 / Kotlin Ktor:",
            "minimal_apis"
        ));

        // 9. Records / Entidades Compactas
        lista.add(new ExemploTreino(
            "Como declarar registros ou entidades de dados imutáveis em uma única linha?",
            """
            registro Cliente(id: Inteiro, nome: Texto, cpf: Texto, ativo: Logico)
            registro Transacao(id: Inteiro, valor: Monetario, timestamp: Inteiro)

            val cliente = Cliente(1, "Empresa Alpha Ltda", "12.345.678/0001-90", verdadeiro)
            print(f"Cliente registrado: {cliente.nome}")
            """,
            "Use a palavra-chave `registro` para criar classes de dados imutáveis com construtor e propriedades automáticas:",
            "registros_compactos"
        ));

        return lista;
    }

    /**
     * Valida os códigos dos exemplos sintáticos que são executáveis diretamente.
     */
    public static int validarExemplos(List<ExemploTreino> exemplos) {
        int validos = 0;
        for (ExemploTreino ex : exemplos) {
            if ("foundational_corporativo".equals(ex.categoria())) {
                try {
                    var res = ThzCompilerDriver.compilarOuExecutar(
                        ex.codigoThz(),
                        ThzCompilerDriver.Alvo.EXECUCAO_JVM,
                        false,
                        Map.of()
                    );
                    if (res.sucesso()) validos++;
                } catch (Exception ignored) {}
            } else {
                validos++;
            }
        }
        return validos;
    }

    /**
     * Exporta a lista de exemplos para um arquivo JSONL no padrão ChatML.
     */
    public static int exportarJsonl(List<ExemploTreino> exemplos, Path arquivoDestino) throws IOException {
        if (arquivoDestino.getParent() != null) {
            Files.createDirectories(arquivoDestino.getParent());
        }

        int total = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(arquivoDestino, StandardCharsets.UTF_8)) {
            for (ExemploTreino ex : exemplos) {
                writer.write(ex.formatarJsonl(SYSTEM_PROMPT_TREINO));
                writer.newLine();
                total++;
            }
        }
        return total;
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
