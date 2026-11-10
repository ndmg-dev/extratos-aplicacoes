-- Ingestão por e-mail (§13): proveniência do documento, token OAuth e controle de mensagens.

-- Proveniência no documento.
alter table documento add column origem            varchar(10);
alter table documento add column email_message_id  varchar;
alter table documento add column email_remetente   varchar;
alter table documento add column email_assunto      text;
alter table documento add column email_data         timestamptz;

-- Documentos existentes são de upload.
update documento set origem = 'UPLOAD' where origem is null;

create index ix_documento_origem on documento (origem);
create index ix_documento_email_message_id on documento (email_message_id);

-- Token OAuth do provedor de e-mail (refresh token + access token em cache).
-- Uma linha por provedor (ex.: 'gmail'). Credenciais ficam só no backend.
create table oauth_token (
    provedor        varchar(30)   primary key,
    email_conta     varchar,
    refresh_token   text          not null,
    access_token    text,
    expira_em       timestamptz,
    escopo          varchar,
    atualizado_em   timestamptz   not null
);

-- Mensagens de e-mail já processadas, para não reprocessar a cada ciclo de polling.
create table email_mensagem_processada (
    message_id      varchar       primary key,
    processado_em   timestamptz   not null,
    remetente       varchar,
    assunto         text,
    qtd_pdf         integer       not null default 0,
    status          varchar
);
