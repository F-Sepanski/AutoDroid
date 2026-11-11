# Registro de Decisões (ADR leve) — AutoDroid
> Append-only. Cada entrada ≤ 6 linhas. Uma decisão revogada recebe "Status: substituída por D-NNN" — nunca é apagada.

## D-001 — Fork Base do KeyMapper para AutoDroid (2026-11-11)
- Status: aceita
- Contexto: Necessidade de criar uma versão customizada e expandida com novos recursos de automação Android sob a marca AutoDroid.
- Decisão: Criado o fork a partir de `keymapperorg/KeyMapper` no repositório `F-Sepanski/AutoDroid` mantendo branch upstream configurada para sincronização contínua.
- Alternativas descartadas: Iniciar um app do zero (descartado por reescrever anos de suporte a evdev, acessibilidade e kernel input).
- Consequências: Preservação da base sólida de entrada nativa do KeyMapper com liberdade para expandir o modelo de dados e UI do AutoDroid.

## D-002 — Orquestração Multi-Engine com NDK e Android SDK 37 (2026-11-11)
- Status: aceita
- Contexto: Compilação do módulo evdev exigia NDK específico (27.3.13750724) e plataforma Android SDK 37.
- Decisão: Configurado Android SDK com NDK 27.3.13750724 via `cmdline-tools` e Java 21 OpenJDK. Configurado `SETUP_CMD` no `multitask` para injetar `local.properties` em cada worktree isolada.
- Alternativas descartadas: Desabilitar o módulo evdev (inviabilizaria captura de eventos de hardware).
- Consequências: Todas as worktrees criadas para subagentes compilam e executam testes unitários e lint sem intervenção manual.
