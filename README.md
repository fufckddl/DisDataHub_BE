# GisDataHub_BE
중앙정보처리기술원 2차 프로젝트(선도소프트) 2팀

## 테스트

- 전체 검증은 `./gradlew test`로 실행합니다.
- 생활인구 대시보드 매퍼는 `sd_area_population`의 snake_case 연령/성별 컬럼을 `AreaPopulationDto`의 camelCase 필드명으로 명시 alias 해야 합니다. 이 계약은 `DashboardPopulationMapperXmlTest`가 보호합니다.
- 같은 worktree에서 여러 `./gradlew test` 프로세스를 동시에 실행하면 Gradle XML 결과 파일이 충돌할 수 있으므로, 검증 증거는 단일 테스트 프로세스의 완료 로그를 기준으로 남깁니다.
