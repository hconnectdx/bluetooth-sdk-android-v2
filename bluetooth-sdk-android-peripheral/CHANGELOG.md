# bluetooth-sdk-android-peripheral 변경 이력

## 1.0.1 (2026-07-10) — GitHub Packages 배포

> 개발 중 내부 테스트 빌드에 1.0.1(sendResponse 수정만), 1.0.2(프로브+송신 큐) 라벨을
> 나눠 썼으나, 정식 릴리즈는 두 작업을 모두 합쳐 **1.0.1 하나로 배포**한다.
> 로컬 AAR(1.0.2 라벨)로 테스트했던 프로젝트는 GitHub Packages의 1.0.1로 의존성을 바꿀 것.

### 배경 (갤럭시워치 CSV 미전달 문제, 핸드오프 문서 PART 1~3)

- 폰(S21)–워치 연결에서 **EATT 5채널이 수립되면 레거시 ATT MTU 교환이 워치 앱 GATT 서버까지
  도달하지 않음**이 btsnoop(HCI)으로 확정됨. `onMtuChanged`는 EATT 수립 시 어떤 GATT 서버
  앱에도 발화하지 않는다 (WearOS 스택이 EATT 채널 MTU를 앱 레벨로 노출하지 않음).
- 그 결과 SDK는 MTU 23(청크 20B)으로 동작 → 실효 ~1.2KB/s, 링크 용량의 극히 일부만 사용.
- 253B notify가 EATT 링크로 무결 운반됨은 실증됐으나, **notify 반환값과 onNotificationSent는
  스택 절단(253B→20B)을 감지하지 못함**도 실증됨 → 실효 전달 크기는 수신자(Central)만이 안다.

### 추가: 엔드투엔드 청크 프로브 (핸드오프 2-12 설계)

CCCD 구독 완료 직후, 프레임 스트림 송신 전에 실효 청크 크기를 수신자 왕복으로 판정한다.
재연결 시마다 재실행, 연결 해제 시 리셋.

```
[워치→폰] 길이 헤더 없는 raw notify 1건, 정확히 N바이트
          "PROBE:<seq>:<N>:" ASCII 헤더 + 0xA5 패딩      (사다리: 253 → 128 → 64)
[폰→워치] RX write로 "PROBE_ACK:<seq>:<수신바이트수>" 회신
[판정]    ack.수신바이트 == N 일 때만 채택. 불일치·700ms 무응답이면 다음 후보.
          전부 실패 시 MTU-3(기본 20B) 유지.
```

- 절단은 ack 불일치로, 유실은 타임아웃으로 자동 기각 — 오탐 경로 없음.
- 채택 결과는 `PeripheralEventListener.onChunkSizeDetermined(chunkSize)`와
  `HCBlePeripheral.currentChunkSize`로 노출.
- 설정: `PeripheralConfig.probeChunkLadder`(기본 [253,128,64], 빈 리스트 = 프로브 비활성),
  `probeAckTimeoutMs`(기본 700).
- **⚠ 배포 순서**: Central(폰) SDK가 재조립기 투입 전 `"PROBE:"` 접두 가로챔 + PROBE_ACK
  회신을 구현해야 한다. 폰 미구현 상태로 워치만 올리면 프로브는 20B로 폴백하고, 프로브
  3발이 폰 재조립기에 흘러들어 스트림을 오염시킬 수 있다. 폰 반쪽과 같이 배포하거나
  `probeChunkLadder = emptyList()`로 꺼둘 것.

### 추가: 논블로킹 송신 API `sendDataAsync()`

- 기존 `sendData`는 동기 블로킹이라 호출자(센서 콜백)를 배치당 수십 초 세워
  **역압으로 수집량 자체가 1/4로 깎이는 문제**가 실측됨 (핸드오프 2-13).
- `sendDataAsync(data)`: 내부 TX 큐에 넣고 즉시 리턴. 전용 송신 스레드(`HCBlePeripheral-TX`)가
  순서대로 전송. 큐 상한 `PeripheralConfig.txQueueCapacity`(기본 32) 도달 시 신규 거부
  (false 반환) — 무한 큐로 인한 메모리 폭주 방지. 연결 해제 시 잔여 큐 폐기.
- `sendData`(동기)는 그대로 유지 — 두 경로는 같은 락으로 직렬화됨.

### 추가: CRC 프레이밍 (opt-in, 기본 off)

- `PeripheralConfig.crcFraming = true`면 프레임을 4바이트 길이 헤더 대신
  `[0xA5 0x5A][길이 4B BE][payload CRC32 4B BE]` + payload 로 감싼다.
- 수신 재조립기가 매직 스캔 재동기화 + CRC 검증을 구현할 때만 켤 것 (부분 전송 시
  프레임 경계 복구 — 핸드오프 1-10 수정방향 2와 세트). 기본 off = 기존 포맷과 완전 호환.

### 수정: GATT 요청 무응답으로 ATT 파이프가 정지되는 결함 (핸드오프 2-1/2-7)

- ATT는 순차 프로토콜 — 응답 필요한 요청 1건에 무응답하면 파이프 전체가 정지한다.
- 미등록 UUID characteristic/descriptor read·write 요청에도 `GATT_REQUEST_NOT_SUPPORTED`로
  반드시 응답하도록 수정. 미구현이던 `onExecuteWrite`에도 응답 추가.
- `sendResponse` 호출을 `respond()` 헬퍼로 일원화 — `gattServer` null/호출 실패 시
  관측 가능한 에러 로그 (핸드오프 2-9 잔여 의심 대응).
- 단, 실기 검증 결과 이 결함은 이번 증상(MTU 미도달)의 단독 원인이 아니었음(원인은 EATT,
  위 배경 참조). 결함 자체는 실존하므로 수정 유지.

### 수정: 광고 ALREADY_STARTED(errorCode=3) 무한 재시도

- 이미 광고 중인데 `startAdvertising`이 실패 콜백을 받으면 실패로 통지해 앱이 무한 재시도
  루프에 빠지던 문제 — errorCode=3은 성공 취급(`onAdvertiseStarted`)으로 변경.

### 사용 예 (워치 앱 적용 가이드)

```kotlin
// 의존성: kr.co.hconnect:bluetooth-sdk-android-peripheral:1.0.1 (GitHub Packages)

// 센서 콜백 등 지연 민감 경로: 동기 sendData 대신
HCBlePeripheral.sendDataAsync(batchBytes)   // 즉시 리턴, 실패(큐 가득/미연결) 시 false

// 프로브 결과 확인 (선택)
override fun onChunkSizeDetermined(chunkSize: Int) {
    Log.d(TAG, "실효 청크 = ${chunkSize}B")  // 253이면 EATT 경로 정상 활용 중
}
```

### 검증 합격 기준 (핸드오프 2-12)

폰 raw 청크 100B+ / 프레임 파싱 성공 / 폰 CSV 샘플 수 == 워치 CSV 샘플 수.

## 1.0.0 — 최초 배포

- BLE Peripheral(GATT Server) 기본 기능: 광고, 연결, Nordic UART 기반 TX(notify)/RX(write),
  4바이트 길이 헤더 프레이밍 + MTU-3 청크 분할, StateFlow 상태 노출.
