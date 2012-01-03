col rlog new_value v_log
set define on
set timi off
set time off

set head off
select SYS_CONTEXT('USERENV', 'DB_NAME')||'-'||SYS_CONTEXT('USERENV', 'SESSION_USER')||'_'||to_char(sysdate,'YYYYMMDDHH24MI') rlog FROM dual;
set head on

spool ./AUTOALERT_DF/1_AUTOALERT/_Install_Logs/run_all_&v_log..log

set define off
set serveroutput on

show user

@ ./AUTOALERT_DF/1_AUTOALERT/Scripts/_insert_tables.sql

COMMIT;

spool off

