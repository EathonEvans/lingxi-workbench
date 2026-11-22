BEGIN;

DELETE FROM lingxi_core.sys_role_menu
 WHERE menu_id IN (
     SELECT id
       FROM lingxi_core.sys_menu
      WHERE code IN (
          'APPLICATION_MANAGE',
          'APPLICATION_PUBLISH',
          'APPLICATION_LIST',
          'APPLICATION'
      )
 );
DELETE FROM lingxi_core.sys_menu
 WHERE code IN ('APPLICATION_MANAGE', 'APPLICATION_PUBLISH');
DELETE FROM lingxi_core.sys_menu
 WHERE code = 'APPLICATION_LIST';
DELETE FROM lingxi_core.sys_menu
 WHERE code = 'APPLICATION';

COMMIT;
