#!/usr/local/bin/python
# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# FROM: sonawatcher; SONA Monitoring Solutions.


import os
import sys
import time
import socket
import requests
from subprocess import Popen, PIPE
from datetime import datetime
from signal import SIGTERM

import monitor.alarm_event as alarm_event
import monitor.watchdog as watchdog
import api.rest_server as REST_SVR
from api.config import CONF
from api.sona_log import LOG
from api.sona_log import USER_LOG
from api.watcherdb import DB
from api.daemon import Daemon


class SimpleFabricWatchD(Daemon):
    def exit(self):
        try:
            pf = file(CONF.get_pid_file(), 'r')
            pid = int(pf.read().strip())
            pf.close()

            LOG.info("--- Daemon STOP [fail to check rest server] ---")

            try:
                LOG.info('PID = ' + str(pid))
                os.killpg(pid, SIGTERM)
            except OSError, err:
                err = str(err)
                if err.find("No such process") > 0:
                    if os.path.exists(self.pidfile):
                        os.remove(self.pidfile)
        except:
            LOG.exception()

    def run(self):
        # be quiet on db_log
        db_log = USER_LOG()
        #b_log.set_log('db.log', CONF.base()['log_rotate_time'], CONF.base()['log_backup_count'])

        pre_stat = dict()

        # DB initiation
        DB_CONN = DB().connection()
        DB.db_initiation(db_log)

        # Start RESTful server
        try:
            REST_SVR.rest_server_start()
        except:
            print 'Rest Server failed to start'
            LOG.exception()
            self.exit()

        # Periodic monitoring
        if CONF.watchdog()['interval'] == 0:
            LOG.info("--- Not running periodic monitoring ---")
            while True:
                time.sleep(3600)
        else:
            LOG.info("--- Periodic Monitoring Start ---")
            history_log.write_log("--- Event History Start ---")

            conn = DB.connection()

            alarm_event.push_event('SimpleFabricWatchd', 'PROC', 'up', 'none', [], str(datetime.now()), False)

            exitFlag = False
            while True:
                try:
                    i = 0
                    while i < 3:
                        i = i + 1
                        # check rest server
                        try:
                            url = 'http://' + socket.gethostbyname(socket.gethostname()) + ':' \
                                  + str(CONF.rest()['rest_server_port']) + '/alive-check'
                            cmd = 'curl -X GET \"' + url + '\"'
                            #LOG.info('cmd = ' + cmd)
                            result = Popen(cmd, stdout=PIPE, stderr=PIPE, shell=True)
                            output, error = result.communicate()
                            if result.returncode != 0:
                                LOG.info('REST SERVER CHECK FAIL [' + str(i) + ']')
                                if i == 3:
                                    LOG.info('fail to check rest server.')
                                    alarm_event.push_event('SimpleFabricwatchd', 'PROC', 'down', 'up', [], str(datetime.now()), True)
                                    conn.close()
                                    exitFlag = True
                                    self.exit()
                                    break
                            else:
                                break

                        except:
                            LOG.exception()

                    if exitFlag:
                        break

                    # do monitoring
                    pre_stat = watchdog.periodic(conn, pre_stat, db_log)

                    time.sleep(CONF.watchdog()['interval'])
                except:
                    alarm_event.push_event('SimpleFabricWatchd', 'PROC', 'down', 'normal', [], str(datetime.now()), False)
                    conn.close()
                    LOG.exception()


if __name__ == "__main__":

    # change to script directory for relative CONFIG_FILE path
    os.chdir(os.path.dirname(os.path.realpath(sys.argv[0])))
     
    CONF.init()
    LOG.init(CONF.base()['log_file_name'])
    history_log = USER_LOG()
    history_log.set_log('event_history.log', CONF.base()['log_rotate_time'], CONF.base()['log_backup_count'])
    alarm_event.set_history_log(history_log)

    daemon = SimpleFabricWatchD(CONF.get_pid_file())

    if len(sys.argv) == 2:

        if 'start' == sys.argv[1]:
            daemon.start()

        elif 'stop' == sys.argv[1]:
            print "Stopping ..."
            alarm_event.push_event('SimpleFabricWatchd', 'PROC', 'down', 'up', [], str(datetime.now()), True)
            daemon.stop()

        elif 'restart' == sys.argv[1]:
            print "Restaring ..."
            alarm_event.push_event('SimpleFabricWatchd', 'PROC', 'restart', 'up', [], str(datetime.now()), False)
            daemon.restart()

        elif 'status' == sys.argv[1]:
            try:
                pf = file(CONF.get_pid_file(), 'r')
                pid = int(pf.read().strip())
                pf.close()
            except IOError:
                pid = None
            except SystemExit:
                pid = None

            if pid:
                print 'SimpleFabricWatchd is running as pid %s' % pid
            else:
                print 'SimpleFabricWatchd is not running.'

        else:
            print "Unknown command"
            sys.exit(2)

    else:
        print "usage: %s start|stop|restart|status" % sys.argv[0]
        sys.exit(2)
