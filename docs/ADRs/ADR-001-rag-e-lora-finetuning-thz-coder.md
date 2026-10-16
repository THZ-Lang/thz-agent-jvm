# ADR-001: Estratégia Híbrida de Conhecimento — RAG Dinâmico e LoRA Fine-Tuning para o Modelo THZ-CODER-0.5B

**Status:** Aprovado  
**Data:** 16/10/2026  
**Contexto:** Ecossistema THZ-LANG / Módulo thz-agent-jvm  
**Decisores:** Time de Arquitetura e Engenharia de Compiladores THZ-LANG  

---

## 1. Contexto e Motivação

O **THZ-Agent** foi projetado para ser um assistente de código autônomo, especialista na linguagem THZ-LANG, capaz de operar 100% local em CPUs modestas sem depender de serviços externos (Ollama, APIs comerciais ou GPUs dedicadas).

Para atingir esse objetivo com um modelo ultracompacto (~0.5 bilhão de parâmetros, `qwen2.5-coder-0.5b-instruct-q4_k_m.gguf`), enfrentamos dois desafios essenciais:
1. **Gramática e Sintaxe Inédita:** O modelo base foi treinado primariamente em linguagens tradicionais (Python, C, Java, Rust) e não possui a representação estatística nativa da sintaxe moderna (Top-Level Statements estilo .NET 10, inferência `val`/`var`, `enquanto`, `para i de 1 ate 10`, TUI) e da sintaxe foundational/COBOL da THZ-LANG.
2. **Capacidade de Contexto Reduzida (SLM):** Modelos de 0.5B possuem menor capacidade de reter instruções longas inseridas exclusivamente via System Prompt, tendendo à alucinação de outras linguagens ou regurgitação de cabeçalhos caso o prompt fique sobrecarregado.

Precisamos de uma solução arquitetural definitiva para capacitar o modelo tanto em **fatos dinâmicos do projeto** quanto em **gramática nativa fluente**.

---

## 2. Decisão Arquitetural: A Estratégia Híbrida (RAG + LoRA)

Adotamos a separação de responsabilidades em duas camadas complementares:

### Camada 1: RAG Dinâmico Local (Para Fatos, Documentação e Código do Workspace)
- **Papel:** Ingestão contínua de arquivos `.thz`, `THZ.md`, configurações e documentação viva do projeto.
- **Mecanismo:** Indexação vetorial semântica leve em CPU (`ProjectIndexer` + `ThzIaEngine` de 128 dimensões).
- **Vantagem:** Atualização instantânea sem necessidade de reprocessar pesos toda vez que o código ou as regras do projeto mudam.
- **Funcionamento:** Antes de gerar uma resposta ou raciocinar sobre ferramentas, o `RagContextService` recupera os trechos mais relevantes do repositório e injeta no contexto.

### Camada 2: LoRA Fine-Tuning (Para Gramática, Vocabulário e Sintaxe Nativa)
- **Papel:** Modificar as matrizes de atenção do modelo para que a sintaxe THZ-LANG seja a sua "língua materna", gerando blocos ````thz```` espontaneamente com taxa zero de alucinação para C#/Python.
- **Mecanismo:** Fine-Tuning com adaptadores de baixo rank (LoRA / QLoRA) sobre o modelo base `Qwen2.5-Coder-0.5B-Instruct`.
- **Dataset Ground Truth:** Geração programática de 1.000 a 3.000 pares de instrução e código validados pelo próprio `ThzCompilerDriver` (garantia de 100% de correção sintática).
- **Resultado Final:** O adapter treinado é fundido (*merged*) aos pesos originais e convertido em um artefato `.gguf` quantizado em Q4_K_M: **`thz-coder-0.5b-q4_k_m.gguf`**.

---

## 3. Especificação do Pipeline LoRA

1. **Dataset Sintético e Ground Truth (`ThzDatasetGenerator.java`):**
   - Pares no padrão ChatML (`system`, `user`, `assistant`).
   - Categorias cobertas:
     - Declaração de variáveis (`val` imutável, `var` mutável, tipagem explícita `Monetario`, `Inteiro`, `Texto`).
     - Estruturas de repetição modernas (`enquanto`, `para ... ate`, `para ... passo`).
     - Estruturas Foundational Corporativas (`PROGRAMA`, `PROCEDIMENTO`, `VARIAVEL`, `EXIBA`, `SOMAR A`).
     - Compatibilidade COBOL (`SCREEN SECTION`, cores ANSI, transições bancárias).
     - Contratos e DDD (`EXIGE`, `GARANTE`, `INVARIANTE`).
     - Dashboards TUI (`TUI.janela`, `TUI.progresso`, `TUI.tabela`).
     - Minimal APIs (`servidor`, `rota`, `responder`).
2. **Hiperparâmetros de Treinamento:**
   - **Base:** `Qwen/Qwen2.5-Coder-0.5B-Instruct`
   - **LoRA Rank ($r$):** 16
   - **LoRA Alpha ($\alpha$):** 32
   - **Módulos Alvo:** `["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]`
   - **Otimizador:** AdamW 8-bit / paged_adamw_8bit
   - **Learning Rate:** $2 \times 10^{-4}$ com warmup linear e cosine decay
   - **Épocas:** 3
3. **Conversão e Distribuição:**
   - Exportação para GGUF via `llama.cpp/convert_hf_to_gguf.py`.
   - Quantização final `q4_k_m`.
   - Disponibilização no HuggingFace oficial do ecossistema e suporte nativo no `ModelDownloader.java`.

---

## 4. Consequências e Benefícios

- **Precisão Factual e Sintática:** O modelo não confunde mais sintaxes de linguagens terceiras, pois o vocabulário THZ-LANG se torna intrínseco aos pesos.
- **Economia de Contexto:** Redução de até 70% do tamanho do System Prompt, pois a sintaxe não precisa mais ser explicada exaustivamente via prompt injection.
- **Portabilidade Total:** O modelo final continua pesando ~400 MB e roda em milissegundos em CPU pura no Windows, Linux e macOS.
