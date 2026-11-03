import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router";
import { useAuth } from "./AuthContext";

// Route the identity provider redirects back to. The code can be exchanged only once.
export function AuthCallback() {
  const { t } = useTranslation();
  const { completeLogin } = useAuth();
  const started = useRef(false);
  const [outcome, setOutcome] = useState<"pending" | "done" | "failed">("pending");

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    completeLogin().then(
      () => setOutcome("done"),
      () => setOutcome("failed"),
    );
  }, [completeLogin]);

  if (outcome === "done") return <Navigate to="/" replace />;
  if (outcome === "failed") return <p role="alert">{t("auth.failed")}</p>;
  return <p>{t("auth.loading")}</p>;
}
