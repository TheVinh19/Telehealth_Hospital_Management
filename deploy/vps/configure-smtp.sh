#!/usr/bin/env bash
# Configure external SMTP on the VPS without copying the development .env.
set -euo pipefail
cd -- "$(dirname -- "$0")"
command -v python3 >/dev/null || { echo 'python3 is required (normally preinstalled on Ubuntu).'; exit 1; }
command -v docker >/dev/null || { echo 'Docker Compose is required.'; exit 1; }
umask 077
python3 - "$@" <<'PYTHON'
import argparse
import datetime
import getpass
import json
import os
from pathlib import Path
import re
import smtplib
import ssl
import subprocess
import sys
import tempfile
import time


KEYS = (
    'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME',
    'SPRING_MAIL_PASSWORD', 'MAIL_FROM', 'SMTP_AUTH', 'SMTP_STARTTLS',
    'SMTP_SSL', 'DEMO_NOTIFICATION_EMAIL',
)
KEY_RE = re.compile(r'^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=')
COMPOSE_KEYS = {
    'SPRING_MAIL_HOST': 'SPRING_MAIL_HOST',
    'SPRING_MAIL_PORT': 'SPRING_MAIL_PORT',
    'SPRING_MAIL_USERNAME': 'SPRING_MAIL_USERNAME',
    'SPRING_MAIL_PASSWORD': 'SPRING_MAIL_PASSWORD',
    'MAIL_FROM': 'MAIL_FROM',
    'SMTP_AUTH': 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH',
    'SMTP_STARTTLS': 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE',
    'SMTP_SSL': 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE',
    'DEMO_NOTIFICATION_EMAIL': 'DEMO_NOTIFICATION_EMAIL',
}


class SetupError(Exception):
    pass


def clean_value(value):
    if not isinstance(value, str) or any(ord(ch) < 32 or ord(ch) == 127 for ch in value):
        raise SetupError('SMTP values must be single-line text without control characters.')
    return value


def dotenv_quote(value):
    # Compose expands unquoted/double-quoted dollars. $$ preserves each dollar;
    # JSON-compatible double quoting preserves quotes, backslashes, spaces and #.
    value = clean_value(value)
    return '"' + value.replace('\\', '\\\\').replace('$', '$$').replace('"', '\\"') + '"'


def process(arguments, *, environment=None, timeout=45):
    try:
        return subprocess.run(arguments, text=True, capture_output=True,
                              env=environment, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise SetupError('A Docker command failed or timed out. Check Docker access on this VPS.') from None


def compose_model(envfile, composefile='compose.yml'):
    # Shell-exported mail values otherwise override --env-file silently.
    environment = os.environ.copy()
    for key in KEYS:
        environment.pop(key, None)
    result = process(['docker', 'compose', '--env-file', str(envfile), '-f', str(composefile),
                      'config', '--format', 'json'], environment=environment)
    if result.returncode:
        # Do not echo Compose stderr: parser errors can contain a secret value.
        raise SetupError('Compose configuration is invalid. No credential values were printed.')
    try:
        return json.loads(result.stdout)
    except (ValueError, TypeError):
        raise SetupError('Docker Compose did not return valid configuration JSON.') from None


def compose_environment(model, service):
    # `compose config` serializes literal dollars as $$ so its output can itself
    # be used as Compose input. Decode that one serialization layer for comparisons.
    values = model.get('services', {}).get(service, {}).get('environment', {})
    return {key: value.replace('$$', '$') if isinstance(value, str) else value
            for key, value in values.items()}


def private_file(path, contents):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as handle:
        handle.write(contents)


def import_settings(path):
    if path.is_symlink() or not path.is_file():
        raise SetupError('The SMTP import must be a regular file, not a symlink.')
    if path.resolve() == Path('.env').resolve():
        raise SetupError('Import only the exported .env.smtp subset, never the server .env.')
    os.chmod(path, 0o600)
    try:
        raw = path.read_text(encoding='utf-8-sig')
    except (OSError, UnicodeError):
        raise SetupError('Cannot read the SMTP import file as UTF-8.') from None
    present = set()
    for line in raw.splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = KEY_RE.match(line)
        if not match or match[1] not in KEYS or match[1] in present:
            raise SetupError('SMTP import has an unknown key, duplicate key or multiline value.')
        present.add(match[1])
    required = set(KEYS) - {'DEMO_NOTIFICATION_EMAIL'}
    if not required <= present:
        raise SetupError('SMTP import is missing required SMTP fields; export the subset again.')
    # Delegate dotenv parsing to the same implementation that runs the service.
    # No shell source/eval and no credentials are passed in command arguments.
    descriptor, mini_name = tempfile.mkstemp(prefix='.smtp-parser-', suffix='.yml', dir='.')
    mini = Path(mini_name)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8', newline='\n') as handle:
            handle.write('services:\n  settings:\n    image: scratch\n    environment:\n')
            for key in KEYS:
                handle.write('      ' + key + ': "${' + key + '-}"\n')
        values = compose_environment(compose_model(path, mini), 'settings')
        return {key: clean_value(str(values.get(key, ''))) for key in KEYS}
    finally:
        mini.unlink(missing_ok=True)


def prompt_settings():
    try:
        terminal = open('/dev/tty', 'r+', encoding='utf-8', buffering=1)
    except OSError:
        raise SetupError('Run interactively in the Bitvise terminal, or use --import .env.smtp.') from None

    def ask(label, default='', secret=False):
        if secret:
            return clean_value(getpass.getpass(label + ': ', stream=terminal))
        terminal.write(label + (f' [{default}]' if default else '') + ': ')
        reply = terminal.readline()
        if not reply:
            raise SetupError('SMTP setup cancelled.')
        return clean_value(reply.rstrip('\n').rstrip('\r') or default)

    with terminal:
        terminal.write('External SMTP: 1 = Resend, 2 = Gmail app password, 3 = other provider\n')
        provider = ask('Provider', '1')
        if provider not in ('1', '2', '3'):
            raise SetupError('Choose provider 1, 2 or 3.')
        if provider == '1':
            host, port, user = 'smtp.resend.com', '587', 'resend'
            sender = ask('Sender email (verified domain, or testing sender)', 'onboarding@resend.dev')
            password = ask('Resend API key (hidden)', secret=True)
            terminal.write('With onboarding@resend.dev, use the email registered with Resend as demo recipient.\n')
        elif provider == '2':
            host, port = 'smtp.gmail.com', '587'
            user = ask('Full Gmail sending address')
            sender = user
            password = ask('Google app password (hidden; not your login password)', secret=True).replace(' ', '')
        else:
            host = ask('SMTP host')
            port = ask('SMTP port', '587')
            user = ask('SMTP username')
            sender = ask('Sender email')
            password = ask('SMTP password/API key (hidden)', secret=True)
        mode = ask('TLS mode: starttls or ssl', 'ssl' if port in ('465', '2465') else 'starttls')
        if mode not in ('starttls', 'ssl'):
            raise SetupError('TLS mode must be starttls or ssl; unencrypted authentication is refused.')
        recipient = ask('Real recipient for the two demo patients (blank keeps account email)')
    return dict(zip(KEYS, (host, port, user, password, sender, 'true',
                            str(mode == 'starttls').lower(), str(mode == 'ssl').lower(), recipient)))


def validate_settings(settings):
    for value in settings.values():
        clean_value(value)
    if not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?', settings['SPRING_MAIL_HOST']):
        raise SetupError('SMTP host must be a hostname or IPv4 address without a URL scheme.')
    if not settings['SPRING_MAIL_PORT'].isdigit() or not 1 <= int(settings['SPRING_MAIL_PORT']) <= 65535:
        raise SetupError('SMTP port must be an integer between 1 and 65535.')
    settings['SPRING_MAIL_PORT'] = str(int(settings['SPRING_MAIL_PORT']))
    if settings['SPRING_MAIL_HOST'].lower() in ('localhost', '127.0.0.1', 'mailpit'):
        raise SetupError('This helper configures external SMTP. Local Mailpit settings were not applied.')
    for key in ('SMTP_AUTH', 'SMTP_STARTTLS', 'SMTP_SSL'):
        settings[key] = settings[key].lower()
        if settings[key] not in ('true', 'false'):
            raise SetupError('SMTP auth and TLS settings must be true or false.')
    if settings['SMTP_AUTH'] != 'true' or not settings['SPRING_MAIL_USERNAME'] or not settings['SPRING_MAIL_PASSWORD']:
        raise SetupError('Authenticated external SMTP requires a nonempty username and password/API key.')
    if settings['SMTP_STARTTLS'] == settings['SMTP_SSL']:
        raise SetupError('Enable exactly one of SMTP_STARTTLS and SMTP_SSL.')
    for key in ('MAIL_FROM', 'DEMO_NOTIFICATION_EMAIL'):
        value = settings[key]
        if (key == 'MAIL_FROM' or value) and not re.fullmatch(r'[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+', value):
            raise SetupError(key + ' must be a plain email address, without a display name.')
    if (settings['SPRING_MAIL_HOST'].lower() == 'smtp.resend.com'
            and settings['MAIL_FROM'].lower() == 'onboarding@resend.dev'
            and not settings['DEMO_NOTIFICATION_EMAIL']):
        raise SetupError('Resend testing sender needs DEMO_NOTIFICATION_EMAIL set to your Resend account email.')


def check_smtp(settings):
    print('Checking SMTP TLS and authentication; no test message is sent...', flush=True)
    context = ssl.create_default_context()
    connection = None
    try:
        host, port = settings['SPRING_MAIL_HOST'], int(settings['SPRING_MAIL_PORT'])
        if settings['SMTP_SSL'] == 'true':
            connection = smtplib.SMTP_SSL(host, port, timeout=20, context=context)
        else:
            connection = smtplib.SMTP(host, port, timeout=20)
            connection.ehlo()
            connection.starttls(context=context)
        connection.ehlo()
        connection.login(settings['SPRING_MAIL_USERNAME'], settings['SPRING_MAIL_PASSWORD'])
        print('SMTP TLS/authentication passed. This does not prove inbox delivery.')
    except smtplib.SMTPAuthenticationError as error:
        raise SetupError(f'SMTP authentication failed (code {error.smtp_code}). Check the API key/app password.') from None
    except ssl.SSLCertVerificationError:
        raise SetupError('SMTP certificate verification failed. Certificate checking was not disabled.') from None
    except (OSError, smtplib.SMTPException):
        raise SetupError('SMTP connection/TLS failed. Check host, port, TLS mode and provider outbound-port restrictions.') from None
    finally:
        if connection is not None:
            # QUIT closes the session; no MAIL FROM, RCPT TO or DATA is issued.
            try:
                connection.quit()
            except (OSError, smtplib.SMTPException):
                connection.close()


def updated_env(original, settings):
    try:
        text = original.decode('utf-8-sig')
    except UnicodeError:
        raise SetupError('The existing .env must be UTF-8; it was not modified.') from None
    retained = []
    for line in text.splitlines(keepends=True):
        match = KEY_RE.match(line)
        if match and match[1] in KEYS:
            # Reject multiline replaced values instead of leaving orphan lines.
            rhs = line[match.end():].strip()
            if rhs.startswith(("'", '"')):
                quote = rhs[0]
                if not re.search(r'(?<!\\)' + re.escape(quote), rhs[1:]):
                    raise SetupError('Existing SMTP values must be single-line; no changes made.')
            continue
        retained.append(line)
    prefix = ''.join(retained)
    if prefix and not prefix.endswith('\n'):
        prefix += '\n'
    return (prefix + ''.join(key + '=' + dotenv_quote(settings[key]) + '\n' for key in KEYS)).encode('utf-8')


def wait_started():
    result = process(['docker', 'compose', 'ps', '-q', 'notification-service'])
    container = result.stdout.strip()
    if result.returncode or not container or '\n' in container:
        raise SetupError('Cannot find the notification-service container.')
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        inspected = process(['docker', 'inspect', container])
        if inspected.returncode:
            raise SetupError('Cannot inspect notification-service.')
        info = json.loads(inspected.stdout)[0]
        state = info['State']
        if state.get('OOMKilled') or state.get('Restarting') or info.get('RestartCount', 0) > 0:
            raise SetupError('notification-service restarted or ran out of memory during startup.')
        if not state.get('Running'):
            raise SetupError('notification-service stopped during startup.')
        logged = process(['docker', 'logs', '--tail=200', container])
        if re.search(r'Started\s+NotificationServiceApplication\b', logged.stdout + logged.stderr):
            return
        time.sleep(3)
    raise SetupError('notification-service did not finish startup within 180 seconds.')


def main():
    parser = argparse.ArgumentParser(description='Configure external SMTP only; preserve DB/encryption credentials.')
    parser.add_argument('--import', dest='import_file', type=Path, help='SMTP-only dotenv export, removed after success')
    args = parser.parse_args()
    env = Path('.env')
    if env.is_symlink() or not env.is_file() or not Path('compose.yml').is_file():
        raise SetupError('Run from the VPS bundle with an existing regular .env and compose.yml.')
    for key in KEYS:
        # Mutations must use the same settings that were validated, even if the
        # shell happens to have mail variables exported from another project.
        os.environ.pop(key, None)
    os.chmod(env, 0o600)
    original = env.read_bytes()
    settings = import_settings(args.import_file) if args.import_file else prompt_settings()
    validate_settings(settings)
    candidate_contents = updated_env(original, settings)
    descriptor, candidate_name = tempfile.mkstemp(prefix='.env.smtp-candidate-', dir='.')
    candidate = Path(candidate_name)
    backup = None
    applied = False
    try:
        with os.fdopen(descriptor, 'wb') as handle:
            handle.write(candidate_contents)
        model = compose_model(candidate)
        notification = compose_environment(model, 'notification-service')
        if any(notification.get(COMPOSE_KEYS[key]) != value for key, value in settings.items()):
            raise SetupError('SMTP values did not round-trip through Compose. Existing .env was kept.')
        if notification.get('SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED') != settings['SMTP_STARTTLS']:
            raise SetupError('Compose must require STARTTLS whenever STARTTLS is enabled.')
        check_smtp(settings)
        if env.read_bytes() != original:
            raise SetupError('.env changed during setup; stopped to preserve the concurrent changes.')
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        backup = Path('.env.before-smtp-' + stamp + '-' + str(os.getpid()))
        private_file(backup, original)
        os.replace(candidate, env)
        applied = True
        print('Recreating only notification-service. Existing PENDING/FAILED mail may retry on startup.', flush=True)
        result = process(['docker', 'compose', 'up', '-d', '--no-deps', '--force-recreate', 'notification-service'], timeout=120)
        if result.returncode:
            raise SetupError('Failed to recreate notification-service.')
        wait_started()
        # Mailpit is no longer needed, but its captured data is not deleted.
        try:
            stopped = process(['docker', 'compose', '--profile', 'mailpit', 'stop', 'mailpit'], timeout=60)
            stop_failed = stopped.returncode != 0
        except SetupError:
            stop_failed = True
        if stop_failed:
            print('SMTP applied; Mailpit could not be stopped. You may stop it separately.')
        if args.import_file:
            try:
                args.import_file.unlink()
            except OSError:
                print('SMTP applied; remove the private .env.smtp import manually after this run.')
        print('SMTP configuration applied; notification-service finished startup.')
        print('Private backup: ' + str(backup) + ' (contains credentials; keep private).')
        print('Verify by making a NEW demo booking/payment and checking the chosen inbox/spam folder.')
        print('Mail previously marked SENT in Mailpit is not resent. Inbox delivery is not yet verified.')
    except (SetupError, OSError, ValueError, KeyError, KeyboardInterrupt) as error:
        if applied and backup is not None:
            # Restore configuration and attempt to restart the previous instance.
            private_file(candidate, original)
            os.replace(candidate, env)
            restored = process(['docker', 'compose', 'up', '-d', '--no-deps', '--force-recreate', 'notification-service'], timeout=120)
            print('Restored previous .env. ' + ('Previous notification configuration restarted.' if restored.returncode == 0
                                               else 'Inspect notification-service manually; restart failed.'))
        if isinstance(error, (SetupError, KeyboardInterrupt)):
            raise
        raise SetupError('SMTP setup could not complete; inspect file permissions and Docker status. No credentials printed.') from None
    finally:
        candidate.unlink(missing_ok=True)


if __name__ == '__main__':
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        print('\nSMTP setup interrupted.', file=sys.stderr)
        sys.exit(1)
    except SetupError as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        sys.exit(1)
PYTHON
