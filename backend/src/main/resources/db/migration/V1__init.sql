-- Esquema inicial: documento (§4.1) e lancamento (§4.2).
-- A ordem das colunas de lancamento reproduz exatamente a §4.2 da spec.

create table documento (
    id                      uuid primary key,
    nome_arquivo_original   varchar       not null,
    nome_padronizado        varchar,
    banco                   varchar,
    competencia             varchar(7),
    cnpj                    varchar(14),
    conta                   varchar,
    storage_key             varchar,
    tamanho_bytes           bigint,
    sha256                  varchar(64),
    status_processamento    varchar       not null,
    stop_reason             varchar,
    raw_claude_json         jsonb,
    documento_reconhecido   boolean,
    total_bruto_informado   numeric(18,2),
    observacoes             text,
    criado_em               timestamptz   not null,
    processado_em           timestamptz
);

create index ix_documento_sha256 on documento (sha256);
create index ix_documento_competencia on documento (competencia);

create table lancamento (
    id                      uuid primary key,
    chave                   varchar       not null unique,
    status                  varchar       not null,
    motivos                 text,
    competencia             varchar(7),
    banco                   varchar,
    cliente_nome            varchar,
    cliente_cnpj            varchar(14),
    agencia                 varchar,
    conta                   varchar,
    identificador           varchar,
    produto                 varchar,
    data_aplicacao          varchar,
    data_vencimento         varchar,
    saldo_inicial_bruto     numeric(18,2),
    origem_saldo_inicial    varchar,
    aplicacoes              numeric(18,2),
    resgates_brutos         numeric(18,2),
    rendimento_bruto        numeric(18,2),
    ir_retido               numeric(18,2),
    iof_retido              numeric(18,2),
    saldo_final_bruto       numeric(18,2),
    ir_provisionado         numeric(18,2),
    iof_provisionado        numeric(18,2),
    saldo_final_liquido     numeric(18,2),
    diferenca_fechamento    numeric(18,2),
    arquivo                 varchar,
    email_id                varchar,
    processado_em           timestamptz,
    documento_id            uuid references documento (id)
);

create index ix_lancamento_status on lancamento (status);
create index ix_lancamento_banco on lancamento (banco);
create index ix_lancamento_competencia on lancamento (competencia);
create index ix_lancamento_cliente_cnpj on lancamento (cliente_cnpj);
