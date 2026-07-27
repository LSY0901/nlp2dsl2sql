# A2A Host 前端模式切换设计

**日期：** 2026-07-27  
**状态：** 已批准（口头确认）  
**范围：** 仅修改 `src/main/resources/static/nlp2dsl2sqlV2.html`

## 目标

在现有页面顶部模式切换栏中增加第 5 个入口「A2A Host」，选中后请求后端 SSE 接口 `GET /aiChat/a2aHost`，与现有四种模式并列可切换。

## 非目标

- 不修改后端 Controller / Service
- 不引入 URL 参数记忆模式
- 不重构模式配置为数组/对象表
- 不新增独立 HTML 页面

## 方案

沿用现有 `mode-switch` + `switchMode` + `getApiEndpoint` 模式（方案 1）。

## 变更点

### 1. Header 按钮

在 `.mode-switch` 内追加：

- 文案：`A2A Host`
- `id="modeA2aHost"`
- `onclick="switchMode('a2aHost')"`

### 2. `switchMode(mode)`

增加对 `#modeA2aHost` 的 `active` 切换：`mode === 'a2aHost'`。

### 3. `getApiEndpoint()`

当 `currentMode === 'a2aHost'` 时返回：

```text
${API_BASE_URL}/aiChat/a2aHost
```

其余模式逻辑不变；默认仍为 `workflow` → `/aiChat/nlp2Dsl2SqlAgentV2`。

### 4. 请求与流式解析

保持现有 SSE 调用方式（`question` 查询参数、流式文本拼接/渲染）不变，不因 A2A Host 单独分支。

## 成功标准

1. 页面可见 5 个模式按钮，点击「A2A Host」后该按钮高亮。
2. 发送问题时请求落在 `/aiChat/a2aHost?question=...`。
3. 切回其他模式时仍请求对应原接口。
4. 后端与默认模式行为无回归。

## 风险与约束

- A2A Host 依赖远程 Agent 配置与连通性；前端仅切换入口，不处理后端可用性。
- 按钮较多时 header 可能换行；沿用现有 `flex-wrap`，不额外改布局。
