# WhiteBlack 1.0.0
Plugin de minijuego de búsqueda de calabazas para Paper/Spigot 1.20.4 y Java 17. Multiverse-Core se utiliza para cargar/administrar mundos; WhiteBlack no lo requiere como dependencia de compilación. Vault es opcional en tiempo de ejecución para entregar dinero.

## Compilación
Requiere Maven 3.9+ y JDK 17+. En esta carpeta ejecutar `mvn clean package`. El resultado será `target/WhiteBlack-1.0.0.jar`.

## Instalación
1. Instala Paper/Spigot compatible, Multiverse-Core y Vault.
2. Instala un plugin de economía compatible con Vault (por ejemplo EssentialsX Economy).
3. Copia el JAR en `plugins/` y reinicia.
4. Carga/crea el mundo con Multiverse-Core y ejecuta `/wb setworld <mundo>`.
5. Sitúate en el lobby y usa `/wb setlobby`; luego en el mapa usa `/wb setspawn`, `/wb pos1` y `/wb pos2` para delimitar la región.
6. Ajusta `/wb setround <minutos>` (nota: el comando toma minutos), `/wb setpumpkins <cantidad>` y `/wb setreward <puntos> <dinero>`.
7. Ejecuta `/wb start`.

## Comandos
`/wb help`, `/wb setlobby`, `/wb setspawn`, `/wb setworld <mundo>`, `/wb pos1`, `/wb pos2`, `/wb start`, `/wb stop`, `/wb next`, `/wb points <jugador>`, `/wb addpoints <jugador> <cantidad>`, `/wb removepoints <jugador> <cantidad>`, `/wb top`, `/wb setround <minutos>`, `/wb setpumpkins <cantidad>`, `/wb setreward <puntos> <dinero>`, `/wb reload`.

Permisos: `whiteblack.admin` (OP por defecto), `whiteblack.play` (todos por defecto).

## Consideraciones
Esta es una primera implementación fuente, no un binario compilado/verificado. Antes de producción debe probarse en una copia del servidor. Las calabazas se generan en posiciones de aire con bloque sólido debajo dentro de las esquinas configuradas; selecciona una región pequeña y despejada para validar. El sistema entrega recompensa a todos los jugadores empatados en el máximo de la ronda. Si Vault/economía no está disponible, se mantienen los puntos internos y se omite el dinero.
