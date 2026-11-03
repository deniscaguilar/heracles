import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { expect, test, type Page } from "@playwright/test";

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

const adminLink = (page: Page) => page.getByRole("link", { name: "Administração" });

test("a regular user signs in, sees the profile and is kept out of /admin", async ({ page }) => {
  await signIn(page, `e2e-user-${randomUUID()}`);

  await expect(page).toHaveURL("/");
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible();
  await expect(adminLink(page)).toHaveCount(0);

  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toBeVisible();
  await expect(page.getByText(/Área do backoffice/)).toHaveCount(0);
});

test("an administrator sees the admin link and the /admin area", async ({ page }) => {
  const sub = `e2e-admin-${randomUUID()}`;
  await signIn(page, sub);
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible(); // first access creates the user

  promoteToAdmin(sub);
  await page.reload();

  await expect(page.getByText("Você entrou como Administrador.")).toBeVisible();
  await adminLink(page).click();
  await expect(page).toHaveURL("/admin");
  await expect(page.getByText(/Área do backoffice/)).toBeVisible();
});

test("signing out hides the profile and /admin asks to sign in again", async ({ page }) => {
  await signIn(page, `e2e-out-${randomUUID()}`);
  await expect(page.getByText("Você entrou como Usuário.")).toBeVisible();

  await page.getByRole("button", { name: "Sair" }).click();
  await expect(page.getByRole("button", { name: "Entrar" })).toBeVisible();

  await page.goto("/admin");
  await expect(page.getByText("Entre para continuar.")).toBeVisible();
});
