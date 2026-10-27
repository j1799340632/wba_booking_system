-- Public synthetic admin for the isolated demo; do not override any existing ID or email.
INSERT INTO user (id,role_id,email,password,name,title,create_time,is_enable,is_delete,is_canceled)
SELECT 900000004,2,'admin@wba.test','$2a$10$u4VyS0LsWgQ6lVpeh1gDou8G738CAD5CMUTs6o0t3geGoz.4w4tG2','Demo Administrator','Administrator',NOW(),1,0,0
WHERE NOT EXISTS (SELECT 1 FROM user WHERE id=900000004 OR email='admin@wba.test');
INSERT INTO user_settings (id,user_id,is_receive_email,is_message_sound)
SELECT 900000004,900000004,0,0
WHERE EXISTS (SELECT 1 FROM user WHERE id=900000004 AND email='admin@wba.test' AND role_id=2)
AND NOT EXISTS (SELECT 1 FROM user_settings WHERE user_id=900000004 OR id=900000004);
