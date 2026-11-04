import { fireEvent, screen, waitFor, within } from "@testing-library/react-native";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, renderShell, routedBackend } from "../test-utils";

const CATALOGS: Record<string, string[]> = {
  OBJECTIVE: ["WEIGHT_LOSS", "MUSCLE_GAIN", "GENERAL_HEALTH", "PERFORMANCE"],
  TRAINING_LEVEL: ["BEGINNER", "INTERMEDIATE", "ADVANCED"],
  ALLERGY: ["PEANUT", "TREE_NUTS", "MILK"],
  INTOLERANCE: ["LACTOSE", "GLUTEN"],
  RESTRICTION: ["NO_PORK", "HALAL"],
  FOOD_PREFERENCE: ["VEGETARIAN", "VEGAN"],
  EQUIPMENT: ["BODYWEIGHT", "DUMBBELLS", "BARBELL"],
  BODY_REGION: ["KNEE", "LOWER_BACK", "OTHER"],
};

const revision = (over: Record<string, unknown> = {}) => ({
  revision: 1, createdAt: "2026-11-04T12:00:00Z", objective: "GENERAL_HEALTH", trainingLevel: "BEGINNER", birthDate: "1990-05-01",
  biologicalSex: null, items: [], screening: {}, eligible: true, ineligibilityReasons: [], ...over,
});

function backend(save: { status: number; body?: unknown } = { status: 200, body: revision() }) {
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": { status: 200, body: { items: ALL_TYPES.map((t) => consentItem(t, { granted: true })) } },
    "GET /api/v1/me/anamnesis": { status: 404, body: { code: "not_found" } },
    "PUT /api/v1/me/anamnesis": save,
  });
  for (const [kind, codes] of Object.entries(CATALOGS)) api.routes[`GET /api/v1/catalogs/${kind}`] = { status: 200, body: { kind, codes } };
  return api;
}

const radio = (name: string) => screen.getByRole("radio", { name });
const next = () => fireEvent.press(screen.getByRole("button", { name: "Próximo" }));

async function open(api = backend()) {
  await renderShell({ authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await fireEvent.press(await screen.findByRole("button", { name: "Anamnese" }));
  await screen.findByRole("header", { name: "Sua anamnese" });
  return api;
}

async function fillAbout() {
  await fireEvent.press(await screen.findByRole("radio", { name: "Ganhar massa muscular" }));
  await fireEvent.press(radio("Iniciante"));
  await fireEvent.changeText(screen.getByLabelText("Data de nascimento"), "1990-05-01");
}

const QUESTION_TEXTS = [
  "Você está grávida ou amamentando?",
  "Você tem alguma condição de saúde que exige acompanhamento?",
  "Você usa algum medicamento que afeta a alimentação ou o treino?",
  "Você já teve algum transtorno alimentar?",
];
// The group is not an accessibility element itself (it would hide its options on iOS), so it is found by its label.
const group = (question: string) => within(screen.getByLabelText(question));

async function answerAll(answer: string) {
  await screen.findByText(QUESTION_TEXTS[0]!);
  for (const question of QUESTION_TEXTS) await fireEvent.press(group(question).getByRole("radio", { name: answer }));
}

describe("anamnesis screens (E2-S11.2, CA9, CA10, CA11)", () => {
  it("walks the five steps and sends the answers, the items and the four screening answers", async () => {
    const api = await open();
    await fillAbout();
    await fireEvent.press(radio("Feminino"));
    await next();

    await fireEvent.press(await screen.findByRole("checkbox", { name: "Amendoim" }));
    await fireEvent.press(screen.getByRole("checkbox", { name: "Lactose" }));
    await fireEvent.changeText(screen.getByLabelText("Outra alergia"), "kiwi");
    await fireEvent.press(screen.getByRole("button", { name: "Adicionar alergia" }));
    await fireEvent.changeText(screen.getByLabelText("Alimento que você não come"), "beterraba");
    await fireEvent.press(screen.getByRole("button", { name: "Adicionar alimento" }));
    await next();

    await fireEvent.press(await screen.findByRole("checkbox", { name: "Halteres" }));
    await fireEvent.press(radio("Joelho"));
    await fireEvent.changeText(screen.getByLabelText("Observação da lesão"), "cirurgia antiga");
    await fireEvent.press(screen.getByRole("button", { name: "Adicionar lesão" }));
    await next();

    for (const question of QUESTION_TEXTS) expect(await screen.findByText(question)).toBeOnTheScreen();
    await answerAll("Não");
    await next();

    expect(await screen.findByRole("header", { name: "Revise e salve" })).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole("button", { name: "Salvar anamnese" }));

    await waitFor(() => expect(api.called("PUT /api/v1/me/anamnesis")).toHaveLength(1));
    const body = api.called("PUT /api/v1/me/anamnesis")[0]!.body as { items: unknown[] };
    expect(body).toEqual({
      objective: "MUSCLE_GAIN", trainingLevel: "BEGINNER", birthDate: "1990-05-01", biologicalSex: "FEMALE",
      items: expect.arrayContaining([
        { kind: "ALLERGY", catalogCode: "PEANUT" },
        { kind: "INTOLERANCE", catalogCode: "LACTOSE" },
        { kind: "ALLERGY", otherText: "kiwi" },
        { kind: "DISLIKED_FOOD", otherText: "beterraba" },
        { kind: "EQUIPMENT", catalogCode: "DUMBBELLS" },
        { kind: "INJURY", bodyRegion: "KNEE", note: "cirurgia antiga" },
      ]),
      screening: { PREGNANT_OR_NURSING: "NO", CONDITION_REQUIRING_CARE: "NO", MEDICATION_AFFECTING_PLAN: "NO", EATING_DISORDER_HISTORY: "NO" },
    });
    expect(body.items).toHaveLength(6);
    expect(await screen.findByRole("header", { name: "Anamnese salva" })).toBeOnTheScreen();
  });

  it("does not leave the first step without objective, level and a believable birth date", async () => {
    await open();
    await next();
    expect(await screen.findByText("Escolha o seu objetivo.")).toBeOnTheScreen();
    expect(screen.getByText("Escolha o seu nível de treino.")).toBeOnTheScreen();
    expect(screen.getByText("Informe a data de nascimento.")).toBeOnTheScreen();

    await fireEvent.press(radio("Ganhar massa muscular"));
    await fireEvent.press(radio("Iniciante"));
    for (const bad of ["2999-01-01", "01/05/1990", "1990-13-45"]) {
      await fireEvent.changeText(screen.getByLabelText("Data de nascimento"), bad);
      await next();
      expect(await screen.findByText("Confira a data de nascimento (AAAA-MM-DD).")).toBeOnTheScreen();
      expect(screen.getByRole("header", { name: "Sobre você" })).toBeOnTheScreen();
    }
  });

  it("asks for the four screening answers: leaving one empty blocks the step", async () => {
    await open();
    await fillAbout();
    await next();
    await next();
    await next();
    await screen.findByText(QUESTION_TEXTS[0]!);
    await fireEvent.press(group(QUESTION_TEXTS[0]!).getByRole("radio", { name: "Não" }));
    await next();
    expect(await screen.findByText("Responda as quatro perguntas para continuar.")).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Revise e salve" })).not.toBeOnTheScreen();
  });

  it("a free-text item cannot be empty or repeated, and can be removed", async () => {
    await open();
    await fillAbout();
    await next();
    await fireEvent.press(await screen.findByRole("button", { name: "Adicionar alergia" }));
    expect(screen.queryByRole("button", { name: "Remover kiwi" })).not.toBeOnTheScreen();
    for (let i = 0; i < 2; i++) {
      await fireEvent.changeText(screen.getByLabelText("Outra alergia"), "kiwi");
      await fireEvent.press(screen.getByRole("button", { name: "Adicionar alergia" }));
    }
    expect(screen.getAllByText("kiwi")).toHaveLength(1);
    await fireEvent.press(screen.getByRole("button", { name: "Remover kiwi" }));
    expect(screen.queryByText("kiwi")).not.toBeOnTheScreen();
  });

  it("not eligible: shows 'procure um profissional', the reasons in words, and no plan", async () => {
    const api = backend({ status: 200, body: revision({ eligible: false, ineligibilityReasons: ["UNDER_MINIMUM_AGE", "CONDITION_REQUIRING_CARE"] }) });
    await open(api);
    await fillAbout();
    await next();
    await next();
    await next();
    await answerAll("Sim");
    await next();
    await fireEvent.press(await screen.findByRole("button", { name: "Salvar anamnese" }));

    expect(await screen.findByRole("header", { name: "Procure um profissional" })).toBeOnTheScreen();
    expect(screen.getByText(/profissional de saúde/)).toBeOnTheScreen();
    expect(screen.getByText("Você tem menos de 18 anos.")).toBeOnTheScreen();
    expect(screen.getByText("Você tem uma condição que pede acompanhamento.")).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Anamnese salva" })).not.toBeOnTheScreen();
  });

  it("sends 'prefiro não dizer' as its own answer", async () => {
    const api = await open();
    await fillAbout();
    await next();
    await next();
    await next();
    await answerAll("Prefiro não dizer");
    await next();
    await fireEvent.press(await screen.findByRole("button", { name: "Salvar anamnese" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/anamnesis")).toHaveLength(1));
    expect((api.called("PUT /api/v1/me/anamnesis")[0]!.body as { screening: Record<string, string> }).screening.EATING_DISORDER_HISTORY).toBe("PREFER_NOT_TO_SAY");
  });

  it("starts from the last revision when there is one", async () => {
    const api = backend();
    api.routes["GET /api/v1/me/anamnesis"] = {
      status: 200,
      body: revision({
        objective: "PERFORMANCE", trainingLevel: "ADVANCED", biologicalSex: "MALE",
        items: [{ kind: "ALLERGY", catalogCode: "MILK" }, { kind: "ALLERGY", otherText: "kiwi" }],
        screening: { PREGNANT_OR_NURSING: "NO", CONDITION_REQUIRING_CARE: "NO", MEDICATION_AFFECTING_PLAN: "NO", EATING_DISORDER_HISTORY: "NO" },
      }),
    };
    await open(api);
    expect(await screen.findByRole("radio", { name: "Desempenho" })).toBeChecked();
    expect(screen.getByLabelText("Data de nascimento")).toHaveDisplayValue("1990-05-01");
    expect(radio("Masculino")).toBeChecked();
    await next();
    expect(await screen.findByRole("checkbox", { name: "Leite" })).toBeChecked();
    expect(screen.getByText("kiwi")).toBeOnTheScreen();
  });

  it("shows a message and keeps the answers when saving fails", async () => {
    const api = backend({ status: 400, body: { code: "validation_error", fields: ["birthDate"] } });
    await open(api);
    await fillAbout();
    await next();
    await next();
    await next();
    await answerAll("Não");
    await next();
    await fireEvent.press(await screen.findByRole("button", { name: "Salvar anamnese" }));
    expect(await screen.findByRole("alert", { name: /Não foi possível salvar/ })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Salvar anamnese" })).toBeEnabled();
  });
});
