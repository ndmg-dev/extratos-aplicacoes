# Especificação — Extração de Extratos de Aplicações Financeiras

> Documento de especificação para construção da aplicação com **Claude Code**.
> Backend em **Java (Spring Boot)**, frontend em **React + Vite + TypeScript**.
> A aplicação reimplementa, em interface web, o pipeline hoje existente no n8n
> (`Extratos de aplicações financeiras`): ler extratos de aplicações financeiras,
> extrair os dados com o Claude, validar o fechamento de saldo e persistir os lançamentos.

---

## 1. Visão geral e objetivo

Uma aplicação web em que o usuário faz **upload de PDFs** de extratos de aplicações
financeiras (CDB, RDB, LCI, LCA, fundos, poupança) de bancos brasileiros (Caixa,
Santander, Sicoob e outros). A aplicação:

1. Envia cada PDF ao **Claude**, que extrai os dados de forma estruturada por meio de uma
   *tool* de esquema fixo (`registrar_extrato`).
2. **Normaliza e valida** os dados, conferindo o fechamento de saldo
   (`saldo_inicial + aplicações + rendimento − resgates = saldo_final`), a soma das
   aplicações × total informado, e a presença de CNPJ.
3. Atribui um **status** a cada lançamento: `OK`, `REVISAR`, `SEM_DETALHE` ou `ERRO`.
4. **Persiste** os lançamentos (um por aplicação/nota/produto) e arquiva o PDF original.
5. Exibe os lançamentos em uma **tabela** com filtros por status/banco/competência, um
   **resumo** com contagens, e permite **exportar** (CSV/XLSX) — substituindo o Google
   Sheets do workflow.

O objetivo é manter **resultados idênticos** ao workflow: o prompt, o esquema da *tool* e a
lógica de validação devem ser portados **verbatim** (ver Apêndices A, B e C).

### Diferenças em relação ao workflow (entrada/saída viram interface)

| Workflow (n8n) | Aplicação web |
|---|---|
| Gatilho Gmail (polling horário) | **Upload manual** de PDFs na tela (drag & drop) |
| Salvar no Google Drive (`BANCO_AAAA-MM_CONTA.pdf`) | **Object storage** (filesystem/MinIO/S3) com a mesma convenção de nome |
| Gravar no Google Sheets (`appendOrUpdate` por `chave`) | **Banco de dados** (upsert por `chave`) + export CSV/XLSX |
| Marcar e-mail como lido | — (não se aplica) |
| Chamada HTTP à API Anthropic (chave em credential) | Chamada feita **no backend**, chave em variável de ambiente |

> A chamada ao Claude **tem que ser feita pelo backend**. Nunca exponha a chave da API no
> frontend/navegador.

---

## 2. Escopo

**No escopo (MVP):**
- Upload de 1..N PDFs por requisição.
- Extração via Claude + validação + persistência + exibição + export.
- Download do PDF original arquivado.
- Reprocessamento de um documento.
- Filtros, busca e resumo por status.

**Fora do escopo do MVP (deixar preparado como extensão — ver §13):**
- Ingestão automática por e-mail (IMAP/Gmail).
- Autenticação/multiusuário/multi-tenant.
- Cadastro de contas/clientes para resolver CNPJ por banco+agência+conta (o workflow já
  deixa um `TODO` para isso em "Preparar documento").

---

## 3. Arquitetura

```
┌──────────────────────────┐        ┌───────────────────────────────────────────┐
│   Frontend (React/Vite)  │  HTTP  │              Backend (Spring Boot)          │
│                          │ ─────► │                                             │
│  • Dropzone de upload    │        │  UploadController ─► ExtracaoService         │
│  • Tabela de lançamentos │ ◄───── │        │                 │                   │
│  • Filtros / resumo      │  JSON  │        │                 ├─► ClaudeClient ───┼──► api.anthropic.com
│  • Export CSV/XLSX       │        │        │                 ├─► ValidacaoService│
└──────────────────────────┘        │        │                 ├─► StorageService ─┼──► FS / MinIO / S3
                                     │        │                 └─► LancamentoRepo ─┼──► PostgreSQL
                                     │  LancamentoController, ExportController      │
                                     └───────────────────────────────────────────┘
```

**Stack backend**
- Java 21, Spring Boot 3.x.
- Spring Web (REST), Spring Data JPA, Bean Validation.
- HTTP client: `RestClient`/`WebClient` do Spring (com retry e timeout).
- JSON: Jackson.
- Banco: PostgreSQL (dev com Docker Compose; migrações com Flyway).
- Storage: abstração `StorageService` com implementações `FilesystemStorage` (default dev)
  e `S3Storage`/MinIO (produção).
- Export XLSX: Apache POI; CSV nativo.
- Testes: JUnit 5, Mockito, Testcontainers (Postgres), WireMock (mock da API Anthropic).

**Stack frontend**
- React 18 + Vite + TypeScript.
- TanStack Query (dados), React Router.
- Tailwind CSS (ou CSS Modules) — ver §10 para orientação de design.
- `react-dropzone` para upload; componente de tabela próprio ou TanStack Table.

**Organização do repositório (monorepo):**
```
/backend     → projeto Spring Boot (Gradle ou Maven)
/frontend    → projeto Vite/React
/docker      → docker-compose (Postgres, MinIO), Dockerfiles
/docs        → este SPECS e material auxiliar
```

---

## 4. Modelo de dados

### 4.1 Tabela `documento` (um por PDF processado)
| Coluna | Tipo | Observação |
|---|---|---|
| id | UUID PK | |
| nome_arquivo_original | varchar | nome do upload |
| nome_padronizado | varchar | `BANCO_AAAA-MM_CONTA.pdf` (ver §9) |
| banco | varchar | |
| competencia | varchar(7) | `AAAA-MM` |
| cnpj | varchar(14) | só dígitos; pode ser nulo |
| conta | varchar | |
| storage_key | varchar | caminho/chave no storage |
| tamanho_bytes | bigint | |
| sha256 | varchar(64) | para deduplicação/idempotência |
| status_processamento | varchar | `PROCESSADO`, `ERRO`, `PENDENTE` |
| stop_reason | varchar | do Claude (ex.: `max_tokens`) |
| raw_claude_json | jsonb | resposta estruturada da *tool* (auditoria) |
| documento_reconhecido | boolean | |
| total_bruto_informado | numeric(18,2) | |
| observacoes | text | |
| criado_em | timestamptz | |
| processado_em | timestamptz | |

### 4.2 Tabela `lancamento` (um por aplicação/nota/produto)
Reproduz **exatamente** as colunas que o workflow grava no Google Sheets, nesta ordem:

| Coluna | Tipo | Origem |
|---|---|---|
| chave | varchar **UNIQUE** | `cnpj|banco|conta|identificador|competencia` (upsert por aqui) |
| status | varchar | `OK` / `REVISAR` / `SEM_DETALHE` / `ERRO` |
| motivos | text | motivos de validação concatenados por `" | "` |
| competencia | varchar(7) | |
| banco | varchar | |
| cliente_nome | varchar | |
| cliente_cnpj | varchar(14) | só dígitos |
| agencia | varchar | |
| conta | varchar | |
| identificador | varchar | |
| produto | varchar | |
| data_aplicacao | varchar | mantida como string (como vem do extrato) |
| data_vencimento | varchar | |
| saldo_inicial_bruto | numeric(18,2) | |
| origem_saldo_inicial | varchar | `extrato` ou `calculado` |
| aplicacoes | numeric(18,2) | |
| resgates_brutos | numeric(18,2) | |
| rendimento_bruto | numeric(18,2) | |
| ir_retido | numeric(18,2) | |
| iof_retido | numeric(18,2) | |
| saldo_final_bruto | numeric(18,2) | |
| ir_provisionado | numeric(18,2) | |
| iof_provisionado | numeric(18,2) | |
| saldo_final_liquido | numeric(18,2) | |
| diferenca_fechamento | numeric(18,2) | |
| arquivo | varchar | nome do arquivo de origem |
| email_id | varchar | vazio na web (campo mantido por compatibilidade) |
| processado_em | timestamptz | |
| documento_id | UUID FK → documento | |

> **Dinheiro = `BigDecimal` / `NUMERIC(18,2)`**. Nunca `double`/`float`. Arredondamento a 2
> casas (`HALF_UP`), replicando `r2 = Math.round(v*100)/100` do workflow.
> Valores ausentes permanecem **`null`** (não zero), exatamente como no workflow, salvo as
> exceções explícitas da lógica (ver Apêndice C).

### 4.3 Upsert
Gravar `lancamento` com **upsert por `chave`** (equivalente ao `appendOrUpdate` do Sheets):
inserir se a chave não existe, atualizar todas as colunas se existe.

---

## 5. Fluxo de processamento (backend)

Para cada PDF recebido em `POST /api/extratos/upload`:

1. **Ler bytes**, calcular `sha256`, converter para **base64**.
   (Idempotência opcional: se já existe `documento` com mesmo `sha256`, oferecer
   reprocessar em vez de duplicar.)
2. **Montar a requisição ao Claude** com o PDF + o prompt + a *tool* (Apêndices A e B) e
   **chamar a API** (ver §6). Guardar `stop_reason` e o conteúdo.
3. **Extrair o resultado da *tool*** (`tool_use.input`); se ausente, *fallback* para JSON
   em bloco de texto (Apêndice C).
4. **Nomear e arquivar o PDF** no storage (`BANCO_AAAA-MM_CONTA.pdf`, §9) e registrar o
   `documento`.
5. **Normalizar e validar** → gerar as linhas de `lancamento` com `status`/`motivos`
   (Apêndice C — portar **verbatim** a lógica do nó "Normalizar e validar").
6. **Upsert** das linhas por `chave`.
7. Retornar o resultado do processamento (documentos + lançamentos + contagem por status).

**Modo de execução:** síncrono por requisição é aceitável para lotes pequenos. Para lotes
grandes, oferecer processamento **assíncrono** (fila/`@Async` + tabela de job) e progresso
via *polling* ou SSE. Começar síncrono; deixar a interface de serviço preparada para async.

**Throttle:** opcionalmente processar os PDFs em série com intervalo curto (o workflow usa
batch de 1 com intervalo de 1500 ms) para respeitar limites de taxa.

---

## 6. Integração com o Claude

Replica o nó HTTP `Claude - Extrair dados`:

- **Endpoint:** `POST https://api.anthropic.com/v1/messages`
- **Headers:** `x-api-key: ${ANTHROPIC_API_KEY}`, `anthropic-version: 2023-06-01`,
  `content-type: application/json`
- **Body:**
  ```json
  {
    "model": "${ANTHROPIC_MODEL}",      // default: claude-sonnet-5-5 (o mesmo do workflow)
    "max_tokens": 4096,
    "tools": [ /* registrar_extrato — Apêndice B */ ],
    "tool_choice": { "type": "auto" },
    "messages": [{
      "role": "user",
      "content": [
        { "type": "document", "source": { "type": "base64", "media_type": "application/pdf", "data": "<PDF_BASE64>" } },
        { "type": "text", "text": "<PROMPT — Apêndice A>" }
      ]
    }]
  }
  ```
- **Resiliência (igual ao workflow):** timeout **180 s**; **retry até 3 tentativas** com
  espera de 5 s entre elas; em erro, **continuar** o processamento marcando o item como
  `ERRO` (não abortar o lote).
- **Modelo configurável** via `ANTHROPIC_MODEL` (default `claude-sonnet-5-5`).
- **Parsing da resposta:** procurar o bloco `content[].type === "tool_use"` e usar `.input`.
  Se não houver, juntar os blocos de texto e extrair o primeiro objeto `{...}` como JSON
  (*fallback*). Guardar `stop_reason`.

---

## 7. API REST

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/extratos/upload` | multipart, campo `files` (1..N PDFs). Processa e retorna documentos + lançamentos + resumo. |
| `GET` | `/api/lancamentos` | lista paginada; filtros: `status`, `banco`, `competencia`, `cnpj`, `q` (busca em produto/identificador/cliente). Ordenação. |
| `GET` | `/api/lancamentos/summary` | contagem por status e totais (ex.: soma de `saldo_final_bruto` por competência). |
| `GET` | `/api/lancamentos/export?format=csv\|xlsx` | exporta as linhas filtradas, **mesma ordem de colunas** da §4.2. CSV em `;` e decimal com vírgula (Excel pt-BR). |
| `GET` | `/api/documentos/{id}` | metadados do documento + lançamentos relacionados. |
| `GET` | `/api/documentos/{id}/pdf` | baixa o PDF original arquivado. |
| `POST` | `/api/documentos/{id}/reprocessar` | reexecuta extração/validação para o documento. |

**Contratos (DTOs):** definir `LancamentoDTO` espelhando a §4.2 e `DocumentoDTO` espelhando
a §4.1 (sem `raw_claude_json` por padrão; incluir sob query flag para auditoria).
**Validação de upload:** aceitar apenas `application/pdf`; limite de tamanho configurável
(ex.: 20 MB por arquivo); rejeitar o resto com 400 e mensagem clara.
**CORS:** liberar a origem do frontend em dev.

---

## 8. Normalização e validação

Portar **verbatim** a lógica do nó "Normalizar e validar" (Apêndice C). Pontos que **não
podem** divergir:

- `TOLERANCIA = 0.05` (R$).
- `r2`: arredondar a 2 casas; `null` permanece `null`.
- `digitos`: remover tudo que não é dígito (CNPJ e conta na `chave`).
- **Uma linha por aplicação.** Sem aplicações → `REVISAR` (ou `ERRO` se não houve resposta
  estruturada).
- **CNPJ** com ≠ 14 dígitos → motivo (e, no arquivamento, pasta/marcação `_SEM_CNPJ`).
- `documento_reconhecido === false` → motivo.
- `stop_reason === "max_tokens"` → motivo ("Resposta cortada").
- **Soma** de `saldo_final_bruto` das aplicações × `total_bruto_informado`: se diferença
  `> TOLERANCIA` → motivo.
- **Saldo inicial**: se `null` e há `saldo_final_bruto` e `rendimento_bruto`, calcular
  `ini = fin - aplicacoes - rendimento + resgates` e marcar `origem_saldo_inicial =
  "calculado"`; senão `"extrato"`.
- **Fechamento** (apenas quando `detalhado !== false` e origem `extrato`):
  `diferenca = fin - (ini + aplicacoes + rendimento - resgates)`; se `|diferenca| >
  TOLERANCIA` → motivo "Saldo não fecha".
- **Status:** `detalhado === false` → `SEM_DETALHE`; senão `REVISAR` se houver qualquer
  problema que **não** comece com "Obs. do extrator", caso contrário `OK`.
- `chave = [cnpj_digitos, banco, conta_digitos, identificador, competencia].join("|")`.

> Observações do extrator (`Obs. do extrator: ...`) entram em `motivos` mas **não** rebaixam
> o status para `REVISAR`.

Implementar como `ValidacaoService` puro (sem I/O), 100% coberto por testes de unidade com
casos dos três bancos e dos casos de borda (saldo não fecha, sem CNPJ, sem aplicações,
`max_tokens`, `detalhado=false`, saldo inicial calculado).

---

## 9. Arquivamento do PDF (nomeação)

Portar a lógica do nó "Preparar documento":
- `banco` em maiúsculas, sem acentos, não-alfanumérico → `_`.
- `competencia` = `AAAA-MM` do extrato; se o Claude não identificou, usar a data atual
  (ou a data do upload) para `ano`/`mes`.
- `conta` = só dígitos.
- Nome: `BANCO_AAAA-MM_CONTA.pdf` (campos vazios são omitidos); se a leitura falhou, usar o
  nome original.
- Organização sugerida no storage: `cnpj/ano/mes/aplicacoes/<nome_padronizado>`; sem CNPJ,
  usar prefixo `_SEM_CNPJ/...`.

---

## 10. Frontend (React/Vite/TS)

**Público-alvo:** contadores e analistas financeiros conferindo extratos. A interface deve
ser **densa, legível e precisa** — ferramenta de trabalho, não landing page. Evitar o visual
"gerado por IA" (fundo creme + serifada + terracota; cartões arredondados idênticos).
Priorizar tipografia tabular para números, hierarquia clara e cores de status **funcionais**.

**Telas/estados:**
1. **Upload** — dropzone para múltiplos PDFs; fila com estado por arquivo
   (*na fila → processando → concluído/erro*); botão "Processar". Mensagens de erro úteis
   (arquivo não é PDF, excedeu tamanho). Vazio é convite à ação, não decoração.
2. **Resultados / Lançamentos** — tabela com as colunas da §4.2; **badge de status**
   (OK=verde, REVISAR=âmbar, SEM_DETALHE=cinza, ERRO=vermelho); filtros por status/banco/
   competência; busca; **cards de resumo** com contagem por status. Números formatados em
   pt-BR (`1.234,56`).
3. **Detalhe do lançamento/documento** — `motivos` completos, todos os valores, link para
   **baixar o PDF** e ação **reprocessar**.
4. **Export** — botão que baixa CSV/XLSX das linhas filtradas.

**Qualidade mínima:** responsivo, foco de teclado visível, `prefers-reduced-motion`
respeitado, contraste acessível. Dados via TanStack Query; estados de carregamento e erro
tratados explicitamente.

---

## 11. Configuração e segurança

Variáveis de ambiente (backend):
- `ANTHROPIC_API_KEY` (obrigatória; **nunca** no frontend).
- `ANTHROPIC_MODEL` (default `claude-sonnet-5-5`).
- `ANTHROPIC_MAX_TOKENS` (default `4096`), `ANTHROPIC_TIMEOUT_MS` (default `180000`).
- `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- `STORAGE_TYPE` (`fs`|`s3`), e credenciais/bucket/endpoint do storage.
- `UPLOAD_MAX_FILE_MB` (default `20`).
- `CORS_ALLOWED_ORIGINS`.

Segurança: chave só no servidor; validar tipo/tamanho de upload; sanitizar nomes de arquivo;
limitar concorrência de chamadas ao Claude; logs sem vazar o base64 dos PDFs nem a chave.

---

## 12. Requisitos não-funcionais

- **Resiliência Claude:** retry 3× / backoff 5 s / timeout 180 s; item que falha vira `ERRO`
  sem derrubar o lote.
- **Idempotência:** `chave` única (upsert); `sha256` para evitar reprocessar o mesmo PDF sem
  intenção.
- **Observabilidade:** logs estruturados por documento (tempo de extração, `stop_reason`,
  status resultante); métrica de contagem por status.
- **Testes:** unidade para `ValidacaoService` e nomeação; integração com WireMock para o
  Claude e Testcontainers para o Postgres; e2e leve do upload→tabela.
- **Build/DevEx:** `docker-compose up` sobe Postgres + MinIO; `README` com passo a passo;
  seeds/fixtures de PDFs de exemplo (se disponíveis) para teste manual.

---

## 13. Extensões futuras (deixar preparado, não implementar no MVP)

- **Ingestão por e-mail (IMAP/Gmail):** um `IngestaoService` que lê e-mails com anexo PDF e
  chama o mesmo `ExtracaoService` — recria o gatilho Gmail do workflow.
- **Cadastro de contas/clientes:** resolver `cliente_cnpj` por `banco + agência + conta`
  quando o extrato não traz o CNPJ (o nó "Preparar documento" já aponta esse `TODO`).
- **Autenticação e multi-tenant.**

---

## 14. Critérios de aceite

1. Upload de um PDF da Caixa, do Santander e do Sicoob produz lançamentos com os **mesmos
   valores e o mesmo `status`** que o workflow produziria para o mesmo arquivo.
2. Caso de "saldo não fecha" resulta em `REVISAR` com o motivo e a `diferenca_fechamento`
   preenchida.
3. Extrato consolidado (`detalhado=false`) resulta em `SEM_DETALHE`.
4. Reenvio do mesmo extrato **atualiza** a linha existente (mesma `chave`), não duplica.
5. Export CSV/XLSX tem exatamente as colunas da §4.2, na ordem, abrindo correto no Excel
   pt-BR.
6. A chave da API **não** aparece em nenhuma resposta ao frontend nem em bundle do cliente.

---

# Apêndices (conteúdo a portar verbatim do workflow)

## Apêndice A — PROMPT (texto enviado ao Claude)

> Enviar como bloco `text` após o `document`. **Não alterar o conteúdo.**

```text
Você extrai dados de extratos de aplicações financeiras (CDB, RDB, LCI, LCA, fundos, poupança) de bancos brasileiros. Leia o documento anexo e responda EXCLUSIVAMENTE chamando a ferramenta registrar_extrato, uma única vez, sem escrever texto fora dela.

REGRAS GERAIS
- Valores em reais como número decimal com ponto (1.234,56 -> 1234.56). Nunca invente valores: se não estiver no documento nem puder ser deduzido pelas regras abaixo, use null.
- Diferencie RETIDO de PROVISIONADO. Retido = IR/IOF efetivamente descontados em resgates ou vencimentos ocorridos no mês. "Provisão", "previsto" e colunas de IR/IOF de posição são PROVISIONADOS. Se não houve resgate no mês, ir_retido = 0 e iof_retido = 0.
- Saldos sempre BRUTOS (principal + rendimento, antes de IR/IOF). Saldos líquidos vão nos campos *_liquido.
- Uma entrada em "aplicacoes" por aplicação/nota/produto detalhado. Se o banco só traz o consolidado, use uma única entrada com identificador "CONSOLIDADO".
- Ignore propaganda, tarifas, pacotes de serviço, créditos contratados, índices econômicos e conta corrente (salvo a regra do Santander abaixo).
- competencia = mês de referência do extrato no formato AAAA-MM.
- Se algo não fechar ou parecer estranho, descreva em "observacoes".

CAIXA (Informativo Mensal CDB/RDB)
- Cada "No. Nota" é uma aplicação. saldo_final_bruto = Valor base + Rend bruto acum. rendimento_bruto = Rend bruto MÊS.
- O campo "Saldo em [data do mês anterior]" é LÍQUIDO: coloque-o em saldo_inicial_liquido e use saldo_inicial_bruto = null, exceto quando for 0,00 (aí saldo_inicial_bruto = 0).
- Se a Data Aplicação estiver dentro do mês do extrato, aplicacoes = Valor base.
- Provisão IR e Provisão IOF são provisionados. Retidos vêm apenas da tabela "Resgates Efetivados no mês" (colunas IOF e IRRF).
- total_bruto_informado = Valor Base + Rend. Bruto Acumulado do bloco TOTAL.

SANTANDER (Extrato Consolidado Inteligente)
- Use a seção Investimentos > Renda Fixa. Cada "Aplicação N°" é uma aplicação.
- saldo_inicial_bruto = Valor Bruto do SALDO ANTERIOR; saldo_final_bruto = Valor Bruto do SALDO ATUAL; rendimento_bruto = "Rendimento Bruto no Período".
- A coluna "Valor IR/IOF" é provisionada e vem somada: coloque o valor do saldo atual em ir_provisionado, deixe iof_provisionado = null.
- Linhas entre SALDO ANTERIOR e SALDO ATUAL são aplicações ou resgates.
- total_bruto_informado = TOTAL GERAL. Se o TOTAL GERAL for maior que a soma das aplicações detalhadas, registre uma aplicação adicional com identificador "RESGATE_AUTOMATICO", produto "Aplicação automática (resgate automático)", detalhado = false, saldo_final_bruto = a diferença e os demais valores null.

SICOOB (SISBR - Captação Remunerada - Extrato Consolidado)
- Rótulos e valores podem aparecer desalinhados no texto extraído: guie-se pelo layout visual da página.
- Uma única entrada "CONSOLIDADO". resgates_liquidos = "Resgates Líquidos"; resgates_brutos = resgates líquidos + IR retido + IOF retido.
- "Imposto de Renda Retido" e "IOF Retido" são retidos; "Imposto de Renda Previsto" e "IOF previsto" são provisionados.
- total_bruto_informado = Saldo Bruto final.
- Confira: saldo bruto anterior + aplicações + rendimento bruto - resgates brutos = saldo bruto final. Se não fechar, releia; persistindo, explique em observacoes.

OUTROS BANCOS: aplique as regras gerais e descreva em observacoes onde encontrou cada valor.
```

## Apêndice B — Esquema da *tool* `registrar_extrato`

> `num` = `{"type": ["number","null"]}`; `txt` = `{"type": ["string","null"]}`.

```json
{
  "name": "registrar_extrato",
  "description": "Registra os dados extraídos de um extrato de aplicações financeiras.",
  "input_schema": {
    "type": "object",
    "properties": {
      "documento_reconhecido": { "type": "boolean", "description": "false se o PDF não for um extrato de aplicação financeira" },
      "banco": { "type": "string", "description": "Nome curto do banco, ex.: CAIXA, SANTANDER, SICOOB" },
      "cliente_nome": { "type": ["string","null"] },
      "cliente_cnpj": { "type": ["string","null"], "description": "Somente dígitos" },
      "agencia": { "type": ["string","null"] },
      "conta": { "type": ["string","null"] },
      "competencia": { "type": "string", "description": "AAAA-MM" },
      "data_posicao": { "type": ["string","null"], "description": "AAAA-MM-DD" },
      "total_bruto_informado": { "type": ["number","null"] },
      "observacoes": { "type": ["string","null"] },
      "aplicacoes": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "identificador": { "type": "string" },
            "produto": { "type": ["string","null"] },
            "detalhado": { "type": "boolean", "description": "false quando o extrato não detalha a aplicação" },
            "data_aplicacao": { "type": ["string","null"] },
            "data_vencimento": { "type": ["string","null"] },
            "indexador": { "type": ["string","null"] },
            "saldo_inicial_bruto": { "type": ["number","null"] },
            "saldo_inicial_liquido": { "type": ["number","null"] },
            "aplicacoes": { "type": ["number","null"] },
            "resgates_brutos": { "type": ["number","null"] },
            "resgates_liquidos": { "type": ["number","null"] },
            "rendimento_bruto": { "type": ["number","null"] },
            "ir_retido": { "type": ["number","null"] },
            "iof_retido": { "type": ["number","null"] },
            "ir_provisionado": { "type": ["number","null"] },
            "iof_provisionado": { "type": ["number","null"] },
            "saldo_final_bruto": { "type": ["number","null"] },
            "saldo_final_liquido": { "type": ["number","null"] }
          },
          "required": ["identificador", "detalhado", "saldo_inicial_bruto", "aplicacoes", "resgates_brutos", "rendimento_bruto", "ir_retido", "iof_retido", "saldo_final_bruto"]
        }
      }
    },
    "required": ["documento_reconhecido", "banco", "competencia", "aplicacoes"]
  }
}
```

## Apêndice C — Lógica de normalização e validação (JS original, a portar para Java)

> Fonte de verdade do comportamento. Portar para `ValidacaoService` em Java preservando
> exatamente as regras (remover apenas as referências ao Drive/n8n, substituídas pela
> origem do arquivo e pelo storage da aplicação).

```javascript
// Converte a resposta do Claude em linhas e valida o fechamento de saldo
const TOLERANCIA = 0.05; // R$
const r2 = v => (v == null ? null : Math.round(v * 100) / 100);
const digitos = s => (s == null ? '' : String(s).replace(/\D/g, ''));
const linhas = [];

// 'respostas' = itens da chamada ao Claude; 'meta' = { arquivo, email_id } por item.
respostas.forEach((item, i) => {
  const meta = metas[i] ?? {};
  const base = {
    arquivo: meta.arquivo ?? '',
    email_id: meta.email_id ?? '',
    processado_em: new Date().toISOString(),
  };

  const conteudo = item.content || [];
  let dados = conteudo.find(c => c.type === 'tool_use')?.input;
  if (!dados) {
    // Plano B: o modelo respondeu em texto com um JSON
    const texto = conteudo.filter(c => c.type === 'text').map(c => c.text).join('\n');
    const achado = texto.match(/\{[\s\S]*\}/);
    if (achado) { try { dados = JSON.parse(achado[0]); } catch (e) { dados = null; } }
  }
  if (!dados) {
    linhas.push({ ...base, chave: `ERRO|${base.email_id}|${base.arquivo}`, status: 'ERRO',
      motivos: 'Sem resposta estruturada' });
    return;
  }

  const d = dados;
  const aps = d.aplicacoes || [];
  const motivosDoc = [];
  if (String(d.cliente_cnpj ?? '').replace(/\D/g, '').length !== 14) {
    motivosDoc.push('CNPJ não identificado no extrato (arquivo salvo em _SEM_CNPJ)');
  }
  if (d.documento_reconhecido === false) motivosDoc.push('Documento não reconhecido como extrato de aplicação');
  if (item.stop_reason === 'max_tokens') motivosDoc.push('Resposta cortada (max_tokens)');
  if (aps.length === 0) motivosDoc.push('Nenhuma aplicação encontrada');

  const soma = aps.reduce((s, a) => s + (a.saldo_final_bruto ?? 0), 0);
  if (d.total_bruto_informado != null && Math.abs(soma - d.total_bruto_informado) > TOLERANCIA) {
    motivosDoc.push(`Soma das aplicações (${r2(soma)}) difere do total do extrato (${d.total_bruto_informado})`);
  }
  if (d.observacoes) motivosDoc.push('Obs. do extrator: ' + d.observacoes);

  if (aps.length === 0) {
    linhas.push({ ...base, chave: `ERRO|${base.email_id}|${base.arquivo}`, status: 'REVISAR',
      motivos: motivosDoc.join(' | '), banco: d.banco, competencia: d.competencia });
    return;
  }

  for (const a of aps) {
    const motivos = [...motivosDoc];
    const apl = a.aplicacoes ?? 0;
    const res = a.resgates_brutos ?? 0;
    const rend = a.rendimento_bruto;
    const fin = a.saldo_final_bruto;
    let ini = a.saldo_inicial_bruto;
    let origemIni = 'extrato';
    let diferenca = null;

    if (ini == null && fin != null && rend != null) {
      ini = fin - apl - rend + res;
      origemIni = 'calculado';
    }

    if (a.detalhado !== false) {
      if ([ini, rend, fin].some(v => v == null)) {
        motivos.push('Campos obrigatórios ausentes');
      } else if (origemIni === 'extrato') {
        diferenca = fin - (ini + apl + rend - res);
        if (Math.abs(diferenca) > TOLERANCIA) motivos.push(`Saldo não fecha (diferença ${r2(diferenca)})`);
      }
    }

    const temProblema = motivos.some(m => !m.startsWith('Obs. do extrator'));
    const status = a.detalhado === false ? 'SEM_DETALHE' : (temProblema ? 'REVISAR' : 'OK');

    linhas.push({
      chave: [digitos(d.cliente_cnpj), d.banco, digitos(d.conta), a.identificador, d.competencia].join('|'),
      status,
      motivos: motivos.join(' | '),
      competencia: d.competencia,
      banco: d.banco,
      cliente_nome: d.cliente_nome,
      cliente_cnpj: digitos(d.cliente_cnpj),
      agencia: d.agencia,
      conta: d.conta,
      identificador: a.identificador,
      produto: a.produto,
      data_aplicacao: a.data_aplicacao,
      data_vencimento: a.data_vencimento,
      saldo_inicial_bruto: r2(ini),
      origem_saldo_inicial: origemIni,
      aplicacoes: r2(apl),
      resgates_brutos: r2(res),
      rendimento_bruto: r2(rend),
      ir_retido: r2(a.ir_retido ?? 0),
      iof_retido: r2(a.iof_retido ?? 0),
      saldo_final_bruto: r2(fin),
      ir_provisionado: r2(a.ir_provisionado),
      iof_provisionado: r2(a.iof_provisionado),
      saldo_final_liquido: r2(a.saldo_final_liquido),
      diferenca_fechamento: r2(diferenca),
      ...base,
    });
  }
});

return linhas;
```

## Apêndice D — Nomeação do arquivo (JS original, nó "Preparar documento")

```javascript
const cnpj = String(d?.cliente_cnpj ?? '').replace(/\D/g, '');
const comp = /^(\d{4})-(\d{2})$/.exec(d?.competencia ?? '');
let ano, mes;
if (comp) { ano = comp[1]; mes = comp[2]; }
else {
  const dt = new Date(/* data do upload */ Date.now());
  ano = String(dt.getFullYear());
  mes = String(dt.getMonth() + 1);
}
const banco = String(d?.banco ?? '').toUpperCase().normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '').replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '');
const conta = String(d?.conta ?? '').replace(/\D/g, '');
const nome = banco && comp
  ? [banco, d.competencia, conta].filter(Boolean).join('_') + '.pdf'
  : (nomeOriginal || 'documento.pdf');
// storage sugerido: cnpj (ou _SEM_CNPJ) / ano / mes / aplicacoes / nome
```
