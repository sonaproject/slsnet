#!/usr/local/bin/python

import smtplib
from email.mime.text import MIMEText
from slackclient import SlackClient

from config import CONF
from sona_log import LOG


# pending alarm info
alarm_count = 0
alarm_subject = ''
alarm_body = ''


def queue_alarm(subject, reason, time):
    global alarm_count, alarm_subject, alarm_body

    alarm_count += 1

    if alarm_count == 1:
       alarm_subject = subject;  # use first subject only

    alarm_body += '[' + time[0:19] + '] ' + subject + '\n'
    if len(reason) > 0:
        alarm_body += reason + '\n'


def flush_pending_alarm():
    global alarm_count, alarm_subject, alarm_body

    if alarm_count <= 0:
        return;  # no alarm pending

    conf = CONF.alarm()

    # copy to local variables and clear global variables
    count = alarm_count
    subject = '[%s] %s' % (conf['site_name'], alarm_subject)
    if (count > 1):
        subject += ' (+ %d events)' % (count - 1)
    body = alarm_body
    alarm_count = 0
    alarm_subject = ''
    alarm_body = ''

    if conf['mail_alarm']:
        mail_from = conf['mail_user'] + '@' + conf['mail_server'].split(':')[0]

        # send to each mail_list entry for gmail smtp seems not handling mutiple To: addresses
        for mail_to in conf['mail_list']:
            msg = MIMEText(body)
            msg['Subject'] = subject
            msg['From'] = mail_from
            msg['To'] = mail_to

            LOG.info('Send Email Alarm: subject=%s to=%s body=%s', subject, mail_to, body)
            try:
                ms = smtplib.SMTP(conf['mail_server'])
                if conf['mail_tls']:
                    ms.starttls()
                ms.login(conf['mail_user'], conf['mail_password'])
                ms.sendmail(mail_from, mail_to, msg.as_string())
                ms.quit()
            except:
                LOG.exception()

    if conf['slack_alarm']:
        ch = conf['slack_channel'].strip()
        if ch[0] != '#':
            ch = '#' + ch

        LOG.info('Send Slack Alarm: channel=%s text=%s', ch, body)
        sc = SlackClient(conf['slack_token'])
        try:
            sc.api_call("chat.postMessage", channel=ch, text=body)
        except:
            LOG.exception()


