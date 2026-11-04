import { ALL_TYPES, consentItem, meRoute, routedBackend } from "../test-utils";

export const CATALOGS: Record<string, string[]> = {
  OBJECTIVE: ["WEIGHT_LOSS", "MUSCLE_GAIN", "GENERAL_HEALTH", "PERFORMANCE"],
  TRAINING_LEVEL: ["BEGINNER", "INTERMEDIATE", "ADVANCED"],
  ALLERGY: ["PEANUT", "TREE_NUTS", "MILK"],
  INTOLERANCE: ["LACTOSE", "GLUTEN"],
  RESTRICTION: ["NO_PORK", "HALAL"],
  FOOD_PREFERENCE: ["VEGETARIAN", "VEGAN"],
  EQUIPMENT: ["BODYWEIGHT", "DUMBBELLS", "BARBELL"],
  BODY_REGION: ["KNEE", "LOWER_BACK", "OTHER"],
};

/** A backend where everything is accepted and the anamnesis does not exist yet. */
export function anamnesisBackend(save: Parameters<typeof routedBackend>[0][string] = { status: 500 }) {
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": { status: 200, body: { items: ALL_TYPES.map((t) => consentItem(t, { granted: true })) } },
    "GET /api/v1/me/anamnesis": { status: 404, body: { code: "not_found" } },
    "PUT /api/v1/me/anamnesis": save,
  });
  for (const [kind, codes] of Object.entries(CATALOGS)) {
    api.routes[`GET /api/v1/catalogs/${kind}`] = { status: 200, body: { kind, codes } };
  }
  return api;
}

export const savedRevision = (over: Record<string, unknown> = {}) => ({
  revision: 1,
  createdAt: "2026-11-04T12:00:00Z",
  objective: "GENERAL_HEALTH",
  trainingLevel: "BEGINNER",
  birthDate: "1990-05-01",
  biologicalSex: null,
  items: [],
  screening: {},
  eligible: true,
  ineligibilityReasons: [],
  ...over,
});
