# ADR-001: Estratégia Híbrida de Conhecimento — RAG Dinâmico e LoRA Fine-Tuning para a Família THZ-CODER (1.5B & 3B)

**Status:** Aprovado  
**Data:** 16/10/2026  
**Contexto:** Ecossistema THZ-LANG / Módulo thz-agent-jvm  
**Decisores:** Time de Arquitetura e Engenharia de Compiladores THZ-LANG  

---

## 1. Contexto e Motivação

O **THZ-Agent** foi projetado para ser um assistente de código autônomo, especialista na linguagem THZ-LANG, capaz de operar 100% local em CPUs modestas sem depender de serviços externos (Ollama, APIs comerciais ou GPUs dedicadas).

Durante a fase de validação experimental com modelos de 0.5B, identificamos que:
1. **0.5B é o limite inferior da viabilidade de LLMs:** Útil para tarefas estreitas como classificação de intenção e autocomplete mono-linha, mas insuficiente para sustentar raciocínios encadeados multi-passo (ReAct com chamadas de ferramentas e observação).
2. **Dimensionamento de Parâmetros Ideal:** Modelos na faixa de **1.5B** constituem o piso mínimo viável para seguir formatos estruturados sem degeneração, enquanto modelos de **3B** representam o *sweet spot* absoluto entre consumo de memória CPU (~2.5 GB) e capacidade analítica de código.

Adotamos a base **Qwen2.5-Coder**, atualmente líder incontestável de mercado na faixa de modelos abertos leves para código.

---

## 2. Decisão Arquitetural: Matriz de Modelos e Estratégia Híbrida

A arquitetura adota a separação de responsabilidades em duas camadas (RAG + LoRA) distribuída em dois perfis de modelo:

### Perfis de Modelo Suportados (Base: Qwen2.5-Coder)
| Perfil | Modelo Base HF | Modelo Final GGUF | Consumo RAM | Velocidade CPU | Caso de Uso |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **Padrão (1.5B)** | `Qwen/Qwen2.5-Coder-1.5B-Instruct` | `thz-coder-1.5b-q4_k_m.gguf` | ~1.3 GB | ~25 - 40 tok/s | Padrão do agente em qualquer CPU. Rápido, estável e livre de loops autoregressivos. |
| **Avançado (3B)** | `Qwen/Qwen2.5-Coder-3B-Instruct` | `thz-coder-3b-q4_k_m.gguf` | ~2.5 GB | ~15 - 25 tok/s | Desenvolvimento avançado, modernização de sistemas COBOL completos, TUI e refatorações complexas. |

### Camada 1: RAG Dinâmico Local (Fatos, Documentação e Código do Workspace)
- **Papel:** Ingestão contínua de arquivos `.thz`, `THZ.md`, configurações e documentação viva do projeto.
- **Mecanismo:** Indexação vetorial semântica leve em CPU (`ProjectIndexer` + `ThzIaEngine` de 128 dimensões).
- **Funcionamento:** Antes de gerar respostas ou invocar ferramentas, o `RagContextService` recupera os trechos mais relevantes do repositório e injeta no contexto.

### Camada 2: LoRA Fine-Tuning (Gramática, Vocabulário e Sintaxe Nativa)
- **Papel:** Adaptar as matrizes de atenção da base Qwen2.5-Coder para a sintaxe nativa THZ-LANG (Top-Level Statements e Foundational COBOL).
- **Dataset Ground Truth:** Geração programática via `ThzDatasetGenerator.java`, validada sintaticamente pelo `ThzCompilerDriver`.
- **Resultado Final:** Adaptador fundido e exportado para GGUF `Q4_K_M` (`thz-coder-1.5b-q4_k_m.gguf` e `thz-coder-3b-q4_k_m.gguf`).

---

## 3. Especificação do Pipeline LoRA

1. **Hiperparâmetros de Treinamento:**
   - **LoRA Rank ($r$):** 16
   - **LoRA Alpha ($\alpha$):** 32
   - **Módulos Alvo:** `["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]`
   - **Otimizador:** AdamW 8-bit / paged_adamw_8bit
   - **Learning Rate:** $2 \times 10^{-4}$ com warmup linear e cosine decay
   - **Épocas:** 3
2. **Conversão e Distribuição:**
   - Exportação para GGUF via `llama.cpp/convert_hf_to_gguf.py`.
   - Quantização final `q4_k_m`.
   - Suporte nativo e catálogo em `AgentConfig.java` e `ModelDownloader.java`.

---

## 4. Consequências e Benefícios

- **Raciocínio Sólido e Fluido:** A transição para a base de 1.5B/3B elimina as degenerações de repetição comuns em 0.5B e garante aderência estrita ao ciclo ReAct.
- **Totalmente Local e Autônomo:** Permanece 100% independente de Ollama ou APIs na nuvem, com o `llama-server` nativo gerenciado pelo próprio agente.
- **Flexibilidade de Hardware:** Máquinas modestas utilizam o perfil 1.5B (~1.3 GB RAM); estações de trabalho de desenvolvedores utilizam o perfil 3B (~2.5 GB RAM).
