import argparse
import json
import os
from pathlib import Path

import torch
from torch.utils.data import Dataset, DataLoader
from transformers import AutoModelForCausalLM, AutoTokenizer


class SimpleDataset(Dataset):
    def __init__(self, path, tokenizer, max_length=128):
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        self.examples = data
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self):
        return len(self.examples)

    def __getitem__(self, idx):
        item = self.examples[idx]
        if isinstance(item, dict):
            text = f"Usuario: {item.get('prompt', '')}\nAsistente: {item.get('completion', item.get('response', ''))}"
        else:
            text = item
        enc = self.tokenizer(text, truncation=True, max_length=self.max_length, padding='max_length', return_tensors='pt')
        return {
            'input_ids': enc['input_ids'].squeeze(0),
            'attention_mask': enc['attention_mask'].squeeze(0),
            'labels': enc['input_ids'].squeeze(0),
        }


def run_quick_train(model_name, dataset_path, output_dir, epochs=1, batch_size=2, lr=5e-5):
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"Device: {device}")

    tokenizer = AutoTokenizer.from_pretrained(model_name)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(model_name)
    model.to(device)
    model.train()

    dataset = SimpleDataset(dataset_path, tokenizer, max_length=128)
    dataloader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    optimizer = torch.optim.AdamW(model.parameters(), lr=lr)

    for epoch in range(epochs):
        print(f"Epoch {epoch+1}/{epochs}")
        for i, batch in enumerate(dataloader):
            input_ids = batch['input_ids'].to(device)
            attention_mask = batch['attention_mask'].to(device)
            labels = batch['labels'].to(device)

            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels)
            loss = outputs.loss

            loss.backward()
            optimizer.step()
            optimizer.zero_grad()

            if i % 10 == 0:
                print(f"  step {i} loss={loss.item():.4f}")

    os.makedirs(output_dir, exist_ok=True)
    save_path = os.path.join(output_dir, 'final_quick')
    model.save_pretrained(save_path)
    tokenizer.save_pretrained(save_path)
    print(f"Saved quick model to {save_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--model', default='distilgpt2')
    parser.add_argument('--dataset', required=True)
    parser.add_argument('--output', default='models/checkpoints/quick_test')
    parser.add_argument('--epochs', type=int, default=1)
    parser.add_argument('--batch-size', type=int, default=2)
    args = parser.parse_args()

    run_quick_train(args.model, args.dataset, args.output, epochs=args.epochs, batch_size=args.batch_size)


if __name__ == '__main__':
    main()
