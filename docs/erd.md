# ERD — 우리가족 체력키움 (family-fitness)

**테이블 19개 · FK 29개 · 모듈 4개 · JSONB 0개**

> [ADR-001](./adr/ADR-001-ddd-hexagonal-and-relational-data.md)에 따른 **목표 ERD 초안**입니다.
> 현재 Flyway 마이그레이션에 적용된 실스키마가 아니므로, 마이그레이션·backfill·검증 뒤에만 적용합니다.

## 줄인 기준

기존 14개에서 JSONB를 제거해 19개가 됐습니다. 아래 규칙으로 불필요한 테이블 증가는 막습니다.

| 뺀 것 | 왜 |
|---|---|
| 로그 테이블 4종 (동기화·API호출·이벤트·설문) | 애플리케이션 로그와 CloudWatch 로 충분하다. DB 에 넣으면 관리 대상만 늘어난다 |
| 확장 훅 7종 (뱃지·리그·포인트·리워드·월간리포트·체력나이·알림) | 5주차 출품 범위가 아니다. 필요해지면 그때 마이그레이션 한 장 |
| 1:1 분해 (영상↔라벨) | 영상의 라벨은 영상 속성이라 한 테이블에 둔다 |
| 마스터 테이블 (측정 항목 코드) | 20개 미만에 배포 없이는 안 바뀐다 → Kotlin enum |
| 이벤트 발행 레지스트리 | 모듈이 넷뿐이고 아직 모듈 간 이벤트가 없다 |

### JSONB 제거 기준

독립적으로 식별·변경·조회되는 값은 행으로 분리합니다. 반대로 코치 도구 실행 단계는 업무 데이터가
아니므로 DB가 아니라 로그/추적 시스템에 보냅니다.

| 기존 JSONB 컬럼 | 목표 구조 |
|---|---|
| `fitness_tests.items` | `fitness_test_items` — 항목별 추세와 규준 검증 |
| `predictions.curves` | `prediction_points` — 시나리오·항목·시점별 예측 |
| `predictions.input_snapshot`, `metrics` | 제거 — 원천 측정 FK와 모델 버전으로 재현 |
| `missions.participants` | `mission_participants` — 참여자별 완료·검증 |
| `coach_runs.proposal` | `coach_run_proposal_items` — 승인 전 제안 항목 |
| `coach_runs.steps` | 제거 — 애플리케이션 로그/trace |
| `vector_store.metadata` | `vector_store`의 명시 컬럼 |
| `coach_messages.citations` | `coach_message_citations` |

이 설계의 이행 순서는 [DDD·헥사고날 전환 가이드](./ddd-hexagonal-guide.md)를 따릅니다.

## 모듈 의존

Spring Modulith 모듈 경계 = ERD 묶음 = 백엔드 패키지. 셋이 1:1입니다.

```mermaid
flowchart LR
    identity["identity"]
    fitness["fitness"]
    activity["activity"]
    coaching["coaching"]
    activity --> identity
    coaching --> identity
    fitness --> identity
```

## 1. identity — 계정 · 가족 · 구성원

계정(users)과 사람(profiles)을 나눈 게 핵심. 두 살 아이는 계정 없이 프로필로만 살고, 나중에 합류하는 남편은 미리 만들어 둔 프로필을 초대코드로 가져간다.

```mermaid
erDiagram
    users {
        uuid id "PK"
        varchar_20 provider
        varchar_191 provider_user_id
        varchar_255 email
        varchar_20 status
        timestamptz created_at
        timestamptz updated_at
    }
    families {
        uuid id "PK"
        varchar_60 name
        varchar_20 region_code
        timestamptz created_at
        timestamptz updated_at
    }
    profiles {
        uuid id "PK"
        uuid family_id "FK"
        uuid user_id "FK"
        varchar_30 display_name
        date birth_date
        varchar_6 sex
        varchar_10 role
        boolean is_owner
        numeric_4_1 height_cm
        numeric_4_1 weight_kg
        varchar_20 support_mode
        varchar_8 claim_code
        timestamptz claim_code_expires_at
        timestamptz consent_personal_at
        timestamptz consent_health_at
        uuid consent_by_user_id "FK"
        timestamptz created_at
        timestamptz updated_at
    }
    cheers {
        uuid id "PK"
        uuid family_id "FK"
        uuid from_profile_id "FK"
        uuid to_profile_id "FK"
        varchar_20 sticker_code
        varchar_200 message
        timestamptz created_at
    }
    families ||--o{ profiles : "family_id"
    users ||--o{ profiles : "user_id"
    users ||--o{ profiles : "consent_by_user_id"
    families ||--o{ cheers : "family_id"
    profiles ||--o{ cheers : "from_profile_id"
    profiles ||--o{ cheers : "to_profile_id"
```

## 2. fitness — 규준 · 측정 · 예측

측정 항목 마스터는 테이블이 아니라 Kotlin enum(FitnessItem)이다. 다만 측정 회차의 항목 값은
독립적으로 조회되므로 `fitness_test_items`에 둔다.

```mermaid
erDiagram
    fitness_norms {
        bigint id "PK"
        varchar_30 item
        varchar_6 sex
        smallint age_from
        smallint age_to
        smallint percentile
        numeric_8_3 value
        smallint source_year
    }
    fitness_tests {
        uuid id "PK"
        uuid profile_id "FK"
        date measured_on
        varchar_20 source
        smallint age_at_test
        numeric_4_1 height_cm
        numeric_4_1 weight_kg
        timestamptz created_at
    }
    fitness_test_items {
        uuid fitness_test_id "PK, FK"
        varchar_30 item "PK"
        numeric_8_3 raw_value
        numeric_5_2 percentile
        varchar_10 grade
    }
    predictions {
        uuid id "PK"
        uuid profile_id "FK"
        uuid fitness_test_id "FK"
        varchar_40 model_version
        timestamptz created_at
    }
    prediction_points {
        uuid prediction_id "PK, FK"
        varchar_20 scenario "PK"
        varchar_30 item "PK"
        smallint years_from_now "PK"
        numeric_8_3 p10
        numeric_8_3 p50
        numeric_8_3 p90
    }
    fitness_tests ||--o{ predictions : "fitness_test_id"
    fitness_tests ||--o{ fitness_test_items : "fitness_test_id"
    predictions ||--o{ prediction_points : "prediction_id"
```

## 3. activity — 활동 기록

웹(PWA)이라 HealthKit·Health Connect 를 못 쓴다. 걸음수는 자기 신고, 영상 완주와 타이머만 서버가 안다.

```mermaid
erDiagram
    activity_daily {
        uuid id "PK"
        uuid profile_id "FK"
        date activity_date
        varchar_10 source
        int steps
        smallint active_minutes
        timestamptz recorded_at
    }
```

## 4. coaching — 영상 · RAG · 코치 · 미션

coach_runs.approved_at 이 NULL 인 동안에는 missions 가 단 한 건도 생기지 않는다. CoachApprovalGateTest 가 증명한다.

```mermaid
erDiagram
    exercise_videos {
        uuid id "PK"
        varchar_32 youtube_id
        varchar_300 title
        varchar_120 channel_name
        varchar_20 channel_type
        smallint duration_sec
        smallint age_from
        smallint age_to
        varchar_30 target_item
        varchar_10 intensity
        varchar_20 space
        varchar_10 noise
        varchar_60 equipment
        varchar_20 labeled_by
        varchar_40 label_model
        timestamptz collected_at
    }
    video_interactions {
        uuid id "PK"
        uuid profile_id "FK"
        uuid video_id "FK"
        varchar_10 kind
        smallint progress
        timestamptz occurred_at
    }
    vector_store {
        uuid id "PK"
        text content
        varchar_30 kind
        uuid video_id "FK"
        varchar_30 fitness_item
        smallint age_from
        varchar_200 source
        vector_1536 embedding
    }
    coach_runs {
        uuid id "PK"
        uuid family_id "FK"
        date week_start
        varchar_10 trigger_type
        varchar_20 status
        text summary
        uuid approved_by "FK"
        timestamptz approved_at
        varchar_300 rejected_reason
        varchar_40 model_name
        timestamptz created_at
    }
    coach_run_proposal_items {
        uuid coach_run_id "PK, FK"
        smallint position "PK"
        varchar_120 title
        varchar_400 description
        varchar_20 target_metric
        int target_value
        uuid video_id "FK"
        varchar_400 rationale
        date starts_on
        date ends_on
    }
    missions {
        uuid id "PK"
        uuid family_id "FK"
        uuid coach_run_id "FK"
        varchar_120 title
        varchar_400 description
        varchar_10 origin
        varchar_20 target_metric
        int target_value
        uuid video_id "FK"
        varchar_400 rationale
        date starts_on
        date ends_on
        uuid created_by "FK"
        timestamptz created_at
    }
    mission_participants {
        uuid mission_id "PK, FK"
        uuid profile_id "PK, FK"
        varchar_20 status
        smallint progress
        varchar_20 verified_by
        timestamptz verified_at
        uuid confirmed_by_profile_id "FK"
    }
    coach_messages {
        uuid id "PK"
        uuid conversation_id
        uuid profile_id "FK"
        varchar_10 role
        text content
        timestamptz created_at
    }
    coach_message_citations {
        uuid coach_message_id "PK, FK"
        smallint position "PK"
        uuid vector_store_id "FK"
        varchar_200 source_label
        varchar_500 excerpt
    }
    exercise_videos ||--o{ video_interactions : "video_id"
    coach_runs ||--o{ missions : "coach_run_id"
    coach_runs ||--o{ coach_run_proposal_items : "coach_run_id"
    exercise_videos ||--o{ missions : "video_id"
    exercise_videos ||--o{ coach_run_proposal_items : "video_id"
    exercise_videos ||--o{ vector_store : "video_id"
    missions ||--o{ mission_participants : "mission_id"
    coach_messages ||--o{ coach_message_citations : "coach_message_id"
    vector_store ||--o{ coach_message_citations : "vector_store_id"
```
