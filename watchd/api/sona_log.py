# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

# Ref) generate example messages
#     logger.debug('debug message')
#     logger.info('informational message')
#     logger.warn('warning')
#     logger.error('error message')
#     logger.critical('critical failure')

import sys
import os
import logging
import traceback
from logging.handlers import TimedRotatingFileHandler
from config import CONF

DEFAULT_LOG_PATH = os.getcwd() + "/log/"
DEFAULT_LOGGER_NAME = 'sona_logger'

class _Log:
    logger = logging.getLogger(DEFAULT_LOGGER_NAME)

    def __init__(self, file_name):
        if not os.path.exists(DEFAULT_LOG_PATH):
            os.makedirs(DEFAULT_LOG_PATH)
        log_file_name = DEFAULT_LOG_PATH + file_name

        # Ref) formatter = logging.Formatter('%(asctime)s %(name)s %(levelname)s %(message)s')
        formatter = logging.Formatter('%(asctime)s.%(msecs)03d %(message)s',
                                      datefmt='%H:%M:%S')
        handler = TimedRotatingFileHandler(log_file_name,
                                           when=CONF.base()['log_rotate_time'],
                                           backupCount=CONF.base()['log_backup_count'])
        handler.setFormatter(formatter)
        self.logger.addHandler(handler)
        self.logger.setLevel(logging.DEBUG)

    @classmethod
    def info(cls, message, *args):
        message = '[m:' + traceback.extract_stack(None, 2)[0][2] + '] ' + message
        cls.logger.info(message % args)

    @classmethod
    def error(cls, message, *args):
        message = '[m:' + traceback.extract_stack(None, 2)[0][2] + '] ' + message
        cls.logger.error(message % args)

    @classmethod
    def exception(cls):
        exc_type, exc_value, exc_traceback = sys.exc_info()
        lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
        method = '[m:' + traceback.extract_stack(None, 2)[0][2] + ']'
        cls.error("Exception Error %s\n%s", method, ''.join('   | ' + line for line in lines))

LOG = _Log(CONF.base()['log_file_name'])

class USER_LOG():
    LOG = None

    def set_log(self, file_name, rotate, backup):
        self.LOG = logging.getLogger(file_name)

        if not os.path.exists(DEFAULT_LOG_PATH):
            os.makedirs(DEFAULT_LOG_PATH)

        log_formatter = logging.Formatter('[%(asctime)s] %(message)s')

        file_name = DEFAULT_LOG_PATH + file_name

        file_handler = logging.handlers.TimedRotatingFileHandler(file_name,
                                                                 when=rotate,
                                                                 backupCount=backup)

        file_handler.setFormatter(log_formatter)

        self.LOG.addHandler(file_handler)
        self.LOG.setLevel(logging.DEBUG)

    def write_log(self, log, *args):
        try:
            self.LOG.debug(log % args)
        except:
            LOG.exception()