# Estado do Projeto — AutoDroid
> Memória de curto prazo entre sessões. Mantido pelo manager após cada merge e ao fim de cada sessão.

Atualizado: 2026-11-12 · Base: develop@da9aeb43

## Entregue Recentemente (últimas 10)
- 2026-11-12 `APK BUILD v7` — APK com auto-type e auto-submit no Assistente via Acessibilidade e queries de visibilidade de pacotes: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (50 MB).
- 2026-11-12 `T-011` (`feat-gemini-assistant-accessibility-auto-type-and-queries`) `da9aeb43` — feat(gemini): auto-type and submit prompt via accessibility overlay and add package visibility queries
- 2026-11-11 `APK BUILD v6` — APK com sensores preservados em background e Gemini Assistant em modo overlay: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (50 MB).
- 2026-11-11 `T-010` (`fix-background-motion-sensor-lifecycle-and-gemini-overlay`) `7ab77cca` — fix(motion,gemini): preserve background motion sensor listening and launch Gemini assistant as overlay
- 2026-11-11 `APK BUILD v5` — APK com supressão de frenagem inercial, shake 500ms, feedback háptico e Gemini Intent sem web search: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (50 MB).
- 2026-11-11 `T-009` (`fix-gemini-intent-and-response-feedback`) `aa93ae8d` — fix(gemini): replace web search with direct Gemini app intent, voice assistant and API toast feedback
- 2026-11-11 `T-008` (`fix-motion-detection-physics-rebound-and-persistence`) `b215c20c` — fix(motion): add rebound brake suppression, biomechanical shake window, haptic feedback and persistence
- 2026-11-11 `APK BUILD v4` — APK com sensibilidade até 50m/s², botões corrigidos, treino com auto-reset, edição de gatilhos e execução no serviço: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (50 MB).
- 2026-11-11 `T-007` (`feat-motion-studio-refinements-and-service-execution`) `3d25f83e` — feat(motion): refine Motion Studio range/layout/training and wire MotionAdapter to accessibility service
- 2026-11-11 `APK BUILD v3` — APK com Motion Studio 3D, Gravador Livre, Calibração, Treinador e Debugger: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (50 MB).
- 2026-11-11 `T-006` (`feat-motion-studio-recorder-debugger`) `d7ce8960` — feat(motion): add Motion Studio with 3D canvas visualizer, calibration, training and telemetry debugger

## Em Andamento
- Build de APK real com interface nativa testado e pronto para instalação.

## Próximos Passos (priorizados)
1. Validação visual e teste de usabilidade pelo usuário nos menus nativos.
2. Renomeação de strings/branding e ícone do app para AutoDroid.

## Débitos Técnicos
- [baixa] Atualizar avisos de depreciação do AGP 9.2 (`android.newDsl`, `libraryVariants`) quando oportuno.

## Pendências com o Usuário
- Quais são os recursos e funções extras prioritárias planejadas para o AutoDroid?
