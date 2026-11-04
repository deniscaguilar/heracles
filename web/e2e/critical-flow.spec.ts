import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { expect, test, type Browser, type Page } from "@playwright/test";

const composeFile = fileURLToPath(new URL("../../docker/compose.yml", import.meta.url));

// The role lives in the database and never in the token, so promoting a user is a database change.
function promoteToAdmin(sub: string) {
  const sql = `update platform.app_user set role='ADMIN' where sub='${sub}'`;
  const out = execFileSync(
    "docker",
    ["compose", "-f", composeFile, "exec", "-T", "postgres", "psql", "-U", "postgres", "-d", "heracles", "-tAc", sql],
    { encoding: "utf8" },
  );
  expect(out).toContain("UPDATE 1");
}

// The mock identity provider shows a form: any username becomes the `sub`.
async function signIn(page: Page, sub: string) {
  await page.goto("/");
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.locator("input[name=username]").fill(sub);
  await page.getByRole("button", { name: "Sign-in" }).click();
}

// First access (and every new version) puts the acceptance gate in front of the app.
async function acceptTexts(page: Page) {
  await expect(page.getByRole("heading", { name: "Antes de continuar" })).toBeVisible();
  const boxes = page.getByRole("checkbox");
  await expect(boxes.first()).toBeVisible();
  for (const box of await boxes.all()) await box.check();
  await page.getByRole("button", { name: "Aceitar e continuar" }).click();
  await expect(page.getByRole("heading", { name: "Antes de continuar" })).toHaveCount(0);
}

async function signInAndAccept(page: Page, sub: string) {
  await signIn(page, sub);
  await acceptTexts(page);
}

// A new environment has no published text (the migrations do not seed them): without the three required ones nobody
// is asked to accept anything. As an operator would, an administrator publishes what is missing before the flows.
// "Comunidade" is optional, but the flow turns it on, and an optional consent without a published text has no switch.
const SEEDED_TEXTS = ["Termos de uso", "Política de privacidade", "Dados de saúde", "Comunidade"];

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const sub = `e2e-seed-${randomUUID()}`;
  await signIn(page, sub);
  // With the texts already there (a rerun) the gate stands in front of the app: accept it like anyone.
  const gate = page.getByRole("heading", { name: "Antes de continuar" });
  const home = page.getByText("Você entrou como Usuário.");
  await expect(gate.or(home)).toBeVisible();
  if (await gate.isVisible()) await acceptTexts(page);
  await expect(home).toBeVisible();
  promoteToAdmin(sub);
  await page.goto("/admin/policies");
  await page.reload();
  for (const name of SEEDED_TEXTS) {
    const region = page.getByRole("region", { name, exact: true });
    await expect(region).toBeVisible();
    if ((await region.getByText("Publicada", { exact: true }).count()) > 0) continue;
    if ((await region.getByRole("button", { name: "Novo rascunho" }).count()) > 0) {
      await region.getByRole("button", { name: "Novo rascunho" }).click();
      await page.getByLabel("Título").fill(`${name} (inicial)`);
      await page.getByLabel("Texto (Markdown)").fill(`Texto inicial de ${name}.`);
      await page.getByRole("button", { name: "Salvar rascunho" }).click();
    }
    await region.getByRole("button", { name: "Publicar" }).click();
    await page.getByRole("button", { name: "Confirmar publicação" }).click();
    await expect(region.getByText("Publicada", { exact: true })).toBeVisible();
  }
  await context.close();
});

const adminLink = (page: Page) => page.getByRole("link", { name: "Administração" });

test("a regular user signs in, sees the profile and is kept out of /admin", async ({ page }) => {
  await signInAndAccept(page, `e2e-user-${randomUUID()}`);

  await expect(page).toHaveURL("/");
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible();
  await expect(adminLink(page)).toHaveCount(0);

  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toBeVisible();
  await expect(page.getByText(/Área do backoffice/)).toHaveCount(0);
});

test("an administrator sees the admin link and the /admin area", async ({ page }) => {
  const sub = `e2e-admin-${randomUUID()}`;
  await signInAndAccept(page, sub);
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible(); // first access creates the user

  promoteToAdmin(sub);
  await page.reload();

  await expect(page.getByText("Você entrou como Administrador.")).toBeVisible();
  await adminLink(page).click();
  await expect(page).toHaveURL("/admin");
  await expect(page.getByText(/Área do backoffice/)).toBeVisible();
});

test("signing out hides the profile and /admin asks to sign in again", async ({ page }) => {
  await signInAndAccept(page, `e2e-out-${randomUUID()}`);
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible();

  await page.getByRole("button", { name: "Sair" }).click();
  await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();

  await page.goto("/admin");
  await expect(page.getByText("Entre para continuar.")).toBeVisible();
});

// E2-S10.5 (CA15): first access, anamnesis, consents, and a new version that obliges everyone to accept again.
async function fillAnamnesis(page: Page) {
  await page.getByLabel("Objetivo").selectOption("GENERAL_HEALTH");
  await page.getByLabel("Nível de treino").selectOption("BEGINNER");
  await page.getByLabel("Data de nascimento").fill("1990-05-01");
  await page.getByRole("button", { name: "Próximo" }).click();
  await page.getByRole("checkbox", { name: "Amendoim" }).check();
  await page.getByRole("button", { name: "Próximo" }).click();
  await page.getByRole("checkbox", { name: "Halteres" }).check();
  await page.getByRole("button", { name: "Próximo" }).click();
  for (const group of await page.getByRole("radiogroup").all()) await group.getByRole("radio", { name: "Não", exact: true }).check();
  await page.getByRole("button", { name: "Próximo" }).click();
  await page.getByRole("button", { name: "Salvar anamnese" }).click();
}

async function publishNewTerms(browser: Browser) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const sub = `e2e-publisher-${randomUUID()}`;
  await signInAndAccept(page, sub);
  promoteToAdmin(sub);
  await page.goto("/admin/policies");
  await page.reload();
  const terms = page.getByRole("region", { name: "Termos de uso" });
  await expect(terms).toBeVisible();
  // a draft left by an earlier run would hide "Novo rascunho"
  if (await terms.getByRole("button", { name: "Descartar rascunho" }).count()) await terms.getByRole("button", { name: "Descartar rascunho" }).click();
  await terms.getByRole("button", { name: "Novo rascunho" }).click();
  await page.getByLabel("Título").fill("Termos de uso (e2e)");
  await page.getByLabel("Texto (Markdown)").fill("Texto novo dos termos.\n\n- primeiro item\n- segundo item");
  await page.getByRole("button", { name: "Salvar rascunho" }).click();
  await terms.getByRole("button", { name: "Pré-visualizar" }).click();
  await expect(page.getByRole("heading", { name: /Pré-visualização/ })).toBeVisible();
  await terms.getByRole("button", { name: "Publicar" }).click();
  await page.getByRole("button", { name: "Confirmar publicação" }).click();
  await expect(terms.getByText("Rascunho", { exact: true })).toHaveCount(0);
  await context.close();
}

test("a new user accepts, fills the anamnesis, sees the consents; a new version makes them accept again", async ({ page, browser }) => {
  await signIn(page, `e2e-flow-${randomUUID()}`);

  // the gate stands in front of everything, health routes included
  await expect(page.getByRole("heading", { name: "Antes de continuar" })).toBeVisible();
  await page.goto("/anamnesis");
  await expect(page.getByRole("heading", { name: "Antes de continuar" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Sua anamnese" })).toHaveCount(0);
  await acceptTexts(page);

  await page.getByRole("link", { name: "Anamnese" }).click();
  await fillAnamnesis(page);
  await expect(page.getByRole("heading", { name: "Anamnese salva" })).toBeVisible();

  await page.getByRole("link", { name: "Meus consentimentos" }).click();
  await expect(page.getByRole("listitem", { name: "Termos de uso" }).getByText("Aceito")).toBeVisible();
  await page.getByRole("listitem", { name: "Comunidade" }).getByRole("button", { name: "Ativar" }).click();
  await expect(page.getByRole("listitem", { name: "Comunidade" }).getByText("Ativado")).toBeVisible();

  await publishNewTerms(browser);

  await page.reload();
  await expect(page.getByRole("heading", { name: "Antes de continuar" })).toBeVisible();
  await expect(page.getByText(/mudaram/)).toBeVisible();
  await expect(page.getByText("Texto novo dos termos.")).toBeVisible();
  await acceptTexts(page);
  await expect(page.getByRole("heading", { name: "Meus consentimentos" })).toBeVisible();
});

test("a person under 18 is told to look for a professional and no plan is offered", async ({ page }) => {
  await signInAndAccept(page, `e2e-minor-${randomUUID()}`);
  await page.getByRole("link", { name: "Anamnese" }).click();
  await page.getByLabel("Objetivo").selectOption("GENERAL_HEALTH");
  await page.getByLabel("Nível de treino").selectOption("BEGINNER");
  const year = new Date().getFullYear() - 15;
  await page.getByLabel("Data de nascimento").fill(`${year}-06-01`);
  for (let i = 0; i < 3; i++) await page.getByRole("button", { name: "Próximo" }).click();
  for (const group of await page.getByRole("radiogroup").all()) await group.getByRole("radio", { name: "Não", exact: true }).check();
  await page.getByRole("button", { name: "Próximo" }).click();
  await page.getByRole("button", { name: "Salvar anamnese" }).click();
  await expect(page.getByRole("heading", { name: "Procure um profissional" })).toBeVisible();
  await expect(page.getByText("Você tem menos de 18 anos.")).toBeVisible();
});
