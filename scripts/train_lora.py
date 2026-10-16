#!/usr/bin/env python3
"""
Pipeline de Fine-Tuning LoRA para o modelo THZ-CODER (0.5B) e conversão para GGUF.
Base: Qwen/Qwen2.5-Coder-0.5B-Instruct
Alvo: thz-coder-0.5b-q4_k_m.gguf para execução local ultra-rápida em CPU.

Uso:
  python train_lora.py --dataset ../build/datasets/thz_instruct_train.jsonl
"""

import os
import sys
import argparse
from pathlib import Path

def parse_args():
    parser = argparse.ArgumentParser(description="Treinamento LoRA para THZ-CODER-0.5B")
    parser.add_argument("--dataset", type=str, default="../build/datasets/thz_instruct_train.jsonl",
                        help="Caminho para o dataset de treino em formato JSONL ChatML")
    parser.add_argument("--base_model", type=str, default="Qwen/Qwen2.5-Coder-0.5B-Instruct",
                        help="Modelo base no HuggingFace ou diretório local")
    parser.add_argument("--output_dir", type=str, default="../build/lora_adapter",
                        help="Diretório de saída para salvar o adaptador LoRA")
    parser.add_argument("--merged_dir", type=str, default="../build/thz_coder_merged",
                        help="Diretório para salvar o modelo consolidado antes de exportar para GGUF")
    parser.add_argument("--epochs", type=int, default=3, help="Número de épocas de treino")
    parser.add_argument("--lr", type=float, default=2e-4, help="Learning rate")
    parser.add_argument("--dry_run", action="store_true", help="Valida dados e dependências sem executar o treino completo")
    return parser.parse_args()

def verificar_dependencias():
    try:
        import torch
        import transformers
        import datasets
        import peft
        import trl
        print(f"[OK] PyTorch {torch.__version__} | Transformers {transformers.__version__} | PEFT {peft.__version__}")
        print(f"[Info] CUDA Disponível: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            print(f"[Info] Dispositivo GPU: {torch.cuda.get_device_name(0)}")
        else:
            print("[Aviso] GPU não detectada. O treino prosseguirá em CPU (mais lento) ou utilize o Google Colab.")
        return True
    except ImportError as e:
        print(f"[Erro de Dependência] {e}")
        print("Instale os pré-requisitos executando:")
        print("  pip install torch transformers datasets peft trl bitsandbytes accelerate")
        return False

def main():
    args = parse_args()
    print("=" * 60)
    print("  THZ-CODER: Treinamento LoRA e Export GGUF")
    print("=" * 60)
    print(f"Dataset:    {args.dataset}")
    print(f"Base Model: {args.base_model}")
    print(f"Saída LoRA: {args.output_dir}")
    print(f"Merged:     {args.merged_dir}")
    print("=" * 60)

    dataset_path = Path(args.dataset)
    if not dataset_path.exists():
        print(f"[Erro] Arquivo de dataset não encontrado: {dataset_path}")
        print("Gere o dataset executando o THZ-Agent:")
        print("  .\\thz-agent.ps1 export-dataset")
        sys.exit(1)

    if not verificar_dependencias():
        sys.exit(1)

    import torch
    from datasets import load_dataset
    from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments
    from peft import LoraConfig, get_peft_model
    from trl import SFTTrainer

    # 1. Carregar Dataset ChatML
    print(f"\n[1/4] Carregando dataset: {dataset_path}")
    raw_dataset = load_dataset("json", data_files=str(dataset_path), split="train")
    print(f"  Total de exemplos carregados: {len(raw_dataset)}")

    if args.dry_run:
        print("\n[Dry Run] Dataset verificado com sucesso. Encerrando sem treinar.")
        return

    # 2. Carregar Tokenizer e Modelo Base
    print(f"\n[2/4] Carregando modelo base: {args.base_model}")
    tokenizer = AutoTokenizer.from_pretrained(args.base_model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    device_map = "auto" if torch.cuda.is_available() else "cpu"
    torch_dtype = torch.float16 if torch.cuda.is_available() else torch.float32

    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        torch_dtype=torch_dtype,
        device_map=device_map,
        trust_remote_code=True
    )

    # 3. Configurar LoRA
    print("\n[3/4] Configurando adaptadores LoRA (Rank=16, Alpha=32)...")
    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # 4. Treinar
    print("\n[4/4] Iniciando SFTTrainer...")
    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=2,
        gradient_accumulation_steps=4,
        learning_rate=args.lr,
        lr_scheduler_type="cosine",
        warmup_ratio=0.1,
        logging_steps=5,
        save_strategy="epoch",
        fp16=torch.cuda.is_available(),
        optim="adamw_torch"
    )

    trainer = SFTTrainer(
        model=model,
        train_dataset=raw_dataset,
        args=training_args,
        peft_config=lora_config
    )

    trainer.train()

    # 5. Salvar Adaptador
    print(f"\nSalvando adaptador LoRA em: {args.output_dir}")
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)

    # 6. Merge dos Pesos para Exportação GGUF
    print(f"\nConsolidando pesos (Merge LoRA + Base) em: {args.merged_dir}...")
    merged_model = model.merge_and_unload()
    merged_model.save_pretrained(args.merged_dir)
    tokenizer.save_pretrained(args.merged_dir)

    print("\n" + "=" * 60)
    print("  TREINAMENTO CONCLUÍDO COM SUCESSO!")
    print("=" * 60)
    print("Para converter o modelo consolidado para GGUF para uso no THZ-Agent:")
    print(f"  python llama.cpp/convert_hf_to_gguf.py {args.merged_dir} --outfile thz-coder-0.5b-f16.gguf")
    print("  llama-quantize thz-coder-0.5b-f16.gguf thz-coder-0.5b-q4_k_m.gguf Q4_K_M")
    print("  copy thz-coder-0.5b-q4_k_m.gguf %USERPROFILE%\\.thz\\models\\thz-coder-0.5b-q4_k_m.gguf")
    print("=" * 60)

if __name__ == "__main__":
    main()
