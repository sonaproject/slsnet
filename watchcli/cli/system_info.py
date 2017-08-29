import json

from log_lib import LOG

class SYS():
    # key = system name
    # value = info_dic
    sys_list = {}
    last_check_time = ''

    sys_thr_flag = True
    sys_redraw_flag = False

    pre_sys_info = ''

    disconnect_type = 'default'
    abnormal_flag = False

    @classmethod
    def set_sys_info(cls, sys_info):
        try:
            sys_info = json.loads(sys_info)
            sys_info = sys_info['result']

            cls.pre_sys_info = sys_info

            for key in sys_info:
                dtl_list = {}

                for item in (dict)(sys_info[key]).keys():
                    dtl_list[item] = sys_info[key][item]

                cls.sys_list[key] = dtl_list

        except:
            LOG.exception_err_write()

    @classmethod
    def get_sys_list(cls):
        return cls.sys_list.keys()

    @classmethod
    def get_sys_line_count(cls):
        # calc line count
        # 2 lines per ONOS node for detailed description
        line_count = 0;
        for key in cls.sys_list.keys():
            if (cls.sys_list[key]['TYPE'] == 'ONOS'):
                line_count += 2
            else:
                line_count += 1
        return line_count

    @classmethod
    def changed_sys_info(cls, new_info):
        # if changed : return true
        try:
            new_sys_info = json.loads(new_info)
            cls.last_check_time = new_sys_info['time']

            if cls.pre_sys_info == new_sys_info['result']:
                return False
            else:
                cls.set_sys_info(new_info)
                return True
        except:
            LOG.exception_err_write()
            return True

    @classmethod
    def get_sys_thr_flag(cls):
        return cls.sys_thr_flag

    @classmethod
    def set_sys_thr_flag(cls, ret):
        cls.sys_thr_flag = ret

    @classmethod
    def get_sys_redraw_flag(cls):
        return cls.sys_redraw_flag

    @classmethod
    def set_sys_redraw_flag(cls, ret):
        cls.sys_redraw_flag = ret

