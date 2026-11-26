package thz.lang.agent.tools;

import thz.lang.driver.ThzCompilerDriver;
import thz.lang.semantico.ErroSemantico;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Ferramenta que compila e executa trechos de código THZ-LANG.
 * Suporta tanto a Sintaxe Foundational (estilo COBOL corporativo)
 * quanto a Sintaxe Moderna (Top-Level statements estilo .NET 10/Kotlin).
 */
public final class ThzEvalTool implements Tool {

    @Override
    public String nome() {
        return "thz_eval";
    }

    @Override
    public String descricao() {
        return "Compila e executa código THZ-LANG (Foundational ou Moderna com Top-Level statements) e retorna o resultado ou erros";
    }

    @Override
    public String parametrosSchema() {
        return "{\"codigo\": \"string contendo o código fonte THZ-LANG a executar\"}";
    }

    @Override
    public NivelPerigo nivelPerigo() {
        return NivelPerigo.SEGURO;
    }

    @Override
    public String executar(String args) {
        String codigo = extrairCodigo(args);
        if (codigo == null || codigo.isBlank()) {
            return "Erro: código THZ-LANG não fornecido para avaliação.";
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(baos, true, StandardCharsets.UTF_8);

        try {
            System.setOut(capture);
            System.setErr(capture);

            ThzCompilerDriver.ResultadoCompilacao res = ThzCompilerDriver.compilarOuExecutar(
                codigo,
                ThzCompilerDriver.Alvo.EXECUCAO_JVM,
                false,
                Map.of()
            );

            capture.flush();
            String stdoutCapturado = baos.toString(StandardCharsets.UTF_8).trim();

            if (!res.sucesso()) {
                StringBuilder sb = new StringBuilder("❌ Falha na compilação do THZ-LANG:\n");
                for (ErroSemantico err : res.erros()) {
                    sb.append(String.format("  • [L%d:C%d] %s%n", err.linha(), err.coluna(), err.mensagem()));
                }
                if (!stdoutCapturado.isEmpty()) {
                    sb.append("\nSaída de diagnóstico:\n").append(stdoutCapturado);
                }
                return sb.toString().trim();
            }

            StringBuilder sb = new StringBuilder("✅ THZ-LANG compilado e executado com sucesso!\n");
            if (!stdoutCapturado.isEmpty()) {
                sb.append("Saída do programa:\n").append(stdoutCapturado).append("\n");
            }
            if (res.resultadoExecucao() != null && !(res.resultadoExecucao() instanceof thz.lang.interpretador.ValorThz.Nulo)) {
                sb.append("Valor de retorno: ").append(res.resultadoExecucao());
            } else if (stdoutCapturado.isEmpty()) {
                sb.append("(Executado sem saída de console)");
            }

            return sb.toString().trim();

        } catch (Throwable t) {
            capture.flush();
            String stdoutCapturado = baos.toString(StandardCharsets.UTF_8).trim();
            StringBuilder sb = new StringBuilder("❌ Erro durante a execução do THZ-LANG: " + t.getMessage() + "\n");
            if (!stdoutCapturado.isEmpty()) {
                sb.append("Saída parcial:\n").append(stdoutCapturado);
            }
            return sb.toString().trim();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    static String extrairCodigo(String args) {
        if (args == null) return null;
        String trimmed = args.trim();

        // Se veio em formato JSON {"codigo": "..."}
        if (trimmed.startsWith("{") && trimmed.contains("\"codigo\"")) {
            int idx = trimmed.indexOf("\"codigo\"");
            int colon = trimmed.indexOf(':', idx + 8);
            if (colon != -1) {
                int start = colon + 1;
                while (start < trimmed.length() && Character.isWhitespace(trimmed.charAt(start))) start++;
                if (start < trimmed.length() && trimmed.charAt(start) == '"') {
                    start++;
                    int end = start;
                    while (end < trimmed.length()) {
                        if (trimmed.charAt(end) == '\\' && end + 1 < trimmed.length()) {
                            end += 2;
                            continue;
                        }
                        if (trimmed.charAt(end) == '"') break;
                        end++;
                    }
                    if (end < trimmed.length()) {
                        return trimmed.substring(start, end)
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\t", "\t")
                            .replace("\\\\", "\\");
                    }
                }
            }
        }

        // Se veio no formato codigo=...
        if (trimmed.startsWith("codigo=")) {
            trimmed = trimmed.substring(7).trim();
        }

        // Se veio com aspas simples ou duplas ao redor
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
        }

        return trimmed;
    }
}
