import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";

type Body = components["schemas"]["AnamnesisBody"];
type ItemBody = components["schemas"]["AnamnesisItemBody"];
type Saved = components["schemas"]["AnamnesisResponse"];
type CatalogKind = components["schemas"]["CatalogResponse"]["kind"];

const KINDS: CatalogKind[] = [
  "OBJECTIVE", "TRAINING_LEVEL", "ALLERGY", "INTOLERANCE", "RESTRICTION", "FOOD_PREFERENCE", "EQUIPMENT", "BODY_REGION",
];
const QUESTIONS = ["PREGNANT_OR_NURSING", "CONDITION_REQUIRING_CARE", "MEDICATION_AFFECTING_PLAN", "EATING_DISORDER_HISTORY"] as const;
const ANSWERS = ["YES", "NO", "PREFER_NOT_TO_SAY"] as const;
type Question = (typeof QUESTIONS)[number];
type Answer = (typeof ANSWERS)[number];
const STEPS = ["about", "food", "training", "screening", "review"] as const;
type Step = (typeof STEPS)[number];

/** Free-text entries, kept apart from the catalog checkboxes: what the person typed and what kind it is. */
type FreeKind = "ALLERGY" | "INTOLERANCE" | "RESTRICTION" | "DISLIKED_FOOD";
interface Draft {
  objective: string;
  trainingLevel: string;
  birthDate: string;
  biologicalSex: "" | "FEMALE" | "MALE";
  codes: Record<string, Set<string>>; // by item kind
  free: { kind: FreeKind; text: string }[];
  injuries: { region: string; note: string }[];
  screening: Partial<Record<Question, Answer>>;
}

const emptyDraft = (): Draft => ({
  objective: "", trainingLevel: "", birthDate: "", biologicalSex: "", codes: {}, free: [], injuries: [], screening: {},
});

const CATALOG_ITEM_KINDS = ["ALLERGY", "INTOLERANCE", "RESTRICTION", "FOOD_PREFERENCE", "EQUIPMENT"] as const;

function draftOf(saved: Saved): Draft {
  const draft = emptyDraft();
  draft.objective = saved.objective;
  draft.trainingLevel = saved.trainingLevel;
  draft.birthDate = saved.birthDate;
  draft.biologicalSex = saved.biologicalSex ?? "";
  for (const item of saved.items) {
    if (item.kind === "INJURY") draft.injuries.push({ region: item.bodyRegion ?? "", note: item.note ?? "" });
    else if (item.kind === "DISLIKED_FOOD") draft.free.push({ kind: "DISLIKED_FOOD", text: item.otherText ?? "" });
    else if (item.catalogCode) (draft.codes[item.kind] ??= new Set()).add(item.catalogCode);
    else if (item.otherText && (item.kind === "ALLERGY" || item.kind === "INTOLERANCE" || item.kind === "RESTRICTION")) {
      draft.free.push({ kind: item.kind, text: item.otherText });
    }
  }
  for (const q of QUESTIONS) {
    const answer = saved.screening[q];
    if (answer === "YES" || answer === "NO" || answer === "PREFER_NOT_TO_SAY") draft.screening[q] = answer;
  }
  return draft;
}

function bodyOf(draft: Draft): Body {
  const items: ItemBody[] = [];
  for (const kind of CATALOG_ITEM_KINDS) for (const code of draft.codes[kind] ?? []) items.push({ kind, catalogCode: code });
  for (const f of draft.free) items.push({ kind: f.kind, otherText: f.text });
  for (const i of draft.injuries) items.push({ kind: "INJURY", bodyRegion: i.region, ...(i.note ? { note: i.note } : {}) });
  return {
    objective: draft.objective,
    trainingLevel: draft.trainingLevel,
    birthDate: draft.birthDate,
    ...(draft.biologicalSex ? { biologicalSex: draft.biologicalSex } : {}),
    items,
    screening: Object.fromEntries(QUESTIONS.map((q) => [q, draft.screening[q]!])),
  };
}

function plausibleBirthDate(value: string): boolean {
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return false;
  const now = new Date();
  const oldest = new Date(now.getFullYear() - 120, now.getMonth(), now.getDate());
  return date <= now && date >= oldest;
}

export function AnamnesisPage() {
  const { t } = useTranslation();
  const { api } = useAuth();
  const [loaded, setLoaded] = useState<"loading" | "error" | "ready">("loading");
  const [catalogs, setCatalogs] = useState<Record<string, string[]>>({});
  const [draft, setDraft] = useState<Draft>(emptyDraft());
  const [step, setStep] = useState<Step>("about");
  const [errors, setErrors] = useState<string[]>([]);
  const [saved, setSaved] = useState<Saved | undefined>();
  const [saveFailed, setSaveFailed] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const lists = await Promise.all(
          KINDS.map(async (kind) => {
            const { data } = await api.GET("/api/v1/catalogs/{kind}", { params: { path: { kind } } });
            if (!data) throw new Error("catalog");
            return [kind, data.codes] as const;
          }),
        );
        const { data, response } = await api.GET("/api/v1/me/anamnesis");
        if (cancelled) return;
        if (!response.ok && response.status !== 404) throw new Error("anamnesis");
        setCatalogs(Object.fromEntries(lists));
        if (data) setDraft(draftOf(data));
        setLoaded("ready");
      } catch {
        if (!cancelled) setLoaded("error");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api]);

  const patch = (change: Partial<Draft>) => setDraft((d) => ({ ...d, ...change }));
  const label = (kind: string, code: string) => t(`catalog.${kind}.${code}`, { defaultValue: code });

  function problems(of: Step): string[] {
    const found: string[] = [];
    if (of === "about") {
      if (!draft.objective) found.push("objective");
      if (!draft.trainingLevel) found.push("trainingLevel");
      if (!draft.birthDate) found.push("birthDateMissing");
      else if (!plausibleBirthDate(draft.birthDate)) found.push("birthDate");
    }
    if (of === "screening" && QUESTIONS.some((q) => !draft.screening[q])) found.push("screening");
    return found;
  }

  function go(to: Step) {
    setErrors([]);
    setStep(to);
  }

  function next() {
    const found = problems(step);
    if (found.length > 0) return setErrors(found);
    go(STEPS[STEPS.indexOf(step) + 1]!);
  }

  async function save() {
    const found = [...problems("about"), ...problems("screening")];
    if (found.length > 0) return setErrors(found);
    setSaving(true);
    setSaveFailed(false);
    try {
      const { data, response } = await api.PUT("/api/v1/me/anamnesis", { body: bodyOf(draft) });
      if (!response.ok || !data) throw new Error("refused");
      setSaved(data);
    } catch {
      setSaveFailed(true);
    } finally {
      setSaving(false);
    }
  }

  if (loaded === "loading") return <p>{t("auth.loading")}</p>;
  if (loaded === "error") return <p role="alert">{t("anamnesis.loadError")}</p>;
  if (saved) return <Outcome saved={saved} />;

  const stepIndex = STEPS.indexOf(step);
  return (
    <section aria-labelledby="anamnesis-title">
      <h1 id="anamnesis-title">{t("anamnesis.title")}</h1>
      <p>{t("anamnesis.progress", { current: stepIndex + 1, total: STEPS.length })}</p>
      <h2>{t(`anamnesis.steps.${step}`)}</h2>

      {step === "about" && (
        <>
          <Select id="objective" label={t("anamnesis.objective")} value={draft.objective} options={catalogs.OBJECTIVE ?? []}
            labelOf={(c) => label("OBJECTIVE", c)} onChange={(v) => patch({ objective: v })} placeholder={t("anamnesis.choose")} />
          <Select id="trainingLevel" label={t("anamnesis.trainingLevel")} value={draft.trainingLevel} options={catalogs.TRAINING_LEVEL ?? []}
            labelOf={(c) => label("TRAINING_LEVEL", c)} onChange={(v) => patch({ trainingLevel: v })} placeholder={t("anamnesis.choose")} />
          <div>
            <label htmlFor="birthDate">{t("anamnesis.birthDate")}</label>
            <input id="birthDate" type="date" value={draft.birthDate} onChange={(e) => patch({ birthDate: e.target.value })} />
          </div>
          <fieldset>
            <legend>{t("anamnesis.biologicalSex")}</legend>
            <p>{t("anamnesis.biologicalSexWhy")}</p>
            {(["FEMALE", "MALE"] as const).map((value) => (
              <label key={value}>
                <input type="radio" name="biologicalSex" checked={draft.biologicalSex === value} onChange={() => patch({ biologicalSex: value })} />{" "}
                {t(`anamnesis.sex.${value}`)}
              </label>
            ))}
            <label>
              <input type="radio" name="biologicalSex" checked={draft.biologicalSex === ""} onChange={() => patch({ biologicalSex: "" })} />{" "}
              {t("anamnesis.sex.NONE")}
            </label>
          </fieldset>
        </>
      )}

      {step === "food" && (
        <>
          <CheckList legend={t("anamnesis.allergies")} kind="ALLERGY" codes={catalogs.ALLERGY ?? []} draft={draft} label={label} onChange={patch} />
          <FreeText kind="ALLERGY" draft={draft} onChange={patch} fieldLabel={t("anamnesis.otherAllergy")} addLabel={t("anamnesis.addAllergy")} />
          <CheckList legend={t("anamnesis.intolerances")} kind="INTOLERANCE" codes={catalogs.INTOLERANCE ?? []} draft={draft} label={label} onChange={patch} />
          <FreeText kind="INTOLERANCE" draft={draft} onChange={patch} fieldLabel={t("anamnesis.otherIntolerance")} addLabel={t("anamnesis.addIntolerance")} />
          <CheckList legend={t("anamnesis.restrictions")} kind="RESTRICTION" codes={catalogs.RESTRICTION ?? []} draft={draft} label={label} onChange={patch} />
          <CheckList legend={t("anamnesis.foodPreferences")} kind="FOOD_PREFERENCE" codes={catalogs.FOOD_PREFERENCE ?? []} draft={draft} label={label} onChange={patch} />
          <FreeText kind="DISLIKED_FOOD" draft={draft} onChange={patch} fieldLabel={t("anamnesis.dislikedFood")} addLabel={t("anamnesis.addFood")} />
        </>
      )}

      {step === "training" && (
        <>
          <CheckList legend={t("anamnesis.equipment")} kind="EQUIPMENT" codes={catalogs.EQUIPMENT ?? []} draft={draft} label={label} onChange={patch} />
          <Injuries draft={draft} regions={catalogs.BODY_REGION ?? []} label={label} onChange={patch} />
        </>
      )}

      {step === "screening" && (
        <>
          <p>{t("anamnesis.screeningIntro")}</p>
          {QUESTIONS.map((q) => (
            <fieldset key={q} role="radiogroup" aria-labelledby={`q-${q}`}>
              <legend id={`q-${q}`}>{t(`anamnesis.questions.${q}`)}</legend>
              {ANSWERS.map((a) => (
                <label key={a}>
                  <input type="radio" name={q} checked={draft.screening[q] === a} onChange={() => patch({ screening: { ...draft.screening, [q]: a } })} />{" "}
                  {t(`anamnesis.answers.${a}`)}
                </label>
              ))}
            </fieldset>
          ))}
        </>
      )}

      {step === "review" && (
        <>
          <h3>{t("anamnesis.reviewTitle")}</h3>
          <p>{t("anamnesis.reviewText")}</p>
          {saveFailed && <p role="alert">{t("anamnesis.saveError")}</p>}
        </>
      )}

      {errors.length > 0 && (
        <ul role="alert">
          {errors.map((e) => (
            <li key={e}>{t(`anamnesis.errors.${e}`)}</li>
          ))}
        </ul>
      )}

      <div>
        {stepIndex > 0 && (
          <button type="button" onClick={() => go(STEPS[stepIndex - 1]!)}>
            {t("anamnesis.back")}
          </button>
        )}
        {step !== "review" ? (
          <button type="button" onClick={next}>
            {t("anamnesis.next")}
          </button>
        ) : (
          <button type="button" disabled={saving} onClick={() => void save()}>
            {t("anamnesis.save")}
          </button>
        )}
      </div>
    </section>
  );
}

function Outcome(props: { saved: Saved }) {
  const { t } = useTranslation();
  if (props.saved.eligible) {
    return (
      <section>
        <h1>{t("anamnesis.savedTitle")}</h1>
        <p>{t("anamnesis.savedText")}</p>
        <Link to="/">{t("nav.home")}</Link>
      </section>
    );
  }
  return (
    <section>
      <h1>{t("anamnesis.professionalTitle")}</h1>
      <p>{t("anamnesis.professionalText")}</p>
      <ul>
        {(props.saved.ineligibilityReasons as string[]).map((reason) => (
          <li key={reason}>{t(`anamnesis.reasons.${reason}`, { defaultValue: t("anamnesis.reasons.OTHER") })}</li>
        ))}
      </ul>
      <Link to="/">{t("nav.home")}</Link>
    </section>
  );
}

function Select(props: {
  id: string; label: string; value: string; options: string[]; placeholder: string;
  labelOf: (code: string) => string; onChange: (value: string) => void;
}) {
  return (
    <div>
      <label htmlFor={props.id}>{props.label}</label>
      <select id={props.id} value={props.value} onChange={(e) => props.onChange(e.target.value)}>
        <option value="">{props.placeholder}</option>
        {props.options.map((code) => (
          <option key={code} value={code}>
            {props.labelOf(code)}
          </option>
        ))}
      </select>
    </div>
  );
}

function CheckList(props: {
  legend: string; kind: string; codes: string[]; draft: Draft; label: (kind: string, code: string) => string; onChange: (change: Partial<Draft>) => void;
}) {
  const chosen = props.draft.codes[props.kind] ?? new Set<string>();
  return (
    <fieldset>
      <legend>{props.legend}</legend>
      {props.codes.map((code) => (
        <label key={code}>
          <input
            type="checkbox"
            checked={chosen.has(code)}
            onChange={(e) => {
              const next = new Set(chosen);
              if (e.target.checked) next.add(code);
              else next.delete(code);
              props.onChange({ codes: { ...props.draft.codes, [props.kind]: next } });
            }}
          />{" "}
          {props.label(props.kind, code)}
        </label>
      ))}
    </fieldset>
  );
}

const MAX_TEXT = 80;
const MAX_NOTE = 200;

function FreeText(props: { kind: FreeKind; draft: Draft; fieldLabel: string; addLabel: string; onChange: (change: Partial<Draft>) => void }) {
  const { t } = useTranslation();
  const [text, setText] = useState("");
  const mine = props.draft.free.filter((f) => f.kind === props.kind);
  const id = `free-${props.kind}`;

  function add() {
    const value = text.trim();
    if (!value || mine.some((f) => f.text.toLowerCase() === value.toLowerCase())) return setText("");
    props.onChange({ free: [...props.draft.free, { kind: props.kind, text: value }] });
    setText("");
  }

  return (
    <div>
      <label htmlFor={id}>{props.fieldLabel}</label>
      <input id={id} type="text" maxLength={MAX_TEXT} value={text} onChange={(e) => setText(e.target.value)} />
      <button type="button" onClick={add}>
        {props.addLabel}
      </button>
      {mine.length > 0 && (
        <ul>
          {mine.map((f) => (
            <li key={f.text}>
              {f.text}{" "}
              <button
                type="button"
                aria-label={t("anamnesis.remove", { name: f.text })}
                onClick={() => props.onChange({ free: props.draft.free.filter((x) => x !== f) })}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Injuries(props: { draft: Draft; regions: string[]; label: (kind: string, code: string) => string; onChange: (change: Partial<Draft>) => void }) {
  const { t } = useTranslation();
  const [region, setRegion] = useState("");
  const [note, setNote] = useState("");
  return (
    <fieldset>
      <legend>{t("anamnesis.injuries")}</legend>
      <label htmlFor="injury-region">{t("anamnesis.injuryRegion")}</label>
      <select id="injury-region" value={region} onChange={(e) => setRegion(e.target.value)}>
        <option value="">{t("anamnesis.choose")}</option>
        {props.regions.map((code) => (
          <option key={code} value={code}>
            {props.label("BODY_REGION", code)}
          </option>
        ))}
      </select>
      <label htmlFor="injury-note">{t("anamnesis.injuryNote")}</label>
      <input id="injury-note" type="text" maxLength={MAX_NOTE} value={note} onChange={(e) => setNote(e.target.value)} />
      <button
        type="button"
        onClick={() => {
          if (!region) return;
          props.onChange({ injuries: [...props.draft.injuries, { region, note: note.trim() }] });
          setRegion("");
          setNote("");
        }}
      >
        {t("anamnesis.addInjury")}
      </button>
      {props.draft.injuries.length > 0 && (
        <ul>
          {props.draft.injuries.map((injury, i) => (
            <li key={i}>
              {props.label("BODY_REGION", injury.region)}
              {injury.note && `: ${injury.note}`}{" "}
              <button
                type="button"
                aria-label={t("anamnesis.remove", { name: props.label("BODY_REGION", injury.region) })}
                onClick={() => props.onChange({ injuries: props.draft.injuries.filter((_, j) => j !== i) })}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </fieldset>
  );
}
