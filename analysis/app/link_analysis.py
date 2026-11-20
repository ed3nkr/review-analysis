"""리뷰-매출 연계 분석 (논문 3.3.4) — 9개 고정 질문 + 반복 불만(R1)

  연결 키: 메뉴(Q1~Q3) / 시간대(Q4~Q6) / 기간(Q7~Q9)  ×  비교: 비중 / 비율(1,000건당) / 변화
  각 질문은 insight 행 여러 개를 만든다: 판정(FLAGGED 표시 / NORMAL 특이사항 없음 / INSUFFICIENT 데이터 부족)
  + 근거 수치(evidence) + p값 + 점주에게 보여 줄 문장.
"""
import re
from collections import Counter
from datetime import time

import numpy as np
import pandas as pd
from scipy.stats import binomtest, chi2_contingency, poisson, spearmanr

from . import rules
from .review_analysis import alias_table, find_groups  # noqa: F401 (토핑 집계 제거 후 미사용)

ALPHA = 0.05          # 유의수준. 여러 대상을 한꺼번에 볼 때는 대상 수로 나눔(본페로니)
MIN_N = 10            # 판정에 필요한 최소 리뷰(언급) 수 — 미만이면 '데이터 부족'
MIN_SHARE_DIFF = 5    # 비중 차이가 이 %p 미만이면 통계적으로 달라도 표시하지 않음
MIN_UNIT_PRICE = 2000 # 평균 단가가 이보다 낮은 메뉴군(무료 제공·옵션·추가 재료)은 메뉴 비교에서 뺌
ZONE = "Asia/Seoul"
SLOT_KO = {"LUNCH": "점심", "DINNER": "저녁"}


def josa(word, pair):
    """받침에 맞는 조사: josa('우동', '은는') → '우동은', josa('카레', '은는') → '카레는'"""
    w = str(word)
    last = re.sub(r"[^가-힣]", "", w)[-1:] or w[-1:]
    has_final = "가" <= last <= "힣" and (ord(last) - 0xAC00) % 28 != 0
    return w + (pair[0] if has_final else pair[1])


def main_groups(units, rv):
    """비교 대상 메뉴군: 판매 비중 1% 이상이거나 리뷰 언급이 MIN_N 이상인 것 (토핑·소스류 제외 효과)"""
    ment = Counter(g for gs in rv["menu"] for g in gs)
    share = units / units.sum()
    return [g for g in units.index if share[g] >= 0.01 or ment[g] >= MIN_N]


def pct(x):
    return f"{x * 100:.0f}%"


def clean(o):
    """numpy 값 → JSON 저장 가능한 값"""
    if isinstance(o, dict):
        return {str(k): clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [clean(v) for v in o]
    if isinstance(o, (np.integer,)):
        return int(o)
    if isinstance(o, (np.floating, float)):
        return None if np.isnan(o) else round(float(o), 4)
    if isinstance(o, (pd.Timestamp,)):
        return str(o.date())
    return o


def insight(code, ttype, tid, result, evidence, p, sentence):
    return dict(question_code=code, target_type=ttype, target_id=None if tid is None else str(tid)[:50],
                result=result, evidence=clean(evidence), p_value=None if p is None else float(p), sentence=sentence)


# ------------------------------------------------------------------ 준비

def prepare(sales, reviews, groups, cutoff):
    """sales: bill_no, sold_at, quantity, amount, status, menu_group_id, topping
       reviews: review_analysis.analyze() 결과 (excluded, menu_group_ids, praised/blamed, time_slot, negative, day, rating)"""
    if isinstance(cutoff, str):
        h, m = cutoff[:5].split(":")
        cutoff = time(int(h), int(m))
    s = sales[sales["status"] == "NORMAL"].copy()
    s["ts"] = pd.to_datetime(s["sold_at"], utc=True).dt.tz_convert(ZONE).dt.tz_localize(None)
    s["date"] = s["ts"].dt.normalize()
    s["slot"] = np.where(s["ts"].dt.time < cutoff, "LUNCH", "DINNER")
    bills = s.drop_duplicates(["bill_no", "sold_at"])

    name = {g["id"]: g["name"] for g in groups}
    units = Counter()
    for gid, q in zip(s["menu_group_id"], s["quantity"]):
        if pd.notna(gid):                                # 메뉴군 단품 기준: '마제소바+돈카츠'는 마제소바 1개로만 셈
            units[name.get(int(gid))] += int(q)
    # 메뉴군 평균 단가 (메뉴 자체로 팔린 줄 기준) → 옵션·무료 제공 메뉴군 제외
    main = s[s["menu_group_id"].notna()]
    price = (main.groupby("menu_group_id")["amount"].sum() / main.groupby("menu_group_id")["quantity"].sum())
    cheap = {name.get(int(gid)) for gid, p in price.items() if p < MIN_UNIT_PRICE}
    units = pd.Series({k: v for k, v in units.items() if k and k not in rules.SKIP_GROUPS and k not in cheap}, dtype=float)

    rv = reviews[~reviews["excluded"]].copy()
    rv["date"] = pd.to_datetime(rv["day"])
    to_names = lambda ids: [name[i] for i in ids if i in name and name[i] not in rules.SKIP_GROUPS]
    for col in ("menu_group_ids", "praised_group_ids", "blamed_group_ids"):
        rv[col.replace("_group_ids", "")] = rv[col].map(to_names)
    rv = rv[(rv["date"] >= s["date"].min()) & (rv["date"] <= s["date"].max())]   # 매출 기간에 맞춤
    return s, bills, units, rv


# ------------------------------------------------------------------ 메뉴 (Q1~Q3)

def q1_menu_share(units, rv):
    ment = Counter(g for gs in rv["menu"] for g in gs)
    eligible = main_groups(units, rv)
    u = units[eligible]
    N = sum(ment[g] for g in eligible)
    if N < 3 * MIN_N:
        return [insight("Q1", "STORE", None, "INSUFFICIENT", {"mentions": N}, None,
                        f"메뉴를 언급한 리뷰가 {N}건뿐이라 메뉴별 비교를 하기 어려워요.")]
    rho = spearmanr(u.values, [ment[g] for g in eligible]).statistic
    out, k = [], len(eligible)
    for g in u.sort_values(ascending=False).index:
        ss, c = u[g] / u.sum(), ment[g]
        rs = c / N
        diff = (rs - ss) * 100
        p = binomtest(c, N, ss).pvalue
        ev = {"units": u[g], "sales_share": ss, "mentions": c, "mention_share": rs, "diff_pp": diff,
              "n_mentions": N, "alpha": ALPHA / k, "spearman": rho}
        if p < ALPHA / k and abs(diff) >= MIN_SHARE_DIFF:
            if diff > 0:
                s = f"{josa(g, '은는')} 판매의 {pct(ss)}인데 리뷰 언급은 {pct(rs)}예요. 팔린 것보다 리뷰에서 더 많이 보이는 메뉴예요."
            else:
                s = f"{josa(g, '은는')} 판매의 {pct(ss)}인데 리뷰 언급은 {pct(rs)}예요. 잘 팔리지만 리뷰로는 덜 알려진 메뉴예요."
            out.append(insight("Q1", "MENU_GROUP", g, "FLAGGED", ev, p, s))
        else:
            out.append(insight("Q1", "MENU_GROUP", g, "NORMAL", ev, p,
                               f"{josa(g, '은는')} 판매 비중({pct(ss)})과 리뷰 언급 비중({pct(rs)})이 비슷해요."))
    return out


def q2_menu_rate(units, rv):
    pra = Counter(g for gs in rv["praised"] for g in gs)
    bla = Counter(g for gs in rv["blamed"] for g in gs)
    rows = []
    for g in main_groups(units, rv):
        n = pra[g] + bla[g]
        rows.append(dict(group=g, units=units[g], praise=pra[g], blame=bla[g], enough=n >= MIN_N,
                         praise_rate=pra[g] / units[g] * 1000, blame_rate=bla[g] / units[g] * 1000))
    df = pd.DataFrame(rows).set_index("group")
    ok = df[df["enough"]]
    if len(ok) < 3:
        return [insight("Q2", "STORE", None, "INSUFFICIENT", {"groups_with_data": len(ok)}, None,
                        "칭찬·불만이 충분히 쌓인 메뉴가 3개 미만이라 메뉴 지도를 그리기 어려워요.")]
    ms, mp = ok["units"].median(), ok["praise_rate"].median()
    out = []
    for g, r in df.iterrows():
        ev = {**r.drop("enough").to_dict(), "median_units": ms, "median_praise_rate": mp}
        if not r["enough"]:
            out.append(insight("Q2", "MENU_GROUP", g, "INSUFFICIENT", ev, None,
                               f"{josa(g, '은는')} 칭찬·불만 리뷰가 {int(r['praise'] + r['blame'])}건이라 판단하기 일러요."))
            continue
        hi_s, hi_p = r["units"] >= ms, r["praise_rate"] >= mp
        if hi_s and hi_p:
            out.append(insight("Q2", "MENU_GROUP", g, "NORMAL", {**ev, "position": "간판 메뉴"}, None,
                               f"{josa(g, '은는')} 많이 팔리고 칭찬도 많은 간판 메뉴예요."))
        elif hi_s:
            out.append(insight("Q2", "MENU_GROUP", g, "NORMAL", {**ev, "position": "무난한 주력"}, None,
                               f"{josa(g, '은는')} 많이 팔리지만 칭찬은 평균보다 적은 무난한 주력이에요."))
        elif hi_p:
            out.append(insight("Q2", "MENU_GROUP", g, "FLAGGED", {**ev, "position": "숨은 강점"}, None,
                               f"{josa(g, '은는')} 덜 팔리지만 1,000개당 칭찬이 {r['praise_rate']:.0f}건으로 많아요. 더 알리면 좋을 숨은 강점이에요."))
        else:
            out.append(insight("Q2", "MENU_GROUP", g, "FLAGGED", {**ev, "position": "점검 대상"}, None,
                               f"{josa(g, '은는')} 판매도 칭찬도 평균보다 적어요. 메뉴 구성이나 맛을 점검해 볼 만해요."))
    return out


def q3_menu_change(s, units, rv, table_names):
    d0, d1 = s["date"].min(), s["date"].max()
    days = (d1 - d0).days + 1
    if days < 84:
        return [insight("Q3", "STORE", None, "INSUFFICIENT", {"days": days}, None,
                        f"매출 기간이 {days}일이라 앞뒤 기간 변화를 비교하기에 짧아요(12주 이상 필요).")]
    third = pd.Timedelta(days=days // 3)
    early_end, late_start = d0 + third, d1 - third
    lab = f"{d0:%m/%d}~{early_end - pd.Timedelta(days=1):%m/%d}", f"{late_start:%m/%d}~{d1:%m/%d}"
    sp = {}
    for key, mask in (("early", s["date"] < early_end), ("late", s["date"] >= late_start)):
        part = s[mask]
        c = Counter()
        for gid, q in zip(part["menu_group_id"], part["quantity"]):
            if pd.notna(gid) and table_names.get(int(gid)) in units.index:
                c[table_names[int(gid)]] += int(q)
        tot = sum(c.values())
        sp[key] = {g: c[g] / tot for g in units.index} if tot else {}
    re_ = rv[rv["date"] < early_end]
    rl = rv[rv["date"] >= late_start]
    ce, cl = Counter(g for gs in re_["menu"] for g in gs), Counter(g for gs in rl["menu"] for g in gs)
    te, tl = sum(ce.values()), sum(cl.values())
    out = []
    for g in main_groups(units, rv):
        sd = (sp["late"].get(g, 0) - sp["early"].get(g, 0)) * 100
        md = ((cl[g] / tl if tl else 0) - (ce[g] / te if te else 0)) * 100
        ev = {"periods": lab, "sales_share_change_pp": sd, "mention_share_change_pp": md,
              "mentions_early": ce[g], "mentions_late": cl[g]}
        if abs(sd) < 3:
            out.append(insight("Q3", "MENU_GROUP", g, "NORMAL", ev, None,
                               f"{josa(g, '은는')} 기간 앞뒤로 판매 비중 변화가 작아요({sd:+.1f}%p)."))
            continue
        if not (min(ce[g], cl[g]) >= MIN_N or ce[g] + cl[g] >= 2 * MIN_N):
            out.append(insight("Q3", "MENU_GROUP", g, "INSUFFICIENT", ev, None,
                               f"{josa(g, '은는')} 판매 비중이 {sd:+.1f}%p 변했지만, 언급 리뷰가 적어 리뷰 변화는 판단하기 어려워요."))
            continue
        p = chi2_contingency([[ce[g], te - ce[g]], [cl[g], tl - cl[g]]])[1]
        word = "늘었" if sd > 0 else "줄었"
        if p < ALPHA and np.sign(md) == np.sign(sd):
            out.append(insight("Q3", "MENU_GROUP", g, "FLAGGED", ev, p,
                               f"{lab[0]}보다 {lab[1]}에 {g} 판매 비중이 {abs(sd):.1f}%p {word}고, 리뷰 언급도 같이 {word}어요."))
        elif p < ALPHA:
            out.append(insight("Q3", "MENU_GROUP", g, "FLAGGED", ev, p,
                               f"{g} 판매 비중은 {abs(sd):.1f}%p {word}는데, 리뷰 언급은 반대로 움직였어요. 원인을 확인해 볼 만해요."))
        else:
            out.append(insight("Q3", "MENU_GROUP", g, "NORMAL", ev, p,
                               f"{g} 판매 비중은 {abs(sd):.1f}%p {word}지만 리뷰 언급은 뚜렷하게 변하지 않았어요."))
    return out


# ------------------------------------------------------------------ 시간대 (Q4~Q6)

def q4_slot_share(bills, rv):
    r = rv[rv["time_slot"].notna()]
    n, nd = len(r), int((r["time_slot"] == "DINNER").sum())
    sd = (bills["slot"] == "DINNER").mean()
    if n < 3 * MIN_N or sd in (0, 1):
        return [insight("Q4", "SLOT", "DINNER", "INSUFFICIENT", {"reviews_with_slot": n}, None,
                        f"점심·저녁을 알 수 있는 리뷰가 {n}건이라 비교하기 어려워요.")]
    rd = nd / n
    diff = (rd - sd) * 100
    p = binomtest(nd, n, sd).pvalue
    ev = {"sales_dinner_share": sd, "review_dinner_share": rd, "n": n, "diff_pp": diff}
    if p < ALPHA and abs(diff) >= MIN_SHARE_DIFF:
        who = "저녁" if diff > 0 else "점심"
        share = rd if diff > 0 else 1 - rd
        sales_share = sd if diff > 0 else 1 - sd
        return [insight("Q4", "SLOT", "DINNER", "FLAGGED", ev, p,
                        f"리뷰의 {pct(share)}가 {who} 손님이에요(매출 영수증은 {pct(sales_share)}). 평판이 {who} 쪽으로 쏠려 있어요.")]
    return [insight("Q4", "SLOT", "DINNER", "NORMAL", ev, p, "점심·저녁 손님이 매출 비율대로 리뷰를 남기고 있어요.")]


def q5_slot_rate(bills, rv):
    r = rv[rv["time_slot"].notna()]
    nb = bills["slot"].value_counts()
    nr = r["time_slot"].value_counts()
    nn = r[r["negative"]]["time_slot"].value_counts()
    if any(nb.get(k, 0) == 0 or nr.get(k, 0) < MIN_N for k in ("LUNCH", "DINNER")):
        return [insight("Q5", "SLOT", "LUNCH_DINNER", "INSUFFICIENT", {"reviews": nr.to_dict()}, None,
                        "점심이나 저녁 리뷰가 적어 시간대별 작성률·불만률을 비교하기 어려워요.")]
    rate = {k: nr[k] / nb[k] * 1000 for k in ("LUNCH", "DINNER")}
    negr = {k: nn.get(k, 0) / nr[k] for k in ("LUNCH", "DINNER")}
    p_rate = chi2_contingency([[nr["LUNCH"], nb["LUNCH"] - nr["LUNCH"]], [nr["DINNER"], nb["DINNER"] - nr["DINNER"]]])[1]
    p_neg = chi2_contingency([[nn.get("LUNCH", 0), nr["LUNCH"] - nn.get("LUNCH", 0)],
                              [nn.get("DINNER", 0), nr["DINNER"] - nn.get("DINNER", 0)]])[1]
    ev = {"bills": nb.to_dict(), "reviews": nr.to_dict(), "review_rate_per_1000": rate, "negative_rate": negr}
    out = []
    if p_rate < ALPHA:
        hi = max(rate, key=rate.get)
        out.append(insight("Q5", "SLOT", "REVIEW_RATE", "FLAGGED", ev, p_rate,
                           f"손님 1,000명당 리뷰가 점심 {rate['LUNCH']:.0f}건, 저녁 {rate['DINNER']:.0f}건이에요. "
                           f"{SLOT_KO[hi]} 손님이 리뷰를 더 잘 남겨요."))
    else:
        out.append(insight("Q5", "SLOT", "REVIEW_RATE", "NORMAL", ev, p_rate, "점심과 저녁 손님의 리뷰 작성률은 비슷해요."))
    if p_neg < ALPHA:
        hi = max(negr, key=negr.get)
        out.append(insight("Q5", "SLOT", "NEG_RATE", "FLAGGED", ev, p_neg,
                           f"불만 리뷰 비율이 점심 {pct(negr['LUNCH'])}, 저녁 {pct(negr['DINNER'])}예요. {SLOT_KO[hi]} 운영을 살펴볼 만해요."))
    else:
        out.append(insight("Q5", "SLOT", "NEG_RATE", "NORMAL", ev, p_neg, "점심과 저녁의 불만 비율은 비슷해요."))
    return out


def q6_slot_change(bills, rv):
    r = rv[rv["time_slot"].notna()].assign(m=lambda x: x["date"].dt.to_period("M"))
    b = bills.assign(m=bills["ts"].dt.to_period("M"))
    months = sorted(b["m"].unique())
    out = []
    for m in months:
        rm, bm = r[r["m"] == m], b[b["m"] == m]
        n, nd = len(rm), int((rm["time_slot"] == "DINNER").sum())
        sd = (bm["slot"] == "DINNER").mean()
        ev = {"sales_dinner_share": sd, "review_dinner_share": nd / n if n else None, "n": n}
        if n < 3 * MIN_N:
            out.append(insight("Q6", "MONTH", str(m), "INSUFFICIENT", ev, None, f"{m.month}월은 시간대를 알 수 있는 리뷰가 {n}건이라 비교하기 어려워요."))
            continue
        # 평소 '리뷰-매출 저녁 비중 차이'(그 달 제외)를 기대치로 삼아, 그 달만 유독 다른지 본다
        ro, bo = r[r["m"] != m], b[b["m"] != m]
        gap = (ro["time_slot"] == "DINNER").mean() - (bo["slot"] == "DINNER").mean() if len(ro) else 0
        p = binomtest(nd, n, min(max(sd + gap, 0.01), 0.99)).pvalue
        ev["usual_gap_pp"] = gap * 100
        if p < ALPHA / len(months):
            out.append(insight("Q6", "MONTH", str(m), "FLAGGED", ev, p,
                               f"{m.month}월에는 리뷰의 {pct(nd / n)}가 저녁 손님이었어요(매출은 {pct(sd)}). 평소와 다른 쏠림이에요."))
        else:
            out.append(insight("Q6", "MONTH", str(m), "NORMAL", ev, p, f"{m.month}월의 점심·저녁 리뷰 비율은 평소와 같아요."))
    return out


# ------------------------------------------------------------------ 기간 (Q7~Q9)

def weekly(bills, rv):
    """완전한 주(7일 모두 영업 기간 안)만 남긴 주별 영수증·리뷰 수"""
    d0, d1 = bills["date"].min(), bills["date"].max()
    wk_s = bills.assign(w=bills["date"].dt.to_period("W")).groupby("w").size()
    wk_r = rv.assign(w=rv["date"].dt.to_period("W")).groupby("w").size()
    wk = pd.DataFrame({"bills": wk_s, "reviews": wk_r}).fillna(0)
    full = [w for w in wk.index if w.start_time >= d0 and w.end_time.normalize() <= d1]
    return wk.loc[full]


def q7_period_share(bills, rv):
    wk = weekly(bills, rv)
    if len(wk) < 8:
        return [insight("Q7", "STORE", None, "INSUFFICIENT", {"weeks": len(wk)}, None, "8주 이상 쌓여야 주별 흐름을 비교할 수 있어요.")]
    corr = wk["bills"].corr(wk["reviews"])
    ev = {"weeks": len(wk), "weekly_corr": corr}
    if corr >= 0.5:
        return [insight("Q7", "STORE", None, "NORMAL", ev, None, f"리뷰 수는 손님 수를 잘 따라가요(주별 상관 {corr:.2f}).")]
    return [insight("Q7", "STORE", None, "FLAGGED", ev, None,
                    f"리뷰 수가 손님 수와 따로 움직여요(주별 상관 {corr:.2f}). 손님이 늘어도 리뷰가 늘지 않는 시기가 있어요.")]


def q8_period_rate(bills, rv):
    bm = bills.assign(m=bills["ts"].dt.to_period("M")).groupby("m").size()
    rm = rv.assign(m=rv["date"].dt.to_period("M")).groupby("m").size()
    overall = rm.sum() / bm.sum()
    if rm.sum() < 3 * MIN_N:
        return [insight("Q8", "STORE", None, "INSUFFICIENT", {"reviews": int(rm.sum())}, None, "리뷰가 적어 월별 작성률을 비교하기 어려워요.")]
    out = []
    for m, nb in bm.items():
        nr = int(rm.get(m, 0))
        p = binomtest(nr, int(nb), overall).pvalue
        rate = nr / nb * 1000
        ev = {"bills": nb, "reviews": nr, "rate_per_1000": rate, "overall_rate_per_1000": overall * 1000}
        if p < ALPHA / len(bm):
            word = "많았" if rate > overall * 1000 else "적었"
            out.append(insight("Q8", "MONTH", str(m), "FLAGGED", ev, p,
                               f"{m.month}월에는 손님 1,000명당 리뷰가 {rate:.0f}건으로 평소({overall * 1000:.0f}건)보다 {word}어요."))
        else:
            out.append(insight("Q8", "MONTH", str(m), "NORMAL", ev, p, f"{m.month}월 리뷰 작성률({rate:.0f}건/1,000명)은 평소 수준이에요."))
    return out


def q9_period_change(bills, rv):
    wk = weekly(bills, rv)
    wk["change"] = wk["bills"].pct_change()
    big = wk[wk["change"].abs() >= 0.15].iloc[0:]
    base_neg = rv["negative"].mean() if len(rv) else 0
    if len(big) == 0:
        return [insight("Q9", "STORE", None, "NORMAL", {"weeks": len(wk)}, None, "매출이 크게(15% 이상) 변한 주가 없었어요.")]
    out = []
    for w, r in big.iterrows():
        before = rv[(rv["date"] >= (w - 2).start_time) & (rv["date"] <= (w - 1).end_time)]
        after = rv[(rv["date"] >= w.start_time) & (rv["date"] <= w.end_time)]
        n, k = len(after), int(after["negative"].sum())
        ev = {"week_start": str(w.start_time.date()), "sales_change_pct": r["change"] * 100,
              "reviews_after": n, "negative_after": k, "negative_before": int(before["negative"].sum()),
              "reviews_before": len(before), "store_negative_rate": base_neg}
        label = f"{w.start_time.month}월 {w.start_time.day}일 주"
        if n < MIN_N:
            out.append(insight("Q9", "WEEK", str(w), "INSUFFICIENT", ev, None,
                               f"{label}에 매출이 {r['change'] * 100:+.0f}% 변했지만, 그 주 리뷰가 {n}건이라 반응을 판단하기 어려워요."))
            continue
        p = binomtest(k, n, base_neg, alternative="greater").pvalue
        if p < ALPHA / len(big):
            out.append(insight("Q9", "WEEK", str(w), "FLAGGED", ev, p,
                               f"{label}에 매출이 {r['change'] * 100:+.0f}% 변했고, 그 주 리뷰 {n}건 중 {k}건이 불만이었어요(평소 {pct(base_neg)})."))
        else:
            out.append(insight("Q9", "WEEK", str(w), "NORMAL", ev, p,
                               f"{label}에 매출이 {r['change'] * 100:+.0f}% 변했지만 리뷰 반응은 평소와 같았어요."))
    return out


def r1_repeat_complaints(rv):
    neg = rv[rv["neg_types"].map(len) > 0].explode("neg_types")
    if len(rv) == 0:
        return []
    span = (rv["date"].max() - rv["date"].min()).days + 1
    out = []
    for t, g in neg.groupby("neg_types"):
        d = np.sort(g["date"].values)
        base = len(d) / span * 56                      # 평소 8주 기대 건수
        best = None
        for i in range(len(d)):
            j = np.searchsorted(d, d[i] + np.timedelta64(56, "D"), side="right")
            k = j - i
            p = poisson.sf(k - 1, base)
            if best is None or p < best["p"]:
                best = dict(start=pd.Timestamp(d[i]).date(), end=pd.Timestamp(d[j - 1]).date(), count=k, expected=base, p=p)
        ev = {k: (str(v) if k in ("start", "end") else v) for k, v in best.items() if k != "p"}
        ev["total"] = len(d)
        if best["count"] >= 3 and best["p"] < ALPHA / len(rules.NEG_RULES):
            a, b = best["start"], best["end"]
            out.append(insight("R1", "NEG_TYPE", t, "FLAGGED", ev, best["p"],
                               f"{a.month}/{a.day}~{b.month}/{b.day}에 '{t}' 불만이 {best['count']}건 몰렸어요(평소 8주에 {base:.1f}건 수준)."))
        else:
            out.append(insight("R1", "NEG_TYPE", t, "NORMAL", ev, best["p"], f"'{t}' 불만은 꾸준히 드문드문 있어요(전체 {len(d)}건)."))
    return out


# ------------------------------------------------------------------ 전체

def run(sales, reviews, groups, cutoff):
    s, bills, units, rv = prepare(sales, reviews, groups, cutoff)
    names = {g["id"]: g["name"] for g in groups}
    out = []
    out += q1_menu_share(units, rv)
    out += q2_menu_rate(units, rv)
    out += q3_menu_change(s, units, rv, names)
    out += q4_slot_share(bills, rv)
    out += q5_slot_rate(bills, rv)
    out += q6_slot_change(bills, rv)
    out += q7_period_share(bills, rv)
    out += q8_period_rate(bills, rv)
    out += q9_period_change(bills, rv)
    out += r1_repeat_complaints(rv)
    period = (s["date"].min().date(), s["date"].max().date())
    summary = dict(period=[str(period[0]), str(period[1])], bills=len(bills), sales_lines=len(s),
                   reviews_used=len(rv), flagged=sum(1 for i in out if i["result"] == "FLAGGED"))
    return out, summary
