# 우리가족 체력키움 (family-fitness)

국민체력100 공개데이터 기반 가족 체력 서비스.
국민대학교 2026-2학기 학생설계형 알파프로젝트 / 한국스포츠정책과학원 공공데이터 활용 경진대회 출품작.

> 아이와 부모가 함께 운동하는 웹 서비스(PWA). 국민체력100 세대별 데이터로 "우리 가족 체력 지도"를 그리고,
> AI 코치가 아이 수준에 맞는 운동 영상을 골라 주고, 보호자가 승인해야 미션이 만들어집니다.

## 지금 상태

착수 직후입니다. 백엔드 뼈대와 스키마가 서 있고, 화면·API 설계가 문서와 보드에 정리돼 있습니다.

| | |
|---|---|
| 백엔드 | [`backend/`](./backend) — Java 25 + Spring Boot 4.1.1. `./gradlew build` 통과, 테스트 14개 실제 DB 위에서 통과 |
| ERD | [`docs/erd.md`](./docs/erd.md) — 테이블 14개 · 모듈 4개 |
| 유저 플로우 · API | [`docs/user-flow.md`](./docs/user-flow.md) |
| FigJam 보드 | https://www.figma.com/board/w0ap0PjCQhcgbZc7zSyTVf |

구현 진행 상황은 [`backend/README.md`](./backend/README.md) 의 모듈 표에 있습니다.

## 개발 시작하기

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'   # Docker 만 있으면 됩니다
open http://localhost:8080/swagger-ui.html
```

## 구조

```
backend/    Spring Boot (모듈러 모놀리스, Spring Modulith)
docs/       ERD · 유저 플로우 · API 명세
.github/    CI (Gradle 빌드 + 포맷 검사)
```

## 팀

유범익 (PM · AI/ML) · 이상진 (Frontend · UX · 활동량 연동) · 최비성 (Backend · 데이터)
지도교수: 김정우 (교양대학)
