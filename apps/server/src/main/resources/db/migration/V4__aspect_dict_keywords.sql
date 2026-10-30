ALTER TABLE aspect_dict
    ADD COLUMN keywords VARCHAR(255) NULL AFTER name;

ALTER TABLE review
    ADD COLUMN clean_reason VARCHAR(512) NULL AFTER clean_tags,
    ADD COLUMN aspect_hits VARCHAR(255) NULL AFTER clean_reason;

INSERT INTO sys_permission (code, name, type, parent_code, path, sort_no)
SELECT 'admin:dict:edit', '编辑词典', 'BUTTON', 'admin:dicts', NULL, 52
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'admin:dict:edit');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'admin:dict:edit'
WHERE r.code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

UPDATE aspect_dict SET keywords = '亮度,够亮,太暗,暗了,灯光亮' WHERE name = '亮度';
UPDATE aspect_dict SET keywords = '色温,暖光,冷光,偏冷,偏暖' WHERE name = '色温';
UPDATE aspect_dict SET keywords = '外观,简约,好看,颜值,造型' WHERE name = '外观';
UPDATE aspect_dict SET keywords = '材质,做工,用料' WHERE name = '材质';
UPDATE aspect_dict SET keywords = '安装,安装说明,自己搞定' WHERE name = '安装';
UPDATE aspect_dict SET keywords = '质量,扎实,做工扎实' WHERE name = '质量';
UPDATE aspect_dict SET keywords = '售后,客服,回复及时' WHERE name = '售后';
UPDATE aspect_dict SET keywords = '价格,贵,便宜,性价比' WHERE name = '价格';
UPDATE aspect_dict SET keywords = '物流,快递,发货慢' WHERE name = '物流';
UPDATE aspect_dict SET keywords = '频闪,护眼,闪烁' WHERE name = '频闪';
UPDATE aspect_dict SET keywords = '寿命,耐用' WHERE name = '寿命';
UPDATE aspect_dict SET keywords = '智能联动,掉线,智能' WHERE name = '智能联动';
UPDATE aspect_dict SET keywords = '遥控,遥控器,失灵' WHERE name = '遥控';
UPDATE aspect_dict SET keywords = '防水' WHERE name = '防水';
UPDATE aspect_dict SET keywords = '包装,包装完好' WHERE name = '包装';
UPDATE aspect_dict SET keywords = NULL WHERE name = '其它';
