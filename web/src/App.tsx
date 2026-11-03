import { Link, Route, Routes } from "react-router";
import { useTranslation } from "react-i18next";
import { AuthCallback } from "./auth/AuthCallback";
import { canSeeAdmin, useAuth } from "./auth/AuthContext";
import { RequireAdminArea } from "./auth/RequireRole";
import { OfflineNotice } from "./OfflineNotice";

function Home() {
  const { t } = useTranslation();
  const { session, login } = useAuth();
  return (
    <>
      <h1>{t("app.name")}</h1>
      <p>{t("app.tagline")}</p>
      {session.status === "anonymous" && (
        <button type="button" onClick={() => void login()}>
          {t("auth.signIn")}
        </button>
      )}
      {session.status === "authenticated" && (
        <p>{t("home.signedInAs", { role: t(`roles.${session.role.toLowerCase()}`) })}</p>
      )}
      {session.status === "error" && <p role="alert">{t("auth.error")}</p>}
    </>
  );
}

function Admin() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t("admin.title")}</h1>
      <p>{t("admin.description")}</p>
    </>
  );
}

function NotFound() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t("notFound.title")}</h1>
      <Link to="/">{t("notFound.back")}</Link>
    </>
  );
}

function Nav() {
  const { t } = useTranslation();
  const { session, logout } = useAuth();
  return (
    <nav aria-label={t("nav.label")}>
      <Link to="/">{t("nav.home")}</Link>
      {canSeeAdmin(session) && <Link to="/admin">{t("nav.admin")}</Link>}
      {session.status === "authenticated" && (
        <button type="button" onClick={() => void logout()}>
          {t("auth.signOut")}
        </button>
      )}
    </nav>
  );
}

export function App() {
  return (
    <>
      <OfflineNotice />
      <Nav />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route
            path="/admin"
            element={
              <RequireAdminArea>
                <Admin />
              </RequireAdminArea>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </>
  );
}
