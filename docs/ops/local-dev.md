# 鏈湴杩愯璇存槑

## 1. 鐜瑕佹眰

- JDK 1.8
- Maven 3.9.9
- MySQL 5.7.44

褰撳墠椤圭洰榛樿浣跨敤 MySQL锛屼笉渚濊禆 Redis銆?
## 2. 鏈湴缂栬瘧

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'
$env:Path='C:\Program Files\Java\jdk-1.8\bin;' + $env:Path
mvn -q -DskipTests compile
```

## 3. 杩愯娴嬭瘯

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'
$env:Path='C:\Program Files\Java\jdk-1.8\bin;' + $env:Path
mvn -q test
```

## 4. 鍚姩搴旂敤

鍙互閫氳繃 IDEA 鍚姩 `com.vemo.codereview.CodeReviewerApplication`锛屼篃鍙互浣跨敤 Maven锛?
```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'
$env:Path='C:\Program Files\Java\jdk-1.8\bin;' + $env:Path
mvn spring-boot:run
```

## 5. 鍏抽敭閰嶇疆

### 5.1 GitLab

```yaml
code-reviewer:
  gitlab:
    url: http://your-gitlab-host
    token: your-gitlab-access-token
```

璇存槑锛氬綋鍓?`token` 鍚屾椂鐢ㄤ簬锛?
- GitLab webhook `X-Gitlab-Token`
- GitLab API `PRIVATE-TOKEN`

### 5.2 澶фā鍨?
褰撳墠璧?OpenAI-compatible 鍗忚锛屼緥濡?DeepSeek锛?
```yaml
code-reviewer:
  llm:
    base-url: https://api.deepseek.com/v1
    api-key: your-api-key
    model: deepseek-chat
```

### 5.3 浼佷笟寰俊

```yaml
code-reviewer:
  notify:
    wecom:
      enabled: true
      webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
```

椤圭洰绾т紒涓氬井淇?webhook 鍙湪椤圭洰绠＄悊鎺ュ彛涓鐩栧叏灞€閰嶇疆銆?