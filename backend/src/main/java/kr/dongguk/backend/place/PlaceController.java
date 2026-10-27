package kr.dongguk.backend.place;

import kr.dongguk.backend.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final KakaoLocalSearchClient kakaoClient;

    @GetMapping("/search")
    public List<PlaceCandidate> search(@RequestParam String query) {
        String q = query.strip();
        if (q.length() < 2) {
            throw new BadRequestException("검색어는 2글자 이상 입력해 주세요");
        }
        return kakaoClient.search(q);
    }
}