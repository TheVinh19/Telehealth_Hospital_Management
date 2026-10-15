# VPS demo with 2 GB RAM and 2 GB swap

Upload the contents of this directory into `~/telehealth` on the VPS.
These files use the existing GHCR v4 images (`sha-0ce0c79`); no rebuild is needed.
Do not upload your development `.env`. Run `bash setup.sh` on the VPS to create
new credentials. Do not rerun setup with a deleted `.env` after initializing data:
existing database passwords and encryption keys must remain unchanged.

## Validate and download

```bash
cd ~/telehealth
bash setup.sh
docker compose config --quiet
docker compose --parallel 1 pull
```

For private packages, authenticate to GHCR first using a token with read access
to those packages. Enter credentials interactively; do not paste them in chat.

## Start in stages

```bash
docker compose up -d postgres redis rabbitmq mailpit
docker compose ps
```

Wait until PostgreSQL, Redis and RabbitMQ report healthy before proceeding.
Run each next service only after the previous one has finished startup (look for
the Spring `Started ...` message and no restart loop):

```bash
docker compose up -d identity-service
docker compose logs --tail=30 identity-service
docker compose up -d realtime-service
docker compose logs --tail=30 realtime-service
docker compose up -d notification-service
docker compose logs --tail=30 notification-service
docker compose up -d appointment-service
docker compose logs --tail=30 appointment-service
docker compose up -d clinical-service
docker compose logs --tail=30 clinical-service
docker compose up -d billing-service
docker compose logs --tail=30 billing-service
docker compose up -d api-gateway web
docker compose ps
docker stats --no-stream
free -h
```

The 320 MiB Java container limits include native memory, with a 112 MiB heap.
The sum of limits exceeds physical RAM: this is an experimental low-concurrency
demo and may use swap. Actual startup and business flows must be measured on the
VPS. Stop and inspect logs if containers restart; do not assume these limits fit
every service. Redis is capped at 24 MiB; PostgreSQL has a small connection budget.

## Inspect through SSH

Web binds only to loopback. On your Windows computer:

```powershell
ssh -N -L 3000:127.0.0.1:3000 thevinh19@103.77.172.232
```

Keep that terminal open and visit `http://localhost:3000`.
Demo accounts from the image remain active; see the public demo caveats below.
Database, broker, Redis and backend ports are
not published. Nginx supports report uploads up to 30 MiB.

Mailpit captures test email internally; external SMTP is not configured by this
bundle until external SMTP is configured as described below. TURN is optional
until `configure-turn.sh` enables its profile, using the existing credentials.

## Domain and HTTPS: thevinh19.duckdns.org

DNS must point to `103.77.172.232`. Before starting, run on the VPS:

```bash
sudo ss -lntp '( sport = :80 or sport = :443 )'
sudo ufw status
```

The VPS already runs host Nginx on ports 80/443. Reuse its existing domain
configuration and certificate rather than starting another public proxy.
Find the active site file and certificate paths before applying changes:

```bash
sudo nginx -T 2>/dev/null | grep -nE 'configuration file|server_name|listen |ssl_certificate|proxy_pass'
```

If UFW is active, allow TCP 80/443; do not enable UFW as part of this step:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

Also allow TCP 80/443 in the provider firewall, if one is enabled. HTTP certificate
validation/HTTPS access require externally reachable ports. Docker publishes
ports directly, so do not rely on UFW alone for fine-grained container access rules.

The existing site has been inspected: `/etc/nginx/sites-enabled/api-gateway.conf`
currently forwards this domain to the deleted application's port 8080 and uses
the existing Certbot certificate. The prepared `host-nginx.conf` changes its
upstream to 3000, adds WebSocket forwarding and permits reports up to 30 MiB.
It retains the existing domain, certificate/key paths, TLS include, DH parameters,
HTTP redirect and access/error log paths.

Upload `host-nginx.conf`, `apply-host-nginx.sh` and the updated `nginx.conf`
into `~/telehealth`, overwriting those files only. Preserve the server's `.env`
and `compose.yml`. On the VPS run:

```bash
cd ~/telehealth
bash apply-host-nginx.sh
```

The script verifies the existing configuration and certificate, validates the
inner proxy, recreates only web, backs up the resolved host site file under
`/var/backups/telehealth-nginx`, then validates and reloads host Nginx. On an apply
or HTTPS check failure it restores the previous host site file. It does not change
certificate renewal settings, Java services or database containers. No extra
proxy container or new certificate is required. For diagnostics:

```bash
sudo nginx -t
curl -I https://thevinh19.duckdns.org
sudo tail -n 30 /var/log/nginx/api-gateway-error.log
```

Nginx preserves the external HTTPS scheme for the gateway. The frontend already
selects WSS on HTTPS, so no image rebuild is needed. Recreating web briefly
interrupts HTTP/WebSocket connections; Java and database services remain running.
Web remains accessible through the existing SSH tunnel for troubleshooting.

Open `https://thevinh19.duckdns.org` in a browser without the SSH tunnel. Check
the browser certificate normally; do not suppress TLS verification. A success
response for the home page does not verify the API or WebSocket behavior.
Inspect DNS A/AAAA records, the host Nginx access/error logs, certificate dates
and provider firewall if the domain is not reachable. Certificate issuance or
renewal must be handled by the existing host certificate manager (if configured).

The current v4 image is a demo with known admin/doctor/patient credentials and
role-switch shortcuts; public HTTPS exposes those demo accounts. Use synthetic
data only. HTTPS does not disable demo access. Demo passwords are reseeded on
startup and login also includes demo password aliases, so changing passwords in
the database alone does not secure a real deployment. Do not add whole-site
Basic authentication: it conflicts with the application's Bearer header.

References: [Nginx proxy module](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Nginx WebSocket proxying](https://nginx.org/en/docs/http/websocket.html).

Persisted data uses the `telehealth-vps` project volumes. Back up `.env`, database
and clinical report volume together before upgrades. Do not use `down -v` on an
installation with data you need.

## Finish TURN and external email

Upload the latest `compose.yml`, `configure-turn.sh`, `turn-entrypoint.sh` and
`turn-probe.py`, preserving `.env`. Run on the VPS:

```bash
cd ~/telehealth
bash configure-turn.sh
```

This generates a private `.env.turnserver.conf`, preserves `TURN_SECRET`, enables
the `turn` profile and starts a 96 MiB Coturn container. The container uses the
same UID/GID as the private file, and fails closed if the file cannot be read or
does not contain authenticated, bounded relay settings. The v4 service uses a
static long-term TURN username/password, so this configuration uses
`lt-cred-mech` rather than REST/HMAC authentication. The new application image
also advertises both UDP and TCP client transports. Relay media remains UDP.

Allow inbound `3478/UDP`, `3478/TCP`, and `49152–49200/UDP` in the provider
firewall. Do not enable or reset the host firewall as part of this setup. The
local probe checks authenticated allocation, integrity, and bidirectional relay;
run it again from another network to verify public reachability. Browser video
still needs a test between Wi-Fi and mobile data. This setup does not provide
TURN-over-TLS on 443/5349; the HTTPS website continues to use Nginx on 443.

For SMTP, export only the existing development mail settings on Windows:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/vps/export-smtp.ps1
```

Transfer the generated, ignored `.env.smtp` privately to the VPS, then run:

```bash
bash configure-smtp.sh --import .env.smtp
```

The helper verifies TLS and SMTP authentication without generating a test email,
preserves unrelated secrets, recreates Notification Service only, and stops
Mailpit. Successful import removes `.env.smtp` on the VPS; delete the local
export too. Without an import, the helper offers interactive Resend/Gmail/custom
setup and hides the password prompt. Resend's `onboarding@resend.dev` testing
sender can send only to the email registered with Resend. Other recipients need
a verified sending domain. Do not assume a DuckDNS subdomain is a verified
email-sending domain. Trigger a new booking/payment and reschedule to check
actual notification delivery and the admin email log. Previously `SENT` Mailpit
messages are not resent; queued `PENDING`/`FAILED` jobs may retry after startup.

References: [Resend SMTP](https://resend.com/docs/send-with-smtp),
[Resend testing sender restrictions](https://resend.com/docs/knowledge-base/403-error-resend-dev-domain),
[Coturn configuration](https://github.com/coturn/coturn/blob/master/examples/etc/turnserver.conf).

## Automatic CD

`.github/workflows/ci.yml` deploys only successful `main`/`master` pushes (or
manual runs on those branches), after all eight application images are published.
Repository variable `CD_ENABLED=true` enables the job, with optional `VPS_PATH`
(default `/home/thevinh19/telehealth`). GitHub environment `vps` stores the
deployment history. Configure Actions secrets `VPS_HOST`, `VPS_USER`,
`VPS_SSH_KEY` and `VPS_KNOWN_HOSTS`. Use a dedicated key and a previously verified
host key; do not disable SSH host verification. `provision-cd-key.sh` installs
the dedicated public key without replacing existing SSH access.

The runner uploads only configuration and helper scripts. Server `.env`, SMTP
credentials, generated TURN configuration, certificate management and persistent
volumes remain on the VPS. It pulls all SHA-tagged images before changing the
release, then starts Java services sequentially for the 2 GB host. Startup log,
internal HTTP and public API checks must pass before the new tag is recorded.
Failed deployment restores the previous configuration and application images.
It does not undo database migrations: future migrations must remain compatible
with the previous application version. Deployments are serialized; stale runs
are skipped. No automatic destructive image/volume pruning is performed.

After a successful deploy, inspect:

```bash
docker compose ps
docker stats --no-stream
free -h
curl -I https://thevinh19.duckdns.org
```
