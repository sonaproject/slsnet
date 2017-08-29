# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

import re
import time
import pexpect
import threading
import base64
from netaddr import IPNetwork, IPAddress

from sona_log import LOG
from config import CONF
from sbapi import SshCommand

class Conditions:
    def __init__(self):
        self.cond_dict = dict()

        self.cond_dict['in_port'] = ''
        self.cond_dict['dl_src'] = ''
        self.cond_dict['dl_dst'] = ''
        self.cond_dict['dl_type'] = '0x0800'
        self.cond_dict['nw_src'] = ''
        self.cond_dict['nw_dst'] = ''
        self.cond_dict['nw_proto'] = ''
        self.cond_dict['tp_src'] = ''
        self.cond_dict['tp_dst'] = ''
        self.cond_dict['tun_id'] = ''

        self.cur_target_ip = ''
        self.cur_target_hostname = ''


class Topology:
    ONOS_SUBNET = 'openstack-networks'
    ONOS_HOST = 'hosts'
    ONOS_FLOATINGIP = 'openstack-floatingips'
    ONOS_OPENSTACKNODES = 'openstack-nodes'

    def __init__(self, openstack_only = False):
        if not openstack_only:
            self.subnets = self.get_subnets(self.get_onos_ip())
            self.floatingips = self.get_floatingips(self.get_onos_ip())

        self.openstack = self.get_openstacknodes(self.get_onos_ip())

    def get_hosts(self, onos_ip, find_cond):
        LOG.info('onos command = ' + self.ONOS_HOST + '| grep ' + find_cond)
        onos_ssh_result = SshCommand.onos_ssh_exec(onos_ip, self.ONOS_HOST + '| grep ' + find_cond)
        return onos_ssh_result

    def get_openstacknodes(self, onos_ip):
        onos_ssh_result = SshCommand.onos_ssh_exec(onos_ip, self.ONOS_OPENSTACKNODES)
        return onos_ssh_result

    def get_openstack_info(self, find_cond, type):
        for line in self.openstack.splitlines():
            if find_cond in line:
                node_info = " ".join(line.split())

                tmp = node_info.split(' ')

                if type == 'ip':
                    if tmp[3].startswith('of:'):
                        target_ip = tmp[4]
                    else:
                        target_ip = tmp[3]

                    return target_ip
                elif type == 'hostname':
                    return tmp[0]

        return ''

    def get_onos_ip(self):
        return str(list(CONF.onos()['list']).pop()).split(':')[-1]

    def get_gateway_ip(self):
        return str(list(CONF.openstack()['gateway_list']).pop()).split(':')[-1]

    def get_subnets(self, onos_ip):
        onos_ssh_result = SshCommand.onos_ssh_exec(onos_ip, self.ONOS_SUBNET)
        subnets = re.findall(r'[0-9]+(?:\.[0-9]+){3}(?:/\d\d)', onos_ssh_result)
        return subnets

    def get_floatingips(self, onos_ip):
        onos_ssh_result = SshCommand.onos_ssh_exec(onos_ip, self.ONOS_FLOATINGIP)
        floatingips = re.findall(r'[0-9]+(?:\.[0-9]+){3}(?: +)(?:[0-9]+(?:\.[0-9]+){3})', onos_ssh_result)

        floatingip_set = {}
        for l in floatingips:
            floatingip_set[l.split(' ')[0]] = l.split(' ')[-1]

        return floatingip_set


def flow_trace(condition_json):
    trace_result = {}

    sona_topology = Topology()

    trace_condition = set_condition(sona_topology, condition_json)

    is_reverse = False
    if dict(condition_json).has_key('reverse'):
        is_reverse = condition_json['reverse']

    if find_target(trace_condition, sona_topology) == False:
        return False, None

    trace_result['trace_result'], trace_result['trace_success'] = onsway_trace(sona_topology, trace_condition)

    if is_reverse:
        reverse_condition = Conditions()

        reverse_condition.cond_dict['nw_src'] = trace_condition.cond_dict['nw_dst']
        reverse_condition.cond_dict['nw_dst'] = trace_condition.cond_dict['nw_src']
        reverse_condition.cond_dict['dl_type'] = trace_condition.cond_dict['dl_type']
        reverse_condition.cond_dict['nw_proto'] = trace_condition.cond_dict['nw_proto']

        reverse_condition.cond_dict['dl_dst'] = get_dl_dst(sona_topology, reverse_condition.cond_dict['nw_src'],
                                                           reverse_condition.cond_dict['nw_dst'])

        if find_target(reverse_condition, sona_topology) == False:
            return False, trace_result

        trace_result['reverse_trace_result'], trace_result['reverse_trace_success'] = onsway_trace(sona_topology, reverse_condition)

    return True, trace_result


def find_target(trace_condition, sona_topology):
    # find switch info
    LOG.info('source ip = ' + trace_condition.cond_dict['nw_src'])
    result = sona_topology.get_hosts(sona_topology.get_onos_ip(), '\"\[' + trace_condition.cond_dict['nw_src'] + '\]\"')

    # source ip = internal vm
    if result != None:
        switch_info = (result.split(',')[2].split('=')[1])[1:-1]
        switch_id = switch_info.split('/')[0]
        vm_port = switch_info.split('/')[1]

        LOG.info('swtich id = ' + switch_id)

        # find target node
        if trace_condition.cond_dict['in_port'] == '':
            trace_condition.cond_dict['in_port'] = vm_port

        trace_condition.cur_target_ip = sona_topology.get_openstack_info(' ' + switch_id + ' ', 'ip')
        trace_condition.cur_target_hostname = sona_topology.get_openstack_info(' ' + switch_id + ' ', 'hostname')

        if trace_condition.cur_target_ip == '' or trace_condition.cur_target_hostname == '':
            return False
    # source ip = external
    else:
        for net in sona_topology.subnets:
            if IPAddress(trace_condition.cond_dict['nw_src']) in IPNetwork(net):
                return False

        if trace_condition.cond_dict['nw_dst'] in sona_topology.floatingips.values():
            LOG.info('floating ip : ' + str(sona_topology.floatingips))

            for key in sona_topology.floatingips.keys():
                value = sona_topology.floatingips[key]

                if trace_condition.cond_dict['nw_dst'] == value:
                    trace_condition.cond_dict['nw_dst'] = key
                    break

        trace_condition.cur_target_ip = sona_topology.get_gateway_ip()

        # find hostname
        trace_condition.cur_target_hostname = sona_topology.get_openstack_info(' ' + trace_condition.cur_target_ip + ' ', 'hostname')

    return True


def set_condition(sona_topology, cond_json):
    condition_obj = Conditions()

    match_json = cond_json['matchingfields']

    for key in dict(match_json).keys():
        if key == 'source_ip':
            condition_obj.cond_dict['nw_src'] = match_json[key]
        elif key == 'destination_ip':
            condition_obj.cond_dict['nw_dst'] = match_json[key]
        elif key == 'source_input_port':
            condition_obj.cond_dict['in_port'] = match_json[key]
        elif key == 'source_mac':
            condition_obj.cond_dict['dl_src'] = match_json[key]
        elif key == 'destination_mac':
            condition_obj.cond_dict['dl_dst'] = match_json[key]
        elif key == 'ethernet_type':
            condition_obj.cond_dict['dl_type'] = match_json[key]
        elif key == 'ip_protocol':
            condition_obj.cond_dict['nw_proto'] = match_json[key]
        elif key == 'source_port':
            condition_obj.cond_dict['tp_src'] = match_json[key]
        elif key == 'destination_port':
            condition_obj.cond_dict['tp_dst'] = match_json[key]
        elif key == 'tun_id':
            condition_obj.cond_dict['tun_id'] = match_json[key]

    if condition_obj.cond_dict['dl_dst'] == '':
        condition_obj.cond_dict['dl_dst'] = get_dl_dst(sona_topology, condition_obj.cond_dict['nw_src'], condition_obj.cond_dict['nw_dst'])

    return condition_obj


def get_dl_dst(sona_topology, src_ip, dst_ip):
    is_same_net = False
    for net in sona_topology.subnets:
        if IPAddress(src_ip) in IPNetwork(net):
            if IPAddress(dst_ip) in IPNetwork(net):
                is_same_net = True

    if not is_same_net:
        return 'fe:00:00:00:00:02'
    else:
        return ''


def make_command(trace_conditions):
    cond_list = ''
    for key in dict(trace_conditions.cond_dict).keys():
        value = trace_conditions.cond_dict[key]

        if value != '':
            cond_list = cond_list + key + '=' + str(value) + ','

    command = 'sudo ovs-appctl ofproto/trace br-int \'' + cond_list.rstrip(',') + '\''
    LOG.info('trace command = ' + command)

    return command


def onsway_trace(sona_topology, trace_conditions):
    retry_flag = True
    up_down_result = []
    is_success = False

    while retry_flag:
        ssh_result = SshCommand.ssh_exec(CONF.openstack()['account'].split(':')[0], trace_conditions.cur_target_ip, make_command(trace_conditions))

        LOG.info('target_node = ' + trace_conditions.cur_target_ip)
        LOG.info('TRACE RESULT = ' + str(ssh_result))

        node_trace = dict()
        node_trace['trace_node_name'] = trace_conditions.cur_target_hostname

        process_result, retry_flag, is_success = process_trace(ssh_result, sona_topology, trace_conditions)

        node_trace['flow_rules'] = process_result

        trace_conditions.cond_dict['in_port'] = ''
        trace_conditions.cond_dict['dl_src'] = ''
        trace_conditions.cond_dict['dl_dst'] = ''
        trace_conditions.cond_dict['eth_dst'] = ''
        trace_conditions.cond_dict['eth_src'] = ''

        up_down_result.append(node_trace)

    return up_down_result, is_success


def process_trace(output, sona_topology, trace_conditions):
    try:
        retry_flag = False
        is_success = False

        result_flow = []
        lines = output.splitlines()

        for line in lines:
            line = line.strip()

            if line.startswith('Rule:'):
                rule_dict = dict()
                tmp = line.split(' ')
                rule_dict['table'] = int(tmp[1].split('=')[1])
                rule_dict['cookie'] = tmp[2].split('=')[1]

                selector_dict = {}
                for col in tmp[3].split(','):
                    tmp = col.split('=')

                    if len(tmp) == 2:
                        if tmp[0] in ['priority']:
                            rule_dict[tmp[0]] = int(tmp[1])
                        elif tmp[0] in ['in_port']:
                            selector_dict[tmp[0]] = int(tmp[1])
                        else:
                            selector_dict[tmp[0]] = tmp[1]

                if len(selector_dict.keys()) > 0:
                    rule_dict['selector'] = selector_dict

            elif line.startswith('OpenFlow actions='):
                action_dict = dict()

                action_list = line.split('=')[1].split(',')
                for action in action_list:
                    if action.startswith('set_field'):
                        type = action.split('->')[1]
                        value = action[action.find(':') + 1:action.find('-')]

                        action_dict[type] = value

                        if type == 'tun_dst':
                            # find next target
                            trace_conditions.cur_target_ip = sona_topology.get_openstack_info(' ' + value + ' ', 'ip')
                            trace_conditions.cur_target_hostname = sona_topology.get_openstack_info(' ' + value + ' ', 'hostname')
                        else:
                            trace_conditions.cond_dict[type] = value

                            if type == 'ip_dst':
                                trace_conditions.cond_dict['nw_dst'] = ''
                    else:
                        if action.startswith('group:'):
                            trace_conditions.cur_target_ip = sona_topology.get_gateway_ip()
                            trace_conditions.cur_target_hostname = sona_topology.get_openstack_info(
                                ' ' + trace_conditions.cur_target_ip + ' ', 'hostname')

                        if len(line.split('=')) == 3:
                            action = action + '=' + line.split('=')[2]

                        LOG.info('action = ' + action)
                        tmp = action.split(':')

                        if action.startswith('group:') or action.startswith('goto_table:'):
                            action_dict[tmp[0]] = int(tmp[1])
                        elif len(tmp) < 2:
                            action_dict[tmp[0]] = tmp[0]
                        else:
                            action_dict[tmp[0]] = tmp[1]

                rule_dict['actions'] = action_dict

                result_flow.append(rule_dict)

                if 'tun_dst' in line or 'group' in line:
                    retry_flag = True

                if 'output' in line or 'CONTROLLER' in line:
                    is_success = True
                    break

        return result_flow, retry_flag, is_success
    except:
        LOG.exception()
        return 'parsing error\n' + output


def traffic_test(condition_json):
    seconds = 0
    trace_result = []
    sona_topology = Topology(True)

    LOG.info('COND_JSON = ' + str(condition_json['traffic_test_list']))

    timeout_arr = []

    for test in condition_json['traffic_test_list']:
        timeout_json = {'command_result': 'timeout', 'node': test['node'], 'instance-id': test['instance_id']}
        timeout_arr.append(timeout_json)

    timeout = 10
    if dict(condition_json).has_key('timeout'):
        timeout = condition_json['timeout']

    i = 0
    for test in condition_json['traffic_test_list']:
        LOG.info('test = ' + str(test))
        run_thread = threading.Thread(target=run_test, args=(sona_topology, test, timeout_arr, i, timeout))
        run_thread.daemon = False
        run_thread.start()

        interval = 0
        if dict(test).has_key('next_command_interval'):
            interval = test['next_command_interval']

        while interval > 0:
            time.sleep(1)
            seconds = seconds + 1
            interval = interval - 1

        i = i + 1

    if timeout > seconds:
        wait_time = timeout - seconds

        while wait_time > 0:
            find_timeout = False
            i = 0
            while i < len(timeout_arr):
                if timeout_arr[i]['command_result'] == 'timeout':
                    find_timeout = True
                i = i + 1

            if find_timeout:
                time.sleep(1)
            else:
                break

            wait_time = wait_time - 1

    i = 0
    while i < len(timeout_arr):
        trace_result.append(timeout_arr[i])
        i = i + 1

    return True, trace_result


PROMPT = ['~# ', 'onos> ', '\$ ', '\# ', ':~$ ']

def run_test(sona_topology, test_json, timeout_arr, index, total_timeout):
    try:
        node = test_json['node']
        ins_id = test_json['instance_id']
        user = test_json['vm_user_id']
        pw = test_json['vm_user_password']
        command = test_json['traffic_test_command']

        ip = sona_topology.get_openstack_info(node, 'ip')

        if ip == '':
            str_output = node + ' node does not exist'
        else:
            node_id = CONF.openstack()['account'].split(':')[0]

            ssh_options = '-o StrictHostKeyChecking=no ' \
                          '-o ConnectTimeout=' + str(CONF.ssh_conn()['ssh_req_timeout'])
            cmd = 'ssh %s %s@%s' % (ssh_options, node_id, ip)

            try:
                LOG.info('ssh_pexpect cmd = ' + cmd)
                ssh_conn = pexpect.spawn(cmd)
                rt1 = ssh_conn.expect(PROMPT, timeout=CONF.ssh_conn()['ssh_req_timeout'])

                if rt1 == 0:
                    cmd = 'virsh console ' + ins_id

                    LOG.info('ssh_pexpect cmd = ' + cmd)
                    ssh_conn.sendline(cmd)
                    rt2 = ssh_conn.expect([pexpect.TIMEOUT, 'Escape character is', 'error:', pexpect.EOF], timeout=CONF.ssh_conn()['ssh_req_timeout'])

                    if rt2 == 0:
                        str_output = cmd + ' timeout'
                    elif rt2 == 1:
                        ssh_conn.sendline('\n')
                        try:
                            rt3 = ssh_conn.expect(['login: ', pexpect.EOF, pexpect.TIMEOUT], timeout=CONF.ssh_conn()['ssh_req_timeout'])

                            LOG.info('rt3 = ' + str(rt3))

                            if rt3 == 2:
                                str_output = 'Permission denied'
                            else:
                                ssh_conn.sendline(user)
                                rt_pw = ssh_conn.expect([pexpect.TIMEOUT, '[P|p]assword:', pexpect.EOF], timeout=CONF.ssh_conn()['ssh_req_timeout'])

                                if rt_pw == 1:
                                    ssh_conn.sendline(pw)
                                    rt4 = ssh_conn.expect([pexpect.TIMEOUT, 'Login incorrect', '~# ', 'onos> ', '\$ ', '\# ', ':~$ '],
                                                          timeout=CONF.ssh_conn()['ssh_req_timeout'])

                                    LOG.info('rt4 = ' + str(rt4))
                                    if rt4 == 0 or rt4 == 1:
                                        str_output = 'auth fail'
                                    else:
                                        ssh_conn.sendline(command)
                                        rt5 = ssh_conn.expect([pexpect.TIMEOUT, '~# ', 'onos> ', '\$ ', '\# ', ':~$ '], timeout=total_timeout)
                                        if rt5 == 0:
                                            str_output = 'timeout'
                                            ssh_conn.sendline('exit')
                                            ssh_conn.close()
                                        else:
                                            str_output = ssh_conn.before
                                            ssh_conn.sendline('exit')
                                            ssh_conn.close()
                                else:
                                    str_output = 'auth fail'
                        except:
                            str_output = 'exception'
                            ssh_conn.sendline('exit')
                            ssh_conn.close()
                    elif rt2 == 2:
                        result = {'command_result': 'virsh console error'}
                        timeout_arr[index] = result
                        return

                    else:
                        str_output = 'connection fail'

            except:
                LOG.exception()
                str_output = 'exception 1'
    except:
        LOG.exception()
        str_output = 'exception 2'

    result = {'command_result': str_output.replace('\r\n', '\n'), 'node': node, 'instance_id': ins_id}
    timeout_arr[index] = result
