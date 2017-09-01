import json
import base64
import os
import multiprocessing as multiprocess
from datetime import datetime
from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer

from log_lib import LOG
from cli import CLI
from config import CONFIG

class RestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        global global_evt
        global global_conn_evt
        global global_history_log

        try:
            request_sz = int(self.headers["Content-length"])
            request_str = self.rfile.read(request_sz)
            body = json.loads(request_str)

            LOG.debug_log('[REST-SERVER] CLIENT INFO = ' + str(self.client_address))
            LOG.debug_log('[REST-SERVER] RECV HEADER = \n' + str(self.headers))
            LOG.debug_log('[REST-SERVER] RECV BODY = \n' + json.dumps(body, sort_keys=True, indent=4))

            if self.path.startswith('/test'):
                t_id = body['transaction_id']

                if os.path.exists('log/flowtrace_' + t_id):
                    os.remove('log/flowtrace_' + t_id)
                result_file = open('log/flowtrace_' + t_id, 'w')
                result_file.write(str(body))
                result_file.close()
            elif self.path.startswith('/traffictest'):
                t_id = body['transaction_id']

                if os.path.exists('log/traffictest_' + t_id):
                    os.remove('log/traffictest_' + t_id)
                result_file = open('log/traffictest_' + t_id, 'w')
                result_file.write(str(body))
                result_file.close()

            elif self.headers.getheader('Authorization') is None:
                LOG.debug_log('[REST-SERVER] no auth header received')

            elif not self.path.startswith('/event'):
                LOG.debug_log('[REST-SERVER] ' + self.path + ' not found')

            elif self.auth_pw(self.headers.getheader('Authorization')):
                reason_str = ''
                if type(body['reason']) == list:
                    if len(body['reason']) > 0:
                        reason_str = '\n-- ' + '\n-- '.join(body['reason']); 
                else:
                    reason_str = str(body['reason'])
                global_history_log.write_history('[%s] %s %s changed from %s to %s %s',
                    body['time'], body['system'], body['item'], body['pre_grade'], body['grade'], reason_str)

                if body['system'] == 'SlsNetWatchd' and body['item'] == 'Daemon':
                    global_conn_evt.set()
                    LOG.debug_log('[REST-SERVER] ' + reason_str);
                else:
                    global_evt.set()
            else:
                LOG.debug_log('[REST-SERVER] not authenticated')
        except:
            LOG.exception_err_write()

    def auth_pw(self, cli_pw):
        if cli_pw == base64.b64encode(CLI.get_auth()):
            return True

        return False


global_evt = None
global_conn_evt = None
global_history_log = None

def run(evt, conn_evt, rest_evt, history_log):
    global global_history_log
    global global_conn_evt
    global global_evt

    global_history_log = history_log
    global_evt = evt
    global_conn_evt = conn_evt

    LOG.debug_log("--- REST Server Start --- ")
    global_history_log.write_history("[%s] --- Event History Start ---", str(datetime.now()))

    try:
        server_address = ("", CONFIG.get_rest_port())
        httpd = HTTPServer(server_address, RestHandler)
        httpd.serve_forever()

    except:
        LOG.exception_err_write()
        # occure rest server err event
        rest_evt.set()

def rest_server_start(evt, conn_evt, rest_evt, history_log):
    rest_server_daemon = multiprocess.Process(name='cli_rest_svr', target=run, args=(evt, conn_evt, rest_evt, history_log))
    rest_server_daemon.daemon = True
    rest_server_daemon.start()

    return rest_server_daemon.pid



