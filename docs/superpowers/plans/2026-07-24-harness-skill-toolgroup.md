# Harness SkillToolGroup 改造计划

> **For agentic workers:** 按任务顺序实现。

**Goal:** 将 `/nlp2Dsl2SqlAgentSkillWorkflow` 改为 HarnessAgent + Classpath Skill + SkillToolGroup，支持 userId/sessionId Memory。

**Architecture:** Classpath 提供 `nlp2sql_query`/`chitchat` SKILL.md；Toolkit 用 `createSkillToolGroup` 绑定业务 Tool；HarnessAgent ReAct 加载 skill 后激活工具组；RuntimeContext 传入 userId/sessionId。

**Tech Stack:** AgentScope 2.0 HarnessAgent、ClasspathSkillRepository、AgentToolRegistry

---

### Task 1: SKILL.md + Agent Bean
### Task 2: Service 改用 Harness + RuntimeContext
### Task 3: Request/前端传 userId、sessionId
### Task 4: 编译验证
