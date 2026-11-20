<div align="center">

<img src="docs/icon.png" width="140" alt="MIUV2">

# MIUV2

**Tu UV en el bolsillo.**
Tareas de Eminus, horario, calendario escolar y credencial digital en una sola app, con o sin internet.

![Versión](https://img.shields.io/badge/versión-2.1-1B4DA0?style=flat-square)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Offline](https://img.shields.io/badge/funciona-sin%20internet-1FA463?style=flat-square)
![Usabilidad](https://img.shields.io/badge/Nielsen-10%20heurísticas-2ea44f?style=flat-square)

[Características](#características) · [Instalación](#instalación) · [Cómo funciona](#cómo-funciona) · [Desarrollo](#desarrollo)

</div>

---

> Abres la app y ves de un vistazo **qué clases tienes hoy** y **qué tareas te faltan**.
> Entregas desde el celular y, si no hay señal, la entrega se envía sola cuando vuelva el internet.

## Características

<table>
<tr>
<td width="50%" valign="top">

### Eminus integrado
- Tareas **pendientes, entregadas y vencidas** de todos tus cursos
- **Entrega desde la app**: elige el archivo, agrega un comentario y listo
- Reconoce las **entregas en equipo**
- **Cola sin internet**: se envía sola al reconectar y te avisa
- **Recordatorio** 24 h y 2 h antes del cierre, y aviso de **tareas nuevas**
- Nunca borra ni reemplaza una entrega ya hecha

</td>
<td width="50%" valign="top">

### Horario de clases
- Se lee de **MiUV** con todos los días de cada materia
- Salón, edificio y académico de cada clase
- **Desliza** entre días de la semana
- Agrega o corrige clases a mano
- La pantalla de inicio muestra **las clases de hoy**

</td>
</tr>
<tr>
<td valign="top">

### Calendario escolar
- Tomado del **calendario oficial** de la UV
- Inicio y fin de clases, exámenes, vacaciones, días festivos
- Filtros por tipo y agrupado por mes
- Siempre disponible sin conexión

</td>
<td valign="top">

### Credencial digital
- Captura de tu **credencial de MiUV** completa
- Código QR con tu matrícula
- Sube el brillo al mostrarla y se abre a pantalla completa
- Guardada en el teléfono para usarla sin internet

</td>
</tr>
</table>

## Instalación

1. Descarga `MIUV2.apk` desde [**Releases**](https://github.com/MilioGa/MIUV2/releases).
2. Ábrelo en tu celular y permite **instalar apps de origen desconocido** si te lo pide.
3. Inicia sesión con tu **matrícula y contraseña** de la UV (las mismas de Eminus y MiUV).

> Solo necesitas internet la primera vez. Después todo queda guardado en tu teléfono.

## Cómo funciona

| Sección | De dónde viene |
|---|---|
| **Tareas** | API de Eminus 4, la misma que usa la página web |
| **Horario y credencial** | MiUV, leído en segundo plano (no tienes que navegar) |
| **Calendario** | Calendario escolar oficial publicado en uv.mx |

**Privacidad:** tu contraseña se guarda **cifrada con el Android Keystore** y solo en tu teléfono. La app no tiene servidor propio: habla directo con Eminus y MiUV. Al cerrar sesión se borra todo.

<details>
<summary><b>Navegación</b></summary>

- Desliza a izquierda y derecha para cambiar entre **Inicio, Tareas, Horario, Calendario y Credencial**
- Desliza hacia abajo en Inicio para **actualizar**
- Ayuda y preguntas frecuentes en el menú de Inicio

</details>

## Desarrollo

Requiere **JDK 21** y el **Android SDK 35**.

```bash
git clone https://github.com/MilioGa/MIUV2.git
cd MIUV2
./gradlew assembleRelease      # genera app/build/outputs/apk/release/
```

<details>
<summary><b>Estructura del proyecto</b></summary>

```
app/src/main/java/mx/uv/miuv2/
  MainActivity.kt     punto de entrada
  data/
    Eminus.kt         cliente de la API de Eminus (login, cursos, tareas, entregas)
    MiUV.kt           scripts para leer horario y credencial del portal MiUV
    Parsers.kt        calendario oficial y lector de horario
    Repo.kt           estado, caché sin internet y cola de entregas (WorkManager)
    Crypto.kt         cifrado de la contraseña (Keystore)
  ui/
    Inicio.kt         pantalla inicial: clases de hoy y pendientes
    Tareas.kt         lista y detalle de tareas, entrega
    Pantallas.kt      horario, calendario, credencial, ayuda y ajustes
    MiUvSync.kt       conexión con MiUV en segundo plano
    Theme.kt          colores y componentes
```

</details>

## Hecho con

[Kotlin](https://kotlinlang.org/) · [Jetpack Compose](https://developer.android.com/jetpack/compose) · [Material 3](https://m3.material.io/) · [OkHttp](https://square.github.io/okhttp/) · [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) · [ZXing](https://github.com/zxing/zxing)

> Proyecto independiente de estudiantes. No está afiliado ni respaldado por la Universidad Veracruzana.

---

<div align="center">
<sub>Hecho por <a href="https://github.com/MilioGa">MilioGa</a></sub>
</div>
