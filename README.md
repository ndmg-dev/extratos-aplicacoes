# Extratos de Aplicações Financeiras

Aplicação web que extrai dados de **extratos de aplicações financeiras em PDF** (CDB, RDB, LCI,
LCA, fundos, poupança) de bancos brasileiros, valida o **fechamento de saldo** e persiste os
lançamentos. Reimplementa, em interface web, o pipeline que existia no n8n.

A especificação completa (fonte de verdade) está em
[`SPECS-extratos-aplicacoes.md`](SPECS-extratos-aplicacoes.md). O prompt, o esquema da tool
`registrar_extrato` e a lógica de normalização/validação foram portados **verbatim** do workflow
original (Apêndices A, B e C da spec).

> **A chamada ao Claude é feita somente no backend.** A chave `ANTHROPIC_API_KEY` nunca aparece
> no frontend, em respostas da API nem no bundle do cliente.

## Arquitetura

```
frontend (React/Vite/TS) ──HTTP──► backend (Spring Boot)
                                      ├─ ClaudeClient ─────► api.anthropic.com
                                      ├─ ValidacaoService (porte verbatim)
                                      ├─ StorageService ───► filesystem | MinIO/S3
                                      └─ Repos (JPA) ──────► PostgreSQL
```

- **Backend:** Java 21 + Spring Boot 3 (Web, Data JPA, Bean Validation, Flyway), PostgreSQL,
  Apache POI (XLSX), AWS SDK v2 (S3/MinIO). Gradle (Kotlin DSL).
- **Frontend:** React 18 + Vite + TypeScript, TanStack Query, React Router, Tailwind CSS,
  react-dropzone.
- **Monorepo:** [`/backend`](backend), [`/frontend`](frontend), [`/docker`](docker),
  [`/docs`](docs) (+ a spec na raiz).

## Pré-requisitos

- **JDK 21** (ex.: Temurin 21).
- **Node 20+** e npm.
- **Docker** + **Docker Compose** (para Postgres e MinIO).
- Uma **chave da API Anthropic** (`ANTHROPIC_API_KEY`).

## Setup rápido (desenvolvimento)

1. **Variáveis de ambiente**
   ```bash
   cp .env.example .env
   # edite .env e preencha ANTHROPIC_API_KEY
   ```

2. **Suba a infraestrutura (Postgres + MinIO)** — a partir de `/docker`:
   ```bash
   cd docker
   docker compose up -d        # sobe postgres, minio e cria o bucket
   ```
   - Postgres: `localhost:5432` (db/usuário/senha padrão `extratos`)
   - MinIO: API `localhost:9000`, console `localhost:9001` (login `minioadmin`/`minioadmin`)

3. **Backend** — a partir de `/backend`:
   ```bash
   export ANTHROPIC_API_KEY=sk-ant-...      # ou use o .env
   ./gradlew bootRun
   ```
   Sobe em `http://localhost:8080`. O Flyway cria o schema automaticamente.
   Em dev o storage padrão é **filesystem** (`STORAGE_TYPE=fs`, pasta `data/arquivos`); para usar
   o MinIO, defina `STORAGE_TYPE=s3` e as credenciais (ver abaixo).

4. **Frontend** — a partir de `/frontend`:
   ```bash
   npm install
   npm run dev
   ```
   Abre em `http://localhost:5173` e faz proxy de `/api` para o backend (`:8080`).

## Stack completa em containers (opcional)

Para rodar backend + frontend também em containers (além de Postgres + MinIO):

```bash
cd docker
ANTHROPIC_API_KEY=sk-ant-... docker compose --profile app up --build
```
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`

Neste modo o backend usa o MinIO (`STORAGE_TYPE=s3`) automaticamente.

## Testes

A partir de `/backend`:
```bash
./gradlew test
```
- **Unidade:** `ValidacaoService` (Caixa/Santander/Sicoob + casos de borda), parsing do Claude,
  nomeação de arquivo, orquestração da extração, controllers (MockMvc).
- **Integração:** persistência com **Testcontainers** (Postgres) — pulados automaticamente se o
  Docker não estiver acessível ao Testcontainers.

Para rodar os testes de integração contra um Postgres externo (ex.: ambientes onde o
Testcontainers não inicializa):
```bash
docker run -d --name pg -e POSTGRES_DB=extratos -e POSTGRES_USER=extratos \
  -e POSTGRES_PASSWORD=extratos -p 55432:5432 postgres:16-alpine
IT_DB_URL=jdbc:postgresql://localhost:55432/extratos IT_DB_USER=extratos IT_DB_PASSWORD=extratos \
  ./gradlew test
```

Frontend (type-check + build): `cd frontend && npm run build`.

## Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | **Obrigatória.** Chave da API Anthropic (só no backend). |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | Modelo usado na extração. |
| `ANTHROPIC_MAX_TOKENS` | `4096` | `max_tokens` da requisição. |
| `ANTHROPIC_TIMEOUT_MS` | `180000` | Timeout por tentativa. |
| `ANTHROPIC_MAX_TENTATIVAS` | `3` | Tentativas com backoff. |
| `ANTHROPIC_BACKOFF_MS` | `5000` | Espera entre tentativas. |
| `ANTHROPIC_VERSION` | `2023-06-01` | Header `anthropic-version`. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/extratos` | JDBC do Postgres. |
| `DB_USER` / `DB_PASSWORD` | `extratos` / `extratos` | Credenciais do banco. |
| `STORAGE_TYPE` | `fs` | `fs` (filesystem) ou `s3` (MinIO/S3). |
| `STORAGE_BASE_PATH` | `data/arquivos` | Pasta base (quando `fs`). |
| `STORAGE_BUCKET` | `extratos` | Bucket (quando `s3`). |
| `STORAGE_ENDPOINT` | — | Endpoint S3/MinIO (ex.: `http://localhost:9000`). |
| `STORAGE_REGION` | `us-east-1` | Região S3. |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | — | Credenciais do storage. |
| `STORAGE_PATH_STYLE` | `true` | Path-style access (necessário no MinIO). |
| `UPLOAD_MAX_FILE_MB` | `20` | Tamanho máximo por PDF. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origens liberadas (CSV). |
| `EXTRACAO_THROTTLE_MS` | `0` | Intervalo entre PDFs no lote (0 = sem throttle). |

## API (resumo)

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/extratos/upload` | multipart `files` (1..N PDFs) → documentos + lançamentos + resumo. |
| `GET` | `/api/lancamentos` | lista paginada; filtros `status`, `banco`, `competencia`, `cnpj`, `q`. |
| `GET` | `/api/lancamentos/summary` | contagem por status e soma por competência. |
| `GET` | `/api/lancamentos/export?format=csv\|xlsx` | exporta as linhas filtradas. |
| `GET` | `/api/documentos/{id}` | metadados + lançamentos (`?incluirRaw=true` inclui o JSON do Claude). |
| `GET` | `/api/documentos/{id}/pdf` | baixa o PDF arquivado. |
| `POST` | `/api/documentos/{id}/reprocessar` | reexecuta extração/validação. |

## Decisões e invariantes

- **Dinheiro = `BigDecimal` / `NUMERIC(18,2)`**, nunca `double`. Arredondamento `HALF_UP` a 2
  casas (replica `r2` do workflow). Valores ausentes permanecem `null` (salvo exceções da spec).
- **Upsert por `chave`** (`cnpj|banco|conta|identificador|competencia`): reenvio atualiza a linha,
  não duplica. `sha256` evita reprocessar o mesmo PDF sem intenção.
- **Tolerância de fechamento `R$ 0,05`** e toda a lógica de status (`OK`/`REVISAR`/`SEM_DETALHE`/
  `ERRO`) portadas verbatim do Apêndice C.
- Export CSV/XLSX respeita a **ordem de colunas da §4.2**; CSV com `;` e vírgula decimal (Excel
  pt-BR).

## Extensões futuras (não implementadas no MVP)

Pontos de extensão preparados (ver §13 da spec): ingestão por e-mail (IMAP/Gmail) reusando o
`ExtracaoService`; cadastro de contas/clientes para resolver CNPJ por banco+agência+conta;
autenticação/multi-tenant.
