# MEMORY.md

## User Identity
- **User Name**: Leo (previously Tianyu; now using Leo)
- **Current Date**: 2026-07-21 (Tuesday)

## Core Project: Nlp2dsl2sql
- **Goal**: Natural language → DSL → SQL multi-step semantic parsing and generation
- **Tech Stack**: Java 21 + Spring Boot 3.5.15, AgentScope 2.0 (Harness + OpenAI extension), DeepSeek v4-pro (OpenAI‑compatible API), PostgreSQL + pgvector, MyBatis-Plus
- **Runtime Port**: 8079
- **Key Engineering Concerns**:
  - DSL parsing ambiguity resolution
  - SQL generation verifiability
  - Error attribution and reflection mechanisms
  - Closed‑loop value of ReAct in a real pipeline (e.g., handling “top‑selling product in the last 3 days”)
- **Long‑term Goal**: Embed ReAct as the default parse–verify–correct loop inside Nlp2dsl2sql

## Current Task: ReAct (Reasoning + Acting) Tech Sharing
- **Focus**: Engineering‑driven, tightly linked to Nlp2dsl2sql; not pure theory
- **Demo Path**: ReAct agent → intent parsing → time‑range query → DSL interpreter call → SQL generation & verification → execution result reflection
- **Available Resources**:
  - Project docs: `knowledge/REACT_ARCHITECTURE.md`, `knowledge/NLP2DSL_SCHEMA.md`
  - Support I can provide: PPT outline, a ≤50‑line runnable ReAct demo (within project), key paper cheat sheet, bilingual talk points
- **Status**: Target audience, duration, and emphasis still to be decided; first‑draft outline or demo ready on request

## Testing & Verification

### Agent Memory Test (2026-07-18)
- **FirstAgent Test (successful)**
  - Model: DeepSeek v4-pro (streaming)
  - Fixed sessionId: `supervisor`
  - Two‑round conversation: user introduced himself (“我叫leo，今天准备开始测试NLP”), second round asked “我叫什么？我今天要干什么？” – agent correctly retained Leo and the test‑NLP task
  - Cross‑turn memory validation passed
  - Log warning: `.agentscope/workspace/AGENTS.md` missing (persona/behaviour spec)

### Pipeline Integration Test (2026-07-21)
- **End‑to‑end query**: User asked “四年级有多少人？” in a conversation.
- **Successful processing**:
  - **Intent recognition** → `METRIC_QUERY`
  - **DSL** → metric `student_count` on entity `student` with filter `grade = grade_4`; DSL validation passed
  - **SQL generation** → `INNER JOIN`ing `student`, `class`, and `grade` tables correctly produced a single‑row result: `student_count = 4`
- **Conversation‑level context reuse**: When the user repeated the exact same query in the same conversation, the agent returned the cached previous result instead of re‑running the pipeline. This demonstrates effective reuse of conversation context.

### Compilation & Deployment Notes
- **Compilation Mismatch (resolved on 2026-07-18)**
  - Older compiled artifact (`FirstAgent.class`) still referenced DashScope, causing “API key required” when running the fat JAR
  - **Fix**: Offline Maven build (`mvn package -DskipTests -o`) successfully aligned artifact → JAR now contains `OpenAIChatModel` for DeepSeek, resolving the mismatch
  - Maven online builds remain broken due to the Alibaba mirror (`maven.aliyun.com:80`) not responding; the offline build is a temporary workaround
- **Running as fat JAR**: When executing `Nlp2dsl2sql-0.0.1-SNAPSHOT.jar` directly, you must disable DataSource and Web auto‑configuration:
  ```
  --spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration --spring.main.web-application-type=none
  ```

## Known Issues & Workarounds
- **SSL/TLS handshake error with streaming**
  - Symptom: “SSE/NDJSON stream failed: Remote host terminated the handshake” during streaming to DeepSeek v4-pro
  - Root cause: Java HTTP client compatibility issue (curl to same API works fine)
  - Workarounds:
    1. Switch model to `deepseek-v4-flash` (no reasoning output)
    2. Disable streaming: `.stream(false)` on the `OpenAIChatModel` configuration
- **Maven online build**: Alibaba mirror unreachable; use offline `-o` mode for now, consider switching to Maven Central or a reliable mirror later

## To-Do List
- [ ] Create `.agentscope/workspace/AGENTS.md` to define persona and local workspace rules
- [ ] Explore the AgentScope workspace directory structure and identify a suitable official example for running a FirstAgent-like agent (the source code already contains commented‑out official examples); decide which one to adapt
- [ ] (Lower priority) Configure a stable Maven repository for normal online builds