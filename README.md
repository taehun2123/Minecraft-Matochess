# MatoChess - Minecraft Auto Battler Plugin

> TFT(팀파이트 전략) 스타일의 마인크래프트 오토 배틀러 플러그인

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.4+-green.svg)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![Spigot](https://img.shields.io/badge/Spigot-1.20.4-orange.svg)](https://www.spigotmc.org/)

## 📖 소개

MatoChess는 리그 오브 레전드: 팀파이트 전략(TFT)에서 영감을 받은 마인크래프트 플러그인입니다. 플레이어들은 유닛을 구매하고 배치하여 다른 플레이어들과 자동 전투를 벌입니다.

### 주요 특징

- 🎮 **15개 유닛, 11개 특성** - 다양한 조합과 시너지
- ⚔️ **자동 전투 시스템** - 전략적 배치와 자동 전투
- 🏆 **랭크 시스템** - 7개 티어, 5개 디비전
- 🎯 **5개 독립 큐** - 동시에 여러 게임 진행
- 🗺️ **40개 전투 체스판** - 사전 생성된 전투 공간
- 💾 **자동 저장** - SQLite 기반 데이터 관리
- 🎨 **인벤토리 GUI** - 직관적인 드래그 앤 드롭 시스템

## 🎯 게임 모드

### 플레이어 수
- **테스트 모드**: 최소 2명
- **일반 모드**: 최소 4명
- **최대 인원**: 8명

### 게임 진행
> 큐 진행방식 변경 예정
1. **큐 참가** - 5개 방 중 선택
2. **준비 단계** - 유닛 구매 및 배치 (30초)
3. **전투 단계** - 자동 전투 진행 (60초)
4. **라운드 반복** - 최후의 1인까지
5. **순위 결정** - 랭크 점수 변동

## 🎮 플레이 방법

### 큐 참가
```
/queue
```
5개 방이 표시된 GUI가 열립니다. 원하는 방을 클릭하여 참가하세요.

### 큐 나가기
```
/queue leave
```

### 게임 중 조작

**E키를 눌러 인벤토리를 여세요!**

#### 핫바 버튼
- **1번**: 경험치 구매 (4G)
- **2번**: 상점 리롤 (2G)
- **3-7번**: 상점 유닛 (클릭하여 구매)
- **8번**: 상점 열기/닫기
- **9번**: 다른 플레이어 관전

#### 인벤토리 조작
- **중간 줄**: 벤치 유닛 (9칸)
- **하단 줄**: 판매 영역 (빨간 유리판)

#### 유닛 관리
- **구매**: 상점 유닛 클릭
- **배치**: 벤치 유닛을 위쪽으로 드래그
- **판매**: 벤치 유닛을 아래쪽 판매 영역으로 드래그

### 배치판 보기

**E키 인벤토리 열기 → 배치판 GUI 자동 표시**

- 8x6 체스판에 유닛 배치
- 각 플레이어는 8x3 영역 사용
- 드래그 앤 드롭으로 배치/제거

### 다른 플레이어 관전

1. 핫바 9번 버튼 클릭
2. 플레이어 머리 클릭
3. 해당 플레이어의 배치판 관전
4. 핫바 9번 "돌아가기" 버튼으로 복귀

## 📊 랭크 시스템

### 티어
1. **Copper** (구리)
2. **Silver** (은)
3. **Gold** (금)
4. **Emerald** (에메랄드)
5. **Diamond** (다이아몬드)
6. **Netherite** (네더라이트)
7. **Ender** (엔더)

각 티어는 5개 디비전으로 구성 (5 → 1)

### 점수 변동 (8인 게임 기준)
- 1위: +50점
- 2위: +30점
- 3위: +20점
- 4위: +10점
- 5위: -10점
- 6위: -20점
- 7위: -30점
- 8위: -50점

*인원 수에 따라 점수 차등 적용*

## 👤 명령어

### 플레이어 명령어
+ 추가) /mtchess ([인수]) 명령어로 확인가능
  
| 명령어 | 설명 |
|--------|------|
| `/queue` | 큐 선택 GUI 열기 |
| `/queue leave` | 큐에서 나가기 |
| `/mcstats` | 내 전적 확인 |

### 관리자 명령어
+ 추가) /mtchess admin ([인수]) 명령어로 확인가능

| 명령어 | 설명 |
|--------|------|
| `/mcadmin setlobby` | 로비 스폰 설정 |
| `/mcadmin setboard` | 체스판 템플릿 설정 |
| `/mcadmin cancelboard` | 체스판 설정 취소 |
| `/mcadmin arenas` | 체스판 사용 현황 |
| `/mcadmin debug` | 디버그 정보 |
| `/mcadmin reload` | 설정 리로드 |

## 🛠️ 설치 방법

### 요구 사항
- Minecraft 1.20.4 이상
- Spigot/Paper 서버
- Java 17 이상

### 설치 단계

1. **플러그인 설치**
   ```bash
   # MatoChess.jar를 plugins 폴더에 복사
   cp MatoChess.jar <서버폴더>/plugins/
   ```

2. **서버 재시작**
   ```bash
   # 서버 재시작하여 설정 파일 생성
   ```

3. **관리자 설정**
   - [SETUP.md](SETUP.md) 문서 참고
   - 로비 스폰 설정
   - 체스판 템플릿 설정
   - 전투 월드 및 체스판 생성

## ⚙️ 설정

### config.yml 주요 설정

```yaml
# 게임 설정
game:
  max-players: 8          # 최대 인원
  min-players: 4          # 최소 인원
  test-mode: false        # 테스트 모드
  test-min-players: 2     # 테스트 최소 인원

  preparation-time: 30    # 준비 시간 (초)
  combat-time: 60         # 전투 시간 (초)

  starting-gold: 5        # 시작 골드
  starting-level: 1       # 시작 레벨
  starting-hp: 100        # 시작 체력

# 큐 설정
matchmaking:
  max-rooms: 5            # 최대 방 개수

# 아레나 설정
arena:
  max-arenas: 40          # 체스판 개수
  arena-spacing: 30       # 체스판 간격
```

## 📚 상세 문서

- **[SETUP.md](SETUP.md)** - 관리자 설정 가이드
- **[개발문서.md](개발문서.md)** - 개발자 문서

## 🔧 빌드

```bash
# Maven 빌드
mvn clean package

# JAR 파일 위치
target/MatoChess-1.0-SNAPSHOT.jar
```

## 🐛 버그 리포트

버그를 발견하셨나요? 이슈를 생성해주세요!

## 📝 라이선스

이 프로젝트는 개인 프로젝트입니다.

## 🙏 크레딧

- **영감**: Riot Games - Teamfight Tactics
- **개발**: MatoChess Team

---

**즐거운 게임 되세요!** 🎮
