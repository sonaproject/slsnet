import requests
import json
from datetime import datetime

from api.sona_log import LOG
from api.watcherdb import DB
from api.config import CONF
import api.alarm as ALARM


def process_event(conn, db_log, node_name, type, id, pre_value, cur_value, reason):
    try:
        if not is_monitor_item(type, id):
            return '-'
        elif pre_value != cur_value:
            occur_event(conn, db_log, node_name, id, pre_value, cur_value, reason)

        return cur_value
    except:
        LOG.exception()


def is_monitor_item(node_type, item_type):
    try:
        conf_dict = CONF_MAP[node_type.upper()]()

        if conf_dict.has_key('alarm_off_list'):
            for item in (CONF_MAP[node_type.upper()]())['alarm_off_list']:
                if item_type in item:
                    return False
    except:
        LOG.exception()

    return True


def get_grade(item, value):
    try:
        critical, major, minor = (CONF.alarm()[item])

        if value == '-1':
            return 'fail'

        if float(value) >= float(critical):
            return 'critical'
        elif float(value) >= float(major):
            return 'major'
        elif float(value) >= float(minor):
            return 'minor'

        return 'normal'
    except:
        LOG.exception()
        return 'fail'


def occur_event(conn, db_log, node_name, item, pre_grade, cur_grade, reason):
    try:
        time = str(datetime.now())
        sql = 'UPDATE ' + DB.EVENT_TBL + \
              ' SET grade = \'' + cur_grade + '\'' + ',' + \
              ' pre_grade = \'' + pre_grade + '\'' + ',' + \
              ' reason = \"' + str(reason) + '\"' + ',' + \
              ' time = \'' + time + '\'' + \
              ' WHERE nodename = \'' + node_name + '\' and item = \'' + item + '\''
        db_log.write_log('----- UPDATE EVENT INFO -----\n' + sql)

        if DB.sql_execute(sql, conn) != 'SUCCESS':
            db_log.write_log('[FAIL] EVENT INFO DB Update Fail.')

        push_event(node_name, item, cur_grade, pre_grade, reason, time)
    except:
        LOG.exception()


history_log = None
def set_history_log(log):
    global history_log
    history_log = log


def push_event(node_name, item, grade, pre_grade, reason, time):
    global history_log

    try:
        history_log.write_log('[%s][%s][%s][%s] %s', node_name, item, grade, pre_grade, reason)

        sql = 'SELECT * FROM ' + DB.REGI_SYS_TBL

        with DB.connection() as conn:
            url_list = conn.cursor().execute(sql).fetchall()

        conn.close()

        for url, auth in url_list:
            header = {'Content-Type': 'application/json', 'Authorization': str(auth)}
            req_body = {'system': node_name, 'item': item, 'grade': grade, 'pre_grade': pre_grade, 'reason': reason, 'time': time}
            req_body_json = json.dumps(req_body)

            try:
                requests.post(str(url), headers=header, data=req_body_json, timeout = 2)
            except:
                # Push event does not respond
                pass

        # send alarm notification
        reason_str = ''
        if type(reason) == list:
            if len(reason) > 0:
                 reason_str = '-- ' + '\n-- '.join(reason)
        else:
            reason_str = str(reason)
        ALARM.send_alarm(node_name + ' ' + item + ' goes ' + grade.upper(),
                         '%s %s state changed: %s -> %s\n%s'
                         % (node_name, item, pre_grade.upper(), grade.upper(), reason_str))

    except:
        LOG.exception()


CONF_MAP = {'ONOS': CONF.onos }
