import { Link, Route, Routes } from "react-router";
import { useTranslation } from "react-i18next";
import { OfflineNotice } from "./OfflineNotice";

function Home() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t("app.name")}</h1>
      <p>{t("app.tagline")}</p>
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

export function App() {
  return (
    <>
      <OfflineNotice />
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </>
  );
}
