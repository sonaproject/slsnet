#!/usr/local/bin/python

import smtplib
from email.mime.text import MIMEText

from config import CONF
from sona_log import LOG


def send_alarm(subject, body):
    conf = CONF.alarm()

    if conf['mail_alarm']:
        mail_from = conf['mail_user'] + '@' + conf['mail_server'].split(':')[0]
        mail_to = ','.join(conf['mail_list'])

        msg = MIMEText(body)
        msg['Subject'] = subject
        msg['From'] = mail_from
        msg['To'] = mail_to

        LOG.info('Send Email Alert: subject=%s to=%s body=%s', subject, mail_to, body)

        ms = smtplib.SMTP('mail.tstream.co.kr')
        if conf['mail_tls']:
            ms.starttls()
        ms.login('slsnetmailer','_slsnetmailer')
        ms.sendmail(mail_from, mail_to, msg.as_string())
        ms.quit()

