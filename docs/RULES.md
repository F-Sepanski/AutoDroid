# Regras e Restrições Específicas do Projeto (`docs/RULES.md`)
> Injetado automaticamente nos subagentes (`agent.md`) e em `<WT>/.task/context.md`. Mantenha enxuto (máx. 50 linhas).

## 1. Armadilhas do Android & Gradle KTS
- **JDK Target:** Usar Java 21 (`/usr/lib/jvm/java-21-openjdk`). Nunca assumir Java 8/11 antigo nem Java 25 experimental para compilação Android.
- **NDK Obrigatório:** O módulo `:evdev` requer o NDK `27.3.13750724` para a task `:evdev:generateLibEvDevEventNames`. O `local.properties` precisa apontar `sdk.dir=/home/etherumbra/Android/Sdk`.
- **Estilo KtLint:** Todo código Kotlin deve aderir estritamente ao KtLint. Execute sempre `./gradlew ktlintFormat` para aplicar as regras de indentação e imports antes de rodar o gate.
- **Compose State:** Em composables, nunca realizar chamadas bloqueantes de I/O diretamente. Use `collectAsStateWithLifecycle()` e delegue transformações para o ViewModel.

## 2. Injeção de Dependência & Arquitetura
- **Hilt:** Todas as ViewModels e Services devem utilizar anotações Hilt (`@HiltViewModel`, `@AndroidEntryPoint`, `@Inject`).
- **DataStore e Room:** Nunca alterar esquemas Room sem migrações explícitas (`Migration` e teste de migração de banco).
- **AIDL e JNI:** No módulo `evdev`, as definições AIDL de `IEvdevCallback` comunicam-se entre processos nativos. Não alterar assinaturas sem atualizar os headers C++/Rust correspondentes.

## 3. Padrões de Teste e Worktrees
- Em worktrees isoladas (`.worktrees/T-NNN-*`), certifique-se de que o `local.properties` esteja criado antes de rodar o Gradle (garantido automaticamente via `SETUP_CMD`).
- Use mocks com MockK ou Mockito apenas quando interfaces reais não puderem ser instanciadas; dê preferência a `test-fixtures` do projeto.
