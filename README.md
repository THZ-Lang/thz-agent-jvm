# thz-agent — Agente Autônomo de Codificação e RAG (Java 25)

Agente autônomo de inteligência artificial em terminal para o ecossistema THZ-LANG, integrado diretamente ao núcleo e CLI (`thz agent`). Opera com ciclo de raciocínio orientado a objetivos (*Agent Loop*), portão de aprovação interativo (*Approval Gate*), memória contextual e suíte de ferramentas de código e sistema de arquivos.

---

## 🌟 Funcionalidades e Destaques

- **Zero-Dependency CLI Experience:** Interface rica em terminal (`TerminalUI`) com cores ANSI, spinner de pensamento, streaming de tokens e histórico persistente de sessões (`SessionMemory`).
- **Segurança com Approval Gate:** Por padrão, qualquer ação com efeitos colaterais no disco (escrita de arquivos, execução de comandos bash/PowerShell e aplicação de diffs) exige confirmação explícita do desenvolvedor (ou modo `--yes` para automação contínua).
- **Indexação RAG Semântica (`ProjectIndexer`):** Mapeamento e busca rápida em arquivos `.thz`, `.thzui`, documentações e manifestos com preservação de contexto estrutural.
- **Backends Flexíveis de IA:**
  - **Local (On-Device):** Gerenciador de processos `llama-server` (`LlamaServerManager`) e download automatizado de pesos quantizados GGUF (`ModelDownloader`).
  - **Nuvem / APIs:** Compatibilidade universal com endpoints compatíveis com OpenAI (`ApiLlmBackend`), Anthropic Claude e servidores locais vLLM / Ollama.

---

## 🛠️ Ferramentas Nativas do Agente (`ToolRegistry`)

| Ferramenta | Descrição |
| :--- | :--- |
| **`read_file`** | Leitura precisa de trechos ou arquivos inteiros com numeração de linhas |
| **`write_file`** | Criação ou sobrescrita atômica de arquivos no workspace |
| **`apply_diff`** | Aplicação de diffs unificados (*patching*) cirúrgicos sem reescrever o arquivo |
| **`list_files`** | Varredura de diretórios com suporte a filtros glob e exclusão de `.git`/`build` |
| **`search_files`** | Busca semântica e textual por palavras-chave em todo o repositório |
| **`exec_command`** | Execução de comandos no shell do sistema operacional (testes, builds, etc.) |

---

## 🚀 Como Utilizar

### 1. Via CLI Global:
```bash
# Iniciar sessão interativa com auto-detecção de modelo local
thz agent

# Com auto-aprovação de ferramentas:
thz agent --yes

# Especificando modelo GGUF local:
thz agent --modelo modelos/qwen2.5-coder-7b.gguf

# Conectando a uma API externa ou OpenAI-compatible:
thz agent --api https://api.openai.com/v1 --api-key sk-... --modelo gpt-4o

# Listar sessões anteriores:
thz agent --sessoes
```

### 2. Via Gradle no Monorepo:
```bash
./gradlew cli --args="agent"
```

---

## 🧱 Arquitetura dos Pacotes

```
JVM/thz-agent-jvm/src/main/java/thz/lang/agent/
├── ThzAgent.java           # Ponto de entrada e parser de argumentos CLI
├── AgentLoop.java          # Loop de raciocínio ReAct (Observe, Plan, Act)
├── ApprovalGate.java       # Portão de segurança e autorização de comandos
├── ToolRegistry.java       # Catálogo centralizado de ferramentas
├── ContextManager.java     # Montagem e compactação de janela de contexto
├── SessionMemory.java      # Armazenamento e persistência de sessões em JSON
├── TerminalUI.java         # Renderização de mensagens, banners e menus interativos
├── tools/                  # Implementação das 6 ferramentas nativas
├── llm/                    # Backends LLM (ApiLlmBackend, LocalLlmBackend, LlamaServerManager)
└── rag/                    # Indexação semântica e busca vetorial de projeto
```

---

## 📦 Dependência do Core

```kotlin
implementation("thz.lang:thz-core:0.4.0")
```
Resolvido via Gradle Composite Build a partir de `../thz-core-jvm`.
