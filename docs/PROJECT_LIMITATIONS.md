# Limitações e decisões de escopo

Registro de decisões do projeto em 10/10/2026. O README apresenta as funcionalidades e os próximos passos.

## Objetivo e limites do escopo

O objetivo é replicar uma playlist disponível apenas em uma plataforma para a outra. Não pressupomos que o usuário já possua uma playlist equivalente no destino.

Decisão: trabalhar com as quotas padrão dos provedores. Solicitar aumento de quota fica fora do escopo atual. O projeto não controla esses limites e não garante converter qualquer playlist inédita grande em uma única execução.

## YouTube: leitura não é pesquisa

A documentação consultada informa quota padrão de 100 chamadas diárias de `search.list`, separada das 10.000 unidades diárias compartilhadas pelos demais endpoints. `playlistItems.list` custa 1 unidade por página; `playlistItems.insert` custa 50 unidades por chamada. Os limites efetivos devem ser conferidos no Google Cloud Console. A renovação diária ocorre à meia-noite do horário do Pacífico.

No código atual, a leitura usa páginas de até 50 itens: ler 250 itens em cinco páginas custa 5 unidades, sem consumir as 100 pesquisas. A busca pode usar uma segunda consulta com o título simplificado. Por isso, o total de buscas pode superar o número de faixas. Repetir a mesma consulta também consome quota.

Caso real: playlist Spotify com 280 itens. Se cada item exigir uma pesquisa inédita no YouTube, serão necessárias 280 pesquisas e pelo menos três janelas diárias padrão (100 + 100 + 80), considerando quota integral disponível e nenhuma busca adicional. Isso não significa necessariamente 72 horas: depende do horário de renovação. A barreira está na descoberta dos equivalentes, não na leitura da origem. A criação e inserção de itens têm custos adicionais próprios.

Reutilizar correspondências confirmadas e candidatos conhecidos pode reduzir pesquisas futuras, sujeito às políticas de armazenamento e atualização dos provedores. Não resolve garantidamente a primeira conversão de uma playlist arbitrária. Buscar por artista ou álbum é uma hipótese a avaliar, sem garantia de cobertura.

`videos.list` consulta vários vídeos cujos IDs já são conhecidos; não descobre equivalentes em lote a partir de títulos e artistas do Spotify. Retomada de análise e reutilização de resultados ainda são funcionalidades planejadas.

## Spotify

O limite de frequência considera uma janela móvel de 30 segundos. Ao atingir o limite, a API retorna HTTP 429 e normalmente fornece `Retry-After`. Aplicativos em desenvolvimento também têm quotas compartilhadas por grupos de endpoints e conta de desenvolvedor, podendo retornar `QUOTA_EXCEEDED`. Não assumir valores ou prazos universais de renovação que o provedor não publica.

## Comportamento implementado — 13/10/2026

O YouTube identifica `quotaExceeded` e lança `YoutubeQuotaExceededException`. O Spotify transforma HTTP 429 em `SpotifyRequestLimitException`, preservando a causa e `Retry-After` quando positivo; sem prazo válido, o campo fica nulo. Os serviços interrompem a operação sem repetição imediata.

A biblioteca Spotify 10.0.0 usada no projeto não preserva o motivo estruturado `QUOTA_EXCEEDED`. Portanto, o código atual não distingue com segurança quota esgotada de limite de frequência. Espera automática, tentativas limitadas e retomada são decisões pendentes da Fase 8; não estão implementadas nos serviços atuais.

## Qualidade das correspondências

A pontuação do matcher representa semelhança, não probabilidade de acerto nem garantia de identidade do áudio. Covers, remixes, versões ao vivo e diferenças de metadados podem produzir correspondências incorretas. Na prévia, o usuário pode escolher entre as alternativas disponíveis ou pular a faixa.

## Fontes

Valores sujeitos a alterações; documentação consultada em 10/10/2026:

- [Quotas do YouTube](https://developers.google.com/youtube/v3/determine_quota_cost)
- [Erros do YouTube](https://developers.google.com/youtube/v3/docs/errors)
- [Consulta de vídeos por IDs](https://developers.google.com/youtube/v3/docs/videos/list)
- [Limites de frequência do Spotify](https://developer.spotify.com/documentation/web-api/concepts/rate-limits)
- [Modos de quota do Spotify](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)
