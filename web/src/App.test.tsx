import { act, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { fakeAuthenticator, renderApp } from "./test-utils";

function setOnline(value: boolean) {
  Object.defineProperty(navigator, "onLine", { value, configurable: true });
  act(() => {
    window.dispatchEvent(new Event(value ? "online" : "offline"));
  });
}

afterEach(() => setOnline(true));

describe("web shell", () => {
  it("shows the home page in pt-BR", async () => {
    await renderApp("/", { authenticator: fakeAuthenticator(undefined) });
    expect(screen.getByRole("heading", { name: "Heracles" })).toBeInTheDocument();
    expect(screen.getByText("Treino, nutrição e saúde")).toBeInTheDocument();
  });

  it("shows a not found page for an unknown route", async () => {
    await renderApp("/nada-aqui", { authenticator: fakeAuthenticator(undefined) });
    expect(screen.getByRole("heading", { name: "Página não encontrada" })).toBeInTheDocument();
  });

  it("says there is no connection when offline and hides the notice when back online", async () => {
    await renderApp("/", { authenticator: fakeAuthenticator(undefined) });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    setOnline(false);
    expect(screen.getByRole("alert")).toHaveTextContent("Sem conexão");
    setOnline(true);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
