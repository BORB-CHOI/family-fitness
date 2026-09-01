# ADR-001: DDD·헥사고날 구조와 JSONB 최소화

**상태:** Accepted  
**일자:** 2026-09-01

## 맥락

현재 스키마는 14개 테이블로 작고, `fitness_tests.items`, `predictions.*`,
`missions.participants`, `coach_runs.*` 등에서 JSONB를 사용한다. 이는 MVP의 화면 단위
읽기에는 합리적이지만, 참여자별 완료 처리·항목별 체력 추세·코치 제안 감사처럼 개별 요소를
독립적으로 변경하거나 조회하는 규칙에는 맞지 않는다.

JSONB를 완전히 없애면서 14개 테이블을 유지할 수는 없다. 독립적인 수명·식별자·동시성 규칙을
가진 값을 행으로 분리하면 테이블 수는 늘어난다. 테이블 수가 아니라 **변경 단위와 불변식**을
기준으로 분리한다.

## 결정

1. 단일 Spring Boot 애플리케이션과 Spring Modulith의 `identity`, `fitness`, `activity`,
   `coaching` 경계는 유지한다. 지금은 Gradle 서브프로젝트로 나누지 않는다. Python AI 서비스는
   별도 저장소·배포 단위로 둔다.
2. 각 모듈을 `domain`, `application`, `adapter`로 점진적으로 옮긴다. 도메인은 Spring·JPA·HTTP에
   의존하지 않고, application은 포트만 의존하며, adapter만 Spring/JPA/웹/AI를 안다.
3. JSONB 제거는 한 번의 재작성 대신 새 정규화 테이블을 추가하고 읽기 전환 후 기존 컬럼을 제거한다.
4. `coach_runs.steps`는 업무 데이터가 아니라 관찰성 데이터이므로 애플리케이션 로그/추적으로 보낸다.
   영속하지 않아도 되는 데이터를 테이블로 바꾸지 않는다.
5. PostgreSQL은 하나만 사용하고 `pgvector`를 설치한다. `ai_documents`의 임베딩 생성·검색은
   Python AI 서비스가 담당하며, Spring Boot는 업무 데이터와 AI 결과의 승인·저장만 담당한다.

## JSONB별 결정

| 현재 컬럼 | 결정 | 새 구조 | 이유 |
|---|---|---|---|
| `fitness_tests.items` | 제거 | `fitness_test_items` | 항목별 추세·유효성·규준 재검증이 필요하다. `FitnessTest` 안에서만 생성·변경한다. |
| `predictions.curves` | 제거 | `prediction_points` | 시나리오·항목·예측 시점별 값을 조회할 수 있어야 한다. |
| `predictions.input_snapshot`, `metrics` | 제거 | 모델 버전·입력 측정 FK만 유지 | 재현에 필요한 원천 데이터는 이미 `fitness_tests`에 있다. 모델 검증 지표는 모델 배포 산출물로 관리한다. |
| `missions.participants` | 제거 | `mission_participants` | 참여자별 완료·검증·동시 업데이트가 독립적이다. `Mission`이 상태 변경을 제어한다. |
| `coach_runs.proposal` | 제거 | `coach_run_proposal_items` | 승인 전 제안 항목은 Mission이 아니며, `CoachRun`에 귀속된다. 승인 시에만 Mission을 만든다. |
| `coach_runs.steps` | 제거 | DB 저장 안 함 | LLM 도구 실행 이력은 로그/트레이스로 보낸다. |
| `vector_store.metadata` | 제거 | `ai_documents`의 명시 컬럼 | 문서 종류·영상·항목·연령·출처·임베딩 버전은 검색·재색인에 필요하므로 컬럼이 낫다. |
| `coach_messages.citations` | 제거 | `coach_message_citations` | 인용 단위에 식별자·정렬·출처 검증이 필요하다. |

결과는 **19개 테이블**이다. `cheers`, `activity_daily`, `video_interactions`는 실제 수명과
조회 패턴이 분명하므로 합치지 않는다. `conversation`이나 운동항목 마스터 테이블은 현재 요구에
불필요하므로 만들지 않는다.

## 컨텍스트와 애그리거트

| Bounded Context | Aggregate root | 즉시 지켜야 할 불변식 |
|---|---|---|
| identity | `Family`, `Profile` | 프로필은 한 가족에 속하고, 부모 권한만 코치 제안을 승인한다. |
| fitness | `FitnessTest`, `Prediction` | 한 측정일·프로필당 측정은 하나이며, 측정 항목은 테스트 밖에서 바뀌지 않는다. |
| activity | `DailyActivity` | 같은 프로필·날짜·출처의 누적은 원자적으로 처리한다. |
| coaching | `CoachRun`, `Mission` | 승인된 `CoachRun`만 Mission을 만든다. Mission 참여 상태는 Mission을 통해서만 전이한다. |

다른 애그리거트는 객체 참조 대신 ID와 공개 조회 포트로 연결한다. 예를 들어 coaching은
`ProfileQuery` 포트로 부모 여부를 묻고 `Profile` JPA 엔티티를 직접 조회하지 않는다.

## 헥사고날 구조

```text
<context>/
  domain/                 # Aggregate, Value Object, domain event, domain service
  application/
    port/in/              # use case (command/query)
    port/out/             # repository, clock, AI, event publisher
    service/              # transaction / use-case orchestration
  adapter/
    in/web/               # controller, request/response DTO
    in/scheduler/         # scheduled job
    out/persistence/      # JPA entity, Spring Data repository, mapper
    out/ai/               # Python AI 서비스 HTTP 클라이언트
```

도메인 객체는 `@Entity`, `@Service`, `JpaRepository`, `ResponseEntity`를 모른다. HTTP DTO와
JPA 엔티티는 adapter에서만 쓰며, application은 도메인 명령과 포트를 조합한다.

Python AI 서비스는 `ai_documents`에만 제한된 DB 역할로 접근한다. 코치 실행·미션·대화처럼
사용자 상태를 바꾸는 테이블은 Spring Boot가 유일하게 쓴다. AI 서비스는 검색 결과와 생성안을
반환하고, Spring Boot가 권한 검증과 트랜잭션 안에서 이를 저장한다.

## 전환 순서

1. Kotlin으로 새 코드부터 작성하고 Java 도메인 코드는 동작을 보존한 채 공존시킨다.
2. `Mission`과 `CoachRun`부터 포트·도메인 모델로 옮긴다. 승인 게이트가 명확하고 테스트가 있다.
3. 새 정규화 테이블을 추가해 기존 JSONB를 backfill하고, 읽기는 새 테이블 우선으로 전환한다.
4. 동등성 검증 및 롤백 기간을 지난 뒤 JSONB 컬럼과 임시 이중 쓰기를 제거한다.
5. 다음 애그리거트로 반복한다. 한 번에 패키지와 스키마 전체를 옮기지 않는다.

## 결과

- 규칙은 도메인 모델에 모이고 웹·JPA·AI 교체가 쉬워진다.
- 매핑 코드와 테이블은 증가한다. 이는 필요한 독립 변경 단위의 비용이다.
- 분석·대량 작업은 application/domain을 우회하는 전용 read adapter를 둘 수 있으나, 쓰기는 항상
  use case를 통한다.
