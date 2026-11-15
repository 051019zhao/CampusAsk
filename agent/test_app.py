import os
import unittest
from unittest.mock import patch

from fastapi import HTTPException

from agent.app import AskRequest, ask, build_graph


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
        self.assertEqual(["history", "search", "model", "search", "model"], [x[0] for x in bridge.calls])

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
        self.assertIn("图书馆开放时间", bridge.calls[1][1]["query"])
        self.assertIn("周末呢？", bridge.calls[1][1]["query"])
        self.assertIn("核对引用", result["steps"])

    def test_endpoint_requires_shared_key(self):
        with patch.dict(os.environ, {"AGENT_SHARED_KEY": "a" * 32}):
            with self.assertRaises(HTTPException) as caught:
                ask(AskRequest(question="图书馆", sessionId="s1", owner="GUEST"), None)
            self.assertEqual(403, caught.exception.status_code)


if __name__ == "__main__":
    unittest.main()
