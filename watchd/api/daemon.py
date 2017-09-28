# Copyright (c) 2017 by Telcoware
# All Rights Reserved.
# SONA Monitoring Solutions.

import sys
import os
import time
import atexit
import subprocess
from signal import SIGTERM
from sona_log import LOG


class Daemon(object):

    def __init__(self, pidfile, stdin='/dev/null', stdout='/dev/null', stderr='/dev/null'):
        self.stdin = stdin
        self.stdout = stdout
        self.stderr = stderr
        self.pidfile = pidfile

    def daemonize(self):
        try:
            pid = os.fork()

            if pid > 0:
                # exit first parent
                sys.exit(0)
        except OSError, e:
            sys.stderr.write("fork #1 failed: %d (%s)\n" % (e.errno, e.strerror))
            sys.exit(1)

        # decouple from parent environment
        os.chdir("/")
        os.setsid()
        os.umask(0)

        # do second fork
        try:
            pid = os.fork()
            if pid > 0:
                # exit from second parent
                sys.exit(0)
        except OSError, e:
            sys.stderr.write("fork #2 failed: %d (%s)\n" % (e.errno, e.strerror))
            sys.exit(1)

        # redirect standard file descriptors
        si = file(self.stdin, 'r')
        so = file(self.stdout, 'a+')
        se = file(self.stderr, 'a+', 0)

        pid = str(os.getpid())

        LOG.info("--- Daemon START ---")
        sys.stderr.write("Started with pid %s\n" % pid)
        sys.stderr.flush()

        if self.pidfile:
            file(self.pidfile,'w+').write("%s\n" % pid)

        atexit.register(self.delpid)
        os.dup2(si.fileno(), sys.stdin.fileno())
        os.dup2(so.fileno(), sys.stdout.fileno())
        os.dup2(se.fileno(), sys.stderr.fileno())

    # delete pid file when parent process kill
    def delpid(self):
        try:
            os.remove(self.pidfile)
        except OSError:
            pass

    # start processes
    def start(self):
        # check process before start
        try:
            pf = file(self.pidfile,'r')
            pid = int(pf.read().strip())
            pf.close()
        except IOError:
            pid = None
        except SystemExit:
            pid = None

        if pid:
            message = "pidfile %s already exist. Check Daemon ...\n"
            sys.stderr.write(message % self.pidfile)
            sys.exit(1)

        # Start the daemon
        self.daemonize()
        self.run()

    # get parents process ID from pid file
    def get_pid(self):
        try:
            pf = file(self.pidfile, 'r')
            pid = int(pf.read().strip())
            pf.close()
        except IOError:
            pid = None
        except SystemExit:
            pid = None
        return pid

    # stop all daemon porcess
    def stop(self):
        try:
            pid = self.get_pid()
        except IOError:
            pid = None

        if not pid:
            message = "pidfile %s does not exist. Daemon not running?\n"
            sys.stderr.write(message % self.pidfile)
            return # not an error in a restart

        # Try killing the daemon process
        try:
            LOG.info("--- Daemon STOP ---")
            while 1:
                for cpid in self.get_child_pid(pid):
                    os.kill(cpid,SIGTERM)
                os.kill(pid, SIGTERM)
                time.sleep(0.1)
        except OSError, err:
            err = str(err)
            if err.find("No such process") > 0:
                if os.path.exists(self.pidfile):
                    os.remove(self.pidfile)
            else:
                print str(err)
                print "Stopping Fail ..."
                sys.exit(1)

    # get id's child process
    def get_child_pid(self, ppid):
        ps_command = subprocess.Popen("pgrep -P %d" % ppid, shell=True, stdout=subprocess.PIPE)
        return [int(p) for p in ps_command.stdout.read().split("\n") if p]

    def restart(self):
        self.stop()
        self.start()

    def run(self):
        """
        You should override this method when you subclass Daemon.
        """

