import { Pressable, StyleSheet, Text, type StyleProp, type ViewStyle } from "react-native";

/** A checkbox, a radio or a plain option: one row that says whether it is chosen. */
export function Choice(props: {
  role: "checkbox" | "radio";
  label: string;
  selected: boolean;
  onPress: () => void;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Pressable
      accessibilityRole={props.role}
      accessibilityState={{ checked: props.selected }}
      accessibilityLabel={props.label}
      onPress={props.onPress}
      style={[styles.choice, props.selected && styles.chosen, props.style]}
    >
      <Text>{props.selected ? (props.role === "checkbox" ? "☑ " : "◉ ") : props.role === "checkbox" ? "☐ " : "○ "}{props.label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  choice: { paddingVertical: 8, paddingHorizontal: 4 },
  chosen: { opacity: 1 },
});
