import { fireEvent, render, screen } from "@testing-library/react-native";
import { Linking } from "react-native";
import { PolicyBlocks, type Block } from "./PolicyBlocks";

const text = (value: string) => ({ type: "text", text: value });

describe("PolicyBlocks (E2-S11.1)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(Linking, "openURL").mockResolvedValue();
  });
  afterEach(() => jest.restoreAllMocks());

  it("draws headings, paragraphs with emphasis, and ordered and unordered lists", async () => {
    const blocks: Block[] = [
      { type: "heading", level: 2, children: [text("Seus dados")] },
      { type: "paragraph", children: [text("Leia "), { type: "strong", children: [text("com atenção")] }, { type: "break" }, { type: "emphasis", children: [text("sempre")] }] },
      { type: "list", ordered: true, items: [[text("primeiro")], [text("segundo")]] },
      { type: "list", ordered: false, items: [[text("um")]] },
    ];
    await render(<PolicyBlocks blocks={blocks} />);
    expect(screen.getByRole("header", { name: "Seus dados" })).toBeOnTheScreen();
    expect(screen.getByText("com atenção")).toBeOnTheScreen();
    expect(screen.getByText("sempre")).toBeOnTheScreen();
    expect(screen.getByText("1.")).toBeOnTheScreen();
    expect(screen.getByText("2.")).toBeOnTheScreen();
    expect(screen.getByText("primeiro")).toBeOnTheScreen();
    expect(screen.getByText("•")).toBeOnTheScreen();
  });

  it("opens an https link and a mailto link outside the app", async () => {
    await render(
      <PolicyBlocks
        blocks={[{ type: "paragraph", children: [
          { type: "link", href: "https://heracles.example/termos", children: [text("termos")] },
          { type: "link", href: "mailto:dpo@heracles.example", children: [text("escreva")] },
        ] }]}
      />,
    );
    await fireEvent.press(screen.getByRole("link", { name: "termos" }));
    expect(Linking.openURL).toHaveBeenCalledWith("https://heracles.example/termos");
    await fireEvent.press(screen.getByRole("link", { name: "escreva" }));
    expect(Linking.openURL).toHaveBeenCalledWith("mailto:dpo@heracles.example");
  });

  it("injects nothing: markup is shown as text, and a link of another scheme is only text", async () => {
    await render(
      <PolicyBlocks
        blocks={[{ type: "paragraph", children: [text("<script>alert(1)</script>"), { type: "link", href: "javascript:alert(2)", children: [text("clique")] }] }]}
      />,
    );
    expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeOnTheScreen();
    expect(screen.queryByRole("link")).not.toBeOnTheScreen();
    expect(Linking.openURL).not.toHaveBeenCalled();
  });

  it("drops a block or an inline type it does not know", async () => {
    await render(<PolicyBlocks blocks={[{ type: "table" } as Block, { type: "paragraph", children: [{ type: "image", href: "https://x" }, text("ok")] }]} />);
    expect(screen.getByText("ok")).toBeOnTheScreen();
  });
});
