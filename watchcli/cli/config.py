import ConfigParser

COMMAND_SECTION_NAME = 'command'
TRACE_SECTION_NAME = 'condition'
REST_SECTION_NAME = 'rest-server'
LOG_SECTION_NAME = 'log'
SSH_SECTION_NAME = 'ssh'
CPT_SECTION_NAME = "OPENSTACK_NODE"

REST_ID_KEY_NAME = 'id'
REST_PW_KEY_NAME = 'pw'
REST_URI_KEY_NAME = 'rest-server'
REST_COMMAND_URI = 'command-uri'
REST_EVENT_REGI_URI = 'event-regi-uri'
REST_EVENT_UNREGI_URI = 'event-unregi-uri'
REST_EVENT_LIST_URI = 'event-list-uri'
REST_TIMEOUT = 'timeout'
REST_SVR_IP = 'client_rest_server_ip'
REST_SVR_PORT = 'client_rest_server_port'

COMMAND_OPT_KEY_NAME = 'option-list'

CLI_LOG_KEY_NAME = 'cli_log'
LOG_ROTATE_KEY_NAME = 'log_rotate_time'
LOG_BACKUP_KEY_NAME = 'log_backup_count'
TRACE_LOG_KEY_NAME = 'trace_log'

CPT_LIST_KEY_NAME = 'list'
CPT_ID_KEY_NAME = 'account'
TRACE_BASE_BRIDGE = 'base_bridge'

CLI_CONFIG_FILE = 'config/cli_config.ini'
TRACE_CONFIG_FILE = 'config/trace_config.ini'

SSH_TIMEOUT = 'timeout'

class CONFIG():

    LOG = None
    config_cli = ConfigParser.RawConfigParser()
    config_trace = ConfigParser.RawConfigParser()

    @classmethod
    def init_config(cls, LOG):
        cls.LOG = LOG
        try:
            # read config
            cls.config_cli.read(CLI_CONFIG_FILE)
            cls.config_trace.read(TRACE_CONFIG_FILE)

            return True
        except:
            return False

    @classmethod
    def get_cmd_list(cls):
        try:
            return cls.config_cli.options(COMMAND_SECTION_NAME)
        except:
            cls.LOG.exception_err_write()
            return []

    @classmethod
    def get_cmd_help(cls, cmd):
        try:
            return cls.config_cli.get(COMMAND_SECTION_NAME, cmd)
        except:
            cls.LOG.exception_err_write()
            return ''

    #@classmethod
    #def get_cnd_list(cls):
    #    try:
    #        return cls.config_trace.items(TRACE_SECTION_NAME)
    #    except:
    #        cls.LOG.exception_err_write()
    #        return []

    @classmethod
    def cli_get_value(cls, section_name, key):
        try:
            return cls.config_cli.get(section_name, key)
        except:
            cls.LOG.exception_err_write()
            return ''

    #@classmethod
    #def trace_get_value(cls, section_name, key):
    #    try:
    #        return cls.config_trace.get(section_name, key)
    #    except:
    #        cls.LOG.exception_err_write()
    #        return ''

    @classmethod
    def get_config_instance(cls):
        return cls.config_cli

    @staticmethod
    def get_cmd_opt_key_name():
        return COMMAND_OPT_KEY_NAME

    @classmethod
    def get_rest_id(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_ID_KEY_NAME)

    @classmethod
    def get_rest_pw(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_PW_KEY_NAME)

    @classmethod
    def get_server_addr(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_URI_KEY_NAME)

    @classmethod
    def get_cmd_addr(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_URI_KEY_NAME) + \
               cls.cli_get_value(REST_SECTION_NAME, REST_COMMAND_URI)

    @classmethod
    def get_regi_uri(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_URI_KEY_NAME) + \
               cls.cli_get_value(REST_SECTION_NAME, REST_EVENT_REGI_URI)

    @classmethod
    def get_unregi_uri(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_URI_KEY_NAME) + \
               cls.cli_get_value(REST_SECTION_NAME, REST_EVENT_UNREGI_URI)

    @classmethod
    def get_event_list_uri(cls):
        return cls.cli_get_value(REST_SECTION_NAME, REST_URI_KEY_NAME) + \
               cls.cli_get_value(REST_SECTION_NAME, REST_EVENT_LIST_URI)

    @classmethod
    def get_rest_port(cls):
        try:
            return cls.config_cli.getint(REST_SECTION_NAME, REST_SVR_PORT)
        except:
            return -1

    @classmethod
    def get_rest_ip(cls):
        try:
            return cls.config_cli.get(REST_SECTION_NAME, REST_SVR_IP)
        except:
            return -1

    @classmethod
    def get_rest_timeout(cls):
        try:
            return cls.config_cli.getint(REST_SECTION_NAME, REST_TIMEOUT)
        except:
            return 10

    @classmethod
    def get_cli_log(cls):
        return cls.cli_get_value(LOG_SECTION_NAME, CLI_LOG_KEY_NAME)

    @classmethod
    def get_cli_log_rotate(cls):
        return cls.cli_get_value(LOG_SECTION_NAME, LOG_ROTATE_KEY_NAME)

    @classmethod
    def get_cli_log_backup(cls):
        return cls.cli_get_value(LOG_SECTION_NAME, LOG_BACKUP_KEY_NAME)

    @classmethod
    def get_trace_log(cls):
        return cls.trace_get_value(LOG_SECTION_NAME, TRACE_LOG_KEY_NAME)

    @classmethod
    def get_trace_log_rotate(cls):
        return cls.trace_get_value(LOG_SECTION_NAME, LOG_ROTATE_KEY_NAME)

    @classmethod
    def get_trace_log_backup(cls):
        return cls.trace_get_value(LOG_SECTION_NAME, LOG_BACKUP_KEY_NAME)

    @classmethod
    def get_ssh_timeout(cls):
        try:
            return cls.config_trace.getint(SSH_SECTION_NAME, SSH_TIMEOUT)
        except:
            return 5

    @classmethod
    def get_trace_cpt_list(cls):
        return cls.trace_get_value(CPT_SECTION_NAME, CPT_LIST_KEY_NAME)

    @classmethod
    def get_trace_cpt_id(cls):
        return cls.trace_get_value(CPT_SECTION_NAME, CPT_ID_KEY_NAME).split(':')[0]

    @classmethod
    def get_trace_base_bridge(cls):
        return cls.trace_get_value(CPT_SECTION_NAME, TRACE_BASE_BRIDGE)


