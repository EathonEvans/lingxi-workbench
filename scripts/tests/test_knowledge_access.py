"""用 SQLite 执行生产授权 SQL 的关系语义回归；不替代 PostgreSQL / MyBatis 集成验收。"""
import re
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAPPER = ROOT / 'application/src/main/java/com/lingxi/workbench/knowledge/repo/mapper/KnowledgeBaseMapper.java'
SQL = re.search(r'@Select\("""(.*?)"""\)', MAPPER.read_text(), re.S).group(1)
SQL = re.sub(r'#\{(\w+)\}', r':\1', SQL)


class KnowledgeAccessSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.executescript("""
            ATTACH DATABASE ':memory:' AS lingxi_core;
            CREATE TABLE lingxi_core.ai_knowledge_base(id INTEGER PRIMARY KEY, status TEXT, access_mode TEXT);
            CREATE TABLE lingxi_core.sys_user(id INTEGER PRIMARY KEY, status TEXT, department_id INTEGER);
            CREATE TABLE lingxi_core.sys_user_application_access(user_id INTEGER PRIMARY KEY, enabled BOOLEAN);
            CREATE TABLE lingxi_core.sys_user_role(user_id INTEGER, role_id INTEGER);
            CREATE TABLE lingxi_core.sys_role(id INTEGER PRIMARY KEY, status TEXT);
            CREATE TABLE lingxi_core.sys_department(id INTEGER PRIMARY KEY, status TEXT, parent_id INTEGER);
            CREATE TABLE lingxi_core.ai_knowledge_base_access(knowledge_base_id INTEGER, subject_type TEXT, subject_id INTEGER,
                PRIMARY KEY(knowledge_base_id, subject_type, subject_id));
            INSERT INTO lingxi_core.ai_knowledge_base VALUES(1, 'ACTIVE', 'RESTRICTED'), (2, 'ACTIVE', 'RESTRICTED');
            INSERT INTO lingxi_core.sys_user VALUES(12, 'ACTIVE', 3), (13, 'ACTIVE', 4);
            INSERT INTO lingxi_core.sys_user_application_access VALUES(12, TRUE), (13, TRUE);
            INSERT INTO lingxi_core.sys_user_role VALUES(12, 2);
            INSERT INTO lingxi_core.sys_role VALUES(2, 'ACTIVE');
            INSERT INTO lingxi_core.sys_department VALUES(3, 'ACTIVE', NULL), (4, 'ACTIVE', 3);
        """)

    def tearDown(self):
        self.db.close()

    def allowed(self, user=12, base=1):
        return bool(self.db.execute(SQL, {'knowledgeBaseId': base, 'userId': user}).fetchone()[0])

    def grant(self, kind, subject):
        self.db.execute('INSERT INTO lingxi_core.ai_knowledge_base_access VALUES(1, ?, ?)', (kind, subject))

    def test_unconfigured_and_missing_identity_are_denied(self):
        self.assertFalse(self.allowed())
        self.assertFalse(self.allowed(user=999))
        self.assertFalse(self.allowed(user=None))

    def test_user_grant_does_not_cross_user_or_knowledge_base(self):
        self.grant('USER', 12)
        self.assertTrue(self.allowed())
        self.assertFalse(self.allowed(user=13))
        self.assertFalse(self.allowed(base=2))

    def test_role_membership_and_role_status_are_live(self):
        self.grant('ROLE', 2)
        self.assertTrue(self.allowed())
        self.db.execute("UPDATE lingxi_core.sys_role SET status='DISABLED'")
        self.assertFalse(self.allowed())
        self.db.execute("UPDATE lingxi_core.sys_role SET status='ACTIVE'")
        self.db.execute('DELETE FROM lingxi_core.sys_user_role')
        self.assertFalse(self.allowed())

    def test_department_grant_only_includes_direct_active_members(self):
        self.grant('DEPARTMENT', 3)
        self.assertTrue(self.allowed())
        self.assertFalse(self.allowed(user=13))
        self.db.execute("UPDATE lingxi_core.sys_department SET status='DISABLED' WHERE id=3")
        self.assertFalse(self.allowed())

    def test_union_and_revocation_are_live(self):
        self.grant('ROLE', 2)
        self.grant('USER', 13)
        self.assertTrue(self.allowed())
        self.assertTrue(self.allowed(user=13))
        self.db.execute('DELETE FROM lingxi_core.ai_knowledge_base_access')
        self.assertFalse(self.allowed())
        self.assertFalse(self.allowed(user=13))

    def test_public_mode_still_requires_active_user_and_application_access(self):
        self.db.execute("UPDATE lingxi_core.ai_knowledge_base SET access_mode='AUTHENTICATED'")
        self.assertTrue(self.allowed())
        self.assertFalse(self.allowed(user=999))
        self.db.execute('UPDATE lingxi_core.sys_user_application_access SET enabled=FALSE WHERE user_id=12')
        self.assertFalse(self.allowed())
        self.db.execute('UPDATE lingxi_core.sys_user_application_access SET enabled=TRUE WHERE user_id=12')
        self.db.execute("UPDATE lingxi_core.sys_user SET status='DISABLED' WHERE id=12")
        self.assertFalse(self.allowed())

    def test_disabled_base_and_unknown_mode_always_deny(self):
        self.grant('USER', 12)
        self.db.execute("UPDATE lingxi_core.ai_knowledge_base SET status='DISABLED' WHERE id=1")
        self.assertFalse(self.allowed())
        self.db.execute("UPDATE lingxi_core.ai_knowledge_base SET status='ACTIVE', access_mode='UNKNOWN' WHERE id=1")
        self.assertFalse(self.allowed())


if __name__ == '__main__':
    unittest.main()
