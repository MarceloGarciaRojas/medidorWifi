# Wi-Fi Signal Analyzer

Aplicación Android nativa en Kotlin y Jetpack Compose. Proyecto original a partir de los requisitos; el APK aportado solo se utilizó para análisis estático funcional (ver [informe](docs/REFERENCIA_APK.md)).

## Compilar

Android Studio con JDK 17 o 21 completo, SDK Android 35 y Build Tools 35.0.0. Mínimo Android 8 (API 26). Abrir esta carpeta y dejar que Android Studio configure `local.properties`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Instalación con un dispositivo autorizado: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Arquitectura

- `Domain.kt`: modelos puros, canales/bandas, transiciones BSSID y alerta con frescura, persistencia y deduplicación.
- `Monitor.kt`: ViewModel, consulta Wi-Fi, recursos sonoros, coordinación de persistencia. La actividad ejecuta `monitor()` dentro de `repeatOnLifecycle(RESUMED)`. El bloque `finally` libera sonido y referencias de roaming. No existen servicios.
- `Storage.kt`: Room para antenas, lecturas manuales y eventos; DataStore para preferencias y BSSID fijado.
- `MainActivity.kt`: Compose Material 3, navegación de cuatro pantallas y paneles inferiores.

Las capas están separadas en archivos dentro de un módulo `app`. No hay SDK publicitario, cuenta, backend ni permiso de Internet. La base de datos no participa en copias de seguridad Android.

## Funciones implementadas

- Consulta de conexión aproximadamente cada segundo mientras la actividad está RESUMED: RSSI, SSID, BSSID, frecuencia, banda, canal, velocidad e IP cuando Android los facilita.
- Escaneos solicitados como máximo cada 30 segundos en una sesión activa; resultados ordenables y buscables con antigüedad del timestamp del sistema. Android puede restringirlos.
- Registro y edición de nombres, ubicación e IP manual por BSSID. Seleccionar un nombre existente agrupa interfaces de una antena física; editar un grupo actualiza sus registros de forma transaccional; no hay inferencia de fabricante ni detección automática de IP.
- Seguimiento de un BSSID persistente, curva de lecturas disponibles y ausencia de lectura reciente pasados 15 segundos.
- Historial manual por zona, tres zonas recientes, filtrado, gráfico y exportación CSV mediante el selector de documentos Android.
- Eventos automáticos de BSSID, pérdida/recuperación observada de conexión Wi-Fi, permanencia e intervalo de observación. Se conservan al cerrar.
- Alerta orientativa por señal débil y alternativa reciente del mismo SSID, diferencia y persistencia configurables.
- Tema automático/claro/oscuro, umbrales, beep, indicador visual variable, vibración, borrado y conservación manual de 30 días.

## Permisos

`ACCESS_WIFI_STATE` permite leer el estado; `CHANGE_WIFI_STATE` solicitar escaneos; `ACCESS_NETWORK_STATE` consultar red e IP; `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` permiten solicitar ubicación precisa necesaria para BSSID/escaneos; `VIBRATE` habilita feedback opcional. No se obtiene la posición GPS. No se pide ubicación en segundo plano. `NEARBY_WIFI_DEVICES` no sustituye ubicación precisa en las APIs de escaneo empleadas. La interfaz ofrece accesos a permisos, Wi-Fi y ubicación.

## Límites y validación pendiente

- No hay teléfono ni emulador conectado en el entorno. Quedan por verificar instalación, renderizado, permisos reales por versión y recorrido entre antenas Cisco.
- Las lecturas son las entregadas por Android: consultar cada segundo no garantiza que el hardware publique un RSSI nuevo a esa frecuencia.
- La ausencia de BSSID en un escaneo no implica fallo físico. La frescura máxima para comparar antenas es 15 segundos; con throttling las alertas pueden no disponer de evidencia suficiente.
- Un cambio observado no mide la duración del roaming. Sin una comprobación de tráfico controlada se muestra «Información insuficiente»; con ausencia Wi-Fi observada, «Posible interrupción». No se afirma «Cambio fluido» o «Cambio lento» sin evidencia. No se realizan sondas externas ni pruebas de disponibilidad del servidor de la tienda.
- Los grupos se identifican por nombre compartido. Conviene utilizar nombres únicos para antenas físicas distintas.
- Los gráficos presentan muestras en orden, no una escala temporal uniforme. El gráfico en vivo retiene hasta 90 muestras durante la sesión.
- La exportación actual incluye lecturas manuales; los eventos se consultan localmente.
- El APK de referencia no fue ejecutado; el feedback implementado es una alternativa original, no una reproducción exacta.

## Pruebas en dispositivo

1. Denegar ubicación, conceder aproximada y después precisa: comprobar mensajes y recuperación.
2. Desactivar/activar Wi-Fi y ubicación; no deben mostrarse BSSID censurados como antenas.
3. Registrar dos BSSID y renombrarlos; guardar lectura, cerrar y abrir, revisar persistencia.
4. Fijar una antena y caminar hacia otra: el objetivo permanece fijo y el cambio conectado se registra una sola vez.
5. Enviar a Inicio/bloquear: no deben continuar tonos ni consultas. Al regresar no debe registrarse un roaming ocurrido durante la pausa.
6. Comparar timestamps de escaneos, comprobar «Sin lectura reciente» y exportar CSV con nombres que contienen comillas/comas.

## Entorno en la nube

Ejecutar `bash scripts/cloud-setup.sh` desde este checkout. Instala o reutiliza herramientas con checksum verificado y ejecuta compilación y pruebas. No modifica código ni requiere credenciales adicionales. Consulta [resultados de validación](docs/VALIDACION.md).
