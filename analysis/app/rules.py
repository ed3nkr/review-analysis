"""리뷰 분석 규칙 (1차: 규칙 기반 — 나중에 BERT 모델로 교체할 자리)

가게마다 다른 것(메뉴군 이름·별칭)은 DB에서 읽고, 여기에는 어느 가게에나 통하는 규칙만 둔다.
"""
import re

MODEL_VERSION = "rule-v1"

# 노이즈(분석에서 뺄 리뷰) — exclude_reason 코드
NOISE_RULES = [
    (re.compile(r"https?://|blog\.|\.com|협찬|체험단|원고료"), "AD"),
    (re.compile(r"이벤트 참여|리뷰 ?이벤트|이벤트로 받"), "EVENT"),
    (re.compile(r"○○|다른 가게|옆 가게|여기 말고"), "OTHER_STORE"),
]

# 부정 유형 6가지 (neg_types 에 이 이름으로 저장)
NEG_RULES = {
    "맛": re.compile(r"식어|짜요|짰어|눅눅|싱거|맛이 덜|불어|퍼졌|질겼|말라|차가웠|밍밍|미지근|딱딱|묽어|달아요|별로였|맛없"),
    "가격": re.compile(r"비싸|가격이 .*올|가격 대비 별로|양에 비해"),
    "서비스": re.compile(r"불친절|누락|안 주|무성의|응대|불쾌"),
    "위생": re.compile(r"머리카락|끈적|깨져|지저분|벌레|비위생"),
    "대기시간": re.compile(r"늦게 나|대기가 길|기다렸|웨이팅이 길|오래 걸"),
    "분위기": re.compile(r"시끄|좁아|추웠|더웠|냄새"),
}
PRAISE_HINT = re.compile(r"맛있|바삭|쫄깃|탱글|진하|시원|부드러|두툼|생각나|딱이|꾸덕|달달|향이 좋|최고|존맛|맛나")

# 문장 나누기 (칭찬/불만을 문장 단위로 판단)
SENTENCE_SPLIT = re.compile(r"[.!?,\n]|다만|그런데|근데|ㅎㅎ|ㅠ")

# 리뷰 본문에서 시간대 추정 (방문 표시가 없을 때만)
LUNCH_WORDS = re.compile(r"점심|런치")
DINNER_WORDS = re.compile(r"저녁|디너|퇴근")

# 연계 분석에서 뺄 공통 메뉴군 (시판 음료·술은 리뷰-매출 비교 대상이 아님)
SKIP_GROUPS = {"음료", "주류"}
