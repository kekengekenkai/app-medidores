# AGENTS.md

<!-- Add repo-specific guidance below. Every line should answer: "Would an agent likely miss this without help?" -->

## Developer Commands

<!-- e.g. exact commands, shortcuts, required order (lint -> typecheck -> test) -->

## Architecture

<!-- e.g. monorepo boundaries, entrypoints, non-obvious package ownership -->

## Setup & Environment

<!-- e.g. env loading quirks, required services, codegen/migrations -->

## Testing

<!-- e.g. how to run a single test, fixtures, integration prerequisites, flaky suites -->

## Conventions

<!-- e.g. style rules that differ from defaults, branch/PR expectations -->

## Android MVP Guide
Sí: para empezar con la app Android descrita, necesitas Android Studio y un JDK compatible (11 o 17).
Sí: usa Kotlin + Jetpack Compose (o XML si prefieres) y apunta a un minSdk razonable (p. ej. 21–23) con target 33+ para soporte moderno.
Sí: el flujo MVP implica importar una planilla .xlsx desde almacenamiento local usando SAF, rellenar la columna "Actual" mediante entrada de voz, y poder guardar/compartir el archivo localmente.
Sí: cada libro exportado o importado contiene una sola hoja en este MVP; se puede cambiar de libro importando otros archivos y retomar el trabajo desde el libro seleccionado mediante una lista desplegable en la parte superior.
Sí: para manipular .xlsx en Android, añade POI (poi-ooxml-lite) y habilita desugaring de Java 8 en Gradle; evita cargar archivos muy grandes en memoria.
Sí: la entrada de voz debe usar RecognizerIntent (speech-to-text) y convertir el resultado a números; solo debe rellenar la columna "Actual"; maneja decimales y separadores regionales.
Sí: la UI debe mostrar las columnas Nombre, Anterior, Actual y Consumo; consumo se puede calcular como Actual - Anterior y mostrarse en la columna correspondiente.
Sí: usa SAF para Importar/Exportar y FileProvider para Compartir; no dependas de almacenamiento externo ni login.
Sí: estructura de código recomendada: app (UI), core-excel (lógica de .xlsx), voice-input (grabación y parseo de voz), storage (SAF), sharing (FileProvider/intent).
Sí: flujo de usuario mínimo: abrir, seleccionar archivo .xlsx, grabar para llenar Actual en filas seleccionadas, guardar, y compartir.
