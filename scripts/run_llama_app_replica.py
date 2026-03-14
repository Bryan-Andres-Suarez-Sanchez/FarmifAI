#!/usr/bin/env python3
"""
Runner de terminal que replica el flujo de la app (RAG + decisiones + LLM).

Backend LLM soportado:
- llama-server (OpenAI-compatible): POST /v1/completions
- llama-server legacy:            POST /completion

Uso rapido:
  python3 scripts/run_llama_app_replica.py --help
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import requests
from difflib import SequenceMatcher


PROJECT_ROOT = Path(__file__).resolve().parents[1]
KB_PATH = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "agrochat_knowledge_base.json"

SIMILARITY_THRESHOLD = 0.40


@dataclass
class RunnerConfig:
    max_tokens: int = 450
    similarity_threshold: float = 0.45
    kb_fast_path_threshold: float = 0.70
    context_relevance_threshold: float = 0.50
    use_llm_for_all: bool = False
    context_length: int = 1800
    detect_greetings: bool = True
    chat_history_enabled: bool = True
    chat_history_size: int = 10
    top_k: int = 3
    strict_app_mode: bool = True
    system_prompt: str = (
        "Eres FarmifAI, un asistente agricola experto. "
        "Si se proporciona contexto de KB, usalo como fuente principal y no inventes "
        "datos fuera de esa base. Si falta un dato en la KB, dilo explicitamente."
    )


@dataclass
class KBEntry:
    entry_id: int
    category: str
    questions: List[str]
    answer: str


@dataclass
class MatchResult:
    answer: str
    matched_question: str
    similarity_score: float
    category: str
    entry_id: int


@dataclass
class ContextResult:
    contexts: List[MatchResult]
    combined_context: str


class SemanticSearchReplica:
    _DISCOURSE_PREFIXES = [
        re.compile(r"^y\s+que\s+me\s+dices\s+de\s+"),
        re.compile(r"^que\s+me\s+dices\s+de\s+"),
        re.compile(r"^me\s+puedes\s+hablar\s+de\s+"),
        re.compile(r"^hablame\s+de\s+"),
        re.compile(r"^dime\s+sobre\s+"),
        re.compile(r"^acerca\s+de\s+"),
        re.compile(r"^sobre\s+"),
        re.compile(r"^amplia\s+y\s+continua\s+la\s+explicacion\s+sobre\s+"),
        re.compile(r"^mas\s+sobre\s+"),
    ]
    _STOP_WORDS = {
        "de", "del", "la", "las", "el", "los", "y", "o", "a", "en", "con",
        "por", "para", "al", "un", "una", "unos", "unas", "que", "me", "te",
        "se", "mi", "tu", "su", "sobre", "acerca", "dices",
    }
    _TOKEN_CANONICAL_MAP = {
        "fertilizante": "fertilizacion",
        "fertilizantes": "fertilizacion",
        "fertilizar": "fertilizacion",
        "abono": "fertilizacion",
        "abonos": "fertilizacion",
        "abonar": "fertilizacion",
        "nutriente": "fertilizacion",
        "nutrientes": "fertilizacion",
        "riego": "riego",
        "regar": "riego",
        "irrigacion": "riego",
        "plagas": "plaga",
        "insectos": "plaga",
        "enfermedades": "enfermedad",
        "hongos": "hongo",
        "siembra": "siembra",
        "sembrar": "siembra",
        "plantar": "siembra",
        "cosechar": "cosecha",
        "cosechas": "cosecha",
    }
    _INTENT_KEYWORDS = {
        "fertilizacion": {"fertilizacion", "npk", "urea", "nitrogeno", "fosforo", "potasio"},
        "riego": {"riego", "goteo", "agua", "irrigacion"},
        "plaga": {"plaga", "gusano", "mosca", "pulgon", "trips", "minador"},
        "enfermedad": {"enfermedad", "hongo", "virus", "bacteria", "tizon", "roya", "pudricion", "mancha"},
        "siembra": {"siembra", "cultivo", "cosecha", "semilla"},
    }

    def __init__(self, kb_path: Path) -> None:
        self.kb_path = kb_path
        self.entries: Dict[int, KBEntry] = {}
        self.questions: List[str] = []
        self.entry_ids: List[int] = []
        self._load_kb()

    def _load_kb(self) -> None:
        payload = json.loads(self.kb_path.read_text(encoding="utf-8"))
        for raw in payload["entries"]:
            entry = KBEntry(
                entry_id=raw["id"],
                category=raw.get("category", "general"),
                questions=list(raw.get("questions", [])),
                answer=raw.get("answer", ""),
            )
            self.entries[entry.entry_id] = entry

        for entry_id in sorted(self.entries.keys()):
            entry = self.entries[entry_id]
            for q in entry.questions:
                self.questions.append(q)
                self.entry_ids.append(entry_id)

    def find_top_k_contexts(self, user_query: str, top_k: int, min_score: float) -> ContextResult:
        query_lower = self._normalize_query_for_search(user_query)
        scores: List[Tuple[int, float]] = []

        for idx, q in enumerate(self.questions):
            score = self._calculate_text_similarity(query_lower, q.lower())
            if score >= min_score:
                scores.append((idx, score))

        if not scores:
            fallback_all = [
                (idx, self._calculate_text_similarity(query_lower, self.questions[idx].lower()))
                for idx in range(len(self.questions))
            ]
            fallback_all.sort(key=lambda x: x[1], reverse=True)
            scores.extend(fallback_all[: max(1, top_k)])

        scores.sort(key=lambda x: x[1], reverse=True)
        top_results = scores[: max(1, top_k)]

        seen_entry_ids = set()
        unique: List[MatchResult] = []
        for idx, score in top_results:
            entry_id = self.entry_ids[idx]
            if entry_id in seen_entry_ids:
                continue
            seen_entry_ids.add(entry_id)
            entry = self.entries[entry_id]
            unique.append(
                MatchResult(
                    answer=entry.answer,
                    matched_question=self.questions[idx],
                    similarity_score=score,
                    category=entry.category,
                    entry_id=entry_id,
                )
            )

        return ContextResult(contexts=unique, combined_context=self._build_combined_context(unique))

    @staticmethod
    def _normalize_text(text: str) -> str:
        out = text.lower()
        out = "".join(
            ch for ch in unicodedata.normalize("NFKD", out) if not unicodedata.combining(ch)
        )
        out = re.sub(r"[^a-z0-9\s]", " ", out)
        out = re.sub(r"\s+", " ", out).strip()
        return out

    def _normalize_query_for_search(self, user_query: str) -> str:
        normalized = self._normalize_text(user_query)
        refined = normalized
        for pattern in self._DISCOURSE_PREFIXES:
            refined = pattern.sub("", refined)
        refined = refined.strip()
        return refined or normalized

    @staticmethod
    def _generate_bigrams(text: str) -> set:
        words = [w for w in re.split(r"\s+", text) if w]
        if len(words) < 2:
            return set()
        return {f"{a} {b}" for a, b in zip(words, words[1:])}

    def _extract_informative_tokens(self, words: set) -> set:
        out = set()
        for word in words:
            mapped = self._TOKEN_CANONICAL_MAP.get(word, word)
            if len(mapped) > 4 and mapped.endswith("es"):
                mapped = mapped[:-2]
            elif len(mapped) > 3 and mapped.endswith("s"):
                mapped = mapped[:-1]

            if len(mapped) >= 3 and mapped not in self._STOP_WORDS:
                out.add(mapped)
        return out

    def _detect_intents(self, tokens: set) -> set:
        detected = set()
        for intent, keywords in self._INTENT_KEYWORDS.items():
            if any(t in keywords for t in tokens):
                detected.add(intent)
        return detected

    @staticmethod
    def _keyword_bonus(words1: set, words2: set) -> float:
        keywords = {
            "cultivar", "sembrar", "plantar", "regar", "fertilizar", "abonar", "cosechar",
            "podar", "fumigar", "controlar", "prevenir", "tratar", "diagnosticar",
            "tomate", "maiz", "papa", "frijol", "cafe", "cebolla", "lechuga", "zanahoria",
            "pepino", "pimenton", "aji", "yuca", "platano", "aguacate", "mango", "naranja",
            "plaga", "enfermedad", "hongo", "virus", "bacteria", "gusano", "mosca", "arana",
            "pulgon", "trips", "minador", "marchitez", "pudricion", "mancha", "amarillo",
            "amarillamiento", "secas", "marchitas", "caidas", "manchadas", "tizon", "norteno",
            "fertilizante", "abono", "npk", "organico", "riego", "goteo", "agua", "suelo",
            "tierra", "sustrato", "semilla", "nutriente", "nitrogeno", "fosforo", "potasio",
            "hoja", "hojas", "raiz", "tallo", "flor", "fruto", "frutos",
            "mejor", "cuando", "como", "porque", "cantidad", "frecuencia",
        }

        important1 = {w for w in words1 if w in keywords}
        important2 = {w for w in words2 if w in keywords}

        if not important1 and not important2:
            return 0.5
        if not important1 or not important2:
            return 0.0

        inter = len(important1 & important2)
        uni = len(important1 | important2)
        return (inter / uni) if uni > 0 else 0.0

    @staticmethod
    def _synonym_bonus(words1: set, words2: set) -> float:
        synonym_groups = [
            {"amarillo", "amarillamiento", "amarillas", "clorosis"},
            {"marchito", "marchitas", "marchitez", "secas", "mustias"},
            {"plaga", "plagas", "insecto", "insectos", "bicho", "bichos"},
            {"enfermedad", "enfermedades", "padecimiento", "mal"},
            {"fertilizante", "abono", "fertilizacion", "abonar", "nutriente"},
            {"riego", "regar", "agua", "irrigacion", "mojado"},
            {"sembrar", "siembra", "plantar", "plantacion", "cultivar"},
            {"cosecha", "cosechar", "recolectar", "recoleccion"},
            {"hoja", "hojas", "follaje", "foliar"},
            {"raiz", "raices", "radicular"},
            {"fruto", "frutos", "frutas", "produccion"},
            {"hongo", "hongos", "fungico", "fungica"},
            {"tizon", "blight", "norteno", "northern"},
            {"control", "controlar", "combatir", "eliminar", "tratamiento", "tratar"},
            {"tomate", "tomates", "jitomate"},
            {"maiz", "elote", "choclo"},
            {"papa", "patata", "papas", "patatas"},
            {"cafe", "cafeto", "cafetal"},
            {"mejor", "optimo", "recomendado", "ideal"},
            {"cuando", "epoca", "momento", "tiempo"},
            {"como", "manera", "forma", "metodo"},
        ]

        matches = 0
        for group in synonym_groups:
            has1 = any(w in group for w in words1)
            has2 = any(w in group for w in words2)
            if has1 and has2:
                matches += 1

        return min(1.0, matches / 5.0) if matches > 0 else 0.0

    def _calculate_text_similarity(self, text1: str, text2: str) -> float:
        norm1 = self._normalize_text(text1)
        norm2 = self._normalize_text(text2)
        if not norm1 or not norm2:
            return 0.0

        words1 = {w for w in re.split(r"\s+", norm1) if w}
        words2 = {w for w in re.split(r"\s+", norm2) if w}
        if not words1 or not words2:
            return 0.0

        informative1 = self._extract_informative_tokens(words1)
        informative2 = self._extract_informative_tokens(words2)

        # Aproximacion al bloque de metricas del helper Kotlin.
        inter = len(words1 & words2)
        uni = len(words1 | words2)
        jaccard = (inter / uni) if uni > 0 else 0.0

        keyword_score = self._keyword_bonus(words1, words2)
        synonym_score = self._synonym_bonus(words1, words2)

        # Aproximacion de Jaro/Fuzzy con ratio de secuencia.
        jaro_score = SequenceMatcher(None, norm1, norm2).ratio()
        fuzzy_norm = SequenceMatcher(None, " ".join(sorted(words1)), " ".join(sorted(words2))).ratio()

        substring_bonus = 0.20 if (norm1 in norm2 or norm2 in norm1) else 0.0

        bigram1 = self._generate_bigrams(norm1)
        bigram2 = self._generate_bigrams(norm2)
        if bigram1 and bigram2:
            bigram_score = len(bigram1 & bigram2) / len(bigram1 | bigram2)
        else:
            bigram_score = 0.0

        token_coverage = (len(informative1 & informative2) / len(informative1)) if informative1 else 0.0
        query_intents = self._detect_intents(informative1)
        candidate_intents = self._detect_intents(informative2)
        misses_intent = bool(query_intents) and not bool(query_intents & candidate_intents)
        shares_intent = bool(query_intents & candidate_intents)

        final = (
            jaccard * 0.20
            + keyword_score * 0.25
            + synonym_score * 0.20
            + bigram_score * 0.10
            + jaro_score * 0.15
            + fuzzy_norm * 0.10
            + substring_bonus
            + token_coverage * 0.20
        )
        if shares_intent:
            final += 0.08
        if misses_intent:
            final *= 0.70
        return max(0.0, min(1.0, final))

    @staticmethod
    def _build_combined_context(results: List[MatchResult]) -> str:
        if not results:
            return ""
        chunks = ["Informacion agricola relevante:\n"]
        for i, r in enumerate(results, start=1):
            chunks.append(f"[{i}] {r.category.upper()}\n{r.answer}")
            if i < len(results):
                chunks.append("\n---\n")
        return "\n".join(chunks).strip()


class LlamaHttpClient:
    def __init__(self, base_url: str, model: Optional[str], timeout: int = 180) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout
        self.session = requests.Session()

    def generate_raw(self, prompt: str, max_tokens: int) -> str:
        payload = {
            "prompt": prompt,
            "max_tokens": max_tokens,
            "temperature": 0.3,
            "top_p": 0.9,
        }
        if self.model:
            payload["model"] = self.model

        # 1) OpenAI-compatible endpoint.
        url_v1 = f"{self.base_url}/v1/completions"
        try:
            r = self.session.post(url_v1, json=payload, timeout=self.timeout)
            r.raise_for_status()
            data = r.json()
            choices = data.get("choices") or []
            if choices:
                return (choices[0].get("text") or "").strip()
        except Exception:
            pass

        # 2) Endpoint legacy de llama.cpp.
        url_legacy = f"{self.base_url}/completion"
        legacy_payload = {
            "prompt": prompt,
            "n_predict": max_tokens,
            "temperature": 0.3,
            "top_p": 0.9,
        }
        r = self.session.post(url_legacy, json=legacy_payload, timeout=self.timeout)
        r.raise_for_status()
        data = r.json()
        text = data.get("content") or data.get("response") or ""
        return text.strip()


class AppPipelineReplica:
    def __init__(self, search: SemanticSearchReplica, cfg: RunnerConfig, llama_client: Optional[LlamaHttpClient]) -> None:
        self.search = search
        self.cfg = cfg
        self.llama_client = llama_client
        self.chat_messages: List[Dict[str, object]] = []
        self.last_context: Optional[str] = None
        self.last_user_query = ""

    @staticmethod
    def _is_simple_greeting(text: str) -> bool:
        q = text.lower().strip()
        exact = {
            "hola", "hey", "buenas", "buenos dias", "buenas tardes", "buenas noches",
            "gracias", "adios", "chao", "hasta luego",
        }
        normalized = "".join(
            ch for ch in unicodedata.normalize("NFKD", q) if not unicodedata.combining(ch)
        )
        return (
            normalized in exact
            or (len(normalized) < 15 and (normalized.startswith("hola") or normalized.startswith("hey") or normalized.startswith("gracias")))
        )

    @staticmethod
    def _truncate_context_preserving_kb(context: str, max_len: int) -> str:
        if len(context) <= max_len:
            return context

        kb_marker = "=== KB ==="
        history_marker = "=== HISTORIAL ==="
        kb_index = context.find(kb_marker)
        history_index = context.find(history_marker)

        if kb_index < 0:
            return context[:max_len]

        if history_index >= 0 and kb_index < history_index:
            kb_section = context[kb_index:history_index].strip()
        else:
            kb_section = context[kb_index:].strip()

        if history_index >= 0:
            if history_index < kb_index:
                history_section = context[history_index:kb_index].strip()
            else:
                history_section = context[history_index:].strip()
        else:
            history_section = ""

        kb_budget = max(300, int(max_len * 0.8))
        history_budget = max(0, max_len - kb_budget)

        kb_truncated = kb_section[:kb_budget]
        history_content = history_section.removeprefix(history_marker).strip()
        if history_budget > 0 and history_content:
            history_truncated = f"\n\n{history_marker}\n{history_content[:history_budget]}"
        else:
            history_truncated = ""

        return (kb_truncated + history_truncated)[:max_len]

    @staticmethod
    def _clean_response(response: str) -> str:
        cleaned = response
        special_tokens = [
            "<|begin_of_text|>", "<|end_of_text|>",
            "<|start_header_id|>", "<|end_header_id|>",
            "<|eot_id|>", "<|eom_id|>",
        ]

        for token in special_tokens:
            while cleaned.startswith(token):
                cleaned = cleaned[len(token):].lstrip()

        for token in special_tokens:
            idx = cleaned.find(token)
            if idx > 0:
                cleaned = cleaned[:idx]
                break

        lines = []
        for line in cleaned.splitlines():
            low = line.lower().strip()
            if low in {"system", "user", "assistant"}:
                continue
            if low.startswith("info:"):
                continue
            lines.append(line)

        cleaned = "\n".join(lines).strip()
        if len(cleaned) < 5:
            return "Puedo ayudarte con informacion sobre cultivos, plagas, riego y mas. Que te gustaria saber?"
        return cleaned

    @staticmethod
    def _is_malformed_response(text: str) -> bool:
        lower = text.lower().strip()
        if not lower:
            return True
        if "<|" in lower or "|>" in lower:
            return True
        if lower in {"user", "assistant", "usuario", "asistente"}:
            return True
        cleaned = re.sub(r"<\|[^>]+\|>", "", lower)
        cleaned = (
            cleaned.replace("assistant", "")
            .replace("asistente", "")
            .replace("user", "")
            .replace("usuario", "")
            .strip()
        )
        return not cleaned

    @staticmethod
    def _default_greeting_response() -> str:
        return (
            "Hola! Soy FarmifAI, tu asistente agricola con IA.\n\n"
            "Puedo ayudarte con:\n"
            "- Cultivos\n"
            "- Plagas\n"
            "- Riego\n"
            "- Fertilizacion\n\n"
            "En que te ayudo?"
        )

    def _generate_llama_response(
        self,
        user_query: str,
        context_from_kb: Optional[str],
        max_tokens: int,
        max_context_length: int,
        system_prompt: str,
    ) -> str:
        if not self.llama_client:
            raise RuntimeError("No se configuro backend Llama HTTP")

        if context_from_kb:
            short_context = self._truncate_context_preserving_kb(context_from_kb, max_context_length)
            user_message = (
                "Usa exclusivamente este CONTEXTO KB para responder.\n"
                "Si el dato no aparece en el contexto, dilo explicitamente.\n"
                "No inventes informacion externa.\n\n"
                f"CONTEXTO KB:\n{short_context}\n\n"
                f"PREGUNTA:\n{user_query}\n"
            )
        else:
            user_message = user_query

        prompt = (
            "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n"
            f"{system_prompt}<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n"
            f"{user_message}<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
        )

        raw = self.llama_client.generate_raw(prompt, max_tokens=max_tokens)
        return self._clean_response(raw)

    def ask(self, user_query: str) -> Dict[str, object]:
        self.last_user_query = user_query

        rag_context = self.search.find_top_k_contexts(
            user_query=user_query,
            top_k=self.cfg.top_k,
            min_score=self.cfg.similarity_threshold,
        )
        best_match = rag_context.contexts[0] if rag_context.contexts else None
        combined_kb_context = rag_context.combined_context
        has_kb_context = (
            best_match is not None
            and best_match.similarity_score >= self.cfg.context_relevance_threshold
        )

        self.last_context = combined_kb_context if combined_kb_context else (best_match.answer if best_match else None)

        # Fast-path de saludo: en la app, un saludo simple no deberia pasar por LLM.
        if self.cfg.detect_greetings and self._is_simple_greeting(user_query):
            response = best_match.answer if (best_match and best_match.answer.strip()) else self._default_greeting_response()
            self.chat_messages.append({"is_user": True, "text": user_query})
            self.chat_messages.append({"is_user": False, "text": response})
            return {
                "response": response,
                "source": "GREETING_FAST_PATH",
                "best_match": best_match,
                "contexts": rag_context.contexts,
            }

        kb_direct_threshold = min(self.cfg.kb_fast_path_threshold, 0.60)
        use_llm_for_all = self.cfg.use_llm_for_all and not self.cfg.strict_app_mode
        if (
            not use_llm_for_all
            and best_match is not None
            and best_match.similarity_score >= kb_direct_threshold
        ):
            response = best_match.answer
            self.chat_messages.append({"is_user": True, "text": user_query})
            self.chat_messages.append({"is_user": False, "text": response})
            return {
                "response": response,
                "source": "KB_DIRECT",
                "best_match": best_match,
                "contexts": rag_context.contexts,
            }

        chat_history_context = None
        if self.cfg.chat_history_enabled and self.chat_messages:
            recent = self.chat_messages[-self.cfg.chat_history_size * 2 :]
            lines = []
            for msg in recent:
                if msg["is_user"]:
                    lines.append(f"Usuario: {msg['text']}")
                else:
                    lines.append(f"Asistente: {str(msg['text'])[:200]}")
            if lines:
                chat_history_context = "\n".join(lines)

        context_parts = []
        if has_kb_context and combined_kb_context:
            context_parts.append(f"=== KB ===\n{combined_kb_context}")
        if chat_history_context:
            context_parts.append(f"=== HISTORIAL ===\n{chat_history_context}")
        context_to_pass = "\n\n".join(context_parts) if context_parts else None

        if self.llama_client:
            try:
                effective_system_prompt = (
                    self.cfg.system_prompt
                    + "\nReglas obligatorias: usa el CONTEXTO KB como fuente principal; no inventes; responde de forma completa."
                    if has_kb_context
                    else self.cfg.system_prompt
                )
                effective_max_tokens = max(self.cfg.max_tokens, 450) if has_kb_context else self.cfg.max_tokens

                response = self._generate_llama_response(
                    user_query=user_query,
                    context_from_kb=context_to_pass,
                    max_tokens=effective_max_tokens,
                    max_context_length=self.cfg.context_length,
                    system_prompt=effective_system_prompt,
                ).strip()

                if len(response) > 10:
                    # Guardrail en modo estricto: si hay buen contexto KB y el LLM dice que no sabe,
                    # priorizar respuesta de la base de conocimiento como en experiencia esperada de app.
                    if (
                        self.cfg.strict_app_mode
                        and has_kb_context
                        and best_match is not None
                        and (
                            self._is_malformed_response(response)
                            or any(
                                bad in response.lower()
                                for bad in (
                                    "no tengo informacion",
                                    "no dispongo de informacion",
                                    "no tengo datos",
                                    "no puedo responder",
                                    "no se",
                                )
                            )
                        )
                    ):
                        response = best_match.answer
                        source = "KB_AFTER_LLM_GUARD"
                    else:
                        source = "LLAMA"

                    self.chat_messages.append({"is_user": True, "text": user_query})
                    self.chat_messages.append({"is_user": False, "text": response})
                    return {
                        "response": response,
                        "source": source,
                        "best_match": best_match,
                        "contexts": rag_context.contexts,
                    }
            except Exception as exc:
                llm_error = str(exc)
            else:
                llm_error = "respuesta vacia o muy corta"
        else:
            llm_error = "backend Llama no configurado"

        if best_match is None:
            response = "No pude procesar tu pregunta. Intenta reformularla."
            source = "GENERIC_FALLBACK"
        elif best_match.similarity_score < SIMILARITY_THRESHOLD:
            response = (
                "Hola! Soy tu asistente agricola. Puedo ayudarte con:\n\n"
                "- Cultivos y siembra\n"
                "- Control de plagas\n"
                "- Riego\n"
                "- Fertilizacion\n\n"
                "Que te gustaria saber?"
            )
            source = "LOW_SCORE_FALLBACK"
        else:
            response = best_match.answer
            source = "KB_FALLBACK"

        self.chat_messages.append({"is_user": True, "text": user_query})
        self.chat_messages.append({"is_user": False, "text": response})
        return {
            "response": response,
            "source": source,
            "best_match": best_match,
            "contexts": rag_context.contexts,
            "llm_error": llm_error,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replica de pipeline AgroChat (RAG + Llama) en terminal")
    parser.add_argument("--kb", type=Path, default=KB_PATH, help="Ruta del agrochat_knowledge_base.json")
    parser.add_argument("--llama-base-url", default=os.getenv("LLAMA_BASE_URL", "http://127.0.0.1:8080"), help="Base URL de llama.cpp server")
    parser.add_argument("--llama-model", default=os.getenv("LLAMA_MODEL"), help="Campo model para /v1/completions (opcional)")
    parser.add_argument("--no-llama", action="store_true", help="Desactiva llamada LLM y prueba solo retrieval/fallback KB")
    parser.add_argument("--query", help="Ejecuta una consulta unica y sale")
    parser.add_argument("--max-tokens", type=int, default=450)
    parser.add_argument("--context-length", type=int, default=1800)
    parser.add_argument("--similarity-threshold", type=float, default=0.45)
    parser.add_argument("--kb-fast-threshold", type=float, default=0.70)
    parser.add_argument("--context-relevance-threshold", type=float, default=0.50)
    parser.add_argument("--use-llm-for-all", action="store_true")
    parser.add_argument("--strict-app", dest="strict_app", action="store_true", default=True, help="Replica estricta del flujo de app (default)")
    parser.add_argument("--no-strict-app", dest="strict_app", action="store_false", help="Permite modo experimental no estricto")
    parser.add_argument("--debug", action="store_true")
    return parser.parse_args()


def print_debug(result: Dict[str, object]) -> None:
    best = result.get("best_match")
    contexts = result.get("contexts") or []
    print("\n[debug] source:", result.get("source"))
    if result.get("llm_error"):
        print("[debug] llm_error:", result["llm_error"])
    if best:
        assert isinstance(best, MatchResult)
        print(f"[debug] best_match: id={best.entry_id} score={best.similarity_score:.3f} q={best.matched_question}")
    if contexts:
        preview = " | ".join(f"{c.similarity_score:.2f}:{c.matched_question[:40]}" for c in contexts)
        print("[debug] top_contexts:", preview)


def run_repl(engine: AppPipelineReplica, debug: bool) -> int:
    print("Replica AgroChat en terminal.")
    print("Comandos: /exit, /quit, /help, /config")
    while True:
        try:
            q = input("\nTu pregunta > ").strip()
        except EOFError:
            print()
            return 0

        if not q:
            continue
        if q in {"/exit", "/quit"}:
            return 0
        if q == "/help":
            print("/exit /quit: salir")
            print("/config: mostrar configuracion activa")
            continue
        if q == "/config":
            print(engine.cfg)
            continue

        result = engine.ask(q)
        print("\nFarmifAI:")
        print(result["response"])
        if debug:
            print_debug(result)


def main() -> int:
    args = parse_args()

    if not args.kb.exists():
        print(f"ERROR: KB no encontrada en {args.kb}", file=sys.stderr)
        return 2

    cfg = RunnerConfig(
        max_tokens=max(50, args.max_tokens),
        context_length=max(200, args.context_length),
        similarity_threshold=max(0.0, min(1.0, args.similarity_threshold)),
        kb_fast_path_threshold=max(0.0, min(1.0, args.kb_fast_threshold)),
        context_relevance_threshold=max(0.0, min(1.0, args.context_relevance_threshold)),
        use_llm_for_all=args.use_llm_for_all,
        strict_app_mode=args.strict_app,
    )

    search = SemanticSearchReplica(args.kb)
    llama_client = None if args.no_llama else LlamaHttpClient(args.llama_base_url, args.llama_model)
    engine = AppPipelineReplica(search=search, cfg=cfg, llama_client=llama_client)

    if args.query:
        result = engine.ask(args.query)
        print(result["response"])
        if args.debug:
            print_debug(result)
        return 0

    return run_repl(engine, debug=args.debug)


if __name__ == "__main__":
    raise SystemExit(main())
