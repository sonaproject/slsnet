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


PIDFILE = CONF.get_pid_file()

class SlsNetWatchD(Daemon):
    def exit(self):
        try:
            pf = file(PIDFILE, 'r')
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
        db_log = USER_LOG()
        db_log.set_log('db.log', CONF.base()['log_rotate_time'], CONF.base()['log_backup_count'])

        pre_stat = dict()

        # DB initiation
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
                                    alarm_event.push_event('slsnetwatcher', 'WATCHER_DISCONNECT', 'critical',
                                                           'normal', 'slsnetwatcher server shutdown',
                                                           str(datetime.now()))
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
                    alarm_event.push_event('slsnetwatcher', 'WATCHER_DISCONNECT', 'critical', 'normal', 'slsnetwatcher server shutdown', str(datetime.now()))
                    conn.close()
                    LOG.exception()


if __name__ == "__main__":
    history_log = USER_LOG()
    history_log.set_log('event_history.log', CONF.base()['log_rotate_time'], CONF.base()['log_backup_count'])
    alarm_event.set_history_log(history_log)

    daemon = SlsNetWatchD(PIDFILE)

    if len(sys.argv) == 2:

        if 'start' == sys.argv[1]:
            try:
                daemon.start()
            except:
                pass

        elif 'stop' == sys.argv[1]:
            print "Stopping ..."
            try:
                alarm_event.push_event('slsnetwatcher', 'WATCHER_DISCONNECT', 'critical', 'normal', 'slsnetwatcher server shutdown', str(datetime.now()))
            except:
                pass
            daemon.stop()

        elif 'restart' == sys.argv[1]:
            print "Restaring ..."
            try:
                alarm_event.push_event('slsnetwatcher', 'WATCHER_DISCONNECT', 'critical', 'normal', 'slsnetwatcher server shutdown', str(datetime.now()))
            except:
                pass
            daemon.restart()

        elif 'status' == sys.argv[1]:
            try:
                pf = file(PIDFILE,'r')
                pid = int(pf.read().strip())
                pf.close()
            except IOError:
                pid = None
            except SystemExit:
                pid = None

            if pid:
                print 'YourDaemon is running as pid %s' % pid
            else:
                print 'YourDaemon is not running.'

        else:
            print "Unknown command"
            sys.exit(2)

    else:
        print "usage: %s start|stop|restart|status" % sys.argv[0]
        sys.exit(2)
