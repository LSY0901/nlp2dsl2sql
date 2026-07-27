# A2A Host 前端模式切换实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `nlp2dsl2sqlV2.html` 增加「A2A Host」模式按钮，选中后请求 `/aiChat/a2aHost`。

**架构：** 沿用现有 `mode-switch` / `switchMode` / `getApiEndpoint` 模式，新增第 5 个 mode 值 `a2aHost`，不改后端。

**技术栈：** 静态 HTML + 原生 JS SSE

**规格：** `docs/superpowers/specs/2026-07-27-a2a-host-frontend-mode-switch-design.md`

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `src/main/resources/static/nlp2dsl2sqlV2.html` | 模式按钮、切换逻辑、SSE endpoint 映射 |

---

### 任务 1：增加 A2A Host 模式入口

**文件：**
- 修改：`src/main/resources/static/nlp2dsl2sqlV2.html`

- [x] **步骤 1：在 `.mode-switch` 追加按钮**

在 `modeSkill` 按钮后增加：

```html
<button class="mode-btn" id="modeA2aHost"
        onclick="switchMode('a2aHost')">A2A Host</button>
```

- [x] **步骤 2：更新 `switchMode`**

在现有 `modeSkill` toggle 后追加：

```javascript
document.getElementById('modeA2aHost').classList.toggle(
        'active', mode === 'a2aHost');
```

- [x] **步骤 3：更新 `getApiEndpoint`**
在 `agentSkillWorkflow` 分支之后、默认 return 之前增加：

```javascript
if (currentMode === 'a2aHost') {
    return `${API_BASE_URL}/aiChat/a2aHost`;
}
```

- [ ] **步骤 4：手工验证**

1. 打开 `http://localhost:8079/nlp2dsl2sqlV2.html`
2. 点击「A2A Host」确认高亮
3. 浏览器 Network 中确认发送请求为 `/aiChat/a2aHost?question=...`
4. 切回「V2 Workflow」确认仍请求 `/aiChat/nlp2Dsl2SqlAgentV2`

- [ ] **步骤 5：Commit（仅当用户要求时）**

```bash
git add src/main/resources/static/nlp2dsl2sqlV2.html \
  docs/superpowers/specs/2026-07-27-a2a-host-frontend-mode-switch-design.md \
  docs/superpowers/plans/2026-07-27-a2a-host-frontend-mode-switch.md
git commit -m "$(cat <<'EOF'
feat: 前端增加 A2A Host 模式切换入口

EOF
)"
```

---

## 自检

1. 规格覆盖：按钮 / switchMode / getApiEndpoint / 默认模式不变 / 请求解析不变 — 均在任务 1。
2. 无占位符。
3. mode 名统一为 `a2aHost`，按钮 id 为 `modeA2aHost`，endpoint 为 `/aiChat/a2aHost`。
