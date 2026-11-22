# Accesibilidad y diseño

La usuaria **no depende del texto**: se guía por **miniaturas, íconos, colores y posición**.
Por eso el diseño prioriza que todo sea **grande, reconocible, constante y a prueba de errores**,
conservando la apariencia de YouTube que ella ya conoce (familiaridad = menos aprendizaje).

## 1. Reglas de oro

1. **Familiar:** misma disposición que YouTube móvil — miniaturas 16:9, reproductor arriba,
   lista debajo, pantalla completa al girar. Lo que ya sabe hacer debe funcionar igual.
2. **Pocos elementos:** solo los botones que ella usa (anterior, reproducir/pausa, siguiente,
   pantalla completa, volver). Nada de menús de tres puntos, compartir, likes, buscador.
3. **Grande:** todo lo tocable mide **mínimo 64 dp**; el botón principal ▶/⏸ mide **96 dp**.
   (La guía general de Android es 48 dp; aquí se duplica por la motricidad.)
4. **Separado:** al menos **16 dp** entre botones para que un toque impreciso no active el vecino.
5. **Constante:** cada botón siempre está en el mismo lugar y tiene el mismo color.
6. **Sin castigo:** ningún toque puede "romper" nada. No hay acciones destructivas al alcance de
   la niña; las opciones peligrosas están en la zona del cuidador.
7. **Respuesta inmediata:** cada toque da **vibración corta + animación visible** para que sepa
   que la app la "escuchó".

## 2. Colores (tema oscuro estilo YouTube)

| Token | Valor | Uso |
|---|---|---|
| `fondo` | `#0F0F0F` | Fondo general (igual al modo oscuro de YouTube) |
| `superficie` | `#272727` | Tarjetas, fondo de botones secundarios |
| `rojo` | `#FF0033` | Botón principal ▶/⏸, barra de progreso, ícono de la app |
| `blanco` | `#FFFFFF` | Íconos y texto sobre fondo oscuro (contraste > 15:1) |
| `gris` | `#AAAAAA` | Duración del video, información secundaria |
| `foco` | `#FFD600` | Borde amarillo de 4 dp en la miniatura que está sonando / presionada |

- El **video que está sonando** se marca siempre con borde amarillo grueso + ícono de ecualizador
  animado, en Inicio y en "A continuación". Así sabe dónde está su canción.
- El **rojo** se reserva para la acción principal; no se usa para nada más (no confundir).
- Contrastes verificados para cumplir WCAG AA como mínimo, AAA donde sea posible.

## 3. Íconos

- Íconos **rellenos** (Material Symbols *Filled*), no de contorno: se reconocen mejor.
- Íconos **blancos dentro de un círculo** de `superficie` (secundarios) o `rojo` (principal),
  para que el área tocable sea visible, no solo el dibujo.
- Tamaño del dibujo ≥ 40 dp dentro de botones de 64–96 dp.
- Conjunto mínimo: ⏮ anterior · ▶/⏸ · ⏭ siguiente · ⛶ pantalla completa · ⌄ volver.
- Cada botón tiene `contentDescription` (por si en algún momento se usa TalkBack o lo usa otra
  persona), aunque la usuaria no lo necesite.

## 4. Miniaturas

- Ocupan **todo el ancho** (lista) o media pantalla (cuadrícula de 2), relación 16:9.
- El **título es opcional** (se puede ocultar desde la zona del cuidador); la miniatura es lo
  que ella reconoce.
- Duración en la esquina inferior derecha (como YouTube), solo como referencia visual.
- Al presionar: la tarjeta se encoge un poco (escala 0,95) + vibración.

## 5. Protección contra toques accidentales

| Mecanismo | Detalle |
|---|---|
| Acción al **soltar**, no al tocar | Si arrastra el dedo fuera del botón, no se activa |
| Tolerancia de movimiento | Un toque con pequeño temblor (≤ 24 dp de desplazamiento) sigue contando como toque, no como desplazamiento |
| **Anti-rebote** | Tras un toque, el mismo botón ignora toques durante 600 ms (evita pausar/reanudar repetido) |
| **Ignorar multitoque** | Si hay 2+ dedos o la palma en pantalla, no se procesa ningún toque |
| Gestos complejos desactivados | Sin deslizar para cerrar, sin pellizcar. El **doble toque para ±10 s** queda configurable (por defecto **apagado**) |
| Controles de pantalla completa | Primer toque solo **muestra** los controles; recién el segundo toque actúa |
| Desplazamiento de listas | Desplazamiento normal (ella ya lo domina en YouTube), con "rebote" visible al final |

## 6. Sonido y volumen

- **Volumen máximo configurable** por el cuidador (protección auditiva). `VolumeLimiter` corrige
  el volumen si se sube por encima del límite con los botones físicos.
- La música **no se detiene** al ir a Inicio; solo se pausa con el botón ⏸.
- Al reabrir la app, continúa donde se quedó (último video y posición).

## 7. Zona del cuidador (oculta)

- Acceso: **mantener presionado 5 segundos** el ícono de la app en la esquina superior izquierda
  de Inicio → aparece un teclado numérico → **PIN de 4 dígitos**.
- Un toque corto o un toque largo menor a 5 s **no hace nada visible**, para que la niña no la
  descubra.
- Si el PIN se olvida: se puede restablecer reinstalando la app (borrando datos).

## 8. Ícono de la app

La familia quiere un ícono **rojo tipo "botón de play"**, que la niña asocie con su música:
rectángulo redondeado rojo con triángulo blanco al centro.

> **Nota:** el logo exacto de YouTube y el nombre "YouTube" son marcas registradas de Google.
> Para este uso **privado e instalado manualmente** se dibujará un ícono propio (vector
> adaptativo) con el mismo concepto visual — rojo, redondeado, triángulo blanco — sin copiar el
> archivo oficial ni usar el nombre "YouTube". La app **no debe publicarse en Google Play** con
> ese ícono.

Nombre visible de la app sugerido: **"Música"** (o el nombre de la niña, p. ej. "Música de ___").

## 9. Validación con la usuaria

Checklist para cada sesión de prueba (fase 7):

- [ ] ¿Reconoce el ícono y abre la app sola?
- [ ] ¿Encuentra y elige el video que quiere?
- [ ] ¿Cambia de canción desde "A continuación"?
- [ ] ¿Gira el celular y vuelve sin perderse?
- [ ] ¿Hubo toques accidentales? ¿Dónde? (ajustar tamaños/espaciado)
- [ ] ¿En algún momento pidió ayuda? ¿Por qué?
