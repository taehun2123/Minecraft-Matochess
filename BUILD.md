# MatoChess 빌드 가이드

## 📦 빌드 방법

### 요구 사항

- **Java Development Kit (JDK)**: 17 이상
- **Maven**: 3.6 이상
- **Git**: (선택사항)

### Maven 설치 확인

```bash
# Maven 버전 확인
mvn --version

# 출력 예시:
# Apache Maven 3.9.0
# Maven home: /usr/share/maven
# Java version: 17.0.7
```

Maven이 설치되어 있지 않다면:

**macOS (Homebrew)**:
```bash
brew install maven
```

**Ubuntu/Debian**:
```bash
sudo apt install maven
```

**Windows (Chocolatey)**:
```bash
choco install maven
```

### 빌드 실행

```bash
# 프로젝트 디렉토리로 이동
cd /Users/mac/Desktop/Coding/MineChess

# Maven 빌드 실행
mvn clean package

# 또는 테스트 건너뛰기
mvn clean package -DskipTests
```

### 빌드 결과

빌드가 성공하면:

```
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 10.234 s
[INFO] Finished at: 2025-10-28T00:00:00+09:00
[INFO] ------------------------------------------------------------------------
```

JAR 파일 위치:
```
target/MatoChess-1.0-SNAPSHOT.jar
```

### 빌드 문제 해결

#### 문제 1: Java 버전 오류

```
[ERROR] Source option 17 is no longer supported. Use 18 or later.
```

**해결**: JDK 17 이상 설치 및 설정

```bash
# Java 버전 확인
java -version

# JAVA_HOME 설정 (macOS/Linux)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

#### 문제 2: 의존성 다운로드 실패

```
[ERROR] Failed to execute goal ... Could not resolve dependencies
```

**해결**: Maven 저장소 정리 후 재시도

```bash
# Maven 로컬 저장소 정리
rm -rf ~/.m2/repository

# 빌드 재시도
mvn clean package
```

#### 문제 3: 메모리 부족

```
[ERROR] Java heap space
```

**해결**: Maven 메모리 증가

```bash
# Maven 옵션 설정
export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=512m"

# 빌드 재시도
mvn clean package
```

## 🔧 개발 환경 빌드

### IDE 사용

**IntelliJ IDEA**:
1. File → Open → pom.xml 선택
2. "Open as Project" 클릭
3. Maven 프로젝트 자동 import
4. Run → Build Project (Ctrl+F9)

**Eclipse**:
1. File → Import → Maven → Existing Maven Projects
2. Root Directory에서 프로젝트 선택
3. Project → Build Project

**Visual Studio Code**:
1. Extension: "Java Extension Pack" 설치
2. 폴더 열기
3. Terminal → mvn clean package

### 빌드 속도 개선

```bash
# 병렬 빌드
mvn clean package -T 4

# 오프라인 모드 (의존성이 이미 다운로드된 경우)
mvn clean package -o

# 테스트 건너뛰기
mvn clean package -DskipTests
```

## 📝 빌드 스크립트

### build.sh (macOS/Linux)

```bash
#!/bin/bash

echo "MatoChess 빌드 시작..."

# Java 버전 확인
java -version

# Maven 빌드
mvn clean package

if [ $? -eq 0 ]; then
    echo "빌드 성공!"
    echo "JAR 파일: target/MatoChess-1.0-SNAPSHOT.jar"

    # 파일 크기 확인
    ls -lh target/MatoChess-1.0-SNAPSHOT.jar
else
    echo "빌드 실패!"
    exit 1
fi
```

실행:
```bash
chmod +x build.sh
./build.sh
```

### build.bat (Windows)

```batch
@echo off
echo MatoChess 빌드 시작...

REM Java 버전 확인
java -version

REM Maven 빌드
mvn clean package

if %errorlevel% equ 0 (
    echo 빌드 성공!
    echo JAR 파일: target\MatoChess-1.0-SNAPSHOT.jar
    dir target\MatoChess-1.0-SNAPSHOT.jar
) else (
    echo 빌드 실패!
    exit /b 1
)
```

## 🚀 배포

### 서버에 배포

```bash
# JAR 파일을 서버로 복사
scp target/MatoChess-1.0-SNAPSHOT.jar user@server:/path/to/minecraft/plugins/

# 또는 로컬 서버
cp target/MatoChess-1.0-SNAPSHOT.jar /path/to/minecraft/plugins/MatoChess.jar
```

### 자동 배포 스크립트

```bash
#!/bin/bash

# 빌드
mvn clean package || exit 1

# 기존 플러그인 백업
SERVER_PATH="/path/to/minecraft"
PLUGINS_PATH="$SERVER_PATH/plugins"

if [ -f "$PLUGINS_PATH/MatoChess.jar" ]; then
    mv "$PLUGINS_PATH/MatoChess.jar" "$PLUGINS_PATH/MatoChess.jar.backup"
fi

# 새 플러그인 복사
cp target/MatoChess-1.0-SNAPSHOT.jar "$PLUGINS_PATH/MatoChess.jar"

echo "배포 완료!"
echo "서버를 재시작하세요."
```

## 📦 릴리스 빌드

### 버전 업데이트

`pom.xml` 수정:
```xml
<version>1.0.0</version>
```

### 최종 릴리스 빌드

```bash
# 클린 빌드 (테스트 포함)
mvn clean verify

# 체크섬 생성
cd target
sha256sum MatoChess-1.0.0.jar > MatoChess-1.0.0.jar.sha256
```

## 🧪 빌드 검증

### JAR 파일 내용 확인

```bash
# JAR 파일 내용 확인
jar tf target/MatoChess-1.0-SNAPSHOT.jar | head -20

# plugin.yml 확인
unzip -p target/MatoChess-1.0-SNAPSHOT.jar plugin.yml

# 크기 확인
ls -lh target/MatoChess-1.0-SNAPSHOT.jar
```

### 의존성 확인

```bash
# 의존성 트리 확인
mvn dependency:tree

# 의존성 분석
mvn dependency:analyze
```

## 📊 빌드 정보

### 현재 설정

- **Java 버전**: 17
- **Maven 버전**: 3.6+
- **Spigot API**: 1.20.4-R0.1-SNAPSHOT
- **출력 JAR**: MatoChess-1.0-SNAPSHOT.jar

### 예상 빌드 시간

- 첫 빌드 (의존성 다운로드): 1-3분
- 이후 빌드: 10-30초

### 예상 JAR 크기

- 약 500KB - 1MB (의존성 제외)
- 의존성 포함 시: 3-5MB

---

**빌드 완료 후 [SETUP.md](SETUP.md)를 참고하여 서버에 설치하세요!**
