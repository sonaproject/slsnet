# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

import os
import sys
import ConfigParser

DEFAULT_CONF_FILE = os.getenv('SLSNET_WATCHD_CFG', 'config.ini')

class ConfReader:
    conf_map = dict()


    def init(self):
        if not os.access(DEFAULT_CONF_FILE, os.R_OK):
            print("cannot open config file for read: %s" % (DEFAULT_CONF_FILE));
            sys.exit(1)
      
        self.config = ConfigParser.ConfigParser()
        self.config.read(DEFAULT_CONF_FILE)
        self.__load_config_map()

    def __load_config_map(self):
        for section in self.config.sections():
            self.conf_map[section] = {key: value for key, value in self.config.items(section)}

    @staticmethod
    def __list_opt(value):
        return list(str(value).replace(" ", "").split(','))

    def base(self):
        value = dict()
        try:
            value['pidfile'] = str(self.conf_map['BASE']['pidfile'])
            value['log_file_name'] = str(self.conf_map['BASE']['log_file_name'])
            value['log_rotate_time'] = str(self.conf_map['BASE']['log_rotate_time'])
            value['log_backup_count'] = int(self.conf_map['BASE']['log_backup_count'])
            value['db_file'] = str(self.conf_map['BASE']['db_file'])

            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def watchdog(self):
        value = dict()
        try:
            value['interval'] = int(self.conf_map['WATCHDOG']['interval'])
            value['check_system'] = self.__list_opt(self.conf_map['WATCHDOG']['check_system'])
            value['method'] = str(self.conf_map['WATCHDOG']['method'])
            value['timeout'] = int(self.conf_map['WATCHDOG']['timeout'])
            value['retry'] = int(self.conf_map['WATCHDOG']['retry'])

            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def ssh_conn(self):
        value = dict()
        try:
            value['ssh_req_timeout'] = int(self.conf_map['SSH_CONN']['ssh_req_timeout'])

            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def rest(self):
        value = dict()
        try:
            value['rest_server_port'] = int(self.conf_map['REST']['rest_server_port'])
            value['user_password'] = self.__list_opt(self.conf_map['REST']['user_password'])

            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def onos(self):
        value = dict()
        try:
            value['list'] = self.__list_opt(self.conf_map['ONOS']['list'])
            value['account'] = str(self.conf_map['ONOS']['account'])
            value['app_list'] = self.__list_opt(self.conf_map['ONOS']['app_list'])
            value['rest_list'] = self.__list_opt(self.conf_map['ONOS']['rest_list'])
            value['device_list'] = self.__list_opt(self.conf_map['ONOS']['device_list'])
            value['link_list'] = self.__list_opt(self.conf_map['ONOS']['link_list'])

            if self.config.has_option('ONOS', 'alarm_off_list'):
                value['alarm_off_list'] = self.__list_opt(self.conf_map['ONOS']['alarm_off_list'])

            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def alarm(self):
        value = dict()
        try:
            value['site_name'] = self.conf_map['ALARM']['site_name']
            value['mail_alarm'] = self.conf_map['ALARM']['mail_alarm'] in ['true','yes']
            value['mail_server'] = self.conf_map['ALARM']['mail_server']
            value['mail_tls'] = self.conf_map['ALARM']['mail_tls'] in ['true','yes']
            value['mail_user'] = self.conf_map['ALARM']['mail_user']
            value['mail_password'] = self.conf_map['ALARM']['mail_password']
            value['mail_list'] = self.__list_opt(self.conf_map['ALARM']['mail_list'])
            value['slack_alarm'] = self.conf_map['ALARM']['slack_alarm'] in ['true','yes']
            value['slack_token'] = self.conf_map['ALARM']['slack_token']
            value['slack_channel'] = self.conf_map['ALARM']['slack_channel']
            return value
        except KeyError as KE:
            return dict({'fail': KE})

    def get_pid_file(self):
        try:
            return str(self.conf_map['BASE']['pidfile'])
        except KeyError as KE:
            return dict({'fail': KE})

    def site(self):
        # dummy section for SITE event entry
        value = dict()
        try:
            return value
        except KeyError as KE:
            return dict({'fail': KE})

CONF = ConfReader()

