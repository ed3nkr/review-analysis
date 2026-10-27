package kr.dongguk.backend.place;

public record PlaceCandidate(
        String kakaoId,       // 카카오 장소 ID (화면에서 후보 구분용, 저장 안 함)
        String name,          // 상호
        String category,      // 예: 일식 > 돈까스,우동 (50자 이내로 줄임)
        String address,       // 지번 주소
        String roadAddress,   // 도로명 주소
        String phone,         // 없을 수 있음
        String x,             // 경도
        String y,             // 위도
        String placeUrl       // 카카오맵 링크 (점주가 눌러서 확인)
) {}