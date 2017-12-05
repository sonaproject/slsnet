#!/usr/local/bin/python
# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

import ConfigParser
import imp
import subprocess
import os
import sys
import getopt
import getpass

CONFIG_FILE = os.getenv('SIMPLEFABRIC_WATCHD_CFG', 'config.ini')
REQUIREMENT_PKG = 'pexpect'
SSH_TIMEOUT = 3
HOME_DIR = os.getenv('HOME')
SSH_DIR = HOME_DIR + '/.ssh/'
ID_RAS_FILE = SSH_DIR + 'id_rsa.pub'

ONOS_INSTALL_DIR = '/opt/onos'


# check 'pexpect' module
def installed_package(package):
    try:
        imp.find_module(package)
        return True
    except ImportError:
        return False


# install 'pexpect' module
def install_package(package):
    try:
        import pip
        return pip.main(['install', package])
    except ImportError:
        print "Must install \"%s\" Package" % package
        exit(1)

# import 'pexpect' module
if installed_package(REQUIREMENT_PKG):
    import pexpect
else:
    # if install_package(REQUIREMENT_PKG) == 0:
    #     import pexpect
    # else:
    print "Must install \"%s\" Package" % REQUIREMENT_PKG
    print "should excute command: \"sudo pip install pexpect\""
    exit(1)


# read ssh setup config file
class CONF:
    def __init__(self):
        self.conf = ConfigParser.ConfigParser()
        self.conf.read(CONFIG_FILE)

    def get_setup_system(self, section=None):
        if section is not None:
            return {key: value for key, value in self.conf.items(section)}['check_system']
        else:
            return None

    def get_system_info(self, system=None):
        if system is not None:
            return {key: value for key, value in self.conf.items(system)}
        else:
            return None

    def get_auto_passwd_flag(self):
        item = {key: value for key, value in self.conf.items('SSH_KEY_SETUP')}

        try:
            return item['auto_password']
        except:
            return 'yes'


# create ssh public key
def ssh_keygen():
    print "[Setup] No ssh id_rsa and id_rsa.pub file"
    print "[Setup] Make ssh id_rsa and id_rsa.pub file"

    subprocess.call('ssh-keygen -t rsa -f ~/.ssh/id_rsa -P \'\' -q', shell=True)


# ssh key copy to account of target system
def key_copy(node, conf):
    username, password = conf['account'].split(":")
    cmd = 'ssh-copy-id -oStrictHostKeyChecking=no -i %s/.ssh/id_rsa.pub %s@%s' \
          % (HOME_DIR, username, node)
    ssh_conn = pexpect.spawn(cmd)

    print "[Setup] Set passwordless system login on %s@%s from local user" % (username, node)

    while True:
        rt = ssh_conn.expect(['password:', pexpect.EOF, pexpect.TIMEOUT], timeout=SSH_TIMEOUT)
        if rt == 0:
            if str(CONF().get_auto_passwd_flag()).lower() == 'no':
                password = str(getpass.getpass('\nPassword for %s@%s ?: ' % (username, node)))
            ssh_conn.sendline(password)
        elif rt == 1:
            ssh_print(node, ssh_conn.before.splitlines())
            break
        elif rt == 2:
            print "[Error] I either got key or connection timeout"

    if username != 'root':
        print '[Setup] check if remote user is of sudoer: %s' % username

        nopw_cmd = 'ssh -oStrictHostKeyChecking=no %s@%s ' \
                   'sudo grep %s /etc/sudoers | grep -v \'\#\''  \
                   % (username, node, username)
        ssh_conn = pexpect.spawn(nopw_cmd)

        if ssh_conn.expect([pexpect.EOF], timeout=SSH_TIMEOUT) == 0:
            if 'nopasswd' in str(ssh_conn.before.splitlines()).lower():
                print '[Check Succ] sudoer set correctly... '
                return
            else:
                print '[Check Fail] Please Set sudoer config for %s\n'\
                      '    Insert \"%s ALL=(ALL) NOPASSWD:ALL\" in /etc/sudoers' \
                      % (username, username)
        else:
            ssh_print(node, ssh_conn.before.splitlines())


# ssh key copy to ONOS instance
def key_copy_2onos(node, conf):
    print "[Setup] Prune the node entry from the known hosts file on %s" % node
    prune_ssh_key_cmd = 'ssh-keygen -f "%s/.ssh/known_hosts" -R %s:8101' % (HOME_DIR, node)
    subprocess.call(prune_ssh_key_cmd, shell=True)

    id_pub_file = file(ID_RAS_FILE, 'r')
    ssh_key = id_pub_file.read().split(" ")[1]
    id_pub_file.close()
    username, password = conf['account'].split(":")

    print "[Setup] Set passwordless ONOS login on %s@%s from local user" % (username, node)
    if ssh_key == '':
        print "[Setup] Read id_ras.pub file Fail"
        exit(1)

    set_ssh_key_cmd = 'ssh %s@%s %s/bin/onos-user-key %s %s' % \
                      (username, node, ONOS_INSTALL_DIR, os.getenv('USER'), ssh_key)
    print set_ssh_key_cmd
    subprocess.call(set_ssh_key_cmd, shell=True)


def ssh_print(node, lines):
    for line in lines:
        if line != '':
            print "%s" % line


def onos():
    conf = CONF().get_system_info('ONOS')
    for node in str(conf['list']).replace(" ", "").split(","):
        print "[Setup] Checking %s" % node
        key_copy(node.split(":")[1], conf)
        key_copy_2onos(node.split(":")[1], conf)
        print ""


def main(argv):
    global CONFIG_FILE

    # change to script directory for relative CONFIG_FILE path
    os.chdir(os.path.dirname(os.path.realpath(sys.argv[0])))
 
    try:
        opts, args = getopt.getopt(argv, "h:c:",["config="])
    except getopt.GetoptError:
        print "Usage: ./ssh_key_setup.py -c <config file>"
        sys.exit(2)

    for opt, arg in opts:
        if opt in ('-c', "--config"):
            CONFIG_FILE = arg
        else:
            if opt != '-h':
                print "unknown option: %s" % opt
            print "Usage: ./ssh_key_setup.py -c <config file>"
            sys.exit(2)

    print ""
    print "[Setup] Checking local user ssh key files"
    if not set(['id_rsa','id_rsa.pub']).issubset(os.listdir(SSH_DIR)):
        print "Generate ssh \'id_rsa\' and \'id_rsa.pub\' key files"
        ssh_keygen()
    else:
        print "Ssh Key files \'id_rsa\' and \'id_rsa.pub\' already exist"
    print ""

    for node in str(CONF().get_setup_system('WATCHDOG')).replace(" ", "").split(","):
        if node.__eq__('ONOS'):
            onos()


if __name__ == "__main__":
    main(sys.argv[1:])
    pass


