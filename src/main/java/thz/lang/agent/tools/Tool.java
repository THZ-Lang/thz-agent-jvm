package thz.lang.agent.tools;

/**
 * Interface base para todas as ferramentas do agente.
 * Cada ferramenta pode ser chamada pelo LLM durante o loop ReAct.
 */
public interface Tool {

    /** Nome da ferramenta (usado no "Action: nome(arg)") */
    String nome();

    /** Descrição para o system prompt do LLM */
    String descricao();

    /** Lista de parâmetros (nome + tipo + descrição) */
    String parametrosSchema();

    /** Executa a ferramenta com o argumento fornecido */
    String executar(String args);

    /** Nível de perigo: determina se precisa de aprovação */
    NivelPerigo nivelPerigo();

    enum NivelPerigo {
        /** Leitura apenas — nunca precisa de aprovação */
        SEGURO,
        /** Escrita — precisa de aprovação */
        MODERADO,
        /** Comando/destrutivo — sempre precisa de aprovação */
        PERIGOSO
    }
}
