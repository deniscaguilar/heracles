import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { canSeeAdmin, useAuth } from "./AuthContext";

// Hides a screen from profiles that must not see it. The backend still refuses the calls (403): this is
// only what the shell shows.
export function RequireAdminArea(props: { children: ReactNode; /** Only administrators, not moderators. */ adminOnly?: boolean }) {
  const { t } = useTranslation();
  const { session, login } = useAuth();

  if (session.status === "loading") return <p>{t("auth.loading")}</p>;
  if (session.status === "error") return <p role="alert">{t("auth.error")}</p>;
  if (session.status === "anonymous") {
    return (
      <>
        <p>{t("auth.signInRequired")}</p>
        <button type="button" onClick={() => void login()}>
          {t("auth.signIn")}
        </button>
      </>
    );
  }
  if (!canSeeAdmin(session) || (props.adminOnly && session.role !== "ADMIN")) {
    return (
      <>
        <h1>{t("forbidden.title")}</h1>
        <p>{t("forbidden.message")}</p>
      </>
    );
  }
  return <>{props.children}</>;
}
