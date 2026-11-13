# MusicSwapix · Front-end

Interface Angular para preparar, revisar e confirmar conversões e consultar o histórico.

## Desenvolvimento

Na pasta do front, execute:

```powershell
npm.cmd ci
npm.cmd start
```

Abra [MusicSwapix local](http://localhost:4200). O servidor de desenvolvimento encaminha as chamadas de `/api` ao backend local na porta `8080`. Siga o [guia principal](../README.md) para iniciar o PostgreSQL e o backend.

## Testes e build

```powershell
npm.cmd test -- --watch=false
npm.cmd run build
```

A página de conversão controla o formulário e as requisições. O módulo `track-review.ts` aplica as escolhas às faixas exibidas e calcula os totais. Os clientes em `src/app/core/api` enviam as requisições HTTP.
