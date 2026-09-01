# DDD·헥사고날 전환 가이드

이 문서는 [ADR-001](./adr/ADR-001-ddd-hexagonal-and-relational-data.md)의 실행 가이드다.

## 유비쿼터스 언어

- **Profile**: 가족 구성원. 로그인 계정(User)과 같지 않다.
- **CoachRun**: 주간 코치가 제안을 만든 한 번의 실행. 승인 전에는 Mission이 아니다.
- **Proposal item**: 승인 대기 중인 코치 제안의 항목.
- **Mission**: 부모가 직접 만들었거나 승인된 제안에서 만들어진 가족의 실행 과제.
- **Verification**: VIDEO_PROGRESS, TIMER, SELF_REPORT, PARENT_CONFIRM 중 완료를 뒷받침하는 근거.

이 용어는 API, DB 컬럼, 코드, 기획서에서 같은 뜻으로 사용한다.

## 첫 번째 구현 단위: Coaching

`CoachRun`의 승인 규칙은 이미 테스트로 보장되고 있으므로, 아래 순서로 옮긴다.

1. `ApproveCoachRunUseCase`와 `CoachRunRepository`를 `application.port`에 정의한다.
2. `CoachRun.approve(approverId, approvedAt)`에 부모 승인·상태 전이 규칙을 둔다.
3. JPA 엔티티/리포지터리/컨트롤러는 adapter로 옮기고 mapper만 통해 도메인 모델과 변환한다.
4. 승인된 proposal item만 `CreateMissionUseCase`에 전달한다.

application service는 트랜잭션과 포트 호출만 조합한다. `if`로 표현되는 정책은 가능한 한
`CoachRun`, `Mission` 같은 도메인 객체의 메서드로 넣는다.

## 데이터 이행 안전장치

- 새 테이블을 먼저 추가한다. 기존 JSONB를 즉시 삭제하지 않는다.
- backfill은 검증 가능한 배치로 실행하고, 행 수·항목 수·값 해시를 비교한다.
- 일정 기간 새·기존 구조를 함께 읽어 결과를 비교한다.
- 외부 API 응답 DTO와 DB 스키마를 도메인 객체에 노출하지 않는다.

구체적인 테이블/컬럼 목록과 제거 기준은 [ADR-001](./adr/ADR-001-ddd-hexagonal-and-relational-data.md)을 따른다.
