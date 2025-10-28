# MatoChess 관리자 설정 가이드

> MatoChess 플러그인을 처음 설치한 후 반드시 수행해야 하는 초기 설정 가이드입니다.

## 📋 목차

1. [요구 사항](#요구-사항)
2. [플러그인 설치](#플러그인-설치)
3. [초기 설정](#초기-설정)
4. [체스판 생성](#체스판-생성)
5. [테스트](#테스트)
6. [문제 해결](#문제-해결)

## 🔧 요구 사항

### 서버 환경
- **Minecraft**: 1.20.4 이상 (1.20.x, 1.21.x 지원)
- **서버 소프트웨어**: Spigot 또는 Paper
- **Java**: 17 이상
- **디스크 공간**: 최소 100MB (체스판 생성 포함)

### 준비물
- MatoChess.jar 파일
- Empty 월드 (체스판 생성용)
- 관리자 권한

## 📦 플러그인 설치

### 1단계: JAR 파일 복사

```bash
# MatoChess.jar를 서버의 plugins 폴더에 복사
cp MatoChess.jar /path/to/server/plugins/
```

### 2단계: 서버 시작

```bash
# 서버를 시작하여 초기 설정 파일 생성
./start.sh  # 또는 서버 시작 스크립트
```

서버 로그에서 다음 메시지를 확인하세요:
```
[MatoChess] MatoChess plugin has been enabled successfully!
[MatoChess] Version: 1.0-SNAPSHOT
```

### 3단계: 서버 중지

```bash
# 설정 파일 편집을 위해 서버 중지
stop
```

## ⚙️ 초기 설정

### config.yml 편집

`plugins/MatoChess/config.yml` 파일을 열어 다음을 확인/수정하세요:

```yaml
# 게임 설정
game:
  max-players: 8          # 최대 인원 (권장: 8)
  min-players: 4          # 최소 인원 (권장: 4)

  # 테스트 모드 (개발/테스트용)
  test-mode: false        # true로 설정 시 2명만으로 테스트 가능
  test-min-players: 2

  preparation-time: 30    # 준비 시간 (초)
  combat-time: 60         # 전투 시간 (초)

# 큐 설정
matchmaking:
  max-rooms: 5            # 최대 방 개수

# 아레나 설정
arena:
  max-arenas: 40          # 생성할 체스판 개수
  arena-spacing: 30       # 체스판 간 간격 (블록)
```

### 테스트 모드 활성화 (선택사항)

소수 인원으로 테스트하려면:

```yaml
game:
  test-mode: true
  test-min-players: 2
```

## 🎯 게임 월드 설정

### Empty 월드 생성

체스판 생성용 빈 월드가 필요합니다.

**방법 1: Multiverse-Core 사용**
```
/mv create matochess_arena normal -t flat
```

**방법 2: 수동 생성**
1. `bukkit.yml`에 월드 추가
2. 서버 재시작하여 월드 생성

**권장 설정**:
- 월드 이름: `matochess_arena`
- 월드 타입: Flat (평지)
- 게임모드: Adventure

## 🏗️ 체스판 설정 및 생성

### 1단계: 로비 스폰 설정

로비 역할을 할 위치로 이동한 후:

```
/mcadmin setlobby
```

✅ **확인 메시지**:
```
§a로비 스폰이 현재 위치로 설정되었습니다!
§7월드: world
§7좌표: X, Y, Z
```

### 2단계: 체스판 템플릿 설정

#### 2-1. 체스판 제작

먼저 8x6 크기의 체스판을 만드세요.

**권장 사양**:
- 크기: 8블록 (가로) × 6블록 (세로)
- 높이: 1-3블록 (선택)
- 재료: 원하는 블록 (돌, 나무판자 등)
- 패턴: 체스판 무늬 (선택)

**예시 구조**:
```
□ ■ □ ■ □ ■ □ ■  (8블록)
■ □ ■ □ ■ □ ■ □
□ ■ □ ■ □ ■ □ ■
■ □ ■ □ ■ □ ■ □
□ ■ □ ■ □ ■ □ ■
■ □ ■ □ ■ □ ■ □
(6블록)
```

#### 2-2. 영역 지정

체스판을 만든 후:

```
/mcadmin setboard
```

**우클릭 순서**:
1. **첫 번째**: 바닥의 한 모서리 우클릭
2. **두 번째**: 대각선 반대편 최상단 우클릭

✅ **확인 메시지**:
```
§a첫 번째 위치 설정 완료!
§e두 번째 모서리를 우클릭하세요
```

```
§a체스판 템플릿 설정 완료!
§e체스판 크기: 8x3x6
```

⚠️ **주의**: 8x6 크기를 권장하지만 다른 크기도 가능합니다.

#### 2-3. 설정 취소 (필요시)

실수한 경우:
```
/mcadmin cancelboard
```

### 3단계: 체스판 생성

#### 3-1. Empty 월드로 이동

```
/mv tp matochess_arena  # Multiverse 사용 시
```

#### 3-2. 40개 체스판 생성

```
/mcadmin setworld matochess_arena
```

✅ **진행 메시지**:
```
§a아레나 월드가 설정되었습니다: matochess_arena
§e체스판 생성을 시작합니다... (시간이 걸릴 수 있습니다)
§a체스판 생성 진행중... 10/40
§a체스판 생성 진행중... 20/40
§a체스판 생성 진행중... 30/40
§a체스판 생성 진행중... 40/40
§a체스판 생성 완료!
§e총 40개 체스판이 생성되었습니다
```

⏱️ **소요 시간**: 약 10-30초 (서버 성능에 따라 다름)

### 4단계: 설정 확인

```
/mcadmin arenas
```

✅ **확인 메시지**:
```
§6§l=== 아레나 정보 ===
§e전체 아레나: §f40
§e사용 가능: §a40
§e사용 중: §c0
```

```
/mcadmin debug
```

✅ **확인 항목**:
```
§6§l=== 디버그 정보 ===
§e등록된 유닛 수: §f15
§e활성 게임: §f0
§e큐 방 수: §f5
§eDB 연결: §f활성 (SQLite)

§6아레나 설정:
§e로비 월드: §fworld
§e아레나 월드: §fmatochess_arena
§e체스판 템플릿 설정: §f완료
§e체스판 개수: §f40
```

## 🧪 테스트

### 기본 기능 테스트

#### 1. 큐 시스템 테스트

```
/queue
```

- 5개 방이 표시되는 GUI 확인
- 방 클릭 시 로비로 이동 확인
- `/queue leave`로 나가기 확인

#### 2. 게임 시작 테스트

**테스트 모드 활성화** (2명으로 테스트):

`config.yml`:
```yaml
game:
  test-mode: true
  test-min-players: 2
```

서버 리로드:
```
/mcadmin reload
```

2명의 플레이어로 같은 방에 입장하면 게임이 자동 시작됩니다.

#### 3. 인벤토리 GUI 테스트

게임 시작 후 **E키**를 눌러:
- 핫바 버튼 확인
- 상점 유닛 구매 테스트
- 리롤 버튼 테스트
- 유닛 배치/판매 테스트

## 🛠️ 문제 해결

### 문제 1: 체스판이 생성되지 않음

**원인**: 체스판 템플릿이 설정되지 않음

**해결**:
```
/mcadmin setboard
# 체스판 영역을 다시 지정
```

### 문제 2: 큐에 참가했지만 로비로 이동하지 않음

**원인**: 로비 스폰이 설정되지 않음

**해결**:
```
/mcadmin setlobby
# 로비 위치에서 다시 설정
```

### 문제 3: 게임이 시작되지 않음

**원인**: 최소 인원 미달

**해결**:
- 테스트 모드 활성화: `test-mode: true`
- 또는 충분한 인원 확보 (최소 4명)

### 문제 4: "체스판 사용 가능: 0"

**원인**: 체스판이 생성되지 않음

**해결**:
```
/mcadmin setworld <월드명>
# 체스판 재생성
```

### 문제 5: 플러그인이 로드되지 않음

**원인**: Java 버전 불일치

**해결**:
```bash
# Java 버전 확인
java -version

# Java 17 이상이어야 합니다
```

## 📊 로그 확인

### 서버 로그 위치

```
logs/latest.log
```

### MatoChess 로그 확인

```bash
# 플러그인 관련 로그 필터링
cat logs/latest.log | grep MatoChess
```

### 주요 로그 메시지

**정상 시작**:
```
[MatoChess] MatoChess plugin has been enabled successfully!
[MatoChess] Registered 15 units
[MatoChess] Registered 13 equipment
[MatoChess] PlayerDataManager initialized
[MatoChess] ArenaManager initialized
```

**오류 발생 시**:
```
[MatoChess] Failed to initialize managers
[MatoChess] 체스판 템플릿이 설정되지 않았습니다!
```

## 🎮 권한 설정

### 플레이어 권한

기본적으로 모든 플레이어가 게임에 참가할 수 있습니다.

### 관리자 권한

```yaml
# permissions.yml 또는 권한 플러그인
matochess.admin: true
```

관리자 권한을 가진 플레이어만 `/mcadmin` 명령어를 사용할 수 있습니다.

## 📚 추가 설정

### 게임 밸런스 조정

`config.yml`에서 조정 가능:

```yaml
game:
  starting-gold: 5         # 시작 골드
  gold-per-round: 1        # 라운드당 골드
  xp-cost-gold: 4          # 경험치 구매 비용

units:
  reroll-cost: 2           # 리롤 비용
  shop-size: 5             # 상점 크기
```

### 랭크 점수 조정

```yaml
ranking:
  placement-points-8:      # 8인 게임
    1: 50
    2: 30
    # ...
```

## ✅ 설정 완료 체크리스트

- [ ] 플러그인 설치 및 서버 시작 확인
- [ ] config.yml 설정 확인
- [ ] 로비 스폰 설정 완료
- [ ] 체스판 템플릿 설정 완료
- [ ] Empty 월드 생성 완료
- [ ] 40개 체스판 생성 완료
- [ ] 아레나 정보 확인 (40개 모두 사용 가능)
- [ ] 큐 시스템 테스트 완료
- [ ] 게임 시작 테스트 완료
- [ ] 인벤토리 GUI 테스트 완료

## 🆘 지원

문제가 계속되면:
1. 로그 파일 확인
2. GitHub Issues에 버그 리포트
3. Discord 서버 (링크가 있다면)

---

**설정 완료! 이제 플레이어들이 게임을 즐길 수 있습니다!** 🎮
