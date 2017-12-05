#!/usr/local/bin/python
# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# FROM: modified from SONA Monitoring Solutions by yjlee at 2017-09-12

#import imp
import os
import sys
import getopt
import pexpect
import subprocess
import traceback
import ConfigParser
import getpass
import signal
import time


CONFIG_FILE = os.getenv('SIMPLEFABRIC_CHECKNET_CFG', 'checknet_config.ini')
conf = None


class ConfReader:
    conf_map = dict()
    base = dict()
    host = dict()

    def __init__(self, conf_file):
        if not os.access(conf_file, os.R_OK):
            print "cannot open config file for read: %s" % (conf_file)
            sys.exit(1)
      
        self.config = ConfigParser.ConfigParser()
        self.config.read(conf_file)
        for section in self.config.sections():
            self.conf_map[section] = {key: value for key, value in self.config.items(section)}
        try:
            self.base['ping_fail_sec'] = int(self.conf_map['BASE']['ping_fail_sec'])
            self.base['ping_late_msec'] = int(self.conf_map['BASE']['ping_late_msec'])
            self.base['ssh_timeout_sec'] = int(self.conf_map['BASE']['ssh_timeout_sec'])
            self.base['default_id'] = str(self.conf_map['BASE']['default_id'])
            self.base['default_passwd'] = str(self.conf_map['BASE']['default_passwd'])
        except KeyError as KE:
            exc_type, exc_value, exc_traceback = sys.exc_info()
            lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
            print ''.join('   || ' + line for line in lines)
            print "BASE section parse failed: %s" % (conf_file)
            sys.exit(1)

        # host entry: dict with key ip, id, passwd, no_ssh (boolean)
        try:
            for name in self.conf_map['HOST'].keys():
                values = self.conf_map['HOST'][name].split(':')
                h = dict()
                h['name'] = name
                h['ip'] = values[0]
                h['ssh_ip'] = h['ip']
                h['id_passwd_configured'] = False
                h['id'] = self.base['default_id']
                h['passwd'] = self.base['default_passwd']
                h['no_ssh'] = False
                values = values[1:]
                while len(values) >= 1:
                    if values[0][0] == '@':
                        h['ssh_ip'] = values[0][1:]
                    elif values[0] == 'no_ssh':
                        h['no_ssh'] = True
                    elif len(values) >= 2 and values[1] != 'no_ssh':
                        h['id_passwd_configured'] = True
                        h['id'] = values[0]
                        h['passwd'] = values[1]
                        values = values[1:]
                    else:
                        print 'invalid value format for HOST %s: %s' % (name, values[0])
                        sys.exit(1)
                    values = values[1:]
                self.host[name] = h
        except KeyError as KE:
            exc_type, exc_value, exc_traceback = sys.exc_info()
            lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
            print ''.join('   || ' + line for line in lines)
            print "HOST section parse failed: %s" % (conf_file)
            sys.exit(1)

    def set_default_passwd(self, passwd):
        self.base['default_passwd'] = passwd
        for name in self.host.keys():
            if not self.host[name]['id_passwd_configured']:
                self.host[name]['passwd'] = passwd


def local_ping_check_for_ssh(host):
    try:
        result = subprocess.Popen('ping -c1 -W%d -n %s' % (conf.base['ping_fail_sec'], host['ssh_ip']),
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
        output, error = result.communicate()
        if result.returncode == 0:
            return True
        else:
            #print '[%s(%s)] local ping check failed(%d) ' \
            #      % (host['name'], host['ssh_ip'], result.returncode)
            return False
    except:
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        print ''.join('   || ' + line for line in lines)
        print '[%s(%s)] local ping check exception' % (host['name'], host['ssh_ip'])
        return False


def ssh_ping_check_all(host):
    try:
        ping_fail_sec = int(conf.base['ping_fail_sec'])
        ping_late_msec = float(conf.base['ping_late_msec'])

        target_list = ""
        for target_name in sorted(conf.host.keys()):
            target_list += " " + target_name + "=" + conf.host[target_name]['ip']

        #cmd = 'echo PING; for n in %s; do name=`echo $n | cut -d= -f1`; ip=`echo $n | cut -d= -f2`; echo -n " $name:"; ping -c1 -w%d -n $ip | awk "/time=/{ if (substr(\\$7,6) < %f) { printf \\"OK  \\" } else { printf \\"LATE\\" } exit} ENDFILE { printf \\"FAIL\\" }"; done' \

        cmd = 'echo PING; for n in %s; do name=`echo $n | cut -d= -f1`; ip=`echo $n | cut -d= -f2`; echo -n " $name:"; ping -c1 -w%d -n $ip | awk "/time=/{ t = substr(\\$7,6); if (t < %f) { printf \\"OK  \\" } else { printf \\"%%-4s\\", t } exit} ENDFILE { printf \\"FAIL\\" }"; done' \
               % (target_list, ping_fail_sec, ping_late_msec)

        ssh_cmd = "ssh -oStrictHostKeyChecking=no %s@%s '%s'" % (host['id'], host['ssh_ip'], cmd)

        sys.stdout.write("@%s:" % host['name'])

        # ping check result per peer host:
        #      OK   (<  conf.base[ping_laste_msec])
        #      LATE (>= conf.base['ping_last_msec'])
        #      FAIL (>= conf.base['ping_fail_sec'])

        ssh_conn = pexpect.spawn(ssh_cmd)
        expect_timeout = conf.base['ssh_timeout_sec']
        ping_happen = False
        rt = -1
        while rt <= 1:
            rt = ssh_conn.expect(['password:', 'PING', pexpect.EOF, pexpect.TIMEOUT],
                                 timeout=expect_timeout)
            if rt == 0:
                ssh_conn.sendline(host['passwd'])
            elif rt == 1:
                ping_happen = True
                expect_timeout = 1000  # wait ping checks
            elif rt == 2:
                if ping_happen:
                    ssh_conn.wait()
                    for line in ssh_conn.before.splitlines()[1:]:
                        if line != '':
                            print "%s" % line
                else:
                    print ' FAIL for ssh failed (might be incorrect password)'
                break
            elif rt == 3:
                print ' FAIL for ssh timeout'
                break
           
    except:
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        print "FAIL for ssh execption"
        print ''.join('   || ' + line for line in lines)


def ssh_ping_check(host, target):
    try:
        ping_timeout = conf.base['ping_timeout']
        #if sys.platform == 'darwin':
        #    timeout = timeout * 1000

        cmd = 'ping -c1 -W%d -n %s' % (ping_timeout, target['ip'])
        ssh_cmd = 'ssh -oStrictHostKeyChecking=no %s@%s %s' % (host['id'], host['ssh_ip'], cmd)

        #print 'SSH_EXEC_EXPECT: %s' % (ssh_cmd)
        ssh_conn = pexpect.spawn(ssh_cmd)
        ping_happen = False
        while True:
            rt = ssh_conn.expect(['password:', 'PING', pexpect.EOF, pexpect.TIMEOUT],
                                 timeout=conf.base['ssh_timeout_sec'])
            if rt == 0:
                ssh_conn.sendline(host['passwd'])
            elif rt == 1:
                ping_happen = True
            elif rt == 2:
                if ping_happen:
                    ssh_conn.wait()
                    if ssh_conn.status == 0:
                       result = ssh_conn.before.splitlines()[1]
                       return True, result[result.rfind('time=')+5:]
                    else:
                       return False, 'timeout'
                else:
                    return False, 'ssh failed'
            elif rt == 3:
                #ssh_print(host['name'], ssh_conn.before.splitlines())
                return False, 'ssh timeout'
    except:
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        print ''.join('   || ' + line for line in lines)
        print '[%s->%s] ssh ping check exception' % (host['name'], target['name'])
        return False, "ssh execption" 


# ssh key copy to account of target system
def key_copy(host):
    ssh_conn = pexpect.spawn('ssh-copy-id -oStrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub %s@%s' % (host['id'], host['ip']))

    while True:
        rt = ssh_conn.expect(['password:', pexpect.EOF], timeout=SSH_TIMEOUT)
        if rt == 0:
            ssh_conn.sendline(password)
        elif rt == 1:
            ssh_print(host['name'], ssh_conn.before.splitlines())
            break
        elif rt == 2:
            print "[Error] I either got key or connection timeout: %s" % host['name']

    ssh_print(host['name'], ssh_conn.before.splitlines())
    print '\n'


def ssh_print(node, lines):
    for line in lines:
        if line != '':
            print "[%s] %s" % (node, line)


def sig_handler(signum, frame):
    print 'execution stopped by signal'
    os._exit(1)


def do_check():
    for host_name in sorted(conf.host.keys()):
        host = conf.host[host_name]
        if host['no_ssh']:
            continue
        elif local_ping_check_for_ssh(host):
            ssh_ping_check_all(host)
        else:
            print '@%s: FAIL for ping to ssh_ip %s failed' % (host['name'], host['ip'])

def main(prog_name, argv):
    global CONFIG_FILE
    global conf

    # change to script directory for relative CONFIG_FILE path
    os.chdir(os.path.dirname(os.path.realpath(sys.argv[0])))

    loop_count_max = 0
    loop_interval_sec = 10

    help_msg = 'Usage: %s [-l <loop_count_max(default:%d,infinit:0)>] [-i <loop_interval_sec(default:%d)>] [-c <checknet_cfg>]' \
               % (prog_name, loop_count_max, loop_interval_sec)

    try:
        opts, args = getopt.getopt(argv, 'hl:i:c:')
    except getopt.GetoptError:
        print help_msg
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-l':
            loop_count_max = int(arg)
        elif opt == '-i':
            loop_interval_sec = int(arg)
        elif opt == '-c':
            CONFIG_FILE = arg
        else:
            if opt != '-h':
                print 'unknown option: %s' % opt
            print help_msg
            sys.exit(2)

    conf = ConfReader(CONFIG_FILE)    
    try:
        if conf.base['default_passwd'] == '':
            conf.set_default_passwd(getpass.getpass('Password for %s@<hosts> ?: ' \
                                                    % conf.base['default_id']))
    except:
        print ""  # close prompt line
        sys.exit(3)       

    # NOTE: sig handler should be registerd after password input
    #       for on interrupt getpass should return back from noecho/raw input mode
    signal.signal(signal.SIGTERM, sig_handler)
    signal.signal(signal.SIGINT, sig_handler)

    loop_count_max_str = ''
    loop_interval_str = ''
    if loop_count_max != 1:
        if loop_count_max == 0:
            loop_count_max_str = ' count_max=unlimited'
        else:
            loop_count_max_str = ' count_max=%d' % loop_count_max
        loop_interval_str = ' interval=%dsecs' % loop_interval_sec

    loop_count = 0
    while loop_count_max == 0 or loop_count < loop_count_max:
        loop_count += 1
        print '[CHECK NET COUNT %d] begin=%s' \
              % (loop_count, time.strftime('%Y-%m-%d_%H:%M:%S'))
        begin_time = time.time()
        do_check()
        elapsed_time = time.time() - begin_time
        print '[CHECK NET COUNT %d] elapsed=%.3fsecs%s%s' \
              % (loop_count, elapsed_time, loop_count_max_str, loop_interval_str)
        if loop_count_max == 0 or loop_count < loop_count_max:
            print ''
            if elapsed_time < loop_interval_sec:
                time.sleep(loop_interval_sec - elapsed_time)

 
if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
    pass


