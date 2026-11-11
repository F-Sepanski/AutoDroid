# Arquitetura — AutoDroid
> Mapa estático do que ESTÁ em BASE. Mantido exclusivamente pelo manager_multi.
> Fork avançado do KeyMapper para automação e remapeamento expandido no Android.

## 1. Stack
- Linguagem/runtime: Kotlin 2.4.0 (JVM 21), Rust 1.99+ (evdev/JNI), C++ (sysbridge JNI)
- Framework/engine: Android SDK 37 (minSdk 26, targetSdk 37), Jetpack Compose, AndroidX, Hilt DI, Room, Coroutines/Flow
- Build System: Gradle 9.7.1, AGP 9.2.1, NDK 27.3.13750724
- Testes: JUnit 4/5, AndroidX Test, Room Testing, MockK/Mockito
- Lint/format: ktlint via `org.jlleitschuh.gradle.ktlint`, rustfmt

## 2. Comandos Canônicos
| Ação | Comando |
|---|---|
| setup | `echo 'sdk.dir=/home/etherumbra/Android/Sdk' > local.properties` |
| build | `./gradlew assembleDebug` |
| typecheck / lint | `./gradlew ktlintCheck` |
| test | `./gradlew testDebugUnitTest` |
| rust test | `cd evdev/src/main/rust/evdev_manager && cargo test --package evdev_manager_core` |
| format | `./gradlew ktlintFormat` |

GATE (espelhado em `.tasks/config`): `./gradlew ktlintCheck`

## 3. Estrutura Multi-Módulo
```
AutoDroid/
├── app/          — Ponto de entrada Android (MainActivity, AutoDroidApp/KeyMapperApp, AccessibilityService)
├── base/         — UI Compose/XML e ViewModels (triggers, actions, constraints, keymaps, settings)
├── api/          — Interfaces contratuais, modelos e payloads compartilhados
├── common/       — Extensões Kotlin, utilitários de coroutines e dispatchers
├── data/         — Camada de persistência Room, DataStore Preferences e DAOs
├── evdev/        — Manipulação de baixo nível de eventos Linux / Android (Rust JNI + libevdev)
├── sysbridge/    — Ponte nativa JNI C++ para privilégios de sistema e Shizuku
├── system/       — Integrações de acessibilidade Android e injeção de input/key events
├── systemstubs/  — Stubs para APIs ocultas/internas da plataforma Android
└── test-fixtures/— Helpers compartilhados de teste e dados mock
```

## 4. Regras Invioláveis & Fronteiras de Camadas
1. Direção de dependências:
   - `common` e `api` são folhas contratuais sem dependência de `base` ou `app`.
   - `data` provê persistência para `base` e `app`, mas não deve acoplar lógica de UI/Compose.
   - `evdev` e `sysbridge` gerenciam canais nativos isolados via AIDL/JNI.
2. Gerenciamento de Threads: Operações de I/O em banco (Room) e leitura evdev devem obrigatoriamente rodar em `Dispatchers.IO` ou background worker específico.
3. Compatibilidade NDK: Manter NDK fixo na versão configurada (`27.3.13750724`) para evitar divergências nos headers `input.h` gerados para libevdev.

## 5. Índice de Módulos
| Módulo | Caminho | Responsabilidade |
|---|---|---|
| app | `app/` | Lifecycle da aplicação, injeção de dependência raiz e serviços em segundo plano |
| base | `base/` | Telas de mapeamento, edição de macros, regras e UI Jetpack Compose |
| api | `api/` | Contratos de dados de keymaps, gatilhos e ações |
| common | `common/` | Utilitários de sistema, extensões de data/hora e helpers funcionais |
| data | `data/` | Banco de dados Room e persistência de preferências de chave |
| evdev | `evdev/` | Captura crua de eventos `/dev/input/event*` em Rust e JNI |
| sysbridge | `sysbridge/` | Chamadas privilegiadas via Shizuku ou binário su |
| system | `system/` | Acessibilidade e interfaces com o WindowManager |

## 6. Arquivos Quentes
- `gradle/libs.versions.toml`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `evdev/build.gradle.kts`

## 7. Convenções e Limites Anti-Entropia
- Tetos (`multitask arch-check`): Arquivo ≤ 350 linhas · Função ≤ 80 linhas · Clones ≥ 8 linhas bloqueados.
- Formatação: obrigatório rodar `./gradlew ktlintFormat` antes de qualquer entrega de código.
