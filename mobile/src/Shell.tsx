import { ActivityIndicator, Button, StyleSheet, Text, View } from "react-native";
import { useTranslation } from "react-i18next";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useAuth } from "./auth/AuthContext";
import { useState } from "react";
import { ConsentGate } from "./consent/ConsentGate";
import { ConsentsScreen } from "./consent/ConsentsScreen";
import { PrivacyScreen } from "./privacy/PrivacyScreen";
import { AnamnesisScreen } from "./profile/AnamnesisScreen";

type Screen = "home" | "anamnesis" | "consents" | "privacy";

// No navigation library (it is chosen in stage 5): the screen is a piece of state, and "Voltar" returns home.
function Signed(props: { role: string }) {
  const { t } = useTranslation();
  const { logout } = useAuth();
  const [screen, setScreen] = useState<Screen>("home");
  if (screen !== "home") {
    return (
      <View style={styles.screen}>
        <Button title={t("nav.back")} onPress={() => setScreen("home")} />
        {screen === "anamnesis" && <AnamnesisScreen />}
        {screen === "consents" && <ConsentsScreen />}
        {screen === "privacy" && <PrivacyScreen />}
      </View>
    );
  }
  return (
    <>
      <Text testID="signed-in-as">{t("home.signedInAs", { role: t(`roles.${props.role.toLowerCase()}`) })}</Text>
      {props.role !== "USER" && <Text>{t("home.adminOnWeb")}</Text>}
      <Button title={t("nav.anamnesis")} onPress={() => setScreen("anamnesis")} />
      <Button title={t("nav.consents")} onPress={() => setScreen("consents")} />
      <Button title={t("nav.privacy")} onPress={() => setScreen("privacy")} />
      <Button title={t("auth.signOut")} onPress={() => void logout()} />
    </>
  );
}

export function Shell() {
  const { t } = useTranslation();
  const { session, login, logout, retry } = useAuth();
  // The device keeps areas for itself (notch, status bar, home indicator): the content starts after them.
  const insets = useSafeAreaInsets();

  return (
    <View
      testID="shell"
      style={[styles.container, { paddingTop: styles.container.padding + insets.top, paddingBottom: styles.container.padding + insets.bottom, paddingLeft: styles.container.padding + insets.left, paddingRight: styles.container.padding + insets.right }]}
    >
      <Text accessibilityRole="header" style={styles.title}>
        {t("app.name")}
      </Text>
      <Text>{t("app.tagline")}</Text>

      {session.status === "loading" && <ActivityIndicator accessibilityLabel={t("auth.loading")} />}

      {session.status === "anonymous" && <Button title={t("auth.signIn")} onPress={() => void login()} />}

      {session.status === "authenticated" && (
        <ConsentGate>
          <Signed role={session.role} />
        </ConsentGate>
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
  screen: { flex: 1, alignSelf: "stretch" },
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 12, padding: 24 },
  title: { fontSize: 28, fontWeight: "700" },
});
