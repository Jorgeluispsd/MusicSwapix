# API local de conversão

Por padrão, a aplicação atende em `http://127.0.0.1:8080`. O fluxo disponível converte playlists do Spotify para o YouTube.

| Método e rota | Entrada | Sucesso |
| --- | --- | --- |
| `GET /api/status` | Sem corpo | `200` com `{"status":"UP"}` |
| `GET /api/conversions/destinations` | Parâmetro `sourcePlaylist` com link ou ID do Spotify | `200` com uma lista de `destinationPlaylistId` |
| `POST /api/conversions/previews/new` | JSON com `sourcePlaylist`, `destinationName` e `visibility` opcional | `200` com a prévia |
| `POST /api/conversions/previews/existing` | JSON com `sourcePlaylist` e `destinationPlaylist` | `200` com a prévia |
| `POST /api/conversions/previews/{previewId}/confirm` | UUID devolvido na prévia, sem corpo | `200` com o resultado e `destinationPlaylistUrl` |
| `DELETE /api/conversions/previews/{previewId}` | UUID devolvido na prévia, sem corpo | `204` sem corpo, inclusive para uma prévia já indisponível |

## Testar no Postman

1. Para consultar destinos anteriores, use `GET /api/conversions/destinations`. Na aba **Params**, preencha `sourcePlaylist` com o link ou ID do Spotify. O histórico pode retornar vários destinos ou `[]`. A consulta usa o banco local. A disponibilidade no YouTube é verificada ao gerar a prévia para um destino selecionado.
2. Escolha uma das rotas de prévia. Na aba **Body**, selecione **raw → JSON** e envie o corpo correspondente abaixo.
3. Revise `matches`, `notFound`, `alreadyPresent` e `newTrackCount`. Copie o `previewId` da resposta.
4. Use esse UUID para confirmar ou cancelar. A prévia expira em 10 minutos por padrão e fica indisponível após reiniciar a aplicação. A confirmação consome o UUID antes de iniciar a execução.

Para criar um destino novo:

```json
{
  "sourcePlaylist": "LINK_OU_ID_DO_SPOTIFY",
  "destinationName": "Minha playlist convertida",
  "visibility": "PRIVATE"
}
```

As visibilidades aceitas são `PRIVATE`, `PUBLIC` e `UNLISTED`. O padrão é `PRIVATE`.

Para usar um destino existente:

```json
{
  "sourcePlaylist": "LINK_OU_ID_DO_SPOTIFY",
  "destinationPlaylist": "LINK_OU_ID_DO_YOUTUBE"
}
```

Gerar a prévia consulta as plataformas quando necessário. Confirmar autoriza a escrita. Cancelar descarta a prévia pendente e uma confirmação posterior recebe `404`. Uma conversão que já começou segue seu fluxo.

## Respostas de erro

Para os erros tratados, a API devolve `status`, `code`, `message`, `path` e `fieldErrors`. Use `code` para identificar o caso na interface.

| HTTP | Código | Situação |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR`, `INVALID_REQUEST_BODY`, `INVALID_PLAYLIST_INPUT`, `INVALID_PATH_PARAMETER` | Entrada inválida |
| `403` | `AUTHORIZATION_DENIED` | Acesso recusado durante a autorização |
| `404` | `PREVIEW_UNAVAILABLE` | Prévia ausente, expirada, cancelada ou consumida |
| `409` | `PREVIEW_CHANGED` | Itens que estavam presentes no destino deixaram de estar disponíveis nele |
| `422` | `NO_TRACKS_TO_INSERT` | Prévia para criação de playlist sem músicas para inserir |
| `429` | `YOUTUBE_QUOTA_EXCEEDED`, `SPOTIFY_RATE_LIMITED` | Limite do provedor. O Spotify pode incluir `Retry-After` |
| `500` | `SYNC_STATE_STORAGE_FAILED` | Falha de acesso ao histórico local |
| `502` | `SPOTIFY_PLAYLIST_READ_FAILED`, `YOUTUBE_PLAYLIST_READ_FAILED`, `YOUTUBE_SEARCH_FAILED`, `YOUTUBE_PLAYLIST_CREATION_FAILED`, `YOUTUBE_TRACK_INSERTION_FAILED` | Falha em uma operação da plataforma |
| `504` | `AUTHORIZATION_TIMEOUT` | O prazo para autorizar acabou |

Se a confirmação falhar durante a escrita ou o registro do histórico, confira a playlist no YouTube antes de gerar outra prévia. Parte da escrita pode ter sido concluída.
