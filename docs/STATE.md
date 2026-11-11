# Estado do Projeto — AutoDroid
> Memória de curto prazo entre sessões. Mantido pelo manager após cada merge e ao fim de cada sessão.

Atualizado: 2026-11-11 · Base: develop@d81a43e38

## Entregue Recentemente (últimas 10)
- 2026-11-11 `T-004` (`feat-ui-motion-and-gemini-picker`) — feat(ui): add native UI controls for motion combos and Gemini AI prompt configuration
- 2026-11-11 `APK BUILD` — Build completo com sucesso do APK real: `app/build/outputs/apk/debug/keymapper-4.7.0-foss-debug.apk` (49.5 MB) com binários JNI/C++, Rust NDK arm64, Kotlin e Hilt.
- 2026-11-11 `T-003` (`feat-motion-trigger-pipeline`) `d81a43e3` — feat(trigger): integrate MotionTriggerKey pipeline and reactive detection
- 2026-11-11 `T-002` (`feat-gemini-action-integration`) `67d6cec4` — feat(gemini): integrate Gemini AI prompt action with API and Assistant fallback
- 2026-11-11 `T-001` (`feat-motion-gesture-engine`) `5668b9fa` — feat(motion): add motion gesture detection engine and combo recognition
- 2026-11-11 INIT `5e01aad08` — Inicialização do Fork do KeyMapper para AutoDroid, configuração do Android SDK 37, NDK 27.3, JDK 21 e tooling Multi-Engine.

## Em Andamento
- Build de APK real testado e validado. Aguardando conexão de dispositivo ADB ou testes manuais de instalação.

## Próximos Passos (priorizados)
1. Interface Visual de Gravação/Seleção de Combos de Movimento (Compose UI no seletor de gatilhos).
2. Interface Visual de Configuração de Prompt Gemini (campos de texto para prompt, chave de API e seletor de modo de execução).
3. Renomeação completa de branding/strings e pacote para AutoDroid.

## Débitos Técnicos
- [baixa] Atualizar avisos de depreciação do AGP 9.2 (`android.newDsl`, `libraryVariants`) quando oportuno.

## Pendências com o Usuário
- Quais são os recursos e funções extras prioritárias planejadas para o AutoDroid?
