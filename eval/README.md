# eval

黄金集：方面情感对错（色温、安装、频闪、售后等）。不连模型，只跑词典规则。

```
python3 -m unittest apps.ai-worker.tests.test_golden_absa
```

在仓库根目录，或：

```
cd apps/ai-worker && python3 -m unittest tests.test_golden_absa tests.test_absa
```

- `fixtures/golden-absa.jsonl`：原文 + 标准方面正负向
- `fixtures/aspect-keywords.json`：与库里方面词典关键词对齐
- 样例导入：`fixtures/sample-reviews.csv`、`fixtures/sample-competitor-reviews.csv`
