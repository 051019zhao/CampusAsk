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
                 "steps": [], "answer": "", "mode": "lexical"}
        result = build_graph(bridge).invoke(state)
        self.assertEqual("请查看公告 [S1]。", result["answer"])
        self.assertEqual("milvus", result["mode"])
        self.assertEqual("https://example.edu/notice", result["citations"][0]["url"])
        self.assertEqual(["search", "model", "search", "model"], [x[0] for x in bridge.calls])

    def test_endpoint_requires_shared_key(self):
        with patch.dict(os.environ, {"AGENT_SHARED_KEY": "a" * 32}):
            with self.assertRaises(HTTPException) as caught:
                ask(AskRequest(question="图书馆", sessionId="s1", owner="GUEST"), None)
            self.assertEqual(403, caught.exception.status_code)


if __name__ == "__main__":
    unittest.main()
