#!/bin/sh
# Prepara el servidor para el actualizador de los TV onn: un sitio nginx aparte (no toca los demas) que sirve
# solo archivos estaticos desde /var/www/tv-onn/<carpeta>/, con certificado de Let's Encrypt.
# Uso (como root en el servidor):  sh tv-onn.sh <carpeta-secreta> [dominio]
set -e
CARPETA="$1"
DOMINIO="${2:-tv.107-175-69-207.nip.io}"
[ -n "$CARPETA" ] || { echo "Uso: sh tv-onn.sh <carpeta-secreta> [dominio]"; exit 1; }

mkdir -p "/var/www/tv-onn/$CARPETA" /var/www/letsencrypt

# 1) solo HTTP, para que Let's Encrypt valide el dominio
cat > /etc/nginx/sites-available/tv-onn <<NG
# Actualizador del launcher de los TV onn (com.remap.buttons). Solo archivos estaticos.
server {
    listen 80;
    listen [::]:80;
    server_name $DOMINIO;
    location ^~ /.well-known/acme-challenge/ { root /var/www/letsencrypt; }
    location / { return 301 https://\$host\$request_uri; }
}
NG
ln -sf /etc/nginx/sites-available/tv-onn /etc/nginx/sites-enabled/tv-onn
nginx -t && systemctl reload nginx
certbot certonly --webroot -w /var/www/letsencrypt -d "$DOMINIO" --non-interactive --agree-tos --register-unsafely-without-email

# 2) HTTPS: sin listado de carpetas; version.json nunca en cache
cat >> /etc/nginx/sites-available/tv-onn <<NG

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $DOMINIO;
    ssl_certificate     /etc/letsencrypt/live/$DOMINIO/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMINIO/privkey.pem;
    root /var/www/tv-onn;
    autoindex off;
    location = / { return 404; }
    location ~ /version\.json\$ { add_header Cache-Control "no-store" always; }
    types { application/vnd.android.package-archive apk; application/json json; }
}
NG
nginx -t && systemctl reload nginx
echo "Listo: https://$DOMINIO/$CARPETA/"
