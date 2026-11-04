import { useEffect, useState } from "react";
import { Button, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
import { Choice } from "../ui";

type Body = components["schemas"]["AnamnesisBody"];
type ItemBody = components["schemas"]["AnamnesisItemBody"];
type Saved = components["schemas"]["AnamnesisResponse"];
type CatalogKind = components["schemas"]["CatalogResponse"]["kind"];

const KINDS: CatalogKind[] = ["OBJECTIVE", "TRAINING_LEVEL", "ALLERGY", "INTOLERANCE", "RESTRICTION", "FOOD_PREFERENCE", "EQUIPMENT", "BODY_REGION"];
const QUESTIONS = ["PREGNANT_OR_NURSING", "CONDITION_REQUIRING_CARE", "MEDICATION_AFFECTING_PLAN", "EATING_DISORDER_HISTORY"] as const;
const ANSWERS = ["YES", "NO", "PREFER_NOT_TO_SAY"] as const;
type Question = (typeof QUESTIONS)[number];
type Answer = (typeof ANSWERS)[number];
const STEPS = ["about", "food", "training", "screening", "review"] as const;
type Step = (typeof STEPS)[number];
type FreeKind = "ALLERGY" | "INTOLERANCE" | "RESTRICTION" | "DISLIKED_FOOD";

interface Draft {
  objective: string;
  trainingLevel: string;
  birthDate: string;
  biologicalSex: "" | "FEMALE" | "MALE";
  codes: Record<string, Set<string>>;
  free: { kind: FreeKind; text: string }[];
  injuries: { region: string; note: string }[];
  screening: Partial<Record<Question, Answer>>;
}

const emptyDraft = (): Draft => ({ objective: "", trainingLevel: "", birthDate: "", biologicalSex: "", codes: {}, free: [], injuries: [], screening: {} });
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

/** There is no date picker (no new dependency): the person types AAAA-MM-DD, and it has to be a real, believable date. */
function plausibleBirthDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return false;
  const now = new Date();
  return date <= now && date >= new Date(now.getFullYear() - 120, now.getMonth(), now.getDate());
}

export function AnamnesisScreen() {
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

  if (loaded === "loading") return <Text>{t("auth.loading")}</Text>;
  if (loaded === "error") return <Text accessibilityRole="alert">{t("anamnesis.loadError")}</Text>;
  if (saved) return <Outcome saved={saved} />;

  const stepIndex = STEPS.indexOf(step);
  const options = (kind: string, value: string, set: (v: string) => void) =>
    (catalogs[kind] ?? []).map((code) => (
      <Choice key={code} role="radio" label={label(kind, code)} selected={value === code} onPress={() => set(code)} />
    ));

  return (
    <ScrollView contentContainerStyle={styles.box}>
      <Text accessibilityRole="header" style={styles.title}>{t("anamnesis.title")}</Text>
      <Text>{t("anamnesis.progress", { current: stepIndex + 1, total: STEPS.length })}</Text>
      <Text accessibilityRole="header" style={styles.subtitle}>{t(`anamnesis.steps.${step}`)}</Text>

      {step === "about" && (
        <>
          <Text style={styles.label}>{t("anamnesis.objective")}</Text>
          {options("OBJECTIVE", draft.objective, (v) => patch({ objective: v }))}
          <Text style={styles.label}>{t("anamnesis.trainingLevel")}</Text>
          {options("TRAINING_LEVEL", draft.trainingLevel, (v) => patch({ trainingLevel: v }))}
          <Text style={styles.label}>{t("anamnesis.birthDate")}</Text>
          <Text>{t("anamnesis.birthDateHint")}</Text>
          <TextInput
            accessibilityLabel={t("anamnesis.birthDate")}
            style={styles.input}
            value={draft.birthDate}
            onChangeText={(v) => patch({ birthDate: v })}
            keyboardType="numbers-and-punctuation"
            maxLength={10}
            placeholder="AAAA-MM-DD"
          />
          <Text style={styles.label}>{t("anamnesis.biologicalSex")}</Text>
          <Text>{t("anamnesis.biologicalSexWhy")}</Text>
          {(["FEMALE", "MALE"] as const).map((value) => (
            <Choice key={value} role="radio" label={t(`anamnesis.sex.${value}`)} selected={draft.biologicalSex === value} onPress={() => patch({ biologicalSex: value })} />
          ))}
          <Choice role="radio" label={t("anamnesis.sex.NONE")} selected={draft.biologicalSex === ""} onPress={() => patch({ biologicalSex: "" })} />
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
          <Text>{t("anamnesis.screeningIntro")}</Text>
          {QUESTIONS.map((q) => (
            <View key={q} accessibilityRole="radiogroup" accessibilityLabel={t(`anamnesis.questions.${q}`)}>
              <Text style={styles.label}>{t(`anamnesis.questions.${q}`)}</Text>
              {ANSWERS.map((a) => (
                <Choice key={a} role="radio" label={t(`anamnesis.answers.${a}`)} selected={draft.screening[q] === a} onPress={() => patch({ screening: { ...draft.screening, [q]: a } })} />
              ))}
            </View>
          ))}
        </>
      )}

      {step === "review" && (
        <>
          <Text style={styles.label}>{t("anamnesis.reviewTitle")}</Text>
          <Text>{t("anamnesis.reviewText")}</Text>
          {saveFailed && <Text accessibilityRole="alert">{t("anamnesis.saveError")}</Text>}
        </>
      )}

      {errors.map((e) => (
        <Text key={e} accessibilityRole="alert" style={styles.error}>{t(`anamnesis.errors.${e}`)}</Text>
      ))}

      <View style={styles.row}>
        {stepIndex > 0 && <Button title={t("anamnesis.back")} onPress={() => go(STEPS[stepIndex - 1]!)} />}
        {step !== "review" ? (
          <Button title={t("anamnesis.next")} onPress={next} />
        ) : (
          <Button title={t("anamnesis.save")} disabled={saving} onPress={() => void save()} />
        )}
      </View>
    </ScrollView>
  );
}

function Outcome(props: { saved: Saved }) {
  const { t } = useTranslation();
  if (props.saved.eligible) {
    return (
      <View style={styles.box}>
        <Text accessibilityRole="header" style={styles.title}>{t("anamnesis.savedTitle")}</Text>
        <Text>{t("anamnesis.savedText")}</Text>
      </View>
    );
  }
  return (
    <View style={styles.box}>
      <Text accessibilityRole="header" style={styles.title}>{t("anamnesis.professionalTitle")}</Text>
      <Text>{t("anamnesis.professionalText")}</Text>
      {(props.saved.ineligibilityReasons as string[]).map((reason) => (
        <Text key={reason}>{t(`anamnesis.reasons.${reason}`, { defaultValue: t("anamnesis.reasons.OTHER") })}</Text>
      ))}
    </View>
  );
}

function CheckList(props: {
  legend: string; kind: string; codes: string[]; draft: Draft; label: (kind: string, code: string) => string; onChange: (change: Partial<Draft>) => void;
}) {
  const chosen = props.draft.codes[props.kind] ?? new Set<string>();
  return (
    <View>
      <Text style={styles.label}>{props.legend}</Text>
      {props.codes.map((code) => (
        <Choice
          key={code}
          role="checkbox"
          label={props.label(props.kind, code)}
          selected={chosen.has(code)}
          onPress={() => {
            const next = new Set(chosen);
            if (next.has(code)) next.delete(code);
            else next.add(code);
            props.onChange({ codes: { ...props.draft.codes, [props.kind]: next } });
          }}
        />
      ))}
    </View>
  );
}

const MAX_TEXT = 80;
const MAX_NOTE = 200;

function FreeText(props: { kind: FreeKind; draft: Draft; fieldLabel: string; addLabel: string; onChange: (change: Partial<Draft>) => void }) {
  const { t } = useTranslation();
  const [text, setText] = useState("");
  const mine = props.draft.free.filter((f) => f.kind === props.kind);

  function add() {
    const value = text.trim();
    if (!value || mine.some((f) => f.text.toLowerCase() === value.toLowerCase())) return setText("");
    props.onChange({ free: [...props.draft.free, { kind: props.kind, text: value }] });
    setText("");
  }

  return (
    <View>
      <Text style={styles.label}>{props.fieldLabel}</Text>
      <TextInput accessibilityLabel={props.fieldLabel} style={styles.input} maxLength={MAX_TEXT} value={text} onChangeText={setText} />
      <Button title={props.addLabel} onPress={add} />
      {mine.map((f) => (
        <View key={f.text} style={styles.row}>
          <Text>{f.text}</Text>
          <Button title="×" accessibilityLabel={t("anamnesis.remove", { name: f.text })} onPress={() => props.onChange({ free: props.draft.free.filter((x) => x !== f) })} />
        </View>
      ))}
    </View>
  );
}

function Injuries(props: { draft: Draft; regions: string[]; label: (kind: string, code: string) => string; onChange: (change: Partial<Draft>) => void }) {
  const { t } = useTranslation();
  const [region, setRegion] = useState("");
  const [note, setNote] = useState("");
  return (
    <View>
      <Text style={styles.label}>{t("anamnesis.injuries")}</Text>
      <Text>{t("anamnesis.injuryRegion")}</Text>
      {props.regions.map((code) => (
        <Choice key={code} role="radio" label={props.label("BODY_REGION", code)} selected={region === code} onPress={() => setRegion(code)} />
      ))}
      <Text>{t("anamnesis.injuryNote")}</Text>
      <TextInput accessibilityLabel={t("anamnesis.injuryNote")} style={styles.input} maxLength={MAX_NOTE} value={note} onChangeText={setNote} />
      <Button
        title={t("anamnesis.addInjury")}
        onPress={() => {
          if (!region) return;
          props.onChange({ injuries: [...props.draft.injuries, { region, note: note.trim() }] });
          setRegion("");
          setNote("");
        }}
      />
      {props.draft.injuries.map((injury, i) => (
        <View key={i} style={styles.row}>
          <Text>{props.label("BODY_REGION", injury.region)}{injury.note ? `: ${injury.note}` : ""}</Text>
          <Button
            title="×"
            accessibilityLabel={t("anamnesis.remove", { name: props.label("BODY_REGION", injury.region) })}
            onPress={() => props.onChange({ injuries: props.draft.injuries.filter((_, j) => j !== i) })}
          />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { padding: 24, gap: 8 },
  title: { fontSize: 24, fontWeight: "700" },
  subtitle: { fontSize: 18, fontWeight: "700", marginTop: 8 },
  label: { fontWeight: "600", marginTop: 8 },
  input: { borderWidth: 1, borderColor: "#999", borderRadius: 6, padding: 8 },
  row: { flexDirection: "row", gap: 12, alignItems: "center" },
  error: { color: "#b00020" },
});
