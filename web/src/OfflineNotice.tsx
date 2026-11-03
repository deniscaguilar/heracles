import { useTranslation } from "react-i18next";
import { useOnline } from "./useOnline";

// The web app has no offline mode (offline belongs to the mobile app): it only says so.
export function OfflineNotice() {
  const { t } = useTranslation();
  if (useOnline()) return null;
  return (
    <div role="alert" className="offline">
      <strong>{t("offline.title")}</strong> {t("offline.message")}
    </div>
  );
}
