BEGIN;

-- Application 当前编辑身份：仅在发布时写入不可变版本，不直接影响线上助手。
ALTER TABLE lingxi_core.ai_application
    ADD COLUMN IF NOT EXISTS avatar VARCHAR(255);

COMMENT ON COLUMN lingxi_core.ai_application.avatar IS
    'Application 编辑态头像；仅允许当前操作人通过 application-avatar 上传通道绑定';

-- 对话消息已锁定 ApplicationVersion，因此名称和头像随版本一并固定。
ALTER TABLE lingxi_core.ai_application_version
    ADD COLUMN IF NOT EXISTS name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS avatar VARCHAR(255);

UPDATE lingxi_core.ai_application_version application_version
SET name = application.name
FROM lingxi_core.ai_application application
WHERE application_version.application_id = application.id
  AND application_version.name IS NULL;

DO
$$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM lingxi_core.ai_application_version
        WHERE name IS NULL
    ) THEN
        RAISE EXCEPTION 'ai_application_version.name 存在空值，无法建立应用版本身份';
    END IF;
END
$$;

ALTER TABLE lingxi_core.ai_application_version
    ALTER COLUMN name SET NOT NULL;

COMMENT ON COLUMN lingxi_core.ai_application_version.name IS
    '发布时的应用展示名称；历史对话按 application_version_id 读取';

COMMENT ON COLUMN lingxi_core.ai_application_version.avatar IS
    '发布时的应用头像；为空时由客户端显示名称缩写';

COMMIT;
