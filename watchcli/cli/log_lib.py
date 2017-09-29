import os
import sys
import logging
import logging.handlers
import traceback
from config import CONFIG

DEFAULT_LOG_PATH = os.path.dirname(os.path.realpath(sys.argv[0])) + "/log/"

class LOG():
    cli_log_flag = False

    LOG = logging.getLogger(__name__)

    @classmethod
    def set_default_log(cls, file_name):
        if not os.path.exists(DEFAULT_LOG_PATH):
            os.makedirs(DEFAULT_LOG_PATH)

        log_formatter = logging.Formatter('[%(asctime)s] %(message)s')

        # set file name
        file_name = DEFAULT_LOG_PATH + file_name

        # use cli rotate/backup config
        file_handler = logging.handlers.TimedRotatingFileHandler(file_name,
                                                                 when=CONFIG.get_cli_log_rotate(),
                                                                 backupCount=int(CONFIG.get_cli_log_backup()))

        file_handler.setFormatter(log_formatter)

        cls.LOG.addHandler(file_handler)
        cls.LOG.setLevel(logging.DEBUG)

    @classmethod
    def set_log_config(cls):
        if (CONFIG.get_cli_log().upper()) == 'ON':
            cls.cli_log_flag = True

    @classmethod
    def exception_err_write(cls):
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        cls.LOG.debug("%s", ''.join('   || ' + line for line in lines))

    @classmethod
    def debug_log(cls, log):
        cls.LOG.debug(log)


class USER_LOG():
    LOG = None

    def set_log(self, file_name, rotate, backup, time_prefix=True):
        self.LOG = logging.getLogger(file_name)

        if not os.path.exists(DEFAULT_LOG_PATH):
            os.makedirs(DEFAULT_LOG_PATH)

        file_name = DEFAULT_LOG_PATH + file_name
        file_handler = logging.handlers.TimedRotatingFileHandler(file_name,
                                                                 when=rotate,
                                                                 backupCount=backup)
        if time_prefix:
            file_handler.setFormatter(logging.Formatter('[%(asctime)s] %(message)s'))
        else:
            file_handler.setFormatter(logging.Formatter('%(message)s'))

        self.LOG.addHandler(file_handler)
        self.LOG.setLevel(logging.DEBUG)

    def cli_log(self, log):
        try:
            if LOG.cli_log_flag:
                self.LOG.debug(log)
        except:
            LOG.exception_err_write()

    def write_history(self, log, *args):
        try:
            self.LOG.debug(log % args)
        except:
            LOG.exception_err_write()

