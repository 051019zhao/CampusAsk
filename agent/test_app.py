import os
import unittest
from unittest.mock import Mock, patch

from fastapi import HTTPException

from agent.app import AskRequest, ask, build_graph, search_queries


class FakeBridge:
    def __init__(self):
        self.calls = []

    def post(self, path, body):
        self.calls.append((path, body))
        if path == "history":
            return []
        if path == "model" and len([x for x in self.calls if x[0] == "model"]) == 1:
            return {"role": "assistant", "content": None, "tool_calls": [{
                "id": "call-1", "type": "function", "function": {
                    "name": "search_campus_knowledge", "arguments": '{"query":"图书馆"}'}}]}
        if path == "search":
            return {"retrievalMode": "milvus", "citations": [{"id": "S1", "title": "图书馆公告",
                "documentId": "K1", "excerpt": "开放时间见公告", "chunk": 1,
                "url": "https://example.edu/notice"}]}
        return {"role": "assistant", "content": "请查看公告 [S1]。"}


class AgentTest(unittest.TestCase):
    def test_explicit_multi_part_query_is_split(self):
        self.assertEqual(["图书馆几点开", "宿舍几点关门"],
                         search_queries("图书馆几点开？宿舍几点关门？", []))
        self.assertEqual(["图书馆几点开"], search_queries("图书馆几点开", []))

    def test_compound_search_deduplicates_same_source(self):
        class DirectBridge(FakeBridge):
            def post(self, path, body):
                if path == "model":
                    self.calls.append((path, body))
                    return {"role": "assistant", "content": "请查看公告 [S1]。"}
                return super().post(path, body)

        bridge = DirectBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆几点开？宿舍几点关门？"}],
                 "owner": "GUEST", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical", "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertEqual(2, result["search_count"])
        self.assertEqual(1, len(result["citations"]))
        self.assertIn("分题检索校园资料", result["steps"])

    def test_graph_searches_then_answers_with_citation(self):
        bridge = FakeBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆几点开"}],
                 "owner": "USER:alice", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical",
                 "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertEqual("请查看公告 [S1]。", result["answer"])
        self.assertTrue(result["verified"])
        self.assertEqual("milvus", result["mode"])
        self.assertEqual("https://example.edu/notice", result["citations"][0]["url"])
        self.assertEqual(["history", "model", "search", "model"], [x[0] for x in bridge.calls])
        self.assertIn("选择查证工具", result["steps"])

    def test_short_followup_uses_session_history_and_repairs_bad_citation(self):
        class RetryBridge(FakeBridge):
            def post(self, path, body):
                if path == "history":
                    self.calls.append((path, body))
                    return [{"question": "图书馆开放时间", "answer": "以公告为准 [S1]。"}]
                if path == "model":
                    self.calls.append((path, body))
                    turns = len([x for x in self.calls if x[0] == "model"])
                    return {"role": "assistant", "content": "开放时间见公告。" if turns == 1
                            else "开放时间见公告 [S1]。"}
                return super().post(path, body)

        bridge = RetryBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "周末呢？"}],
                 "owner": "USER:alice", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical",
                 "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertTrue(result["verified"])
        self.assertEqual("开放时间见公告 [S1]。", result["answer"])
        query = next(body["query"] for path, body in bridge.calls if path == "search")
        self.assertIn("图书馆开放时间", query)
        self.assertIn("周末呢？", query)
        self.assertIn("补充首轮检索", result["steps"])

    def test_endpoint_requires_shared_key(self):
        with patch.dict(os.environ, {"AGENT_SHARED_KEY": "a" * 32}):
            with self.assertRaises(HTTPException) as caught:
                ask(AskRequest(question="图书馆", sessionId="s1", owner="GUEST"), None)
            self.assertEqual(403, caught.exception.status_code)

    def test_endpoint_returns_only_sources_used_in_answer(self):
        result = {"verified": True, "answer": "见公告 [S2]。", "turns": 2,
                  "tool_count": 2, "search_count": 2, "steps": ["检索校园资料"],
                  "mode": "milvus", "citations": [
                      {"id": "S1", "documentId": "K1", "excerpt": "甲"},
                      {"id": "S2", "documentId": "K2", "excerpt": "乙"}]}
        with patch.dict(os.environ, {"AGENT_SHARED_KEY": "a" * 32}), \
             patch("agent.app.build_graph", return_value=Mock(invoke=Mock(return_value=result))):
            response = ask(AskRequest(question="测试", sessionId="s1", owner="GUEST"), "a" * 32)
        self.assertEqual(["S2"], [item["id"] for item in response["citations"]])
        self.assertEqual(2, response["modelTurns"])

    def test_invented_link_is_corrected_before_return(self):
        class LinkBridge(FakeBridge):
            def post(self, path, body):
                if path == "model":
                    self.calls.append((path, body))
                    turns = len([x for x in self.calls if x[0] == "model"])
                    return {"role": "assistant", "content":
                            "详见 https://invented.example/notice [S1]。" if turns == 1
                            else "详见原文公告 [S1]。"}
                return super().post(path, body)

        bridge = LinkBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆几点开"}],
                 "owner": "GUEST", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical", "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertTrue(result["verified"])
        self.assertEqual("详见原文公告 [S1]。", result["answer"])
        self.assertIn("补充首轮检索", result["steps"])

    def test_model_chooses_source_detail_after_search(self):
        class DetailBridge(FakeBridge):
            def post(self, path, body):
                if path == "model":
                    self.calls.append((path, body))
                    turn = len([entry for entry in self.calls if entry[0] == "model"])
                    if turn < 3:
                        name = "search_campus_knowledge" if turn == 1 else "read_campus_source"
                        arguments = '{"query":"图书馆"}' if turn == 1 else '{"documentId":"K1"}'
                        return {"role": "assistant", "content": None, "tool_calls": [{"id": f"call-{turn}",
                            "type": "function", "function": {"name": name, "arguments": arguments}}]}
                    return {"role": "assistant", "content": "详情见公告 [S1]。"}
                if path == "source":
                    self.calls.append((path, body))
                    return {"documentId": "K1", "title": "图书馆公告",
                            "chunks": [{"ordinal": 1, "text": "图书馆具体开放安排见官网公告。"}]}
                return super().post(path, body)

        bridge = DetailBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆开放安排的详情"}],
                 "owner": "GUEST", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical", "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertTrue(result["verified"])
        self.assertEqual(["history", "model", "search", "model", "source", "model"],
                         [entry[0] for entry in bridge.calls])
        self.assertIn("图书馆具体开放安排", result["citations"][0]["excerpt"])
        self.assertIn("阅读来源详情", result["steps"])

    def test_unsearched_source_id_is_rejected(self):
        class WrongSourceBridge(FakeBridge):
            def post(self, path, body):
                if path == "model":
                    self.calls.append((path, body))
                    turn = len([entry for entry in self.calls if entry[0] == "model"])
                    if turn == 1:
                        return {"role": "assistant", "content": None, "tool_calls": [{"id": "search",
                            "function": {"name": "search_campus_knowledge", "arguments": '{"query":"图书馆"}'}}]}
                    if turn == 2:
                        return {"role": "assistant", "content": None, "tool_calls": [{"id": "read",
                            "function": {"name": "read_campus_source", "arguments": '{"documentId":"D-other"}'}}]}
                    return {"role": "assistant", "content": "请查看公告 [S1]。"}
                return super().post(path, body)

        bridge = WrongSourceBridge()
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆"}],
                 "owner": "GUEST", "session_id": "session", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [],
                 "steps": [], "answer": "", "mode": "lexical", "history": [], "verified": False}
        result = build_graph(bridge).invoke(state)
        self.assertTrue(result["verified"])
        self.assertNotIn("source", [entry[0] for entry in bridge.calls])
        self.assertIn('"error": "ValueError"',
                      result["messages"][-2]["content"] if result["messages"][-2]["role"] == "tool" else "")


if __name__ == "__main__":
    unittest.main()
