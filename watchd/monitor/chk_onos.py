import requests
import json
import base64

from api.sona_log import LOG
from api.watcherdb import DB
from api.config import CONF
from api.sbapi import SshCommand


# /onos/v1/applications
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




def onos_check(conn, db_log, node_name, node_ip):
    # called on each ONOS node in NODE_INFO_TBL
    try:
        # check cluster nodes
        node_list = []
        node_status = 'ok'
        node_fail_reason = []
        ret, rsp = onos_api_req(node_ip, 'onos/v1/cluster')
        if rsp is not None:
            try:
                node_tbl = dict()
                for node in rsp['nodes']:
                    node_tbl[node['ip']] = node

                for onos_node in CONF.onos()['list']:
                    if len(onos_node.split(':')) != 2:
                        continue;
                    id = onos_node.split(':')[0]
                    ip = onos_node.split(':')[1]
                    if id is '' or ip is '':
                        continue;
                    if ip in node_tbl:
                        node = node_tbl[ip];
                        node['id'] = id
                        node['monitor_item'] = True
                        if node['status'] != 'READY':
                           node_status = 'nok'
                           node_fail_reason.append('Node ' + id + ' DOWN')
                        node_tbl.pop(ip)
                    else:
                        node = {'id': id, 'ip': ip, 'status': 'nok',
                                'monitor_item': True}
                        node_status = 'nok'
                        node_fail_reason.append('Node ' + id + ' DOWN')
                    node_list.append(node)

                for node in node_tbl.values():
                    node['monitor_item'] = False;
                    node_list.append(node)
 
            except:
                LOG.exception()
                LOG.error("\'%s\' ONOS Check Error(nodes)", node_ip)
                node_status = 'fail'

        # check devices
        device_list = []
        device_status = 'ok'
        device_fail_reason = []
        ret, rsp = onos_api_req(node_ip, 'onos/v1/devices')
        if rsp is not None:
            try:
                device_tbl = dict()
                for device in rsp['devices']:
                    device['id'] = 'of:' + device['chassisId'].rjust(16, '0')
                    device_tbl[device['id']] = device
 
                for id in CONF.onos()['device_list']:
                    if id is '':
                        continue;  # no config
                    if id in device_tbl:
                        device = device_tbl[id];
                        device['monitor_item'] = True
                        if not device['available']:
                           device_status = 'nok'
                           device_fail_reason.append('Device ' + id + ' DOWN')
                        device_tbl.pop(id)
                    else:
                        device = { 'id':id, 'available':False, 'channelId':'-',
                                   'name':'-', 'role':'-', 'monitor_item':True }
                        device_status = 'nok'
                        device_fail_reason.append('Device ' + id + ' DOWN')
                    device_list.append(device)

                for device in device_tbl.values():
                    device['monitor_item'] = False;
                    device_list.append(device)
 
            except:
                LOG.exception()
                LOG.error("\'%s\' ONOS Check Error(devices)", node_ip)
                device_status = 'fail'
        else:
            LOG.error("\'%s\' ONOS Check Error(devices)", node_ip)
            device_status = 'fail'

        # check links
        link_list = []
        link_status = 'ok'
        link_fail_reason = []
        ret, rsp = onos_api_req(node_ip, 'onos/v1/links')
        if rsp is not None:
            try:
                link_tbl = dict()
                for link in rsp['links']:
                    link['src'] = link['src']['device'] + '/' + link['src']['port']
                    link['dst'] = link['dst']['device'] + '/' + link['dst']['port']
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
                LOG.error("\'%s\' ONOS Check Error(links)", node_ip)
                link_status = 'fail'

        # check apps
        app_list = []
        app_status = 'ok'
        app_fail_reason = []
        ret, rsp = onos_api_req(node_ip, 'onos/v1/applications')
        if rsp is not None:
            try:
                active_app_list = []
                for app_rsp in rsp['applications']:
                    if app_rsp['state'] == 'ACTIVE':
                        active_app_list.append(app_rsp['name'].replace('org.onosproject.', ''))

                for app in CONF.onos()['app_list']:
                    if app in active_app_list:
                        app_json = {'name': app, 'status': 'ok', 'monitor_item': True}
                        active_app_list.remove(app)
                    else:
                        app_json = {'name': app, 'status': 'nok', 'monitor_item': True}
                        app_status = 'nok'
                        app_fail_reason.append(app_json)
                    app_list.append(app_json)

                for app in active_app_list:
                    app_json = {'name': app, 'status': 'ok', 'monitor_item': False}
                    app_list.append(app_json)

            except:
               LOG.exception()
               LOG.error("\'%s\' ONOS Check Error(apps)", node_ip)
               app_status = 'fail'

        else:
            LOG.error("\'%s\' ONOS Check Error(apps)", node_ip)
            link_status = 'fail'

        # store to db
        try:
            sql = 'UPDATE ' + DB.ONOS_TBL + \
                  ' SET ' + \
                  ' cluster = \"' + str(node_list) + '\",' \
                  ' device = \"' + str(device_list) + '\",' \
                  ' link = \"' + str(link_list) + '\",' \
                  ' app = \"' + str(app_list) + '\"' \
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
        app_status = 'fail'

    return node_status, device_status, link_status, app_status, node_fail_reason, device_fail_reason, link_fail_reason, app_fail_reason


