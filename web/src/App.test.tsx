import { act, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import { App } from "./App";

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

function setOnline(value: boolean) {
  Object.defineProperty(navigator, "onLine", { value, configurable: true });
  act(() => {
    window.dispatchEvent(new Event(value ? "online" : "offline"));
  });
}

afterEach(() => setOnline(true));

describe("web shell", () => {
  it("shows the home page in pt-BR", () => {
    renderAt("/");
    expect(screen.getByRole("heading", { name: "Heracles" })).toBeInTheDocument();
    expect(screen.getByText("Treino, nutrição e saúde")).toBeInTheDocument();
  });

  it("shows a not found page for an unknown route", () => {
    renderAt("/nada-aqui");
    expect(screen.getByRole("heading", { name: "Página não encontrada" })).toBeInTheDocument();
  });

  it("says there is no connection when offline and hides the notice when back online", () => {
    renderAt("/");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    setOnline(false);
    expect(screen.getByRole("alert")).toHaveTextContent("Sem conexão");
    setOnline(true);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
