INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'alert', r.primary_model_id, r.backup_model_id, r.enabled
FROM llm_route r
WHERE r.purpose_code = 'ask'
  AND NOT EXISTS (SELECT 1 FROM llm_route x WHERE x.purpose_code = 'alert')
LIMIT 1;

INSERT INTO llm_route (purpose_code, primary_model_id, backup_model_id, enabled)
SELECT 'alert', r.primary_model_id, r.backup_model_id, r.enabled
FROM llm_route r
WHERE r.purpose_code = 'report_summary'
  AND NOT EXISTS (SELECT 1 FROM llm_route x WHERE x.purpose_code = 'alert')
LIMIT 1;
