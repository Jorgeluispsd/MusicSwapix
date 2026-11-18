# MusicSwapix

Ferramenta para converter playlists entre **Spotify e YouTube**, com prévia e revisão de faixas antes da confirmação.

## 💡 Motivação

Este projeto nasceu de uma necessidade pessoal: em diversas ocasiões, como eventos ou encontros com amigos, eu ouvia músicas incríveis que me marcavam, mas por não ter Spotify na época, ficava limitado a apenas ouvi-las naquele momento. A solução ideal seria ter as mesmas playlists em diferentes aplicativos de música, sem o trabalho tedioso de pesquisar cada música manualmente.

O objetivo é permitir uma conversão direta e eficiente de playlists entre Spotify e YouTube Music, eliminando a necessidade de reconstruir playlists do zero quando você decide mudar de plataforma de streaming ou simplesmente quer ter suas playlists favoritas disponíveis em mais de um serviço.

## 🚧 Status do projeto

Versão bidirecional implementada e validada em testes automatizados e conversões locais. As funcionalidades implementadas e os próximos passos estão resumidos abaixo.

- [x] Setup do projeto (Maven + Spring Boot)
- [x] Autenticação com Spotify (OAuth + PKCE + persistência de refresh_token)
- [x] Leitura de playlists do Spotify
- [x] Busca de músicas equivalentes no YouTube
- [x] Matching e cálculo de confiança entre faixas
- [x] Persistência do histórico (PostgreSQL e H2) e prevenção de duplicatas
- [x] Leitura de playlists do YouTube
- [x] Tratamento específico de quota YouTube e de HTTP 429 Spotify
- [x] Conversão Spotify → YouTube para playlist nova ou existente
- [x] API local para consultar destinos anteriores, gerar prévia, confirmar e cancelar
- [x] Interface Angular com revisão de faixas e histórico
- [x] PostgreSQL via Docker Compose para desenvolvimento local
- [x] Conversão YouTube → Spotify para playlist nova ou existente
- [x] Leitura de playlists privadas do YouTube com autorização Google

## ⚙️ Como funciona

O conversor lê a playlist de origem e gera uma prévia com as correspondências, as músicas já presentes e as faixas sem correspondência. Depois da confirmação, cria a playlist ou adiciona as faixas encontradas ao destino escolhido. O histórico registra os vínculos entre playlists e as inserções confirmadas.

```
model/       -> Song, MatchResult — estruturas de dados comuns aos dois serviços
service/     -> MusicService — contrato comum entre Spotify e YouTube
controller/  -> endpoints da API local
converter/   -> análise, prévias, execução e histórico de conversões
spotify/     -> implementação do lado Spotify
youtube/     -> implementação do lado YouTube
matcher/     -> lógica de similaridade para encontrar a música equivalente
state/       -> histórico de sincronizações e controle de duplicata (PostgreSQL ou H2)
state/database/ -> inicialização e operações JDBC para PostgreSQL e H2
state/sql/   -> SqlResourceLoader
errors/      -> exceções por autorização, Spotify, YouTube e persistência
config/      -> configuração dos clientes de API (injeção de dependência via Spring)
```

O matcher compara os candidatos e aplica regras para versões diferentes. A pontuação indica semelhança entre os dados disponíveis. A prévia mostra o candidato selecionado para cada faixa encontrada.

## 🛠️ Tecnologias

- Java 21 + Spring Boot
- [spotify-web-api-java](https://github.com/spotify-web-api-java/spotify-web-api-java) — cliente para a Spotify Web API
- [google-api-services-youtube](https://developers.google.com/youtube/v3) — cliente oficial para a YouTube Data API v3
- PostgreSQL via Docker Compose — banco padrão de histórico e vínculos
- H2 — alternativa local com o perfil h2
- Angular e TypeScript — interface web
- Maven

## Como instalar e fazer a primeira conversão

Os comandos abaixo usam PowerShell no Windows. Execute cada bloco na pasta indicada. O MusicSwapix funciona localmente com o backend na IDE, o frontend no terminal e o PostgreSQL no Docker.

### 1. Instalar os programas

| Programa | Uso |
| --- | --- |
| Git | Baixar o repositório |
| JDK 21 | Compilar e executar o backend Java |
| IntelliJ IDEA com suporte Java e Maven | Importar o projeto e iniciar o backend |
| Node.js 24 com npm | Instalar e executar o Angular |
| Docker Desktop com Compose | Executar o PostgreSQL |
| Navegador | Usar o app e autorizar as contas |

Você também pode executar o backend com Maven instalado no PATH. O IntelliJ oferece Maven integrado. O DBeaver é opcional para consultar o banco.

Depois de instalar o Node.js, abra um terminal novo e verifique:

```powershell
node --version
npm.cmd --version
git --version
```

No IntelliJ, selecione especificamente o JDK 21. A configuração de Java 21 no POM também precisa ser acompanhada por um SDK compatível na IDE.

### 2. Clonar e criar o arquivo de configuração

No terminal, entre na pasta em que deseja guardar o projeto e execute:

```powershell
git clone https://github.com/Jorgeluispsd/MusicSwapix.git
cd MusicSwapix
Copy-Item .env.example .env
```

A pasta que contém o `pom.xml` é a raiz do clone. O arquivo deve se chamar exatamente `.env`. No Explorador do Windows, habilite a exibição de extensões para conferir se ele ficou como `.env.txt` por engano.

Preencha o arquivo com seus próprios valores ao concluir os próximos passos:

```dotenv
SPOTIFY_CLIENT_ID=COLE_O_CLIENT_ID_DO_SEU_APP
SPOTIFY_CLIENT_SECRET=COLE_O_CLIENT_SECRET_DO_SEU_APP
SPOTIFY_REDIRECT_URI=http://127.0.0.1:8888/callback
YOUTUBE_API_KEY=COLE_A_CHAVE_DO_SEU_PROJETO_GOOGLE
POSTGRES_PASSWORD='ESCOLHA_UMA_SENHA_LOCAL'
```

Os valores em maiúsculas acima são exemplos de preenchimento. Escolha uma senha própria para o banco e mantenha as aspas simples externas. Para facilitar o preenchimento, evite a própria aspa simples dentro da senha. Essa senha será usada pelo PostgreSQL e pelo backend.

### 3. Entender quais autorizações são suas

Quem clona o projeto configura o próprio app Spotify e o próprio projeto Google Cloud. Com essas credenciais, administra as permissões e as cotas dos próprios aplicativos, sem depender da autorização do autor do MusicSwapix.

No Spotify, usar o Client ID de outra pessoa faz as requisições pertencerem ao app dela. A conta que autoriza precisa estar permitida nesse aplicativo em Development Mode. Com seu próprio Client ID, você controla essa lista.

As regras atuais do Spotify exigem Premium ativo do proprietário do app em Development Mode. Para apps novos, são permitidos cinco usuários por app e um Client ID por desenvolvedor. Apps antigos podem conservar Client IDs e usuários já existentes. Esses limites pertencem ao aplicativo do Spotify usado na integração.

[Regras oficiais do Development Mode](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide)

### 4. Criar o app do Spotify e configurar o callback

1. Entre no [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) com a conta que será proprietária do app.
2. Clique em **Create app**. Preencha nome e descrição. Quando a tela pedir as APIs utilizadas, selecione **Web API**. Aceite os termos solicitados para criar o app.
3. No campo **Redirect URIs**, adicione `http://127.0.0.1:8888/callback`. Se o app já foi criado, abra **Settings**, encontre a edição das Redirect URIs, clique em **Add** e salve.
4. Copie o **Client ID** do app para `SPOTIFY_CLIENT_ID` no `.env`.
5. Abra **View client secret** e copie o valor para `SPOTIFY_CLIENT_SECRET`.
6. Em **Users Management**, confira as contas autorizadas. Para usar outra conta além da proprietária, cadastre o nome e o e-mail associado à conta Spotify dentro do limite disponível.

Confira o callback nos dois lugares:

| Local | Valor |
| --- | --- |
| Redirect URIs do painel Spotify | `http://127.0.0.1:8888/callback` |
| Linha do `.env` | `SPOTIFY_REDIRECT_URI=http://127.0.0.1:8888/callback` |

O callback é o endereço local para o qual o navegador volta após o consentimento. O MusicSwapix abre o servidor nessa porta durante o login. Inicie o backend antes de autorizar e mantenha-o em execução até o retorno do navegador. Use o endereço com `127.0.0.1` conforme o exemplo.

O fluxo de usuário do projeto usa Authorization Code com PKCE. O Client Secret também configura os componentes de Client Credentials presentes no código. O Spotify fornece essas credenciais no painel. Os tokens de sessão são gerados pelo login. A leitura e a busca usam os escopos de leitura de playlists privadas e colaborativas. A escrita acrescenta as permissões de modificar playlists privadas e públicas, podendo exigir novo consentimento quando a sessão anterior tinha apenas leitura.

[Criação de apps Spotify](https://developer.spotify.com/documentation/web-api/concepts/apps) · [Regras de Redirect URI](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)

### 5. Criar o projeto Google e obter a chave do YouTube

Você vai configurar dois itens do Google. A chave de API permite buscas e consultas públicas. O cliente OAuth, baixado como JSON, permite pedir autorização da conta para escrita e leitura privada.

1. Entre no [Google Cloud Console](https://console.cloud.google.com/) com sua conta Google.
2. No seletor de projetos no topo, escolha **Novo projeto**, dê um nome e crie. Se já tiver um projeto para essa integração, selecione-o.
3. Confira o nome do projeto no topo antes de criar cada credencial.
4. Abra **APIs e serviços → Biblioteca**.
5. Pesquise **YouTube Data API v3**, abra o resultado e clique em **Ativar**.
6. Abra **APIs e serviços → Credenciais → Criar credenciais → Chave de API**.
7. Nas restrições da chave, limite **Restrições de API** à **YouTube Data API v3** e salve. Se configurar restrições de aplicativo, elas precisam permitir as chamadas feitas pelo backend Java na sua máquina. Restrições por referenciador de site são destinadas a chamadas de navegador.
8. Copie o valor gerado para `YOUTUBE_API_KEY` no `.env`.

A integração implementada usa a YouTube Data API v3. Os passos seguintes configuram a autorização OAuth dessa integração no mesmo projeto.

[Gerenciamento de chaves de API](https://docs.cloud.google.com/docs/authentication/api-keys) · [Registro de aplicações YouTube](https://developers.google.com/youtube/v3/guides/authentication)

### 6. Configurar a autorização Google e baixar o JSON

Prepare o JSON antes da primeira conversão completa, inclusive se a playlist de origem do Spotify for pública. A escrita no YouTube usa OAuth.

1. No mesmo projeto Google Cloud, abra **Google Auth Platform**. Em algumas telas, o acesso aparece por **APIs e serviços → Tela de consentimento OAuth**.
2. Se aparecer **Começar**, preencha o nome do aplicativo, o e-mail de suporte e o contato do desenvolvedor.
3. Para uma conta pessoal, selecione público **Externo**. Mantenha o app em teste durante a configuração local.
4. Em **Público-alvo → Usuários de teste**, adicione o e-mail Google que vai autorizar o MusicSwapix. Para playlists privadas, essa conta precisa ter acesso à playlist. Para escrever no YouTube, use uma conta com canal YouTube e permissão para editar o destino.
5. Abra **Clientes → Criar cliente** e escolha **App para computador**.
6. Dê um nome ao cliente, como `MusicSwapix Local`, e clique em **Criar**. Para esse cliente de computador, o retorno local é configurado pelo MusicSwapix em uma porta temporária.
7. Baixe o JSON. Se o diálogo já tiver sido fechado, abra o cliente criado e procure a opção de download do JSON.
8. Crie a pasta `.playlist-converter` dentro da sua pasta de usuário. No PowerShell:

   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\.playlist-converter"
   ```

9. Mova o JSON baixado para essa pasta e renomeie-o para **`google-client-secret.json`**. Confira a extensão completa para evitar `.json.json`.

O caminho final no Windows será:

```text
C:\Users\SEU_USUARIO\.playlist-converter\google-client-secret.json
```

O prefixo `%USERPROFILE%` se refere à sua pasta de usuário do Windows. Em outros sistemas, o projeto usa `~/.playlist-converter/google-client-secret.json`.

O projeto solicita o escopo `https://www.googleapis.com/auth/youtube.force-ssl` quando abre a autorização Google. Confira as permissões exibidas na tela e use a conta configurada como usuária de teste. Se a tela bloquear o acesso, confira a conta selecionada e a configuração do cliente antes de prosseguir.

[OAuth para aplicativos instalados](https://developers.google.com/identity/protocols/oauth2/native-app) · [Configuração de um cliente Desktop](https://developers.google.com/workspace/admin/reports/v1/quickstart/go#authorize_credentials_for_a_desktop_application)

### 7. Conferir os arquivos antes de iniciar

A estrutura esperada é:

```text
MusicSwapix/
├── .env                     preenchido por você, somente local
├── .env.example             modelo versionado
├── pom.xml
├── docker-compose.yaml
├── src/
└── frontend/

Pasta do seu usuário/
└── .playlist-converter/
    ├── google-client-secret.json   JSON baixado por você
    ├── spotify_tokens.json         criado após autorizar Spotify
    └── google-tokens/              criado pelo fluxo Google
        └── StoredCredential       sessão persistida pela biblioteca
```

Prepare manualmente o `.env` e o JSON do Google. O código cria os arquivos de sessão após a autorização. H2 e PostgreSQL guardam histórico e vínculos entre playlists. As credenciais OAuth ficam na pasta do usuário.

### 8. Subir o banco

Abra o Docker Desktop e aguarde **Engine running**. Na raiz do clone:

```powershell
docker compose up -d postgres
docker compose ps
```

O contêiner PostgreSQL deve aparecer como `Up`. Ele publica a porta local `5432` e guarda os dados no volume `postgres_data`. O backend aplica o schema ao iniciar.

Se a porta já estiver ocupada por um banco de outra cópia do projeto, pare aquele contêiner antes de iniciar este. A senha de `POSTGRES_PASSWORD` é aplicada ao inicializar um volume vazio. Para um volume já inicializado, o backend precisa usar a senha existente do banco.

Como alternativa, selecione o perfil **`h2`** na configuração de execução do Spring no IntelliJ. Nesse perfil, o H2 usa um arquivo na pasta do usuário e permite executar sem iniciar PostgreSQL. Cada banco conserva seu próprio histórico.

### 9. Iniciar o backend

1. No IntelliJ, use **File → Open** e escolha a pasta `MusicSwapix` que contém o `pom.xml`.
2. Aguarde a importação do Maven. Se ela faltar, clique com o botão direito no `pom.xml` e escolha **Add as Maven Project**.
3. Abra **File → Project Structure → Project** e selecione **JDK 21** em **SDK**.
4. Abra `src/main/java/com/jorge/playlistconverter/PlaylistConverterApplication.java` e execute pelo triângulo ao lado de `main`.
5. Na configuração de execução, use a raiz do clone em **Working directory**. Para PostgreSQL, deixe **Active profiles** vazio. Variáveis configuradas no sistema ou na execução da IDE podem prevalecer sobre o `.env`.
6. Aguarde a mensagem `Started PlaylistConverterApplication` e abra [o status da API](http://127.0.0.1:8080/api/status). A resposta esperada contém `"status": "UP"`.

Com Maven no PATH, você também pode executar da raiz:

```powershell
mvn spring-boot:run
```

Se o terminal informar que `mvn` é desconhecido, use o botão Run ou pressione **Ctrl duas vezes** no IntelliJ e execute o comando pelo **Run Anything**. O Maven baixa as dependências na primeira importação ou compilação, então mantenha acesso à internet.

### 10. Iniciar o frontend

Em um segundo terminal, partindo da raiz do clone:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

No macOS/Linux, use `npm` no lugar de `npm.cmd`. A instalação com `ci` é necessária no clone novo e quando você precisa sincronizar as dependências com o lockfile. Para iniciar novamente com as dependências instaladas, basta `npm.cmd start`.

Abra [MusicSwapix](http://localhost:4200). Mantenha os terminais do backend e do Angular em execução. O proxy do Angular encaminha `/api` ao backend em `127.0.0.1:8080`.

### 11. Fazer a primeira conversão

1. Use uma playlist pequena, de duas a cinco faixas, para conferir o setup.
2. Clique em **Tema YouTube** para Spotify → YouTube ou **Tema Spotify** para YouTube → Spotify.
3. Escolha **Nova playlist** ou **Playlist existente**.
4. Cole o link ou ID da origem. Para playlist nova, informe nome e visibilidade. Para playlist existente, informe o link ou ID do destino, que sua conta deve poder editar.
5. Clique em **Gerar prévia**. O Spotify pode abrir o navegador para autorização de leitura ou busca. Uma origem privada do YouTube também pode solicitar autorização Google.
6. Revise as correspondências. Você pode selecionar entre as alternativas oferecidas, abrir a faixa na plataforma de destino e pular itens.
7. Clique em **Confirmar**. O aplicativo cria ou atualiza o destino. Na primeira escrita, o provedor pode pedir permissões adicionais. Spotify permite destino privado ou público. YouTube também permite não listado.
8. Abra o link da playlist no resultado e confira o histórico no app.

Mantenha a aplicação ativa durante o consentimento. O callback Google espera até três minutos. Se o prazo terminar, gere uma nova tentativa. O botão de cancelamento descarta a prévia pendente. Consulte [a API local](docs/API.md) para os exemplos do Postman.

### 12. Entender os tokens e usar novamente

| Provedor | O que o projeto salva | Reutilização |
| --- | --- | --- |
| Spotify | Refresh token e escopos em `~/.playlist-converter/spotify_tokens.json` | Reutiliza o access token válido em memória e tenta renovar com o refresh token quando necessário ou após reiniciar |
| Google/YouTube | Credencial em `~/.playlist-converter/google-tokens/` | Reutiliza o access token válido ou tenta renovar com o refresh token salvo |

O MusicSwapix obtém e persiste tokens automaticamente. A biblioteca prepara o diretório Google. O Spotify cria a pasta de sessão. A pasta necessária para colocar o JSON pode ser criada no passo 6.

No Spotify, a necessidade de novos escopos ou um refresh token inválido leva a nova autorização. No Google, `invalid_grant` remove a credencial inválida e inicia um novo login. Falhas técnicas de renovação devolvem erro. No Google, apps externos em teste podem ter refresh tokens com duração de sete dias para esses escopos, o que exige nova autorização.

[Expiração de refresh tokens Google](https://developers.google.com/identity/protocols/oauth2#expiration)

Excluir a sessão salva pode exigir novo login após reiniciar o backend. Excluir o JSON OAuth faz a preparação do cliente Google falhar mesmo com uma sessão salva, pois o código carrega esse arquivo ao preparar o fluxo.

Os clones executados pelo mesmo usuário do sistema compartilham `~/.playlist-converter`. Ao trocar de app Spotify, cliente OAuth Google ou conta, uma sessão antiga pode deixar de ser compatível. Pare o backend e remova somente a sessão do provedor que deseja autorizar novamente, mantendo o JSON necessário. O projeto usa uma sessão local Google identificada como `local-user` e um arquivo Spotify, com seleção de conta feita no login.

### 13. Proteger credenciais e conferir o Git

Guarde o `.env`, o JSON Google e os arquivos de sessão apenas no ambiente local. O `.gitignore` do projeto cobre `.env`, `.playlist-converter`, `spotify_tokens*.json`, `google-client-secret*.json`, `client_secret*.json` e `google-tokens/`.

Antes de commitar, na raiz:

```powershell
git status --short
git check-ignore -- .env
git diff --cached --name-only
```

O segundo comando deve mostrar `.env`. Confira também os arquivos de credenciais que colocar dentro do repositório, usando `git check-ignore -- CAMINHO_DO_ARQUIVO`. Os caminhos recomendados para JSON e sessões ficam na pasta do usuário.

O Git continua rastreando arquivos que já foram adicionados antes de uma regra de ignore. Se uma credencial real foi publicada, revogue ou rotacione a credencial no provedor e examine o histórico do repositório. Remover o arquivo da última versão ainda deixa cópias em commits anteriores. Compartilhe erros com chaves, tokens e links de autorização ocultos.

### 14. Resolver problemas comuns

| Sintoma | O que conferir |
| --- | --- |
| Docker API/daemon indisponível | Abra Docker Desktop e espere o motor iniciar. Execute `docker info` para conferir a conexão |
| `POSTGRES_PASSWORD is missing a value` | Crie o `.env` na mesma pasta de `docker-compose.yaml` e preencha a variável |
| `password authentication failed` | Use a senha do volume PostgreSQL existente. Alterar o `.env` depois da primeira criação do banco exige sincronizar a senha no banco |
| Porta 5432 ocupada | Pare o outro banco que ocupa a porta antes de subir este |
| `mvn` desconhecido | Use Run Anything do IntelliJ ou instale Maven no PATH |
| Erro do compilador/Javac/Lombok ao abrir o clone | Selecione JDK 21 no Project SDK e na execução. Reimporte o Maven |
| Porta 8080 ou 4200 ocupada | Pare a cópia anterior do backend ou do Angular. Para encerrar o Angular, use Ctrl+C no terminal que o iniciou |
| Redirect URI inválida no Spotify | Compare a URI cadastrada e `SPOTIFY_REDIRECT_URI`. Use `http://127.0.0.1:8888/callback` |
| Spotify pede login, mas recusa a conta | Confira Users Management do app cujo Client ID está no `.env`, o proprietário Premium e as permissões da playlist |
| JSON Google ausente | Confira `%USERPROFILE%\.playlist-converter\google-client-secret.json`, o nome e a extensão |
| JSON de tipo incorreto | Crie um cliente OAuth **App para computador** e baixe o JSON dele |
| Google bloqueia a autorização | Confira o projeto do cliente OAuth, Público-alvo e Usuários de teste, e a conta selecionada |
| Sessão expirada ou autorização revogada | Tente novamente para permitir renovação ou novo consentimento. Falhas técnicas exigem conferir a conexão e a causa no backend |
| Permissões insuficientes | Autorize os escopos solicitados e use uma conta com acesso à origem e permissão para editar o destino |
| Chave YouTube recusada | Confira `YOUTUBE_API_KEY`, a ativação de YouTube Data API v3 e as restrições da chave no projeto Google correto |
| `429` | Confira `code` na resposta. Aguarde o prazo informado pelo provedor, quando disponível |
| `502` ao gerar prévia | Abra Inspecionar → Rede/Network → requisição → Resposta/Response e veja `code`. Pode haver falha de leitura ou busca em uma plataforma |
| Callback local não conecta | Confira se o backend continua ativo e se a autorização ainda está dentro do prazo. Recomece o fluxo se ele expirou |

O JSON OAuth é necessário para escrever no YouTube e para ler origens privadas. Um `502` na prévia Spotify → YouTube pode ocorrer antes dessa etapa, durante a leitura Spotify ou busca YouTube. Nesse caso, o código da resposta ajuda a localizar a operação que falhou.

Para conferir a comunicação, abra [o status direto do backend](http://127.0.0.1:8080/api/status) e [o status via Angular](http://localhost:4200/api/status). O backend deve estar ativo para os dois funcionarem. Se apenas o segundo falhar, confira o proxy e a execução do frontend.

### 15. Testes e consulta ao banco

Na raiz, execute `mvn test` pelo terminal com Maven ou pelo Run Anything do IntelliJ. Na pasta `frontend`, execute:

```powershell
npm.cmd test -- --watch=false
npm.cmd run build
```

Os testes usam mocks e H2 temporário. Para consultar o PostgreSQL com DBeaver, use host `127.0.0.1`, porta `5432`, banco e usuário `musicswapix`, e a senha do seu `.env`. O banco contém `sync_job`, `sync_run` e `synced_track`.

No perfil `h2`, o console informa o endereço nos logs. O caminho JDBC local é `jdbc:h2:~/.playlist-converter/data.db`.

## 📌 Limitações conhecidas

### Acesso e quotas

O acesso às playlists depende das permissões da conta, dos escopos e das regras vigentes do provedor. O serviço informa acesso negado quando recebe HTTP 403; esse código sozinho não determina a causa. Salvar uma playlist na biblioteca não deve ser apresentado como garantia de acesso pela API.

O projeto trabalha com as quotas padrão; solicitar aumento está fora do escopo atual. Uma playlist inédita com 280 músicas pode exigir 280 pesquisas no YouTube e três janelas de quota padrão. Os detalhes e decisões estão em [docs/PROJECT_LIMITATIONS.md](docs/PROJECT_LIMITATIONS.md).

Quando recebe `quotaExceeded`, o YouTube lança uma exceção específica. No Spotify, HTTP 429 preserva o prazo `Retry-After` quando positivo. A biblioteca atual não preserva o motivo estruturado `QUOTA_EXCEEDED`, portanto o projeto não consegue distinguir os dois tipos de limitação com segurança. Os serviços encerram a operação sem retornar uma lista parcial como resultado completo; espera automática e retomada ainda estão pendentes.

### Outras limitações

- Em um teste de YouTube → Spotify, a prévia leu 216 faixas e encontrou 204 correspondências. A confirmação adicionou 200 faixas em dois lotes de 100, pulou 4 correspondências repetidas e deixou 12 faixas sem correspondência. Esse resultado corresponde a uma execução com a configuração testada.
- A busca pode omitir uma faixa existente. A prévia permite revisar alternativas disponíveis e pular faixas.
- A prévia expira e fica em memória. Se a inserção for interrompida, confira o destino antes de tentar novamente.

- O Spotify limita apps em Development Mode a 5 usuários autorizados e exige conta Premium para desenvolvimento — isso afeta quem consegue testar o projeto sem passar pelo processo de extensão de quota da Spotify.
- O matching entre faixas é baseado em similaridade de texto (título/artista); casos como remixes, covers ou versões ao vivo podem exigir revisão manual quando a confiança do match for baixa.

## 📄 Licença

Este projeto está sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

Desenvolvido por [Jorge] como projeto de portfólio, aplicando integração com APIs REST, OAuth, persistência de dados e arquitetura orientada a interfaces em Java.
