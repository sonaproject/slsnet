import curses
import platform
from subprocess import Popen, PIPE

from log_lib import LOG
from system_info import SYS
from config import CONFIG
from cli import CLI

from asciimatics.widgets import Frame, ListBox, Layout, Divider, Text, \
    Button, Widget, TextBox, PopUpDialog
from asciimatics.scene import Scene
from asciimatics.screen import Screen
from asciimatics.exceptions import NextScene, StopApplication
from asciimatics.event import KeyboardEvent
from collections import defaultdict

WHITE = '\033[1;97m'
BLUE = '\033[1;94m'
YELLOW = '\033[1;93m'
GREEN = '\033[1;92m'
RED = '\033[1;91m'
BLACK = '\033[1;90m'
BG_WHITE = '\033[0;97m'
BG_BLUEW = '\033[0;37;44m'
BG_SKYW = '\033[0;37;46m'
BG_PINKW = '\033[0;37;45m'
BG_YELLOWW = '\033[0;30;43m'
BG_GREENW = '\033[0;37;42m'
BG_RED = '\033[0;91m'
BG_BLACK = '\033[0;90m'
ENDC = '\033[0m'
BOLD = '\033[1m'
UNDERLINE = '\033[4m'
OFF = '\033[0m'

MAIN_WIDTH = 80
MAIN_CENTER = (MAIN_WIDTH / 2 - 2)

ONOS_STATUS_ITEMS = ['PING', 'ONOS_CLUSTER', 'ONOS_DEVICE', 'ONOS_LINK', 'ONOS_APP']


class SCREEN():
    main_scr = None

    menu_flag = False
    cli_flag = False
    quit_flag = False
    restart_flag = False
    resize_err_flag = False

    main_instance = None

    @classmethod
    def set_screen(cls):
        cls.main_scr = curses.initscr()

        curses.noecho()
        curses.cbreak()
        curses.start_color()
        cls.main_scr.keypad(1)
        curses.curs_set(0)
        cls.main_scr.refresh()

        curses.init_pair(1, curses.COLOR_BLACK, curses.COLOR_CYAN)
        curses.init_pair(2, curses.COLOR_GREEN, curses.COLOR_BLACK)
        curses.init_pair(3, curses.COLOR_MAGENTA, curses.COLOR_BLACK)

    @classmethod
    def set_main_str(cls, x, y, str, color):
        try:
            cls.main_scr.addstr(x, y, str, color)
            cls.refresh_screen()
        except:
            LOG.exception_err_write()

    @classmethod
    def screen_exit(cls):
        try:
            curses.endwin()
        except:
            LOG.exception_err_write()

    @classmethod
    def refresh_screen(cls):
        try:
            cls.main_scr.refresh()
        except:
            LOG.exception_err_write()

    @classmethod
    def get_screen(cls):
        return cls.main_scr

    @classmethod
    def get_ch(cls):
        try:
            return cls.main_scr.getch()
        except:
            LOG.exception_err_write()
            return ''

    @classmethod
    def draw_system(cls, menu_list):
        try:
            box_system = cls.display_sys_info()

            cls.draw_refresh_time(menu_list)

            box_system.refresh()
        except:
            LOG.exception_err_write()

    @classmethod
    def draw_refresh_time(cls, menu_list):
        try:
            time_color = curses.color_pair(3)

            str_time = 'Last Check Time [' + SYS.last_check_time.split('.')[0] + ']'
            cls.set_main_str(SYS.get_sys_line_count() + 2 + 3 + len(menu_list) + 2 + 1, MAIN_WIDTH - len(str_time),
                             str_time, time_color)
        except:
            LOG.exception_err_write()

    @classmethod
    def draw_menu(cls, menu_list, selected_menu_no):
        try:
            box_menu = cls.draw_select(menu_list, selected_menu_no)
            box_menu.refresh()
        except:
            LOG.exception_err_write()

    @staticmethod
    def draw_select(menu_list, selected_menu_no):
        box_type = curses.newwin(len(menu_list) + 2, MAIN_WIDTH, SYS.get_sys_line_count() + 3 + 3, 1)
        box_type.box()

        try:
            highlightText = curses.color_pair(1)
            normalText = curses.A_NORMAL

            box_type.addstr(0, MAIN_CENTER - 3, ' MENU ', normalText)

            for i in range(1, len(menu_list) + 1):
                if i is selected_menu_no:
                    box_type.addstr(i, 2, str(i) + "." + menu_list[i - 1], highlightText)
                else:
                    box_type.addstr(i, 2, str(i) + "." + menu_list[i - 1], normalText)
        except:
            LOG.exception_err_write()

        return box_type

    @classmethod
    def draw_event(cls, type='default'):
        try:
            warn_color = curses.color_pair(3)

            box_event = curses.newwin(3, MAIN_WIDTH, SYS.get_sys_line_count() + 3, 1)
            box_event.box()

            normalText = curses.A_NORMAL

            box_event.addstr(0, MAIN_CENTER - 3, ' EVENT ', normalText)

            if type == 'disconnect':
                box_event.addstr(1, 2, '[Server shutdown] check server and restart', warn_color)
            elif type == 'rest_warn':
                box_event.addstr(1, 2, '[Rest failure] check client and restart', warn_color)
            else:
                # if occur event
                if SYS.abnormal_flag:
                    str = '[Event occurred]'

                    box_event.addstr(1, 2, str, warn_color)
                    box_event.addstr(1, 2 + len(str), ' Check the event history.', normalText)
                else:
                    str = '[Event] normal'

                    box_event.addstr(1, 2, str, normalText)

            box_event.refresh()
        except:
            LOG.exception_err_write()

    @classmethod
    def display_header(cls, menu):
        try:
            width = 60
            print BG_WHITE + "+%s+" % ('-' * width).ljust(width) + ENDC
            print BG_WHITE + '|' + BG_BLUEW + BOLD + \
                  ("{0:^" + str(width) + "}").format(menu) + BG_WHITE + '|' + ENDC
            print BG_WHITE + "+%s+" % ('-' * width).ljust(width) + ENDC
        except:
            LOG.exception_err_write()

    @classmethod
    def display_status(cls):
        onos_list = ['TYPE', 'IP'] + ONOS_STATUS_ITEMS
        try:
            print ''
            cls.draw_grid('ONOS', onos_list)
        except:
            LOG.exception_err_write()

    @classmethod
    def display_event(cls, cnt=20):
        try:
            cmd = 'tail -n ' + str(cnt) + ' log/event_history.log'
            result = Popen(cmd, stdout=PIPE, stderr=PIPE, shell=True)
            output, error = result.communicate()

            if result.returncode != 0:
                LOG.debug_log("Cmd Fail, cause => %s", error)
                print 'Failed to load file'
            else:
                print '\n * Only the last ' + str(cnt) + ' logs are printed.'
                print ' * Please refer to the log file for details. (path = log/event_history.log)\n'
                print output
        except:
            LOG.exception_err_write()

    @staticmethod
    def draw_grid(sys_type, list):
        print '[' + sys_type + ']'
        sorted_list = sorted(SYS.sys_list.keys())

        data = []
        for sys in sorted_list:
            if not (dict)(SYS.sys_list[sys])['TYPE'] == sys_type:
                continue

            line = []
            line.append(sys)

            status = 'OK'
            for item in list:
                if item in ['TYPE']:
                    continue

                value = SYS.sys_list[sys][item]

                if (dict)(SYS.sys_list[sys]).has_key(item):
                    line.append(value)

                    if item in ['IP']:
                        continue

                    if value == 'none':
                        status = 'loading'
                    elif not (value == 'ok' or value == 'normal' or value == '-'):
                        status = 'NOK'
                else:
                    line.append('-')

            line.insert(1, status)
            data.append(line)

        header = []

        col = dict()
        col['title'] = 'SYSTEM'
        col['size'] = '6'

        header.append(col)

        col_status = dict()
        col_status['title'] = 'STATUS'
        col_status['size'] = '6'

        header.append(col_status)

        for item in list:
            if item in ['TYPE']:
                continue

            if str(item).startswith(sys_type):
                item = item[len(sys_type) + 1:]

            col = dict()
            col['title'] = item

            if item == 'IP':
                size = 16
            else:
                size = len(item)
                if size < 8:
                    size = 8

            col['size'] = str(size)

            header.append(col)

        CLI.draw_grid(header, data)

        print ''

    @classmethod
    def display_sys(cls, header=False):
        try:
            width = 60

            if not header:
                print "+%s+" % ('-' * width).ljust(width) + ENDC

            print '| SYSTEM INFO | TIME : ' + SYS.last_check_time.split('.')[0] + \
                  ("{0:>" + str(
                      width - len(SYS.last_check_time.split('.')[0]) - len('SYSTEM INFO | TIME : ')) + "}").format(
                      '|') + ENDC
            print "+%s+" % ('-' * width).ljust(width) + ENDC

            sorted_list = sorted(SYS.sys_list.keys())

            for sys in sorted_list:
                str_status = 'OK'

                status_list = (dict)(SYS.sys_list[sys]).keys()
                for key in status_list:
                    if str(key).upper() == 'TYPE' or str(key).upper() == 'IP':
                        continue

                    value = (dict)(SYS.sys_list[sys])[key]
                    if (value == 'none'):
                        str_status = 'loading'
                        break
                    elif not (value == 'ok' or value == 'normal' or value == '-'):
                        str_status = 'NOK'
                        break

                color = GREEN
                if str_status is not 'OK':
                    color = RED
                print '| ' + sys.ljust(6) + ' [' + color + str_status + OFF + ']' + \
                      ("{0:>" + str(width - 6 - len(str_status) - 3) + "}").format('|') + ENDC

            print "+%s+" % ('-' * width).ljust(width) + ENDC

            warn = ' * Not real time information'.rjust(width)
            print warn
        except:
            LOG.exception_err_write()

    @classmethod
    def display_help(cls):
        try:
            print ''
            for cmd in CLI.command_list:
                print '\t' + cmd.ljust(15) + '  ' + CONFIG.get_cmd_help(cmd)

                if (CONFIG.get_config_instance().has_section(cmd)):
                    opt_list = CONFIG.cli_get_value(cmd, CONFIG.get_cmd_opt_key_name())

                    print '\t' + ' '.ljust(15) + '  - option : ' + opt_list.strip()
            print ''
        except:
            LOG.exception_err_write()

    @classmethod
    def start_screen(cls, screen, scene):
        if (screen.width < 100 or screen.height < 25):
            cls.resize_err_flag = True
            return

        scenes = [
            Scene([FlowTraceView(screen)], -1, name="Flow Trace")
        ]

        screen.play(scenes, stop_on_resize=True, start_scene=scene)

    @classmethod
    def set_exit(cls):
        cls.quit_flag = True

    @classmethod
    def display_sys_info(cls):
        box_sys = curses.newwin(SYS.get_sys_line_count() + 2, MAIN_WIDTH, 1, 1)
        box_sys.box()

        try:
            status_text_OK = curses.color_pair(2)
            status_text_NOK = curses.color_pair(3)
            normal_text = curses.A_NORMAL

            box_sys.addstr(0, MAIN_CENTER - 10, ' MONITORING STATUS ', normal_text)

            i = 1

            sorted_list = sorted(SYS.sys_list.keys())

            for sys in sorted_list:
                # show node name 
                col = 2
                str_info = sys.ljust(6)
                box_sys.addstr(i, col, str_info)
                col += len(str_info) + 1

                # show status
                box_sys.addstr(i, col, "[")
                col += 1;
                str_status = 'OK'
                status_list = (dict)(SYS.sys_list[sys]).keys()
                for key in status_list:
                    if str(key).upper() == 'TYPE' or str(key).upper() == 'IP':
                        continue
                    value = (dict)(SYS.sys_list[sys])[key]
                    if (value == 'none'):
                        str_status = 'LOADING'
                        break
                    elif not (value == 'ok' or value == 'normal' or value == '-'):
                        str_status = 'NOK'
                        SYS.abnormal_flag = True
                        break
                if str_status is 'OK':
                    box_sys.addstr(i, col, str_status, status_text_OK)
                else:
                    box_sys.addstr(i, col, str_status, status_text_NOK)
                box_sys.addstr(i, col + len(str_status), ']')
                col += 8;

                # show base info
                #type = 'TYPE=' + SYS.sys_list[sys]['TYPE']
                #box_sys.addstr(i, col, type)
                #col += len(type) + 1
                #ip = '(IP=' + SYS.sys_list[sys]['IP'] + ')'
                #box_sys.addstr(i, col, ip)
                #col += len(ip) + 1
                #i += 1
                #col = 9;

                # show monitoring details
                if (SYS.sys_list[sys]["TYPE"] == 'ONOS'):
                    box_sys.addstr(i, col, '(')
                    col += 1;
                    for key in ONOS_STATUS_ITEMS:
                        value = (dict)(SYS.sys_list[sys])[key]
                        if str(key).startswith('ONOS_'):
                            key = key[len('ONOS_'):]
                        box_sys.addstr(i, col, key + '=');
                        col += len(key + '=');

                        str_status = 'ok'
                        if not (value == 'ok' or value == 'normal' or value == '-'):
                            str_status = 'nok'
                        if str_status is 'ok':
                            box_sys.addstr(i, col, str_status, status_text_OK)
                        else:
                            box_sys.addstr(i, col, str_status, status_text_NOK)
                        col += len(str_status) + 1;
                    box_sys.addstr(i, col - 1, ')')

                i += 1

        except:
            LOG.exception_err_write()

        return box_sys


    isKeypad = False

    def process_event(self, event):
        if isinstance(event, KeyboardEvent):
            c = event.key_code

            # mac OS
            if platform.system() == 'Darwin':
                if c == 127:
                    event.key_code = Screen.KEY_BACK
            else:
                if c == 8:
                    event.key_code = Screen.KEY_BACK
                # for keypad
                elif c == -1:
                    return
                elif c == 79:
                    self.isKeypad = True
                    return
                elif (self.isKeypad and c >= 112 and c <= 121):
                    event.key_code = c - 64
                    self.isKeypad = False
                elif c == -102:
                    event.key_code = 46

        return super(FlowTraceView, self).process_event(event)

    def key_name(self, key):
        try:
            default_width = 12

            key = '* ' + key

            if len(key) < default_width:
                for i in range(default_width - len(key)):
                    key = key + ' '

            key = key + ' '
        except:
            LOG.exception_err_write()

        return key

    @staticmethod
    def _quit():
        SCREEN.set_exit()
        raise StopApplication("User pressed quit")

