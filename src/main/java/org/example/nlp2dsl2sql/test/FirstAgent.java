package org.example.nlp2dsl2sql.test;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;

import java.nio.file.Paths;

public class FirstAgent {
    public static void main(String[] args) {

        //官方示例
//        HarnessAgent agent = HarnessAgent.builder()
//                .name("note-taker")
//                .sysPrompt("你是一个帮助用户做笔记的助手。")
//                // 字符串形式由 ModelRegistry 解析 —— 自动读取 DASHSCOPE_API_KEY；
//                // 切换其他厂商时改用 "openai:gpt-5.5"、"anthropic:claude-sonnet-4-5"、
//                // "gemini:gemini-2.0-flash" 或 "ollama:llama3"。
//                .model("dashscope:qwen-plus")
//                .workspace(Paths.get(".agentscope/workspace"))
//                .compaction(CompactionConfig.builder()
//                        .triggerMessages(30)
//                        .keepMessages(10)
//                        .build())
//                .build();

//        qwen
//        DashScopeChatModel model =
//                DashScopeChatModel.builder()
//                        .apiKey("sk-ws-H.EDXRXHD.kASn.MEYCIQDqhe5WWZFnhaY0QJv2FZcE4sY3yHzymUYxD8BtnYStwwIhAJ08RRcXJuBtqhqyK4P2XJNG77Ie0WZCFLQEiYauX-u3")
//                        .modelName("qwen-plus")
//                        .build();


        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey("sk-874c67f766fa44fd9b8e929d6614a81a")
                .baseUrl("https://api.deepseek.com")
                .modelName("deepseek-v4-pro")
                .build();


        HarnessAgent agent =
                HarnessAgent.builder()
                        .name("NLP2DSL2SQL")
                        .sysPrompt("你是一个自然语言转SQL数据查询助手。")
                        .model(model)
                        .workspace(Paths.get(".agentscope/workspace"))
                        .compaction(CompactionConfig.builder()
                                //当消息数量达到30条时进行压缩
                                .triggerMessages(30)
                                //保留最近10条消息
                                .keepMessages(10)
                                .build())
                        .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("supervisor")
                .userId("lsy")
                .build();

        // 第一轮：自我介绍 + 当天的事
        agent.call(new UserMessage("我叫leo,今天准备开始测试NLP"), ctx).block();

        // 第二轮：同 sessionId，自动恢复上一轮状态后回答
        agent.call(new UserMessage("我叫什么？我今天要干什么？"), ctx).block();
    }
}