# Textos iniciais de consentimento num ambiente novo

Um ambiente novo **não tem nenhum texto publicado**: as migrações criam as tabelas, mas não semeiam as políticas. Sem os três textos obrigatórios (`TERMS_OF_USE`, `PRIVACY_POLICY`, `HEALTH_DATA_CONSENT`) publicados, ninguém é solicitado a aceitar nada e a barreira de saúde não tem o que conferir. Um consentimento opcional sem texto publicado aparece como "Texto ainda não publicado", sem o interruptor.

## Ambiente local (desenvolvimento)

```sh
docker compose -f docker/compose.yml up -d --wait
docker/seed-policies.sh
```

O script publica, como administrador, os sete textos que faltarem, com **texto de desenvolvimento** (sem validade jurídica). Rodar de novo não muda nada. Ele promove o próprio usuário (`seed-admin`) a `ADMIN` direto no banco, então só serve no ambiente local. O teste Playwright do web faz o mesmo para os textos de que precisa.

## Ambiente real (antes do go-live)

1. **Os textos são escritos e revisados por um advogado.** Termos de uso, política de privacidade e consentimento de dados de saúde (LGPD, dado sensível). Não saem do código nem de IA.
2. Um `ADMIN` publica cada um em `/admin/policies`: *Novo rascunho*, conferir em *Pré-visualizar*, *Publicar*. O formato é restrito (parágrafos, títulos, listas, negrito, itálico, links `https` e `mailto`).
3. Publicar os **três obrigatórios antes de abrir o app a qualquer pessoa**. Os opcionais (IA nos exames, comunidade, notificações, notificações promocionais) só precisam de texto quando a funcionalidade for ligada.
4. Conferir: um usuário novo vê a tela "Antes de continuar" com os três textos e não chega à anamnese antes de aceitar.
5. Publicar uma versão nova de um obrigatório **obriga todos a aceitar de novo**; a tela avisa quem já tinha aceitado a anterior.
