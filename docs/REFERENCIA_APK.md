# Referencia funcional
Se inspeccionaron el manifiesto binario, las cadenas de recursos y los identificadores DEX del APK aportado, sin reutilizar código ni recursos.

Paquete observado: `dev.egl.com.intensidadwifi`. Actividades: `MedidorGeneral`, `RssiDetector`, `VistaRedPrincipal`, `VistaRedExterna`, `VistaDispositivo`, `VistaMLO`, `SettingsActivity`.

Los recursos describen escala roja/verde, lectura RSSI, parpadeo, sonido dinámico según nivel y opción de invertir su velocidad. Hay permisos de ubicación, Wi-Fi, Internet, foreground service e identificadores publicitarios en el APK de referencia. La implementación nueva omite publicidad, Internet y servicios persistentes.

No se ejecutó el APK: no se ha confirmado su apariencia, cadencia sonora ni respuesta exacta del parpadeo. La alternativa original usa tonos cortos, menos espaciados con mejor señal, y un indicador que cambia de opacidad al variar RSSI. Todas las opciones vienen desactivadas.
