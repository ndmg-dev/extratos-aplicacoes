-- =============================================================================
-- Registra "Extratos de Aplicações" no CRM (tabela public.sistemas), setor CONTABIL.
-- Rode uma vez contra o banco de PRODUÇÃO do CRM (crm_mendonca_galvao).
-- O CRM abre a URL em iframe automaticamente (SystemViewer). Nenhuma mudança de
-- código no CRM é necessária.
--
-- Idempotente: não insere de novo se o slug já existir.
-- =============================================================================

INSERT INTO public.sistemas
    (id, nome, descricao, slug, categoria, url, icone, allowed_origin, ativo, created_at, updated_at, setor)
SELECT
    gen_random_uuid(),
    'Extratos de Aplicações',
    'Extração e conferência de extratos de aplicações financeiras (CDB, RDB, LCI/LCA, fundos, poupança), com ingestão automática por e-mail e validação de fechamento de saldo.',
    'extratos-aplicacoes',
    'AUTOMATION',
    'https://aplicacoes.nucleodigital.cloud',
    'landmark',
    NULL,
    true,
    now(),
    now(),
    'CONTABIL'
WHERE NOT EXISTS (
    SELECT 1 FROM public.sistemas WHERE slug = 'extratos-aplicacoes'
);

-- Conferência:
-- SELECT nome, slug, setor, url, ativo FROM public.sistemas WHERE slug = 'extratos-aplicacoes';
