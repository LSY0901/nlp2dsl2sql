# SOP Agent (9002) A2A 联调清单

## 目的

本仓 A2A Host 依赖 9002 端口暴露标准 A2A 协议，不再调用 `GET /chat/stream`。

## 参考

- **Host 入口：** `GET /aiChat/a2aHost`
- **设计文档：** [2026-07-27-a2a-agentscope-host-design.md](./2026-07-27-a2a-agentscope-host-design.md)

## 联调清单

- [ ] **AgentCard** — 9002 服务暴露 `GET /.well-known/agent-card.json`，返回合法 AgentCard JSON
- [ ] **A2A 可调用** — 可被 Java `A2aAgent.call(UserMessage)` 正常调用并返回文本
- [ ] **配置连通** — `a2a.sop-agent.base-url=http://127.0.0.1:9002` 与本仓 Host 配置一致且可访问
- [ ] **复合问双工具成功** — 复合问题（如「六年级最高分是谁？最高分奖励是什么？」）同时触发 `call_sql_agent` 与 `call_sop_agent` 且均成功
- [ ] **最终回答完整** — Host 最终回答同时包含最高分人员与奖励政策说明
- [ ] **不依赖旧端点** — 旧 `/chat/stream` 可保留，但本仓 Host 不得依赖该端点

## 验证示例

```bash
# 1. 确认 SOP AgentCard
curl -s http://127.0.0.1:9002/.well-known/agent-card.json

# 2. 复合问联调（8079 Host + 9002 SOP）
curl -N "http://127.0.0.1:8079/aiChat/a2aHost?question=六年级最高分是谁？最高分奖励是什么"
```

预期：SSE 流中出现 `[A2A工具开始] call_sql_agent` 与 `[A2A工具开始] call_sop_agent`，最终回答同时覆盖数据查询与 SOP 规范内容。
