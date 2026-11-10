# Deploy no Coolify + integração no CRM (módulo Contábil)

Implanta **Extratos de Aplicações** na VPS (Coolify) e registra como sistema do
setor **Contábil** no CRM Mendonça Galvão. O CRM abre o sistema em iframe.

Arquitetura em produção: um único serviço público (**frontend**, nginx) com o
domínio `aplicacoes.nucleodigital.cloud`; ele faz proxy de `/api` para o
**backend** (Spring Boot) pela rede interna; **postgres** e o **storage**
(volume) ficam internos. Ver [`../docker-compose.yml`](../docker-compose.yml).

---

## 1. Repositório no GitHub

O repositório local já está versionado e com o remote configurado para
`git@github.com:ndmg-dev/extratos-aplicacoes.git`. Crie o repositório **vazio**
`extratos-aplicacoes` na organização `ndmg-dev` (sem README/licença) e envie:

```bash
cd /home/arthur/Documentos/APLICACOES_APP
git push -u origin main
```

> O `.env` e o `client_secret_*.json` estão no `.gitignore` e **não** são enviados.

## 2. Criar o recurso no Coolify

1. Painel do Coolify → **Add New Resource** → **Git Repository** → selecione
   `ndmg-dev/extratos-aplicacoes` (branch `main`).
2. **Build Pack: Docker Compose**. O Coolify lê o `docker-compose.yml` da raiz.
3. Deixe o build criar os três serviços: `postgres`, `backend`, `frontend`.

## 3. Domínio

1. Aponte o DNS: registro **A** de `aplicacoes.nucleodigital.cloud` → IP da VPS.
2. No Coolify, serviço **frontend** → aba **Domains** → `https://aplicacoes.nucleodigital.cloud`
   (porta **80**). O Coolify emite o certificado TLS automaticamente.
3. **Não** exponha `backend` nem `postgres` (ficam só na rede interna).

## 4. Variáveis de ambiente (aba Environment Variables)

Preencha conforme [`../.env.example`](../.env.example). Mínimo obrigatório:

| Variável | Valor |
|---|---|
| `DB_PASSWORD` | senha forte (`openssl rand -base64 36`) |
| `ANTHROPIC_API_KEY` | chave da API Anthropic |
| `EMAIL_CLIENT_ID` / `EMAIL_CLIENT_SECRET` | credenciais OAuth do Google |
| `EMAIL_REDIRECT_URI` | `https://aplicacoes.nucleodigital.cloud/api/email/oauth/callback` |
| `EMAIL_QUERY` | `subject:"EXTRATO - APLICAÇÕES" has:attachment filename:pdf` |
| `PUBLIC_URL` | `https://aplicacoes.nucleodigital.cloud` |

(`DB_NAME`/`DB_USER` têm default `extratos`; `ANTHROPIC_MODEL` default `claude-sonnet-5-5`.)

## 5. Deploy

Clique em **Deploy**. O Flyway cria o schema no primeiro boot. Verifique:
`https://aplicacoes.nucleodigital.cloud/api/lancamentos/summary` deve responder JSON.

## 6. Autorizar o Gmail (uma vez)

1. No **Google Cloud** → Credenciais → cliente OAuth → **Authorized redirect URIs**:
   adicione `https://aplicacoes.nucleodigital.cloud/api/email/oauth/callback`.
   (A Gmail API já está ativada; mantenha o app **publicado** para o refresh token
   não expirar.)
2. Abra `https://aplicacoes.nucleodigital.cloud` → **Lançamentos** → botão
   **Autorizar conta** na barra de ingestão (ou acesse `GET /api/email/oauth/url`).
3. Autorize com `docs.contabil@mendoncagalvao.com.br`. A barra deve ficar verde.

## 7. Registrar no CRM (módulo Contábil)

Rode o script contra o banco de **produção** do CRM (`crm_mendonca_galvao`):

```bash
psql "$CRM_DATABASE_URL" -f deploy/registrar-sistema-crm.sql
```

(ou cole o conteúdo de [`../deploy/registrar-sistema-crm.sql`](../deploy/registrar-sistema-crm.sql)
no pgAdmin do CRM). Ele insere o sistema no setor **CONTABIL** apontando para
`https://aplicacoes.nucleodigital.cloud`.

## 8. Conferir

Acesse o CRM → setor **Contábil** → **Extratos de Aplicações**. Deve abrir o
sistema em iframe (o nginx já libera `frame-ancestors` para os domínios
`*.mendoncagalvao.com.br` e `*.nucleodigital.cloud`).

---

## Operação

- **Backup**: o volume `pgdata` (Postgres) e o volume `storage` (PDFs arquivados)
  são persistentes. Configure backup do Postgres no Coolify.
- **Logs**: por documento, no backend (nunca registram a chave nem o conteúdo).
- **Reprocessar/ingestão**: polling automático a cada 5 min (`EMAIL_POLL_INTERVAL_MS`).
