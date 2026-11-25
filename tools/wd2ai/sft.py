"""Response-only Qwen3 training on reviewed WD2 plan decisions using local Unsloth."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path

from base_model import REVISION, TEMPLATE_SHA256, WEIGHTS_SHA256

CONTEXT = 2048
SEED = 3407


def encode_example(tokenizer, record):
    messages = record["messages"]
    if [m["role"] for m in messages] != ["system", "user", "assistant"]:
        raise ValueError("Expected a single system, user, assistant exchange")
    target = messages[-1]["content"]
    selection = json.loads(target)
    if (set(selection) != {"plan_id"} or not re.fullmatch(r"P[0-9]{2}", selection["plan_id"])
            or target != json.dumps(selection, separators=(",", ":"))):
        raise ValueError("Expected compact plan JSON")
    prefix = tokenizer.apply_chat_template(messages[:2], tokenize=False,
                                           add_generation_prompt=True, enable_thinking=False)
    full = tokenizer.apply_chat_template(messages, tokenize=False, enable_thinking=False)
    if full != prefix + target + tokenizer.eos_token + "\n":
        raise ValueError("Training and inference chat templates disagree")
    prefix_ids = tokenizer.encode(prefix, add_special_tokens=False)
    # Final newline belongs to the next message. Supervise the JSON and its terminator.
    ids = tokenizer.encode(full[:-1], add_special_tokens=False)
    if (ids[:len(prefix_ids)] != prefix_ids or ids[-1] != tokenizer.eos_token_id
            or tokenizer.decode(ids[len(prefix_ids):-1], skip_special_tokens=False) != target):
        raise ValueError("Assistant token boundary or EOS mismatch")
    if len(ids) > CONTEXT:
        raise ValueError(f"Decision exceeds context ({len(ids)} > {CONTEXT})")
    return {"input_ids": ids, "attention_mask": [1] * len(ids),
            "labels": [-100] * len(prefix_ids) + ids[len(prefix_ids):]}


def file_hash(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def records(path):
    with path.open(encoding="utf8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


def train(args):
    # Import Unsloth before transformers so its model and kernel patches are active.
    from unsloth import FastLanguageModel
    import torch
    from datasets import Dataset
    from transformers import (AutoTokenizer, DataCollatorForSeq2Seq, Trainer,
                              TrainingArguments, set_seed)

    if file_hash(args.base / "model.safetensors") != WEIGHTS_SHA256 or file_hash(
            args.base / "tokenizer_config.json") != TEMPLATE_SHA256:
        raise ValueError("Provision the pinned stock checkpoint with base_model.py first")
    if args.out.exists() and any(args.out.iterdir()) and not args.resume:
        raise ValueError("Output already exists; use --resume or a new directory")
    args.out.mkdir(parents=True, exist_ok=True)
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True)
    train_path = args.dataset / "train.jsonl"
    val_path = args.dataset / "validation.jsonl"
    train_rows = records(train_path)
    validation = [] if args.smoke else records(val_path)
    if not train_rows:
        raise ValueError("No real decisions supplied")
    if not args.smoke:
        manifest = json.loads((args.dataset / "report.json").read_text(encoding="utf8"))
        for split, count in (("train", 1600), ("validation", 200), ("test", 200)):
            path = args.dataset / f"{split}.jsonl"
            if manifest["counts"][split] != count or manifest["sha256"][split] != file_hash(path):
                raise ValueError(f"Split provenance or count mismatch for {split}")
        if len(train_rows) != 1600 or len(validation) != 200:
            raise ValueError("Expected the complete reviewed dataset")
    training_rows = [encode_example(tokenizer, row) for row in train_rows]
    validation_rows = [encode_example(tokenizer, row) for row in validation]
    supervised = [sum(token != -100 for token in row["labels"]) for row in training_rows]
    example = next(iter(training_rows))
    metadata = {"base": REVISION, "base_sha256": WEIGHTS_SHA256, "template_sha256": TEMPLATE_SHA256,
                "train_sha256": file_hash(train_path), "seed": SEED, "smoke": args.smoke,
                "train_count": len(training_rows), "validation_count": len(validation_rows),
                "supervised_range": [min(supervised), max(supervised)],
                "first_supervised_text": tokenizer.decode([t for t in example["labels"] if t != -100]),
                "max_train_tokens": max(len(row["input_ids"]) for row in training_rows),
                "device": torch.cuda.get_device_name(), "cuda": torch.version.cuda}
    record_path = args.out / "run.json"
    if args.resume and json.loads(record_path.read_text()) != metadata:
        raise ValueError("Resume metadata differs from the original run")
    record_path.write_text(json.dumps(metadata, indent=2), encoding="utf8")
    set_seed(SEED)
    bf16 = torch.cuda.is_bf16_supported()
    model, _ = FastLanguageModel.from_pretrained(
        model_name=str(args.base.resolve()), max_seq_length=CONTEXT, load_in_4bit=False,
        dtype=torch.bfloat16 if bf16 else torch.float16, local_files_only=True)
    model = FastLanguageModel.get_peft_model(
        model, r=16, lora_alpha=32, lora_dropout=0, random_state=SEED,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        use_gradient_checkpointing="unsloth")
    trainer_args = TrainingArguments(
        output_dir=str(args.out), num_train_epochs=1, max_steps=1 if args.smoke else -1,
        per_device_train_batch_size=1 if args.smoke else 2, gradient_accumulation_steps=1 if args.smoke else 4,
        per_device_eval_batch_size=1, learning_rate=1e-4, warmup_ratio=0.04,
        logging_steps=1 if args.smoke else 5, save_steps=1 if args.smoke else 25,
        eval_strategy="no" if args.smoke else "steps", eval_steps=25,
        load_best_model_at_end=not args.smoke, metric_for_best_model="eval_loss",
        report_to="none", seed=SEED, bf16=bf16, fp16=not bf16, dataloader_pin_memory=False)
    trainer = Trainer(model=model, args=trainer_args, train_dataset=Dataset.from_list(training_rows),
                      eval_dataset=Dataset.from_list(validation_rows) if validation_rows else None,
                      data_collator=DataCollatorForSeq2Seq(tokenizer, label_pad_token_id=-100))
    result = trainer.train(resume_from_checkpoint=True if args.resume else None)
    trainer.save_state()
    trainer.save_model(str(args.out / "adapter"))
    tokenizer.save_pretrained(args.out / "adapter")
    (args.out / "metrics.json").write_text(json.dumps(result.metrics, indent=2))
    FastLanguageModel.for_inference(model)
    feature = training_rows[0]
    start = feature["labels"].index(next(t for t in feature["labels"] if t != -100))
    inputs = torch.tensor([feature["input_ids"][:start]], device=model.device)
    with torch.inference_mode():
        generated = model.generate(inputs, attention_mask=torch.ones_like(inputs), do_sample=False,
                                   max_new_tokens=20, pad_token_id=tokenizer.eos_token_id)
    (args.out / "sample.json").write_text(json.dumps({"raw_output": tokenizer.decode(
        generated[0, start:], skip_special_tokens=False)}), encoding="utf8")
    if args.merge:
        model.save_pretrained_merged(str(args.out / "merged"), tokenizer, save_method="merged_16bit")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    for arg in ("base", "dataset", "out"):
        p.add_argument("--" + arg, type=Path, required=True)
    p.add_argument("--smoke", action="store_true")
    p.add_argument("--resume", action="store_true")
    p.add_argument("--merge", action="store_true")
    os.environ.setdefault("WANDB_DISABLED", "true")
    train(p.parse_args())

