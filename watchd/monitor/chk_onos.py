import requests
import json
import base64

from api.sona_log import LOG
from api.watcherdb import DB
from api.config import CONF
from api.sbapi import SshCommand


# /onos/v1/applications
# N/A for rest apis /onos/v1/web:list
# /onos/v1/clusters
# /onos/v1/devices
# /onos/v1/links

def onos_api_req(node_ip, url_path):
    try:
        url = "http://%s:%d/%s" % (node_ip, CONF.onos()['api_port'], url_path)
        auth = CONF.onos()['api_user_passwd'].split(':')
        timeout = CONF.onos()['api_timeout_sec']
           
        #LOG.info('ONOS API REQUEST: url=%s auth=%s timeout=%s', url, auth, timeout)
        rsp = requests.get(url, auth=(auth[0], auth[1]), timeout=timeout)
        #LOG.info('ONOS API RESPONSE: status=%s body=%s', str(rsp.status_code), rsp.content)

    except:
        # req timeout
        LOG.exception()
        return -1, None

    if rsp.status_code != 200:
        return -2, None

    try:
        body = json.loads(rsp.content.replace("\'", '"'))
        return rsp.status_code, body

    except:
        LOG.exception()
        return -2, None


def onos_app_check(conn, db_log, node_name, node_ip):
    try:
        status = 'ok'
        app_active_list = list()

        app_list = []
        fail_reason = []

        ret, rsp = onos_api_req(node_ip, 'onos/v1/applications')
        if rsp is not None:
            for app_rsp in rsp['applications']:
                if app_rsp['state'] == 'ACTIVE':
                    app_active_list.append(app_rsp['name'].replace('org.onosproject.', ''))

            for app in CONF.onos()['app_list']:
                if app in app_active_list:
                    app_json = {'name': app, 'status': 'ok', 'monitor_item': True}
                    app_active_list.remove(app)
                else:
                    status = 'nok'
                    app_json = {'name': app, 'status': 'nok', 'monitor_item': True}
                    fail_reason.append(app_json)
                app_list.append(app_json)

            for app in app_active_list:
                app_json = {'name': app, 'status': 'ok', 'monitor_item': False}
                app_list.append(app_json)
        else:
            LOG.error("\'%s\' ONOS Application Check Error", node_ip)
            status = 'fail'
            app_list = 'fail'

        try:
            sql = 'UPDATE ' + DB.ONOS_TBL + \
                  ' SET applist = \"' + str(app_list) + '\"' +\
                  ' WHERE nodename = \'' + node_name + '\''
            db_log.write_log('----- UPDATE ONOS APP INFO -----\n' + sql)

            if DB.sql_execute(sql, conn) != 'SUCCESS':
                db_log.write_log('[FAIL] ONOS APP DB Update Fail.')
        except:
            LOG.exception()
    except:
        LOG.exception()
        status = 'fail'

    return status, fail_reason


def onos_rest_check(conn, db_log, node_name, node_ip):
    try:
        web_status = 'ok'

        web_list = []
        fail_reason = []

        web_rt = SshCommand.onos_ssh_exec(node_ip, 'web:list')

        if web_rt is not None:
            for web in CONF.onos()['rest_list']:
                for line in web_rt.splitlines():
                    if line.startswith('ID') or line.startswith('--'):
                        continue

                    if ' ' + web + ' ' in line:
                        if not ('Active' in line and 'Deployed' in line):
                            rest_json = {'name': web, 'status': 'nok', 'monitor_item': True}
                            fail_reason.append(rest_json)
                            web_status = 'nok'
                        else:
                            rest_json = {'name': web, 'status': 'ok', 'monitor_item': True}

                        web_list.append(rest_json)

            for line in web_rt.splitlines():
                if line.startswith('ID') or line.startswith('--'):
                    continue

                name = " ".join(line.split()).split(' ')[10]

                if not name in CONF.onos()['rest_list']:
                    if not ('Active' in line and 'Deployed' in line):
                        rest_json = {'name': name, 'status': 'nok', 'monitor_item': False}
                    else:
                        rest_json = {'name': name, 'status': 'ok', 'monitor_item': False}

                    web_list.append(rest_json)
        else:
            LOG.error("\'%s\' ONOS Rest Check Error", node_ip)
            web_status = 'fail'
            web_list = 'fail'

        try:
            sql = 'UPDATE ' + DB.ONOS_TBL + \
                  ' SET weblist = \"' + str(web_list) + '\"' +\
                  ' WHERE nodename = \'' + node_name + '\''
            db_log.write_log('----- UPDATE ONOS REST INFO -----\n' + sql)

            if DB.sql_execute(sql, conn) != 'SUCCESS':
                db_log.write_log('[FAIL] ONOS REST DB Update Fail.')
        except:
            LOG.exception()

    except:
        LOG.exception()
        web_status = 'fail'

    return web_status, fail_reason


def onos_conn_check(conn, db_log, node_name, node_ip):
    # called on each ONOS node in NODE_INFO_TBL
    try:

        # check cluster nodes
        # TODO: need to be optimized
        node_rt = SshCommand.onos_ssh_exec(node_ip, 'nodes')
        cluster_ip_list = []
        cluster_list = []
        cluster_fail_reason = []
        cluster_status = 'ok'
        if node_rt is not None:
            try:
                sql = 'SELECT ip_addr FROM ' + DB.NODE_INFO_TBL + ' WHERE type = \'ONOS\''
                nodes_info = conn.cursor().execute(sql).fetchall()

                for onos_ip in nodes_info:
                    find_flag = False
                    summary_rt = SshCommand.onos_ssh_exec(onos_ip[0], 'summary')
                    if summary_rt is not None:
                        data_ip = str(summary_rt).split(',')[0].split('=')[1]

                        for line in node_rt.splitlines():
                            id = line.split(',')[0].split('=')[1]
                            address = line.split(',')[1].split('=')[1]
                            state = line.split(',')[2].split('=')[1].split(' ')[0]

                            if data_ip == address.split(':')[0]:
                                find_flag = True
                                cluster_ip_list.append(address)

                                rest_json = {'id': id, 'address': address, 'status': 'ok',
                                             'monitor_item': True}
                                cluster_list.append(rest_json)

                                if not state == 'READY':
                                    cluster_status = 'nok'
                                    cluster_fail_reason.append(rest_json)

                        if not find_flag:
                            rest_json = {'id': data_ip, 'address': '-', 'status': 'nok',
                                         'monitor_item': True}
                            cluster_list.append(rest_json)
                            cluster_status = 'nok'
                            cluster_fail_reason.append(rest_json)
                    else:
                        rest_json = {'id': onos_ip[0], 'address': '-', 'status': 'nok',
                                     'monitor_item': True}
                        cluster_list.append(rest_json)

                if summary_rt is not None:
                    for line in nodesrt.splitlines():
                        id = line.split(',')[0].split('=')[1]
                        address = line.split(',')[1].split('=')[1]
                        state = line.split(',')[2].split('=')[1].split(' ')[0]

                        if not state == 'READY':
                            status = 'nok'
                        else:
                            status = 'ok'

                        if not address in cluster_ip_list:
                            rest_json = {'id': id, 'address': address, 'status': status,
                                         'monitor_item': True}
                            cluster_list.append(rest_json)
            except:
                pass
        else:
            LOG.error("\'%s\' Connection Check Error(nodes)", node_ip)
            cluster_status = 'fail'

        # check devices
        device_list = []
        device_status = 'ok'
        device_fail_reason = []
        device_rt = SshCommand.onos_ssh_exec(node_ip, 'devices')
        device_tbl = dict()
        if device_rt is not None:
            try:
                for line in device_rt.splitlines():
                    if not line.startswith('id=of'):
                        continue
                    device = dict()
                    for f in line.split(','):
                        s = f.split('=')
                        if (len(s) >= 2):
                            device[s[0].strip()] = s[1].strip()
                    device_tbl[device['id']] = device
 
                for id in CONF.onos()['device_list']:
                    if id is '':
                        continue;  # no config
                    if id in device_tbl:
                        device = device_tbl[id];
                        device['monitor_item'] = True
                        if device['available'] != 'true':
                           device_status = 'nok'
                           device_fail_reason.append('Device ' + id + ' DOWN')
                        device_tbl.pop(id)
                    else:
                        device = { 'id':id, 'available':"false", 'channelId':'-',
                                   'name':'-', 'role':'-', 'monitor_item':True }
                        device_status = 'nok'
                        device_fail_reason.append('Device ' + id + ' DOWN')
                    device_list.append(device)

                for device in device_tbl.values():
                    device['monitor_item'] = False;
                    device_list.append(device)
 
            except:
                LOG.exception()
                LOG.error("\'%s\' Connection Check Error(devices)", node_ip)
                device_status = 'fail'
        else:
            LOG.error("\'%s\' Connection Check Error(devices)", node_ip)
            device_status = 'fail'

        # check links
        link_list = []
        link_status = 'ok'
        link_fail_reason = []
        link_rt = SshCommand.onos_ssh_exec(node_ip, 'links')
        link_tbl = dict()
        if link_rt is not None:
            try:
                for line in link_rt.splitlines():
                    if not line.startswith('src=of'):
                        continue
                    link = dict()
                    for f in line.split(','):
                        s = f.split('=')
                        if (len(s) >= 2):
                            link[s[0].strip()] = s[1].strip()
                    link_tbl[link['src'] + '-' + link['dst']] = link

                for id in CONF.onos()['link_list']:
                    if id is '':
                        continue;
                    if len(id.split('-')) != 2:
                        link = {'src':id, 'dst':'(invalid_link_config)',
                                'expected':'false', 'state':'-', 'type':"-",
                                'monitor_item':True }
                        link_status = 'nok'
                        link_fail_reason.append('Link ' + id + ' is configed as INVALID ID FORMAT')
                        link_list.append(link)
                        continue;

                    if id in link_tbl:
                        link = link_tbl[id];
                        link['monitor_item'] = True
                        if link['state'] != 'ACTIVE':
                            link_status = 'nok'
                            link_fail_reason.append('Link ' + id + ' DOWN')
                        link_list.append(link)
                        link_tbl.pop(id);
                    else:
                        link = {'src':id.split('-')[0], 'dst':id.split('-')[1],
                                'expected':'false', 'state':'-', 'type':"-",
                                'monitor_item':True }
                        link_status = 'nok'
                        link_fail_reason.append('Link ' + id + ' DOWN')
                        link_list.append(link)

                    rev_id = id.split('-')[1] + '-' + id.split('-')[0]
                    if rev_id in link_tbl:
                        link = link_tbl[rev_id];
                        link['monitor_item'] = True
                        if link['state'] != 'ACTIVE':
                            link_status = 'nok'
                            link_fail_reason.append('Link' + rev_id + ' DOWN')
                        link_list.append(link)
                        link_tbl.pop(rev_id)
                    else:
                        link = {'src':rev_id.split('-')[0], 'dst':rev_id.split('-')[1],
                                'expected':'false', 'state':'-', 'type':"-",
                                'monitor_item':True }
                        link_status = 'nok'
                        link_fail_reason.append('Link ' + rev_id + ' DOWN')
                        link_list.append(link)

                for link in link_tbl.values():
                    link['monitor_item'] = False;
                    link_list.append(link)
 
            except:
                LOG.exception()
                LOG.error("\'%s\' Connection Check Error(links)", node_ip)
                link_status = 'fail'
        else:
            LOG.error("\'%s\' Connection Check Error(links)", node_ip)
            link_status = 'fail'

        try:
            sql = 'UPDATE ' + DB.ONOS_TBL + \
                  ' SET ' + \
                  ' cluster = \"' + str(cluster_list) + '\",' \
                  ' device = \"' + str(device_list) + '\",' \
                  ' link = \"' + str(link_list) + '\"' \
                  ' WHERE nodename = \'' + node_name + '\''
            db_log.write_log('----- UPDATE ONOS CONNECTION INFO -----\n' + sql)

            if DB.sql_execute(sql, conn) != 'SUCCESS':
                db_log.write_log('[FAIL] ONOS CONNECTION DB Update Fail.')
        except:
            LOG.exception()

    except:
        LOG.exception()
        cluster_status = 'fail'
        device_status = 'fail'
        link_status = 'fail'

    return cluster_status, device_status, link_status, cluster_fail_reason, device_fail_reason, link_fail_reason


