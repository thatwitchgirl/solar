#!/system/bin/sh
# Solar Y1 boot prep — library folders on SD + launcher switch scripts + TLS sanity.

SD=/storage/sdcard0
i=0
while [ "$i" -lt 30 ]; do
    [ -d "$SD" ] && break
    sleep 1
    i=$((i + 1))
done

if [ -d "$SD" ]; then
    for d in Music Podcasts Themes JJ_Themes; do
        [ -d "$SD/$d" ] || mkdir -p "$SD/$d"
    done
    chmod 755 "$SD/Music" "$SD/Podcasts" "$SD/Themes" "$SD/JJ_Themes" 2>/dev/null
fi

# Seed launcher switch scripts for unmodified Rockbox-y1 APK (expects /data/data/switch-to-stock.sh).
if [ -f /system/etc/solar/switch-to-stock.sh ]; then
    cp /system/etc/solar/switch-to-stock.sh /data/data/
    chmod 755 /data/data/switch-to-stock.sh
fi
if [ -f /system/etc/solar/switch-to-rockbox.sh ]; then
    cp /system/etc/solar/switch-to-rockbox.sh /data/data/
    chmod 755 /data/data/switch-to-rockbox.sh
fi

if [ ! -f /system/lib/libconscrypt_jni.so ]; then
    log -p w -t SolarInit "missing /system/lib/libconscrypt_jni.so — OkHttp/Reach TLS needs clean_install or Solar ROM"
fi
if [ ! -f /system/etc/security/cacerts/6187b673.0 ]; then
    log -p w -t SolarInit "missing ISRG X1 cacert — podcast MediaPlayer HTTPS needs modern cacerts on /system"
fi
