"""
네이버 플레이스 방문자 리뷰 수집기 (졸업작품용 — 본인 매장, 소량, 천천히)

  python naver_reviews.py --name "백소정 당산점" --phone 02-2068-5333 --address "서울 영등포구 양평로 68"
                                                     # 플레이스 ID를 자동으로 찾아서 수집
  python naver_reviews.py 1234567890                 # ID를 알면 바로: 최근 리뷰 300건 → reviews_1234567890.csv
  python naver_reviews.py 1234567890 --since 2026-10-20  # 이 날짜 이후 리뷰만 (증분 수집)
  python naver_reviews.py 1234567890 --since 2026-04-01 --max 3000 --show --wait-human
                                                     # 처음 한 번 과거 리뷰 받기: 사람 확인 화면이 뜨면
                                                     # 창에서 직접 인증할 때까지 기다렸다가 이어서 수집
  python naver_reviews.py 1234567890 --max 100 --show  # 브라우저 창을 띄워서 보면서
  python naver_reviews.py 1234567890 --dump            # 받은 원본 JSON도 저장 (문제 생기면 이 파일로 진단)

증분 수집: 매번 최신 리뷰 첫 화면(10건 남짓)부터 읽고, --since 날짜보다 오래된 리뷰가 나오면 멈춘다.
          네이버가 '실제 사용자 확인' 화면을 띄우면 우회하지 않는다. 기본은 즉시 멈추고 모은 것만 저장,
          --wait-human 이면 사람이 창에서 직접 인증할 때까지 기다렸다가 이어서 수집한다.

방식: 실제 브라우저(Playwright)로 모바일 리뷰 페이지를 열고 '더보기'를 누르며,
      페이지가 네이버 서버에서 받아 오는 리뷰 JSON을 그대로 가로채 읽는다.
      화면의 HTML 구조(자주 바뀜)가 아니라 데이터(JSON)를 읽어서 상대적으로 덜 깨진다.

출력 CSV는 시스템의 리뷰 업로드 형식과 같다:
  review_id, written_date, visit_date, rating, visit_slot, content, keywords, author_hash
작성자 닉네임은 파일에도 남기지 않고 해시로만 쓴다.
"""
import argparse
import csv
import hashlib
import json
import random
import re
import sys
import time
from datetime import date

URL = "https://m.place.naver.com/restaurant/{pid}/review/visitor?reviewSort=recent"
FIELDS = ["review_id", "written_date", "visit_date", "visit_time", "rating", "visit_slot", "content", "keywords",
          "author_hash"]
KST = __import__("datetime").timezone(__import__("datetime").timedelta(hours=9))


# ---------- JSON → 리뷰 ----------

def find_review_items(obj):
    """응답 JSON·페이지 내장 데이터 어디에 있든 '리뷰처럼 생긴 dict'(body + visited/created)를 하나씩 찾는다."""
    if isinstance(obj, dict):
        if "body" in obj and ("visited" in obj or "created" in obj):
            yield obj
        for v in obj.values():
            yield from find_review_items(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from find_review_items(v)


APOLLO_JS = "() => window.__APOLLO_STATE__ || null"
CAPTCHA_WORDS = ("실제 사용자", "보안 확인", "자동입력 방지", "자동 입력 방지", "captcha", "로봇이 아닙니다",
                 "정답을 입력", "영수증 이미지", "확인을 위해")


def expand(obj, state, depth=4):
    """페이지 데이터의 참조({"__ref": "키"})를 실제 내용으로 바꿔 펼친다 (방문 시간대 태그 등이 따로 저장돼 있음)"""
    if depth < 0:
        return obj
    if isinstance(obj, dict):
        ref = obj.get("__ref")
        if ref and isinstance(state, dict) and len(obj) == 1:
            return expand(state.get(ref, obj), state, depth - 1)
        return {k: expand(v, state, depth - 1) for k, v in obj.items()}
    if isinstance(obj, list):
        return [expand(v, state, depth - 1) for v in obj]
    return obj


def blocked(page):
    """네이버가 사람 확인(CAPTCHA) 화면을 띄웠는지 — 본문, 페이지 안의 틀(iframe), 새로 뜬 창까지 확인"""
    try:
        pages = page.context.pages
    except Exception:
        pages = [page]
    for pg in pages:
        try:
            for fr in pg.frames:
                if "captcha" in (fr.url or "").lower():
                    return True
                try:
                    text = fr.evaluate("() => document.body ? document.body.innerText : ''")
                except Exception:
                    continue
                if any(w.lower() in text.lower() for w in CAPTCHA_WORDS):
                    return True
        except Exception:
            continue
    return False


def wait_enter(message):
    """터미널에서 사람이 Enter를 누를 때까지 기다림. q 를 입력하면 그만두기 → False"""
    print(message)
    try:
        answer = input("  인증을 마쳤으면 Enter, 그만두려면 q + Enter: ")
    except EOFError:
        return False
    return answer.strip().lower() != "q"


def page_state(page):
    """페이지 HTML에 미리 실려 온 데이터 (네이버 플레이스는 첫 화면을 이렇게 보냄)"""
    try:
        return page.evaluate(APOLLO_JS)
    except Exception:
        return None


def parse_day(s, today):
    """'6.29.일' / '24.12.3.화' / '2026.6.29' / '2026-06-29' → '2026-06-29'. 연도가 없으면 올해(미래면 작년)."""
    if not s:
        return ""
    nums = [int(n) for n in re.findall(r"\d+", str(s))]
    try:
        if len(nums) >= 3:
            y, m, d = nums[:3]
            if y < 100:
                y += 2000
        elif len(nums) == 2:
            m, d = nums
            y = today.year if (m, d) <= (today.month, today.day) else today.year - 1
        else:
            return ""
        return date(y, m, d).isoformat()
    except ValueError:
        return ""


def strings_in(obj, skip=("body",)):
    """항목 안의 모든 문자열 (본문 제외) — 방문 시간대 태그 찾기용"""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k not in skip:
                yield from strings_in(v, skip)
    elif isinstance(obj, list):
        for v in obj:
            yield from strings_in(v, skip)
    elif isinstance(obj, str):
        yield obj


def visit_moment(item):
    """방문 시각 (영수증 인증 리뷰에 있는 representativeVisitDateTime, UTC) → 한국 시각 datetime 또는 None"""
    from datetime import datetime
    v = item.get("representativeVisitDateTime")
    if not v:
        return None
    try:
        return datetime.fromisoformat(str(v).replace("Z", "+00:00")).astimezone(KST)
    except ValueError:
        return None


def to_row(item, today, cutoff="16:00"):
    author = item.get("author") or {}
    if isinstance(author, dict):
        who = str(author.get("id") or author.get("nickname") or author.get("__ref") or "")
    else:
        who = str(author)
    keywords = []
    for k in item.get("votedKeywords") or []:
        if isinstance(k, dict):
            name = k.get("displayName") or k.get("name")
            if name:
                keywords.append(name)

    # 점심/저녁: 방문 시각이 있으면 매장 기준 시각(cutoff)으로 나눔. 없으면 방문 태그 글자에서 찾음
    moment = visit_moment(item)
    visit_time, slot = "", ""
    if moment:
        visit_time = moment.strftime("%H:%M")
        slot = "점심에 방문" if visit_time < cutoff else "저녁에 방문"
    else:
        for s in strings_in(item.get("visitCategories") or []):
            if "점심" in s or s.upper() == "LUNCH":
                slot = "점심에 방문"
                break
            if "저녁" in s or s.upper() == "DINNER":
                slot = "저녁에 방문"
                break

    rating = item.get("rating")
    return {
        "review_id": item.get("id") or "",
        "written_date": parse_day(item.get("created"), today) or (moment.date().isoformat() if moment else ""),
        "visit_date": moment.date().isoformat() if moment else parse_day(item.get("visited"), today),
        "visit_time": visit_time,
        "rating": rating if isinstance(rating, (int, float)) and 1 <= rating <= 5 else "",
        "visit_slot": slot,
        "content": (item.get("body") or "").strip(),
        "keywords": ";".join(keywords),
        "author_hash": hashlib.sha256(who.encode()).hexdigest()[:16] if who else "",
    }


# ---------- 플레이스 ID 찾기 ----------

SEARCH_URLS = [
    "https://m.place.naver.com/place/list?query={q}",
    "https://m.search.naver.com/search.naver?query={q}",
]
HOME_URL = "https://m.place.naver.com/restaurant/{pid}/home"
PLACE_LINK = re.compile(r"/(?:restaurant|place|cafe|bakery|bar)/(\d{5,})")


def find_place_items(obj):
    """JSON·페이지 데이터에서 '가게처럼 생긴 dict'(id + name + 주소)를 모두 찾는다."""
    if isinstance(obj, dict):
        if obj.get("id") and obj.get("name") and (obj.get("roadAddress") or obj.get("address")):
            yield obj
        for v in obj.values():
            yield from find_place_items(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from find_place_items(v)


def ids_from_links(links):
    """화면의 링크에서 플레이스 ID 뽑기: [(href, 글자)] → {id: 이름 후보}"""
    found = {}
    for href, text in links:
        m = PLACE_LINK.search(href or "")
        if m:
            name = (text or "").strip().split("\n")[0][:40]
            if m.group(1) not in found or (name and not found[m.group(1)]):
                found[m.group(1)] = name
    return found


def digits(s):
    return re.sub(r"\D", "", str(s or ""))


def squash(s):
    """주소·이름 비교용: 공백·괄호 내용·기호 제거, 시/도 표기 통일"""
    s = re.sub(r"\(.*?\)", "", str(s or ""))
    s = s.replace("서울특별시", "서울").replace("특별시", "").replace("광역시", "")
    return re.sub(r"[\s\-·,]", "", s)


def pick_place(cands, name, phone, address):
    """후보 중 우리 가게 고르기: 전화번호 > 도로명주소 > 이름 순으로 맞춰 본다. (점수, 후보) 목록 반환"""
    scored = []
    for c in cands:
        score = 0
        cphones = [digits(c.get(k)) for k in ("phone", "virtualPhone", "tel") if c.get(k)]
        if phone and digits(phone) in cphones:
            score += 100
        addrs = [squash(c.get(k)) for k in ("roadAddress", "address", "fullAddress") if c.get(k)]
        a = squash(address)
        if a and any(x and (a in x or x in a) for x in addrs):
            score += 50
        if name and squash(c.get("name")) == squash(name):
            score += 30
        elif name and c.get("name") and (squash(name) in squash(c.get("name")) or squash(c.get("name")) in squash(name)):
            score += 10
        scored.append((score, c))
    scored.sort(key=lambda t: -t[0])
    return scored


def place_detail(page, pid):
    """가게 홈 화면을 열어 이름·주소·전화 읽기"""
    page.goto(HOME_URL.format(pid=pid), wait_until="domcontentloaded", timeout=30000)
    page.wait_for_timeout(1500)
    for c in find_place_items(page_state(page)):
        if str(c.get("id")) == str(pid):
            return c
    return None


def find_place_id(name, phone, address, show, dump=False):
    """네이버 검색 결과에서 이름·전화·주소가 맞는 가게의 플레이스 ID를 찾는다."""
    from urllib.parse import quote
    from playwright.sync_api import sync_playwright

    cands = {}       # id → 가게 정보 dict
    link_ids = {}    # 정보 없이 링크로만 찾은 id → 이름
    debug = []

    def add(data):
        for c in find_place_items(data):
            cands.setdefault(str(c["id"]), c)

    def on_response(resp):
        if "graphql" not in resp.url and "search" not in resp.url:
            return
        try:
            data = resp.json()
        except Exception:
            return
        if dump:
            debug.append({"url": resp.url, "data": data})
        add(data)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not show)
        ctx = browser.new_context(**p.devices["iPhone 13"], locale="ko-KR")
        page = ctx.new_page()
        page.on("response", on_response)
        query = name if not address else f"{name} {' '.join(address.split()[:2])}"   # 이름 + 시·구

        for i, url in enumerate(SEARCH_URLS):
            print(f"검색({i + 1}): {query}")
            page.goto(url.format(q=quote(query)), wait_until="domcontentloaded", timeout=30000)
            page.wait_for_timeout(3000)
            state = page_state(page)
            if dump:
                debug.append({"url": page.url, "title": page.title(), "apollo": state})
            add(state)
            links = page.eval_on_selector_all("a[href]", "els => els.map(e => [e.href, e.innerText])")
            for pid, nm in ids_from_links(links).items():
                if pid not in cands:
                    link_ids.setdefault(pid, nm)
            if not cands and not link_ids:
                page.screenshot(path=f"debug_search_{i + 1}.png", full_page=True)
            if cands or link_ids:
                break

        # 링크로만 찾은 후보는 홈 화면에서 주소·전화를 읽어 온다 (최대 5곳)
        for pid in list(link_ids)[:5]:
            print(f"  후보 {pid} 정보 읽는 중…")
            d = place_detail(page, pid)
            cands[pid] = d or {"id": pid, "name": link_ids[pid]}
        browser.close()

    if dump:
        with open("raw_search.json", "w", encoding="utf-8") as f:
            json.dump(debug, f, ensure_ascii=False, indent=1)
        print("검색 원본 저장: raw_search.json")

    print(f"후보 {len(cands)}개")
    scored = pick_place(list(cands.values()), name, phone, address)
    for sc, c in scored[:5]:
        print(f"  후보 {c['id']}  {c.get('name')}  {c.get('roadAddress') or c.get('address') or ''}  "
              f"{c.get('phone') or c.get('virtualPhone') or ''}  (점수 {sc})")
    if not scored or scored[0][0] < 50:
        return None   # 전화나 주소가 맞는 후보가 없으면 자동으로 고르지 않음
    if len(scored) > 1 and scored[1][0] == scored[0][0]:
        return None   # 똑같이 맞는 후보가 둘 이상이면 고르지 않음
    return str(scored[0][1]["id"])


# ---------- 리뷰 수집 ----------

def crawl(place_id, max_reviews, show, dump, since=None, wait_human=False, cutoff="16:00"):
    from playwright.sync_api import sync_playwright

    today = date.today()
    reviews = {}          # review_id(또는 내용 해시) → row
    raw = []

    def take(data):
        for it in find_review_items(data):
            row = to_row(expand(it, data), today, cutoff)
            if not row["written_date"] or not (row["content"] or row["keywords"]):
                continue
            key = row["review_id"] or hashlib.sha256(json.dumps(row, ensure_ascii=False).encode()).hexdigest()
            reviews.setdefault(key, row)

    def on_response(resp):
        if "graphql" not in resp.url:
            return
        try:
            data = resp.json()
        except Exception:
            return
        if dump:
            raw.append({"url": resp.url, "data": data})
        take(data)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not show)
        ctx = browser.new_context(**p.devices["iPhone 13"], locale="ko-KR")
        page = ctx.new_page()
        page.on("response", on_response)

        print(f"열기: {URL.format(pid=place_id)}")
        page.goto(URL.format(pid=place_id), wait_until="domcontentloaded", timeout=30000)
        page.wait_for_timeout(3000)
        state = page_state(page)          # 첫 페이지 리뷰는 HTML에 실려 옴
        if dump:
            raw.append({"url": "__APOLLO_STATE__", "data": state})
        take(state)
        print(f"  첫 화면에서 {len(reviews)}건")

        def reached_since():
            """since 보다 오래된 리뷰까지 내려왔으면 더 볼 필요 없음"""
            if not since or not reviews:
                return False
            return min(r["visit_date"] or r["written_date"] for r in reviews.values()) < since

        def handle_block(where):
            """사람 확인 화면: 기본은 멈춤. --wait-human 이면 사람이 창에서 직접 인증하고 Enter 를 누를 때까지 기다림"""
            if not wait_human:
                return f"사람 확인 화면이 떠서 멈췄어요 ({where}). 우회하지 않고 지금까지 모은 것만 저장해요"
            ok = wait_enter(f"\n  >>> 사람 확인 화면이 떴어요 ({where}). 브라우저 창에서 직접 인증해 주세요.")
            if not ok:
                return "그만두기를 골라서 멈췄어요. 지금까지 모은 것만 저장해요"
            page.wait_for_timeout(2000)
            take(page_state(page))
            return None

        stopped = handle_block("첫 화면") if blocked(page) else None
        stalls = 0
        while not stopped and len(reviews) < max_reviews and stalls < 3 and not reached_since():
            before = len(reviews)
            page.mouse.wheel(0, 20000)
            page.wait_for_timeout(800)
            more = page.locator("a:has-text('더보기'), button:has-text('더보기')").last
            try:
                if more.is_visible():
                    more.click(timeout=3000)
            except Exception:
                pass
            page.wait_for_timeout(int(random.uniform(3000, 5000)))   # 천천히 (서버 부담·차단 방지)
            stalls = stalls + 1 if len(reviews) == before else 0
            oldest = min((r["visit_date"] or r["written_date"] for r in reviews.values()), default="")
            print(f"  모은 리뷰 {len(reviews)}건  (가장 오래된 리뷰 {oldest})")
            if blocked(page):
                stopped = handle_block(f"{len(reviews)}건 모은 뒤")
                stalls = 0
            elif wait_human and stalls >= 2:
                # 감지는 못 했지만 리뷰가 안 늘어남 → 인증 화면이 떠 있을 수 있으니 사람에게 확인
                ok = wait_enter(f"\n  >>> {len(reviews)}건에서 리뷰가 더 안 늘어요. 창에 사람 확인 화면이 있으면 직접 인증해 주세요.\n"
                                "      (화면에 '더보기'가 없고 목록이 끝났다면 q 를 눌러 마치세요)")
                if not ok:
                    stopped = "그만두기를 골라서 멈췄어요. 지금까지 모은 것만 저장해요"
                else:
                    page.wait_for_timeout(2000)
                    take(page_state(page))
                    stalls = 0
        if stopped:
            print("  " + stopped)

        if not reviews:
            page.screenshot(path=f"debug_{place_id}.png", full_page=True)
        browser.close()

    if dump:
        with open(f"raw_{place_id}.json", "w", encoding="utf-8") as f:
            json.dump(raw, f, ensure_ascii=False, indent=1)
        print(f"원본 JSON 저장: raw_{place_id}.json ({len(raw)}개 응답)")

    rows = list(reviews.values())
    if since:
        rows = [r for r in rows if (r["visit_date"] or r["written_date"]) >= since]
    rows = rows[:max_reviews]
    rows.sort(key=lambda r: r["visit_date"] or r["written_date"], reverse=True)
    return rows


def write_csv(path, rows):
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)


def main():
    ap = argparse.ArgumentParser(description="네이버 플레이스 방문자 리뷰 → CSV")
    ap.add_argument("place_id", nargs="?", help="네이버 플레이스 ID (모르면 --name 으로 자동 찾기)")
    ap.add_argument("--name", help="가게 이름 (플레이스 ID 자동 찾기용)")
    ap.add_argument("--phone", help="가게 전화번호 (자동 찾기 정확도↑)")
    ap.add_argument("--address", help="가게 도로명 주소 (자동 찾기 정확도↑)")
    ap.add_argument("--find-only", action="store_true", help="ID만 찾고 수집은 안 함")
    ap.add_argument("--max", type=int, default=300, help="최대 리뷰 수 (기본 300)")
    ap.add_argument("--cutoff", default="16:00", help="이 시각 전 방문 = 점심, 이후 = 저녁 (매장 설정과 같게, 기본 16:00)")
    ap.add_argument("--since", help="이 날짜(YYYY-MM-DD) 이후 리뷰만. 더 오래된 리뷰가 나오면 멈춤")
    ap.add_argument("--out", help="저장할 CSV 경로 (기본 reviews_<ID>.csv)")
    ap.add_argument("--show", action="store_true", help="브라우저 창 보이기")
    ap.add_argument("--wait-human", action="store_true",
                    help="사람 확인 화면이 뜨면 창에서 직접 인증할 때까지 기다림 (--show 와 함께)")
    ap.add_argument("--dump", action="store_true", help="받은 원본 JSON도 저장")
    a = ap.parse_args()

    place_id = a.place_id
    if not place_id:
        if not a.name:
            sys.exit("플레이스 ID나 --name(가게 이름) 중 하나는 있어야 해요.")
        place_id = find_place_id(a.name, a.phone, a.address, a.show, a.dump)
        if not place_id:
            sys.exit("전화번호나 주소가 맞는 가게를 하나로 정하지 못했어요. 위 후보 중 맞는 ID를 골라 "
                     "'python naver_reviews.py <ID>' 로 실행해 주세요. (후보가 없으면 debug_search_*.png 와 --dump 로 만든 raw_search.json 을 보내 주세요)")
        print(f"찾은 플레이스 ID: {place_id}")
        if a.find_only:
            return
    if not place_id.isdigit():
        sys.exit("플레이스 ID는 숫자예요. 예: https://map.naver.com/p/entry/place/1234567890 → 1234567890")

    if a.since and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", a.since):
        sys.exit("--since 는 2026-10-20 처럼 써 주세요.")
    if a.wait_human and not a.show:
        sys.exit("--wait-human 은 창이 보여야 인증할 수 있어서 --show 와 같이 써 주세요.")
    if not re.fullmatch(r"\d{2}:\d{2}", a.cutoff):
        sys.exit("--cutoff 은 15:30 처럼 써 주세요.")
    rows = crawl(place_id, a.max, a.show, a.dump, a.since, a.wait_human, a.cutoff)
    if not rows and a.since:
        print(f"{a.since} 이후 새 리뷰가 없어요.")
        return
    if not rows:
        sys.exit("리뷰를 하나도 못 읽었어요. --show --dump 로 다시 실행해서 화면과 raw_*.json 을 확인해 주세요. "
                 f"(화면 캡처: debug_{place_id}.png)")

    out = a.out or f"reviews_{place_id}.csv"
    try:
        write_csv(out, rows)
    except PermissionError:
        # 엑셀 등에서 파일을 열어 두면 윈도우가 잠가 둠 → 모은 결과를 잃지 않게 다른 이름으로 저장
        out = out.removesuffix(".csv") + time.strftime("_%H%M%S") + ".csv"
        print(f"원래 파일이 다른 프로그램(엑셀 등)에 열려 있어서 {out} 로 저장해요.")
        write_csv(out, rows)
    days = [r["visit_date"] or r["written_date"] for r in rows]
    print(f"\n저장: {out}  리뷰 {len(rows)}건  기간 {min(days)} ~ {max(days)}  "
          f"본문 있음 {sum(1 for r in rows if r['content'])}  별점 있음 {sum(1 for r in rows if r['rating'] != '')}  "
          f"점심/저녁 표시 {sum(1 for r in rows if r['visit_slot'])}")


if __name__ == "__main__":
    main()
