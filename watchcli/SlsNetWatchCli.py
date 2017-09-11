#!/usr/bin/python
import os
import sys
import time
import threading
import readline
import curses
import multiprocessing
import signal

import cli.cli_rest as cli_rest
from cli.log_lib import LOG
from cli.config import CONFIG
from cli.system_info import SYS
from cli.cli import CLI
from cli.log_lib import USER_LOG
from cli.screen import SCREEN

menu_list = ["Monitoring Details", "Event History", "CLI", "Exit"]

evt_thread = None
conn_evt_thread = None
SLEEP_TIME = 0.5

def main():
    global evt_thread
    global conn_evt_thread

    try:
        from PIL import Image
    except:
        print "This program requires the pillow package."
        print 'Please run \'sudo pip install pillow\' and try again.'
        return

    try:
        from asciimatics.scene import Scene
    except:
        print "This program requires the asciimatics package."
        print 'Please run \'sudo pip install asciimatics\' and try again.'
        return

    # add for readline bug fix
    os.unsetenv('LINES')
    os.unsetenv('COLUMNS')

    # read config
    if not CONFIG.init_config(LOG):
        print 'read config fail...'
        return

    # set log
    LOG.set_default_log('watchcli_err.log')

    # set history log
    history_log = USER_LOG()
    history_log.set_log('event_history.log', CONFIG.get_cli_log_rotate(), int(CONFIG.get_cli_log_backup()), time_prefix=False)

    # set cli log
    cli_log = USER_LOG()
    cli_log.set_log('watchcli_cli.log', CONFIG.get_cli_log_rotate(), int(CONFIG.get_cli_log_backup()))
    CLI.set_cli_log(cli_log, history_log)

    # read log option
    LOG.set_log_config()

    # Start RESTful server for event
    evt = multiprocessing.Event()
    disconnect_evt = multiprocessing.Event()
    rest_evt = multiprocessing.Event()

    # create listen event thread
    evt_thread = threading.Thread(target=listen_evt, args=(evt,))
    evt_thread.daemon = False
    evt_thread.start()

    conn_evt_thread = threading.Thread(target=listen_disconnect_evt, args=(disconnect_evt, rest_evt))
    conn_evt_thread.daemon = False
    conn_evt_thread.start()

    try:
        # create rest server process
        child_pid = cli_rest.rest_server_start(evt, disconnect_evt, rest_evt, history_log)
    except:
        print 'Rest Server failed to start'
        #print 'Processing shutdown...'
        LOG.exception_err_write()
        SYS.set_sys_thr_flag(False)
        conn_evt_thread.join()
        evt_thread.join()
        return

    # regi event
    if CLI.send_regi() == False:
        print 'Event registration failed'
        #print 'Processing shutdown...'
        LOG.exception_err_write()
        SYS.set_sys_thr_flag(False)
        conn_evt_thread.join()
        evt_thread.join()
        return

    # get event list
    if CLI.get_event_list() == False:
        print 'Get Event List failed'
        #print 'Processing shutdown...'
        LOG.exception_err_write()
        SYS.set_sys_thr_flag(False)
        conn_evt_thread.join()
        evt_thread.join()
        return

    # inquiry controller info
    try:
        res_code, sys_info = CLI.req_sys_info()
    except:
        print "Cannot connect rest server."
        #print 'Processing shutdown...'
        SYS.set_sys_thr_flag(False)
        conn_evt_thread.join()
        evt_thread.join()
        return

    if res_code != 200:
        print "Rest server does not respond to the request."
        print "code = " + str(res_code)
        #print 'Processing shutdown...'
        if not SYS.disconnect_type == 'disconnect':
            CLI.send_regi('unregi')
        SYS.set_sys_thr_flag(False)
        conn_evt_thread.join()
        evt_thread.join()
        return

    SYS.set_sys_info(sys_info)

    # set command list
    CLI.set_cmd_list()

    # set search list
    CLI.set_search_list()

    set_readline_opt()

    # system check
    check_system()

    # select input menu
    select_menu()

    # exit
    #print 'Processing shutdown...'
    if not SYS.disconnect_type == 'disconnect':
        CLI.send_regi('unregi')

    SYS.set_sys_thr_flag(False)
    conn_evt_thread.join()
    evt_thread.join()

    return

def select_menu():
    selected_menu_no = 1

    try:
        SCREEN.set_screen()

        SCREEN.draw_system(menu_list)
        SCREEN.draw_event(SYS.disconnect_type)
        SCREEN.draw_menu(menu_list, selected_menu_no)

        SYS.set_sys_redraw_flag(True)

        x = SCREEN.get_ch()

        # 27 = ESC
        while x != 27:
            if x == curses.KEY_DOWN:
                if selected_menu_no != len(menu_list):
                    selected_menu_no += 1

            elif x == curses.KEY_UP:
                if selected_menu_no != 1:
                    selected_menu_no -= 1

            elif x == ord("\n"):
                # stop timer
                SYS.set_sys_redraw_flag(False)

                SCREEN.refresh_screen()
                SCREEN.screen_exit()

                menu = menu_list[selected_menu_no - 1]
                empty_cmd = 0

                if menu in ['Monitoring Details', 'Event History', 'CLI']:
                    if menu == 'CLI':
                        #SCREEN.display_header(menu_list[selected_menu_no - 1])
                        #SCREEN.display_sys(True)
                        SCREEN.display_help()
                        
                    elif menu == 'Monitoring Details':
                        SCREEN.display_status()
                        CLI.process_cmd("onos-conn cluster")
                        CLI.process_cmd("onos-conn device")
                        CLI.process_cmd("onos-conn link")
                        CLI.process_cmd("onos-svc app")
                        CLI.process_cmd("onos-svc rest")

                    elif menu == 'Event History':
                        SCREEN.display_event()

                    readline.set_completer(CLI.pre_complete_cli)

                    while True:
                        # mac OS
                        if 'libedit' in readline.__doc__:
                            CLI.modify_flag = True
                            CLI.save_buffer = readline.get_line_buffer()

                        # select_command (handling tab event)
                        cmd = CLI.input_cmd()
                        cmd = cmd.strip()

                        # on two consecutive empty cmd input, go back to menu
                        if len(cmd) == 0:
                            empty_cmd += 1
                            if (empty_cmd >= 2):
                                break
                        else:
                            empty_cmd = 0

                        if CLI.is_menu(cmd):
                            break
                        elif CLI.is_exit(cmd):
                            return
                        elif cmd == 'help':
                            SCREEN.display_help()
                        elif cmd == 'monitoring-details':
                            SCREEN.display_status()
                        elif cmd.startswith('event-history'):
                            if len(cmd.split(' ')) == 2:
                                try:
                                    num = int(cmd.split(' ')[1])
                                    SCREEN.display_event(num)
                                except:
                                    print 'log count param is wrong. ex) event-history 15'
                            else:
                                SCREEN.display_event()
                        else:
                            # send command
                            CLI.process_cmd(cmd)

                            while not CLI.get_cli_ret_flag():
                                time.sleep(SLEEP_TIME)

                elif menu == 'Flow Trace':
                    from asciimatics.screen import Screen
                    from asciimatics.exceptions import ResizeScreenError

                    # clear screen
                    SCREEN.get_screen().clear()
                    SCREEN.screen_exit()

                    last_scene = None

                    while True:
                        try:
                            Screen.wrapper(SCREEN.start_screen, catch_interrupt=True, arguments=[last_scene])

                            if SCREEN.quit_flag:
                                return
                            elif SCREEN.menu_flag:
                                SCREEN.set_screen()
                                SCREEN.main_scr.timeout(-1)
                                break
                            elif SCREEN.resize_err_flag:
                                SCREEN.screen_exit()
                                SCREEN.menu_flag = True
                                return

                        except ResizeScreenError as e:
                            last_scene = e.scene

                elif menu == 'Exit':
                    break

            SCREEN.draw_system(menu_list)
            SCREEN.draw_event(SYS.disconnect_type)
            SCREEN.draw_menu(menu_list, selected_menu_no)
            SCREEN.refresh_screen()

            SYS.set_sys_redraw_flag(True)

            x = SCREEN.get_ch()

        SCREEN.screen_exit()
    except:
        LOG.exception_err_write()


def listen_disconnect_evt(evt, rest_evt):
    global evt_thread

    while SYS.get_sys_thr_flag():
        try:
            evt.wait(1)

            if evt.is_set():
                LOG.debug_log('Get disconnect event')
                evt.clear()
                SYS.disconnect_type = 'disconnect'

                if SYS.get_sys_redraw_flag():
                    SCREEN.draw_event(SYS.disconnect_type)
                    SCREEN.get_screen().clear()
                    SCREEN.screen_exit()

                print '\nCheck monitoring server.'
                os.killpg(os.getpid(), signal.SIGKILL)
                #os.kill(os.getpid(), signal.SIGKILL)

            time.sleep(SLEEP_TIME)
            rest_evt.wait(SLEEP_TIME)

            if rest_evt.is_set():
                LOG.debug_log('Get rest error event')
                rest_evt.clear()
                SYS.disconnect_type = 'rest_warn'

                if SYS.get_sys_redraw_flag():
                    SCREEN.draw_event(SYS.disconnect_type)
                    SCREEN.get_screen().clear()
                    SCREEN.screen_exit()

                print '\nCheck client rest server.'
                os.killpg(os.getpid(), signal.SIGKILL)
                #os.kill(os.getpid(), signal.SIGKILL)

            time.sleep(SLEEP_TIME)
        except:
            LOG.exception_err_write()


def listen_evt(evt):
    while SYS.get_sys_thr_flag():
        try:
            evt.wait(1)

            if evt.is_set():
                evt.clear()
                # system check
                check_system()

            time.sleep(SLEEP_TIME)
        except:
            LOG.exception_err_write()

def check_system():
    try:
        # inquiry onos info
        res_code, sys_info = CLI.req_sys_info()

        if res_code != 200:
            SYS.disconnect_type = 'disconnect'
            SCREEN.draw_event(SYS.disconnect_type)
            LOG.debug_log(
                '[SYSTEM_CHECK_THREAD] Rest server does not respond to the request. RES_CODE = ' + str(res_code))
            return

        ret = SYS.changed_sys_info(sys_info)

        if SYS.get_sys_redraw_flag():
            if ret is True:
                SCREEN.draw_system(menu_list)
                SCREEN.draw_event(SYS.disconnect_type)
            else:
                SCREEN.draw_refresh_time(menu_list)
    except:
        LOG.exception_err_write()

def set_readline_opt():
    try:
        delims = readline.get_completer_delims().replace("-", "^")
        readline.set_completer_delims(delims)

        # mac OS
        if 'libedit' in readline.__doc__:
            readline.parse_and_bind("bind -e")
            readline.parse_and_bind("bind '\t' rl_complete")
        else:
            readline.parse_and_bind("tab:complete")

        readline.parse_and_bind('set editing-mode vi')
    except:
        LOG.exception_err_write()

def complete_dummy(text, state):
    pass

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        LOG.exception_err_write()
