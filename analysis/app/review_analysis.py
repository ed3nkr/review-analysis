"""리뷰 1건씩 분석: 노이즈 판별, 메뉴군 태깅(칭찬/불만), 부정 유형, 점심/저녁"""
import re

import pandas as pd

from . import rules


def alias_table(groups):
    """메뉴군 [{id, name, aliases}] → [(리뷰에서 찾을 말, 메뉴군 id)] (긴 말부터 — '치즈카츠'가 '카츠'보다 먼저)"""
    table = []
    for g in groups:
        for word in [g["name"], *(g.get("aliases") or [])]:
            w = (word or "").strip()
            if len(w) >= 2:          # 한 글자 별칭은 오탐이 많아 제외
                table.append((w, g["id"]))
    table.sort(key=lambda t: -len(t[0]))
    return table


def find_groups(text, table):
    """문장 안에서 언급된 메뉴군 id 목록 (찾은 말은 지워서 짧은 말이 다시 걸리지 않게)"""
    found, rest = [], text
    for word, gid in table:
        if word in rest:
            found.append(gid)
            rest = rest.replace(word, " ")
    return list(dict.fromkeys(found))


RELIABILITY_CUTOFF = 0.3  # 노이즈 모델: 정상일 확률이 이보다 낮으면 제외 (설계 초깃값, 실험에서 조정)
TYPE_THRESHOLD = 0.5      # 부정 유형 모델: 유형 확률이 이 이상이면 그 유형


def split_sentences(text):
    return [s.strip() for s in rules.SENTENCE_SPLIT.split(text) if s and s.strip()]


def rule_types(text):
    return [k for k, p in rules.NEG_RULES.items() if p.search(text)]


def tag_review(text, table, sentence_types=None):
    """본문 → (언급 메뉴군, 칭찬 메뉴군, 불만 메뉴군)
    sentence_types: {문장: [부정 유형]} — 모델이 있으면 메뉴 문장의 불만을 모델로, 없으면 규칙('맛' 표현)으로 판단"""
    if not isinstance(text, str) or not text.strip():
        return [], [], []
    menus, praised, blamed = [], [], []
    for sent in split_sentences(text):
        found = find_groups(sent, table)
        if not found:
            continue
        menus += found
        complained = bool(sentence_types.get(sent)) if sentence_types is not None else bool(rules.NEG_RULES["맛"].search(sent))
        if complained:
            blamed += found
        elif rules.PRAISE_HINT.search(sent):
            praised += found
    uniq = lambda xs: list(dict.fromkeys(xs))
    return uniq(menus), uniq(praised), uniq(blamed)


def time_slot(tag, text):
    """방문 표시('점심에 방문') 우선, 없으면 본문 단어로. 모르면 None"""
    for source in (tag, text):
        if not isinstance(source, str):
            continue
        lunch, dinner = bool(rules.LUNCH_WORDS.search(source)), bool(rules.DINNER_WORDS.search(source))
        if lunch != dinner:
            return "LUNCH" if lunch else "DINNER"
    return None


def analyze(reviews: pd.DataFrame, groups, noise_model=None, type_model=None):
    """리뷰 분석 파이프라인 (논문 3.3.3)
      ① 규칙 기반 1차 필터 → ② 노이즈 분류 모델(신뢰도) → ③ 부정 유형 분류 모델(멀티라벨) → ④ 메뉴·시간대 태깅
    reviews: id, content, rating, day, visit_slot_tag, author_hash
    모델이 None 이면 그 단계는 규칙(rule-v1)으로 대신한다."""
    rv = reviews.copy()
    rv["content"] = rv["content"].fillna("")
    text = rv["content"].str.strip()
    has_text = text.str.len() > 0

    # ① 규칙 기반 1차 필터: 형식으로 판별되는 노이즈
    reason = pd.Series([None] * len(rv), index=rv.index, dtype=object)
    reason[has_text & (text.str.replace(r"[^가-힣]", "", regex=True).str.len() < 2)] = "MEANINGLESS"
    for pattern, code in rules.NOISE_RULES:
        hit = reason.isna() & text.map(lambda s: bool(pattern.search(s)))
        reason[hit] = code
    dup = rv[has_text].duplicated(subset=["author_hash", "content"], keep="first")
    dup_idx = dup[dup].index
    reason[dup_idx] = reason[dup_idx].where(reason[dup_idx].notna(), "DUPLICATE")

    # ② 노이즈 분류 모델: 규칙을 통과한 본문 리뷰의 '정상일 확률' → 0.3 미만 제외
    rv["reliability"] = 1.0
    if noise_model is not None:
        idx = rv.index[has_text & reason.isna()]
        if len(idx):
            rv.loc[idx, "reliability"] = noise_model.normal_prob(text[idx].tolist())
        reason[reason.isna() & (rv["reliability"] < RELIABILITY_CUTOFF)] = "LOW_RELIABILITY"
    rv.loc[reason.notna() & (reason != "LOW_RELIABILITY"), "reliability"] = 0.0   # 규칙으로 걸러진 리뷰
    rv["excluded"] = reason.notna()
    rv["exclude_reason"] = reason

    # ③ 부정 유형 분류 (정상 리뷰만): 유형별 확률 → 0.5 이상인 유형
    keep = rv.index[has_text & ~rv["excluded"]]
    rv["neg_probs"] = None
    rv["neg_types"] = [[] for _ in range(len(rv))]
    table = alias_table(groups)
    sentence_types = None
    if type_model is not None:
        if len(keep):
            probs = type_model.type_probs(text[keep].tolist())
            rv.loc[keep, "neg_probs"] = pd.Series(probs, index=keep)
            rv.loc[keep, "neg_types"] = pd.Series([[k for k, p in pr.items() if p >= TYPE_THRESHOLD] for pr in probs], index=keep)
        # 메뉴가 나온 문장만 모아 문장 단위로 다시 판단 (어느 메뉴에 대한 불만인지)
        menu_sents = list(dict.fromkeys(s for t in text[keep] for s in split_sentences(t) if find_groups(s, table)))
        sp = type_model.type_probs(menu_sents) if menu_sents else []
        sentence_types = {s: [k for k, p in pr.items() if p >= TYPE_THRESHOLD] for s, pr in zip(menu_sents, sp)}
    else:
        rv.loc[keep, "neg_types"] = pd.Series([rule_types(t) for t in text[keep]], index=keep)

    rv["neg_score"] = rv["neg_probs"].map(lambda d: max(d.values()) if isinstance(d, dict) and d else None)
    # 어떤 부정 유형에도 해당하지 않으면 부정 리뷰가 아님 (설계). 본문 없는 리뷰는 별점으로만 판단
    rv["negative"] = (rv["neg_types"].map(len) > 0) | (~has_text & (rv["rating"].fillna(5) <= 2))
    rv["sentiment"] = rv["negative"].map({True: "NEGATIVE", False: "POSITIVE"})
    rv.loc[rv["excluded"], "sentiment"] = None

    # ④ 메뉴·시간대 태깅
    tags = rv["content"].map(lambda s: tag_review(s, table, sentence_types))
    rv["menu_group_ids"] = tags.map(lambda t: t[0])
    rv["praised_group_ids"] = tags.map(lambda t: t[1])
    rv["blamed_group_ids"] = tags.map(lambda t: t[2])
    rv["time_slot"] = [time_slot(t, c) for t, c in zip(rv["visit_slot_tag"], rv["content"])]

    used = [m.name for m in (noise_model, type_model) if m is not None]
    rv["model_version"] = "+".join(used + [rules.MODEL_VERSION])
    return rv
