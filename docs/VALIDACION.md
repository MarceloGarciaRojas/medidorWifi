# Validación de la entrega

Fecha: 2 de noviembre de 2026.

`bash scripts/cloud-setup.sh`: BUILD SUCCESSFUL. Compilación debug y 12 pruebas ejecutadas, todas aprobadas.

- cl.medidorwifi.DomainTest: 8 pruebas aprobadas.
- cl.medidorwifi.StorageLifecycleTest: 4 pruebas aprobadas.

Cobertura: BSSID real/censurado, límites RSSI y canales, roaming y reinicio de referencia, intervalos de ausencia, alertas frescas persistentes y deduplicadas, escape CSV, persistencia Room tras reabrir, conservación, grupos, cancelación/reanudación de coroutine por lifecycle y manifiesto sin Internet ni servicios.

APK verificado con apksigner (firma v2), mínimo API 26 y target API 35. No hay teléfono ni emulador conectado: instalación, interfaz, permisos de fabricante, sonidos y recorrido Cisco siguen pendientes de validación física. Las pruebas de lifecycle prueban el mecanismo de cancelación, no el consumo real del teléfono.

La validación corresponde a la compilación local previa a la publicación del código. Publicar el repositorio no publica el entorno en la nube.
