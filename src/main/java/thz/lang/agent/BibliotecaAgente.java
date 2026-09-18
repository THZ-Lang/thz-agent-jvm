package thz.lang.agent;

import thz.lang.agent.rag.ProjectIndexer;
import thz.lang.interpretador.*;

import java.util.Map;

/**
 * BibliotecaAgente — Stdlib AGENTE.* para THZ-LANG.
 * Registra no módulo thz-agent-jvm para evitar dependência circular.
 */
public final class BibliotecaAgente {

    private static String modeloAtual = "";

    private BibliotecaAgente() {}

    public static void registrar(Map<String, BibliotecaPadrao.FuncaoStdlib> m) {

        BibliotecaPadrao.registrarPublico(m, "AGENTE.iniciar", (args, ctx, interp) -> {
            StdlibHelper.exigirAridade("AGENTE.iniciar", args, 1, ctx);
            StdlibHelper.exigirClasse("AGENTE.iniciar", args.get(0), "TEXTO", ctx);
            String caminho = ((ValorThz.Texto) args.get(0)).valor();
            modeloAtual = caminho;
            return ValorThz.TEXTO("Modelo configurado: " + caminho);
        });

        BibliotecaPadrao.registrarPublico(m, "AGENTE.perguntar", (args, ctx, interp) -> {
            StdlibHelper.exigirAridade("AGENTE.perguntar", args, 1, ctx);
            StdlibHelper.exigirClasse("AGENTE.perguntar", args.get(0), "TEXTO", ctx);
            String prompt = ((ValorThz.Texto) args.get(0)).valor();
            String resposta = "[THZ-Agent] Resposta simulada para: "
                + prompt.substring(0, Math.min(50, prompt.length()));
            return ValorThz.TEXTO(resposta);
        });

        BibliotecaPadrao.registrarPublico(m, "AGENTE.rodar", (args, ctx, interp) -> {
            StdlibHelper.exigirAridade("AGENTE.rodar", args, 1, ctx);
            StdlibHelper.exigirClasse("AGENTE.rodar", args.get(0), "TEXTO", ctx);
            String objetivo = ((ValorThz.Texto) args.get(0)).valor();
            return ValorThz.TEXTO("[THZ-Agent] Objetivo: " + objetivo);
        });

        BibliotecaPadrao.registrarPublico(m, "AGENTE.indexar", (args, ctx, interp) -> {
            StdlibHelper.exigirAridade("AGENTE.indexar", args, 1, ctx);
            StdlibHelper.exigirClasse("AGENTE.indexar", args.get(0), "TEXTO", ctx);
            String caminho = ((ValorThz.Texto) args.get(0)).valor();
            int arquivos = ProjectIndexer.indexar(caminho);
            return ValorThz.TEXTO("Projeto indexado: " + arquivos + " arquivos, "
                + ProjectIndexer.totalChunks() + " chunks");
        });

        BibliotecaPadrao.registrarPublico(m, "AGENTE.buscarContexto", (args, ctx, interp) -> {
            StdlibHelper.exigirAridade("AGENTE.buscarContexto", args, 1, ctx);
            StdlibHelper.exigirClasse("AGENTE.buscarContexto", args.get(0), "TEXTO", ctx);
            String query = ((ValorThz.Texto) args.get(0)).valor();
            int topK = args.size() > 1 ? StdlibHelper.comoInteiroArg(args.get(1), ctx).intValue() : 3;
            String resultado = ProjectIndexer.buscarFormatado(query, topK);
            return ValorThz.TEXTO(resultado);
        });

        BibliotecaPadrao.registrarPublico(m, "AGENTE.status", (args, ctx, interp) -> {
            String status = modeloAtual.isEmpty()
                ? "Nenhum modelo carregado"
                : "Modelo: " + modeloAtual;
            return ValorThz.TEXTO(status);
        });
    }
}
