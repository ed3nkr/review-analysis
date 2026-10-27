# frontend (React + Vite)

디자인: Figma "리뷰와 매출" (매장 · 매장 등록 · 상태 화면) 기준

```bash
npm install      # 처음 한 번 (react, react-dom, lucide-react, vite)
npm run dev      # http://localhost:5173
```
- 백엔드(Spring, 8080)를 먼저 켜 두어야 함. `/api` 요청은 vite.config.js 의 proxy 로 8080 에 전달됨.
- 아이콘은 Figma 디자인에 쓰인 Lucide 아이콘을 `lucide-react` 패키지로 사용.
