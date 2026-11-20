"""KLUE-BERT 모델 2개 (논문 3.3.3) — Colab 노트북으로 학습한 폴더를 읽어 추론만 한다.

  models/noise    : 정상/노이즈 이진 분류  → 정상일 확률 = reliability
  models/negtype  : 부정 유형 6개 멀티라벨 → 유형별 확률 = neg_probs

모델 폴더가 없거나 torch·transformers 가 없으면 해당 단계는 규칙(rule-v1)으로 대신한다.
폴더 위치는 환경변수 MODEL_DIR 로 바꿀 수 있다 (기본: analysis/models).
"""
import os
from pathlib import Path

DEFAULT_DIR = Path(__file__).resolve().parent.parent / "models"
_cache = {}


class BertClassifier:
    def __init__(self, path, multi_label):
        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        self.torch = torch
        self.tok = AutoTokenizer.from_pretrained(path)
        self.model = AutoModelForSequenceClassification.from_pretrained(path).eval()
        self.multi = multi_label
        self.labels = [self.model.config.id2label[i] for i in range(self.model.config.num_labels)]
        self.name = f"klue-{Path(path).name}"

    def predict(self, texts, batch_size=32, max_len=128):
        """문장 목록 → 확률 행렬 (행: 문장, 열: self.labels 순서)"""
        out = []
        for i in range(0, len(texts), batch_size):
            enc = self.tok(texts[i:i + batch_size], truncation=True, max_length=max_len,
                           padding=True, return_tensors="pt")
            with self.torch.no_grad():
                logits = self.model(**enc).logits
            probs = self.torch.sigmoid(logits) if self.multi else self.torch.softmax(logits, dim=-1)
            out += probs.tolist()
        return out


class NoiseModel(BertClassifier):
    def normal_prob(self, texts):
        idx = self.labels.index("normal") if "normal" in self.labels else 0
        return [row[idx] for row in self.predict(texts)]


class NegTypeModel(BertClassifier):
    def type_probs(self, texts):
        """문장 목록 → [{유형: 확률}, ...]"""
        return [dict(zip(self.labels, row)) for row in self.predict(texts)]


def _load(cls, name, multi):
    if os.environ.get("ANALYSIS_RULES_ONLY") == "1":
        return None
    path = Path(os.environ.get("MODEL_DIR") or DEFAULT_DIR) / name
    if not (path / "config.json").exists():
        return None
    key = str(path)
    if key not in _cache:
        try:
            _cache[key] = cls(path, multi)
        except ImportError:          # torch / transformers 미설치
            _cache[key] = None
    return _cache[key]


def load():
    """(노이즈 모델 또는 None, 부정 유형 모델 또는 None)"""
    return _load(NoiseModel, "noise", False), _load(NegTypeModel, "negtype", True)
