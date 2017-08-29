# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

import pexpect

from subprocess import Popen, PIPE
from config import CONF
from sona_log import LOG

class SshCommand:
    ssh_options = '-o StrictHostKeyChecking=no ' \
              '-o ConnectTimeout=' + str(CONF.ssh_conn()['ssh_req_timeout'])

    @classmethod
    def ssh_exec(cls, username, node, command):
        cmd = 'ssh %s %s@%s %s' % (cls.ssh_options, username, node, command)

        try:
            result = Popen(cmd, stdout=PIPE, stderr=PIPE, shell=True)
            output, error = result.communicate()

            if result.returncode != 0:
                LOG.error("\'%s\' SSH_Cmd Fail, cause => %s", node, error)
                return
            else:
                # LOG.info("ssh command execute successful \n%s", output)
                return output
        except:
            LOG.exception()

    @classmethod
    def onos_ssh_exec(cls, node_ip, command):
        local_ssh_options = cls.ssh_options + " -p 8101"

        cmd = 'ssh %s %s %s' % (local_ssh_options, node_ip, command)

        try:
            result = Popen(cmd, stdout=PIPE, stderr=PIPE, shell=True)
            output, error = result.communicate()

            if result.returncode != 0:
                LOG.error("ONOS(%s) SSH_Cmd Fail, cause => %s", node_ip, error)
                return
            else:
                # LOG.info("ONOS ssh command execute successful \n%s", output)
                return output
        except:
            LOG.exception()

    @classmethod
    def ssh_pexpect(cls, username, node, onos_ip, command):
        cmd = 'ssh %s %s@%s' % (cls.ssh_options, username, node)

        try:
            LOG.info('ssh_pexpect cmd = ' + cmd)
            ssh_conn = pexpect.spawn(cmd)

            rt1 = ssh_conn.expect(['#', '\$', pexpect.EOF], timeout=CONF.ssh_conn()['ssh_req_timeout'])

            if rt1 == 0:
                cmd = 'ssh -p 8101 karaf@' + onos_ip + ' ' + command

                LOG.info('ssh_pexpect cmd = ' + cmd)
                ssh_conn.sendline(cmd)
                rt2 = ssh_conn.expect(['Password:', pexpect.EOF], timeout=CONF.ssh_conn()['ssh_req_timeout'])

                if rt2 == 0:
                    ssh_conn.sendline('karaf')
                    ssh_conn.expect(['#', '\$', pexpect.EOF], timeout=CONF.ssh_conn()['ssh_req_timeout'])

                    str_output = str(ssh_conn.before)

                    ret = ''
                    for line in str_output.splitlines():
                        if (line.strip() == '') or ('#' in line) or ('$' in line) or ('~' in line) or ('@' in line):
                            continue

                        ret = ret + line + '\n'

                    return ret
                else:
                    return "fail"
            elif rt1 == 1:
                LOG.error('%s', ssh_conn.before)
            elif rt1 == 2:
                LOG.error("[ssh_pexpect] connection timeout")

            return "fail"
        except:
            LOG.exception()
            return "fail"

