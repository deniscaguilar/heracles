import { ActivityIndicator, Button, StyleSheet, Text, View } from "react-native";
import { useTranslation } from "react-i18next";
import { useAuth } from "./auth/AuthContext";

export function Shell() {
  const { t } = useTranslation();
  const { session, login, logout, retry } = useAuth();

  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        {t("app.name")}
      </Text>
      <Text>{t("app.tagline")}</Text>

      {session.status === "loading" && <ActivityIndicator accessibilityLabel={t("auth.loading")} />}

      {session.status === "anonymous" && <Button title={t("auth.signIn")} onPress={() => void login()} />}

      {session.status === "authenticated" && (
        <>
          <Text testID="signed-in-as">{t("home.signedInAs", { role: t(`roles.${session.role.toLowerCase()}`) })}</Text>
          {session.role !== "USER" && <Text>{t("home.adminOnWeb")}</Text>}
          <Button title={t("auth.signOut")} onPress={() => void logout()} />
        </>
      )}

      {session.status === "error" && (
        <>
          <Text accessibilityRole="alert">{t("auth.error")}</Text>
          <Button title={t("auth.retry")} onPress={() => void retry()} />
          <Button title={t("auth.signOut")} onPress={() => void logout()} />
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 12, padding: 24 },
  title: { fontSize: 28, fontWeight: "700" },
});
