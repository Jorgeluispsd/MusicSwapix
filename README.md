# MusicSwapix

Ferramenta para converter playlists entre **Spotify e YouTube**, com prévia e revisão de faixas antes da confirmação.

## 💡 Motivação

Este projeto nasceu de uma necessidade pessoal: em diversas ocasiões, como eventos ou encontros com amigos, eu ouvia músicas incríveis que me marcavam, mas por não ter Spotify na época, ficava limitado a apenas ouvi-las naquele momento. A solução ideal seria ter as mesmas playlists em diferentes aplicativos de música, sem o trabalho tedioso de pesquisar cada música manualmente.

O objetivo é permitir uma conversão direta e eficiente de playlists entre Spotify e YouTube Music, eliminando a necessidade de reconstruir playlists do zero quando você decide mudar de plataforma de streaming ou simplesmente quer ter suas playlists favoritas disponíveis em mais de um serviço.

## 🚧 Status do projeto

Em desenvolvimento — construído em fases, cada uma validada antes de avançar para a próxima. As funcionalidades implementadas e os próximos passos estão resumidos abaixo.

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

O conversor lê a playlist de origem e gera uma prévia com as correspondências, as músicas já presentes e as faixas sem correspondência. Depois da confirmação, cria a playlist ou adiciona os vídeos encontrados ao destino escolhido. O histórico registra os vínculos entre playlists e as inserções confirmadas.

```
model/       -> Song, MatchResult — estruturas de dados comuns aos dois serviços
service/     -> MusicService — contrato comum entre Spotify e YouTube
controller/  -> endpoints da API local
converter/   -> análise, prévias, execução e histórico de conversões
spotify/     -> implementação do lado Spotify
youtube/     -> implementação do lado YouTube
matcher/     -> lógica de similaridade para encontrar a música equivalente
state/       -> histórico de sincronizações e controle de duplicata (PostgreSQL ou H2)
state/database/ -> H2DatabaseInitializer e H2SyncStateStore
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

## 🚀 Como rodar localmente

### Pré-requisitos

- Java 21
- Node.js 24 e npm
- Docker com Compose
- Maven
- Conta Spotify **Premium** (exigido pelo Development Mode da Spotify para acesso à API)
- Conta Google (para gerar uma chave da YouTube Data API v3)

### Credenciais do Spotify

1. Entre no [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) e clique em **Create app**. Preencha o nome e a descrição, selecione **Web API** e aceite os termos.
2. Cadastre o **callback obrigatório do Spotify** no campo **Redirect URIs** do app. Se o app já estiver criado, abra **Settings → Edit**. Cole o endereço abaixo, clique em **Add** e salve.

   ```text
   http://127.0.0.1:8888/callback
   ```

   No `.env` da raiz do MusicSwapix, deixe a linha exatamente assim:

   ```dotenv
   SPOTIFY_REDIRECT_URI=http://127.0.0.1:8888/callback
   ```

   Use `127.0.0.1`, a porta `8888` e o caminho `/callback` nos dois lugares. Mantenha o MusicSwapix rodando enquanto autoriza no navegador. É para esse endereço que o Spotify envia o navegador após a autorização.
3. Abra **Settings** no app criado. Copie **Client ID** para `SPOTIFY_CLIENT_ID` e use **View client secret** para copiar o segredo para `SPOTIFY_CLIENT_SECRET`.
4. Em **Users Management**, verifique se a conta que fará o login está autorizada. Para testar com outra conta em modo de desenvolvimento, adicione o nome e o e-mail associado à conta Spotify.

[Criação de apps](https://developer.spotify.com/documentation/web-api/concepts/apps) · [Regras de redirect URI](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)

### Chave da API do YouTube

1. Abra o [Google Cloud Console](https://console.cloud.google.com/) e crie ou selecione um projeto no seletor do topo.
2. Em **APIs e serviços → Biblioteca**, pesquise **YouTube Data API v3**, abra a página da API e clique em **Ativar**.
3. No mesmo projeto, abra **APIs e serviços → Credenciais → Criar credenciais → Chave de API**. Em **Restrições de API**, selecione **YouTube Data API v3** e salve.
4. Copie o valor da chave para `YOUTUBE_API_KEY` no `.env`. O MusicSwapix usa essa chave nas buscas e na leitura de playlists públicas.

[Guia de chaves do Google Cloud](https://docs.cloud.google.com/docs/authentication/api-keys)

### Autorização Google para playlists privadas e escrita

1. No mesmo projeto, abra **Google Auth Platform**. Se for a primeira vez, clique em **Começar** e preencha o nome do app, o e-mail de suporte e o e-mail de contato. Para testar com uma conta pessoal, escolha **Externo** em **Público-alvo**.
2. Em **Público-alvo → Usuários de teste**, adicione a conta Google que você usará no MusicSwapix enquanto o app estiver em teste. Para ler uma playlist privada, use uma conta com acesso a ela.
3. Em **Clientes → Criar cliente**, escolha **App para computador**, dê um nome ao cliente e clique em **Criar**. Para esse tipo de cliente, pule o cadastro manual de callback. O MusicSwapix abre um endereço local temporário quando inicia a autorização do Google.
4. Baixe o JSON do cliente OAuth e salve-o como `~/.playlist-converter/google-client-secret.json`. No Windows, o caminho é `%USERPROFILE%\.playlist-converter\google-client-secret.json`. Crie a pasta se necessário. Mantenha o JSON fora do repositório.

O JSON é a credencial usada para pedir autorização no navegador. A chave em `YOUTUBE_API_KEY` continua sendo usada nas consultas públicas. [Guia oficial de OAuth para aplicativos instalados](https://developers.google.com/identity/protocols/oauth2/native-app)

### Setup

1. Clone o repositório:
   ```
   git clone https://github.com/Jorgeluispsd/MusicSwapix.git
   cd MusicSwapix
   ```

2. Na raiz do clone, copie `.env.example` para `.env`, ao lado de `pom.xml` e `docker-compose.yaml`. Preencha com suas credenciais e escolha uma senha local para o banco.
   ```
   SPOTIFY_CLIENT_ID=
   SPOTIFY_CLIENT_SECRET=
   SPOTIFY_REDIRECT_URI=http://127.0.0.1:8888/callback
   YOUTUBE_API_KEY=
   POSTGRES_PASSWORD='senha-local-de-exemplo'
   ```

3. No Windows, abra o Docker Desktop e aguarde o motor iniciar. No terminal, na raiz do clone (a pasta que contém o `docker-compose.yaml` e o `.env`), execute:

   ```powershell
   docker compose up -d postgres
   docker compose ps
   ```

   O PostgreSQL deve aparecer como `Up`. Se a porta `5432` já estiver ocupada por outro contêiner, pare-o antes de subir o banco deste clone. O PostgreSQL aplica a senha do `.env` ao inicializar um volume novo. Para um banco já inicializado, use a senha que está configurada nele.

4. No IntelliJ, abra diretamente a pasta clonada que contém o `pom.xml`. Aguarde a importação do Maven e selecione **Java 21** em **File → Project Structure → Project SDK**. Rode o método `main` de `PlaylistConverterApplication` com o diretório de trabalho apontando para a raiz do clone.

   Se você tem o Maven no PATH, também pode rodar da raiz pelo terminal:

   ```powershell
   mvn spring-boot:run
   ```

   Se o terminal informar que `mvn` não é reconhecido, use o botão **Run** do IntelliJ ou pressione **Ctrl duas vezes** no IntelliJ e execute `mvn spring-boot:run` pelo **Run Anything**.

5. Em outro terminal, instale as dependências do front e inicie o Angular.

   ```powershell
   cd frontend
   npm.cmd ci
   npm.cmd start
   ```

   Abra [MusicSwapix local](http://localhost:4200).

A aplicação inicia a API em `127.0.0.1:8080` por padrão. Consulte o [guia da API local](docs/API.md) para testar o fluxo no Postman. A aplicação pode abrir o navegador local para autorização e reutiliza as sessões salvas quando disponíveis.

### Testes e banco local

Execute `mvn test` para rodar a suíte. Se `mvn` não estiver no PATH, pressione Ctrl duas vezes no IntelliJ e execute `mvn test` pelo Run Anything.

Os testes automatizados usam mocks e um banco H2 temporário para validar o fluxo sem acessar as APIs externas ou o banco pessoal.

O PostgreSQL guarda histórico e vínculos no volume `postgres_data`. Para consultar pelo DBeaver, use `127.0.0.1:5432`, banco e usuário `musicswapix`, e a senha do seu `.env`. O backend cria as tabelas ao iniciar. Para abrir o H2 local, inicie o Spring com o perfil `h2`.

> ⚠️ **Nenhuma credencial deste projeto é distribuída publicamente.** Cada pessoa que for rodar o projeto precisa criar seu próprio app no Spotify e sua própria chave no Google Cloud — é assim que projetos open source que integram com APIs de terceiros funcionam. O `.env` nunca é commitado (veja `.gitignore`).

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
