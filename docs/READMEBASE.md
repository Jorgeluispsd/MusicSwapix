# Base técnica · MusicSwapix

O [README principal](../README.md) descreve o fluxo local e as limitações do projeto.

## Prévia e confirmação

1. A página de conversão chama o cliente Angular em `core/api`. O proxy de desenvolvimento encaminha `/api` ao Spring Boot.
2. O controller recebe a requisição. O serviço de prévia chama o conversor, que lê a origem, consulta candidatos e guarda a prévia em memória com prazo de validade.
3. No Angular, `track-review.ts` aplica as escolhas locais e calcula os totais. Na confirmação, o cliente envia o ID da prévia, as versões escolhidas e as posições puladas.
4. No Java, `PlaylistReviewSelections` valida as decisões contra a prévia armazenada. `PlaylistConfirmationService` consome a prévia após validar e chama o executor de playlist nova ou existente.

## Persistência e autorização

- `JdbcSyncStateStore` concentra as operações JDBC. O PostgreSQL é o padrão e o perfil `h2` abre o banco local antigo. O PostgreSQL guarda os dados no volume `postgres_data`.
- Spotify usa OAuth com PKCE e callback local. O Google usa OAuth de aplicativo para computador para operações autenticadas de escrita. O backend local reutiliza as sessões salvas em `~/.playlist-converter`.
- O Docker Compose inicia o PostgreSQL. O backend roda pela IDE e o Angular roda em um terminal separado.

## Verificação

Rode `mvn test` na raiz e `npm.cmd test -- --watch=false` na pasta do front. Os testes com mocks e H2 temporário evitam chamadas às APIs de música. Para validar o PostgreSQL, faça uma conversão pequena e confira o histórico pelo DBeaver.

A conversão YouTube → Spotify e a retomada automática após interrupções ficam para etapas futuras.
