"""Response-only Qwen3 training on reviewed WD2 plan decisions using local Unsloth."""
import argparse
import hashlib
from importlib.metadata import version
import json
import os
import re
from pathlib import Path

from base_model import REVISION, TEMPLATE_SHA256, WEIGHTS_SHA256
from corpus import verify_dataset

CONTEXT = 2048
DEPLOYMENT_OUTPUT_BUDGET = 20
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
    required = max(len(ids), len(prefix_ids) + DEPLOYMENT_OUTPUT_BUDGET)
    if required > CONTEXT:
        raise ValueError(f"Decision exceeds deployment context ({required} > {CONTEXT})")
    return {"input_ids": ids, "attention_mask": [1] * len(ids),
            "labels": [-100] * len(prefix_ids) + ids[len(prefix_ids):]}


def file_hash(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def records(path):
    with path.open(encoding="utf8") as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line)


def tokenized_rows(path, tokenizer, limit=None):
    from itertools import islice
    for row in islice(records(path), limit):
        features = encode_example(tokenizer, row)
        features.pop("attention_mask")
        features["length"] = len(features["input_ids"])
        features["supervised_length"] = sum(token != -100 for token in features["labels"])
        yield features


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
    if not args.smoke:
        verify_dataset(args.dataset)
    from datasets import Features, Sequence, Value
    schema = Features({"input_ids": Sequence(Value("int32")), "labels": Sequence(Value("int32")),
                       "length": Value("int32"), "supervised_length": Value("int32")})
    def load(path, limit=None):
        return Dataset.from_generator(tokenized_rows, features=schema,
                                      gen_kwargs={"path": path, "tokenizer": tokenizer, "limit": limit},
                                      cache_dir=str(args.out / "token-cache"))
    training_rows = load(train_path, 128 if args.smoke else None)
    validation_rows = None if args.smoke else load(val_path)
    supervised = training_rows["supervised_length"]
    example = training_rows[0]
    metadata = {"base": REVISION, "base_sha256": WEIGHTS_SHA256, "template_sha256": TEMPLATE_SHA256,
                "train_sha256": file_hash(train_path), "seed": SEED, "smoke": args.smoke,
                "train_count": len(training_rows), "validation_count": len(validation_rows) if validation_rows else 0,
                "supervised_range": [min(supervised), max(supervised)],
                "first_supervised_text": tokenizer.decode([t for t in example["labels"] if t != -100]),
                "max_train_tokens": max(training_rows["length"]),
                "per_device_batch": args.train_batch, "gradient_accumulation": args.gradient_steps,
                "length_bucketing": args.length_bucketing, "benchmark_steps": args.benchmark_steps,
                "checkpoint_steps": args.checkpoint_steps,
                "device": torch.cuda.get_device_name(), "cuda": torch.version.cuda,
                "validation_sha256": None if args.smoke else file_hash(val_path),
                "packages": {name: version(name) for name in ("unsloth", "unsloth-zoo", "torch", "transformers",
                            "trl", "peft", "datasets", "bitsandbytes")}}
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
    # Keep eight evaluations per epoch for both the original and expanded experiment.
    effective_batch = args.train_batch * args.gradient_steps
    checkpoint_steps = args.checkpoint_steps or max(1, len(training_rows) // (effective_batch * 8))
    max_steps = (args.benchmark_steps or 1) if args.smoke else -1
    trainer_args = TrainingArguments(
        output_dir=str(args.out), num_train_epochs=1, max_steps=max_steps,
        per_device_train_batch_size=args.train_batch, gradient_accumulation_steps=args.gradient_steps,
        per_device_eval_batch_size=2, learning_rate=1e-4, warmup_ratio=0.04,
        logging_steps=1 if args.smoke else 10, save_steps=max_steps if args.smoke else checkpoint_steps,
        save_total_limit=3, train_sampling_strategy="group_by_length" if args.length_bucketing else "random",
        eval_strategy="no" if args.smoke else "steps", eval_steps=checkpoint_steps,
        load_best_model_at_end=not args.smoke, metric_for_best_model="eval_loss",
        report_to="none", seed=SEED, bf16=bf16, fp16=not bf16, dataloader_pin_memory=False)
    trainer = Trainer(model=model, args=trainer_args, train_dataset=training_rows,
                      eval_dataset=validation_rows, data_collator=DataCollatorForSeq2Seq(
                          tokenizer, label_pad_token_id=-100, pad_to_multiple_of=8))
    result = trainer.train(resume_from_checkpoint=True if args.resume else None)
    trainer.save_state()
    (args.out / "configuration.json").write_text(json.dumps({
        "lora_rank": 16, "lora_alpha": 32, "sequence_length": CONTEXT,
        "precision": "bfloat16" if bf16 else "float16", "seed": SEED,
        "learning_rate": trainer_args.learning_rate, "epochs": trainer_args.num_train_epochs,
        "per_device_batch": trainer_args.per_device_train_batch_size,
        "gradient_accumulation": trainer_args.gradient_accumulation_steps,
        "selected_checkpoint": trainer.state.best_model_checkpoint,
        "best_validation_loss": trainer.state.best_metric}, indent=2), encoding="utf8")
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
    p.add_argument("--train-batch", type=int)
    p.add_argument("--gradient-steps", type=int)
    p.add_argument("--length-bucketing", action="store_true")
    p.add_argument("--benchmark-steps", type=int)
    p.add_argument("--checkpoint-steps", type=int)
    os.environ.setdefault("WANDB_DISABLED", "true")
    options = p.parse_args()
    if options.train_batch is None:
        options.train_batch = 1 if options.smoke else 2
    if options.gradient_steps is None:
        options.gradient_steps = 1 if options.smoke else 4
    if (options.train_batch < 1 or options.gradient_steps < 1 or
            options.checkpoint_steps is not None and options.checkpoint_steps < 1 or
            options.benchmark_steps is not None and (not options.smoke or options.benchmark_steps < 1)):
        p.error("Use positive batch sizes and only benchmark during a smoke run")
    train(options)
