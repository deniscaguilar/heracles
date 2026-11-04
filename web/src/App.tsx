import { Link, Route, Routes } from "react-router";
import { useTranslation } from "react-i18next";
import { AuthCallback } from "./auth/AuthCallback";
import { canSeeAdmin, useAuth } from "./auth/AuthContext";
import { RequireAdminArea } from "./auth/RequireRole";
import { GatedRoutes } from "./consent/ConsentGate";
import { PoliciesPage } from "./admin/PoliciesPage";
import { ConsentsPage } from "./consent/ConsentsPage";
import { PrivacyPage } from "./privacy/PrivacyPage";
import { AnamnesisPage } from "./profile/AnamnesisPage";
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
  const { session } = useAuth();
  return (
    <>
      <h1>{t("admin.title")}</h1>
      <p>{t("admin.description")}</p>
      {session.status === "authenticated" && session.role === "ADMIN" && <Link to="/admin/policies">{t("admin.policies")}</Link>}
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
      {session.status === "authenticated" && (
        <>
          <Link to="/anamnesis">{t("nav.anamnesis")}</Link>
          <Link to="/consents">{t("nav.consents")}</Link>
          <Link to="/privacy">{t("nav.privacy")}</Link>
        </>
      )}
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
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route element={<GatedRoutes />}>
            <Route path="/" element={<Home />} />
            <Route path="/anamnesis" element={<AnamnesisPage />} />
            <Route path="/consents" element={<ConsentsPage />} />
            <Route path="/privacy" element={<PrivacyPage />} />
            <Route
              path="/admin"
              element={
                <RequireAdminArea>
                  <Admin />
                </RequireAdminArea>
              }
            />
            <Route
              path="/admin/policies"
              element={
                <RequireAdminArea adminOnly>
                  <PoliciesPage />
                </RequireAdminArea>
              }
            />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </main>
    </>
  );
}
