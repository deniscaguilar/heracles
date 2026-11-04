import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { fakeAuthenticator, renderApp } from "../test-utils";
import { anamnesisBackend, savedRevision } from "./anamnesis.test-helpers";

const next = () => userEvent.click(screen.getByRole("button", { name: "Próximo" }));

async function open(api = anamnesisBackend({ status: 200, body: savedRevision() })) {
  await renderApp("/anamnesis", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await screen.findByRole("heading", { name: "Sua anamnese" });
  return api;
}

async function fillAbout() {
  await userEvent.selectOptions(await screen.findByLabelText("Objetivo"), "MUSCLE_GAIN");
  await userEvent.selectOptions(screen.getByLabelText("Nível de treino"), "BEGINNER");
  await userEvent.type(screen.getByLabelText("Data de nascimento"), "1990-05-01");
}

async function answerScreening(answer: "Não" | "Sim" | "Prefiro não dizer" = "Não") {
  for (const group of screen.getAllByRole("radiogroup")) {
    await userEvent.click(within(group).getByRole("radio", { name: answer }));
  }
}

describe("anamnesis in steps (E2-S10.2, CA9, CA10, CA11)", () => {
  it("walks the five steps and sends the answers, the items and the four screening answers", async () => {
    const api = await open();
    await fillAbout();
    await userEvent.click(screen.getByRole("radio", { name: "Feminino" }));
    await next();

    // food
    await userEvent.click(await screen.findByRole("checkbox", { name: "Amendoim" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Lactose" }));
    await userEvent.type(screen.getByLabelText("Outra alergia"), "kiwi");
    await userEvent.click(screen.getByRole("button", { name: "Adicionar alergia" }));
    await userEvent.type(screen.getByLabelText("Alimento que você não come"), "beterraba");
    await userEvent.click(screen.getByRole("button", { name: "Adicionar alimento" }));
    await next();

    // training
    await userEvent.click(await screen.findByRole("checkbox", { name: "Halteres" }));
    await userEvent.selectOptions(screen.getByLabelText("Região da lesão"), "KNEE");
    await userEvent.type(screen.getByLabelText("Observação da lesão"), "cirurgia antiga");
    await userEvent.click(screen.getByRole("button", { name: "Adicionar lesão" }));
    await next();

    // screening: all four are asked
    expect(await screen.findAllByRole("radiogroup")).toHaveLength(4);
    await answerScreening("Não");
    await next();

    // review
    expect(await screen.findByRole("heading", { name: "Revise e salve" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Salvar anamnese" }));

    await waitFor(() => expect(api.called("PUT /api/v1/me/anamnesis")).toHaveLength(1));
    expect(api.called("PUT /api/v1/me/anamnesis")[0]!.body).toEqual({
      objective: "MUSCLE_GAIN",
      trainingLevel: "BEGINNER",
      birthDate: "1990-05-01",
      biologicalSex: "FEMALE",
      items: expect.arrayContaining([
        { kind: "ALLERGY", catalogCode: "PEANUT" },
        { kind: "INTOLERANCE", catalogCode: "LACTOSE" },
        { kind: "ALLERGY", otherText: "kiwi" },
        { kind: "DISLIKED_FOOD", otherText: "beterraba" },
        { kind: "EQUIPMENT", catalogCode: "DUMBBELLS" },
        { kind: "INJURY", bodyRegion: "KNEE", note: "cirurgia antiga" },
      ]),
      screening: {
        PREGNANT_OR_NURSING: "NO",
        CONDITION_REQUIRING_CARE: "NO",
        MEDICATION_AFFECTING_PLAN: "NO",
        EATING_DISORDER_HISTORY: "NO",
      },
    });
    expect((api.called("PUT /api/v1/me/anamnesis")[0]!.body as { items: unknown[] }).items).toHaveLength(6);
    expect(await screen.findByRole("heading", { name: "Anamnese salva" })).toBeInTheDocument();
  });

  it("does not leave the first step without objective, level and a believable birth date", async () => {
    await open();
    await next();
    expect(await screen.findByText("Escolha o seu objetivo.")).toBeInTheDocument();
    expect(screen.getByText("Escolha o seu nível de treino.")).toBeInTheDocument();
    expect(screen.getByText("Informe a data de nascimento.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Sobre você" })).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Objetivo"), "MUSCLE_GAIN");
    await userEvent.selectOptions(screen.getByLabelText("Nível de treino"), "BEGINNER");
    await userEvent.type(screen.getByLabelText("Data de nascimento"), "2999-01-01");
    await next();
    expect(await screen.findByText("Confira a data de nascimento.")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Sobre você" })).toBeInTheDocument();
  });

  it("asks for the four screening answers: leaving one empty blocks the step", async () => {
    await open();
    await fillAbout();
    await next();
    await next();
    await next();
    const groups = await screen.findAllByRole("radiogroup");
    await userEvent.click(within(groups[0]!).getByRole("radio", { name: "Não" }));
    await next();
    expect(await screen.findByText("Responda as quatro perguntas para continuar.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Revise e salve" })).not.toBeInTheDocument();
  });

  it("an item of free text cannot be empty or repeated", async () => {
    await open();
    await fillAbout();
    await next();
    await userEvent.click(await screen.findByRole("button", { name: "Adicionar alergia" }));
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Outra alergia"), "kiwi");
    await userEvent.click(screen.getByRole("button", { name: "Adicionar alergia" }));
    await userEvent.type(screen.getByLabelText("Outra alergia"), "kiwi");
    await userEvent.click(screen.getByRole("button", { name: "Adicionar alergia" }));
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    await userEvent.click(screen.getByRole("button", { name: "Remover kiwi" }));
    expect(screen.queryByRole("listitem")).not.toBeInTheDocument();
  });

  it("not eligible: shows 'procure um profissional', the reasons in words, and no plan", async () => {
    const api = anamnesisBackend({
      status: 200,
      body: savedRevision({ eligible: false, ineligibilityReasons: ["UNDER_MINIMUM_AGE", "CONDITION_REQUIRING_CARE"] }),
    });
    await open(api);
    await fillAbout();
    await next();
    await next();
    await next();
    await answerScreening("Sim");
    await next();
    await userEvent.click(await screen.findByRole("button", { name: "Salvar anamnese" }));

    expect(await screen.findByRole("heading", { name: "Procure um profissional" })).toBeInTheDocument();
    expect(screen.getByText(/profissional de saúde/)).toBeInTheDocument();
    expect(screen.getByText("Você tem menos de 18 anos.")).toBeInTheDocument();
    expect(screen.getByText("Você tem uma condição que pede acompanhamento.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Anamnese salva" })).not.toBeInTheDocument();
    // "prefer not to say" counts as an answer the person gave, so it is sent as such
    expect((api.called("PUT /api/v1/me/anamnesis")[0]!.body as { screening: Record<string, string> }).screening.CONDITION_REQUIRING_CARE).toBe("YES");
  });

  it("sends 'prefiro não dizer' as its own answer", async () => {
    const api = await open();
    await fillAbout();
    await next();
    await next();
    await next();
    await answerScreening("Prefiro não dizer");
    await next();
    await userEvent.click(await screen.findByRole("button", { name: "Salvar anamnese" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/anamnesis")).toHaveLength(1));
    expect((api.called("PUT /api/v1/me/anamnesis")[0]!.body as { screening: Record<string, string> }).screening.EATING_DISORDER_HISTORY).toBe("PREFER_NOT_TO_SAY");
  });

  it("starts from the last revision when there is one", async () => {
    const api = anamnesisBackend({ status: 200, body: savedRevision() });
    api.routes["GET /api/v1/me/anamnesis"] = {
      status: 200,
      body: savedRevision({
        objective: "PERFORMANCE", trainingLevel: "ADVANCED", biologicalSex: "MALE",
        items: [{ kind: "ALLERGY", catalogCode: "MILK" }, { kind: "ALLERGY", otherText: "kiwi" }],
        screening: { PREGNANT_OR_NURSING: "NO", CONDITION_REQUIRING_CARE: "NO", MEDICATION_AFFECTING_PLAN: "NO", EATING_DISORDER_HISTORY: "NO" },
      }),
    };
    await open(api);
    expect(await screen.findByLabelText("Objetivo")).toHaveValue("PERFORMANCE");
    expect(screen.getByLabelText("Data de nascimento")).toHaveValue("1990-05-01");
    expect(screen.getByRole("radio", { name: "Masculino" })).toBeChecked();
    await next();
    expect(await screen.findByRole("checkbox", { name: "Leite" })).toBeChecked();
    expect(screen.getByText("kiwi")).toBeInTheDocument();
  });

  it("shows a message and keeps the answers when saving fails", async () => {
    const api = anamnesisBackend({ status: 400, body: { code: "validation_error", fields: ["birthDate"] } });
    await open(api);
    await fillAbout();
    await next();
    await next();
    await next();
    await answerScreening("Não");
    await next();
    await userEvent.click(await screen.findByRole("button", { name: "Salvar anamnese" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível salvar");
    expect(screen.getByRole("button", { name: "Salvar anamnese" })).toBeEnabled();
  });
});
