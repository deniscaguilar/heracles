-- Catalogs of the anamnesis (spec RF8): seed data, read by anyone, changed only by a migration (editing them from the
-- backoffice is out of scope for stage 2). The Portuguese label is the clients' i18n job, by `code`. No user_id here.
SET LOCAL ROLE heracles_owner;

CREATE TABLE profile.catalog_item (
    kind       text NOT NULL CHECK (kind IN (
        'OBJECTIVE', 'TRAINING_LEVEL', 'ALLERGY', 'INTOLERANCE', 'RESTRICTION', 'FOOD_PREFERENCE', 'EQUIPMENT', 'BODY_REGION')),
    code       text NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    sort_order integer NOT NULL,
    PRIMARY KEY (kind, code)
);

REVOKE ALL ON profile.catalog_item FROM heracles_app;
GRANT SELECT ON profile.catalog_item TO heracles_app;

INSERT INTO profile.catalog_item (kind, code, sort_order) VALUES
    ('OBJECTIVE', 'WEIGHT_LOSS', 1), ('OBJECTIVE', 'MUSCLE_GAIN', 2), ('OBJECTIVE', 'GENERAL_HEALTH', 3), ('OBJECTIVE', 'PERFORMANCE', 4),
    ('TRAINING_LEVEL', 'BEGINNER', 1), ('TRAINING_LEVEL', 'INTERMEDIATE', 2), ('TRAINING_LEVEL', 'ADVANCED', 3),
    ('ALLERGY', 'PEANUT', 1), ('ALLERGY', 'TREE_NUTS', 2), ('ALLERGY', 'MILK', 3), ('ALLERGY', 'EGG', 4), ('ALLERGY', 'WHEAT', 5),
    ('ALLERGY', 'SOY', 6), ('ALLERGY', 'FISH', 7), ('ALLERGY', 'SHELLFISH', 8), ('ALLERGY', 'SESAME', 9),
    ('INTOLERANCE', 'LACTOSE', 1), ('INTOLERANCE', 'GLUTEN', 2), ('INTOLERANCE', 'FRUCTOSE', 3), ('INTOLERANCE', 'CAFFEINE', 4),
    ('RESTRICTION', 'NO_PORK', 1), ('RESTRICTION', 'NO_BEEF', 2), ('RESTRICTION', 'NO_ALCOHOL', 3), ('RESTRICTION', 'HALAL', 4), ('RESTRICTION', 'KOSHER', 5),
    ('FOOD_PREFERENCE', 'VEGETARIAN', 1), ('FOOD_PREFERENCE', 'VEGAN', 2), ('FOOD_PREFERENCE', 'PESCETARIAN', 3), ('FOOD_PREFERENCE', 'LOW_CARB', 4),
    ('EQUIPMENT', 'BODYWEIGHT', 1), ('EQUIPMENT', 'DUMBBELLS', 2), ('EQUIPMENT', 'BARBELL', 3), ('EQUIPMENT', 'KETTLEBELL', 4),
    ('EQUIPMENT', 'RESISTANCE_BANDS', 5), ('EQUIPMENT', 'PULL_UP_BAR', 6), ('EQUIPMENT', 'BENCH', 7), ('EQUIPMENT', 'MACHINES', 8),
    ('EQUIPMENT', 'TREADMILL', 9), ('EQUIPMENT', 'STATIONARY_BIKE', 10),
    ('BODY_REGION', 'NECK', 1), ('BODY_REGION', 'SHOULDER', 2), ('BODY_REGION', 'ELBOW', 3), ('BODY_REGION', 'WRIST', 4),
    ('BODY_REGION', 'UPPER_BACK', 5), ('BODY_REGION', 'LOWER_BACK', 6), ('BODY_REGION', 'HIP', 7), ('BODY_REGION', 'KNEE', 8),
    ('BODY_REGION', 'ANKLE', 9), ('BODY_REGION', 'OTHER', 10);
