import type { ReactNode } from "react";
import { Linking, StyleSheet, Text, View } from "react-native";
import type { components } from "@heracles/api-client";

export type Block = components["schemas"]["BlockResponse"];
export type Inline = components["schemas"]["InlineResponse"];

// The backend only lets https and mailto links through; the screen checks again, so a wrong block never opens anything.
const SAFE_LINK = /^(https:\/\/|mailto:)/i;

function inlines(children: Inline[] | null | undefined): ReactNode[] {
  return (children ?? []).map((node, i) => {
    switch (node.type) {
      case "text":
        return node.text ?? "";
      case "strong":
        return <Text key={i} style={styles.strong}>{inlines(node.children as Inline[])}</Text>;
      case "emphasis":
        return <Text key={i} style={styles.emphasis}>{inlines(node.children as Inline[])}</Text>;
      case "break":
        return "\n";
      case "link":
        return node.href && SAFE_LINK.test(node.href) ? (
          <Text key={i} accessibilityRole="link" style={styles.link} onPress={() => void Linking.openURL(node.href!)}>
            {inlines(node.children as Inline[])}
          </Text>
        ) : (
          inlines(node.children as Inline[])
        );
      default:
        return null; // an unknown kind is dropped, never guessed
    }
  });
}

/** Draws the restricted blocks of a policy with native views only: no Markdown library and no web view. */
export function PolicyBlocks(props: { blocks: Block[] }) {
  return (
    <View style={styles.text}>
      {props.blocks.map((block, i) => {
        switch (block.type) {
          case "paragraph":
            return <Text key={i}>{inlines(block.children as Inline[])}</Text>;
          case "heading":
            return <Text key={i} accessibilityRole="header" style={styles.heading}>{inlines(block.children as Inline[])}</Text>;
          case "list":
            return (
              <View key={i}>
                {(block.items as Inline[][] | null | undefined)?.map((item, j) => (
                  <View key={j} style={styles.item}>
                    <Text>{block.ordered ? `${j + 1}.` : "•"}</Text>
                    <Text style={styles.itemText}>{inlines(item)}</Text>
                  </View>
                ))}
              </View>
            );
          default:
            return null;
        }
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  text: { gap: 8 },
  strong: { fontWeight: "700" },
  emphasis: { fontStyle: "italic" },
  link: { color: "#0a58ca", textDecorationLine: "underline" },
  heading: { fontSize: 18, fontWeight: "700" },
  item: { flexDirection: "row", gap: 6 },
  itemText: { flex: 1 },
});
