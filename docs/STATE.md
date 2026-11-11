# Estado do Projeto — AutoDroid
> Memória de curto prazo entre sessões. Mantido pelo manager após cada merge e ao fim de cada sessão.

Atualizado: 2026-11-11 · Base: develop@67d6cec4c

## Entregue Recentemente (últimas 10)
- 2026-11-11 `T-002` (`feat-gemini-action-integration`) `67d6cec4` — feat(gemini): integrate Gemini AI prompt action with API and Assistant fallback
- 2026-11-11 `T-001` (`feat-motion-gesture-engine`) `5668b9fa` — feat(motion): add motion gesture detection engine and combo recognition
- 2026-11-11 INIT `5e01aad08` — Inicialização do Fork do KeyMapper para AutoDroid, configuração do Android SDK 37, NDK 27.3, JDK 21 e tooling Multi-Engine.

## Em Andamento
- Nenhum subagente ativo no momento.

## Próximos Passos (priorizados)
1. T-003: Criar `MotionTriggerKey` em `base/src/.../trigger/` conectando o `MotionAdapter` ao ciclo de vida do KeyMapDetectionController / AccessibilityService.
2. T-004: Interface de gravação e seleção de combos gestuais e tela de configuração da ação Gemini (prompt, chave API opcional, modo).
3. T-005: Branding do AutoDroid (strings, launcher icons, nome do app).

## Débitos Técnicos
- [baixa] Atualizar avisos de depreciação do AGP 9.2 (`android.newDsl`, `libraryVariants`) quando oportuno.

## Pendências com o Usuário
- Quais são os recursos e funções extras prioritárias planejadas para o AutoDroid?
