import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PolicyBlocks, type Block } from "./PolicyBlocks";

const text = (value: string) => ({ type: "text", text: value });

describe("PolicyBlocks (E2-S10.1)", () => {
  it("draws headings, paragraphs with emphasis and breaks, and lists", () => {
    const blocks: Block[] = [
      { type: "heading", level: 2, children: [text("Seus dados")] },
      {
        type: "paragraph",
        children: [text("Leia "), { type: "strong", children: [text("com atenção")] }, { type: "break" }, { type: "emphasis", children: [text("sempre")] }],
      },
      { type: "list", ordered: true, items: [[text("primeiro")], [text("segundo")]] },
      { type: "list", ordered: false, items: [[text("um")], [text("outro")]] },
    ];
    const { container } = render(<PolicyBlocks blocks={blocks} />);

    expect(screen.getByRole("heading", { level: 2, name: "Seus dados" })).toBeInTheDocument();
    expect(container.querySelector("strong")).toHaveTextContent("com atenção");
    expect(container.querySelector("em")).toHaveTextContent("sempre");
    expect(container.querySelector("br")).not.toBeNull();
    expect(container.querySelector("ol")?.querySelectorAll("li")).toHaveLength(2);
    expect(container.querySelector("ul")?.querySelectorAll("li")).toHaveLength(2);
  });

  it("draws an https link that opens apart, and a mailto link", () => {
    render(
      <PolicyBlocks
        blocks={[
          {
            type: "paragraph",
            children: [
              { type: "link", href: "https://heracles.example/termos", children: [text("termos")] },
              { type: "link", href: "mailto:dpo@heracles.example", children: [text("escreva para nós")] },
            ],
          },
        ]}
      />,
    );
    const web = screen.getByRole("link", { name: "termos" });
    expect(web).toHaveAttribute("href", "https://heracles.example/termos");
    expect(web).toHaveAttribute("target", "_blank");
    expect(web).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.getByRole("link", { name: "escreva para nós" })).toHaveAttribute("href", "mailto:dpo@heracles.example");
  });

  it("injects no HTML: markup in the text is shown as text, and a link of another scheme is only text", () => {
    const { container } = render(
      <PolicyBlocks
        blocks={[
          {
            type: "paragraph",
            children: [
              text('<img src=x onerror="alert(1)"><script>alert(2)</script>'),
              { type: "link", href: "javascript:alert(3)", children: [text("clique")] },
            ],
          },
        ]}
      />,
    );
    expect(container.querySelector("img, script")).toBeNull();
    expect(container).toHaveTextContent('<img src=x onerror="alert(1)"><script>alert(2)</script>');
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(container).toHaveTextContent("clique");
  });

  it("ignores a block or inline type it does not know instead of guessing", () => {
    const { container } = render(
      <PolicyBlocks blocks={[{ type: "table" } as Block, { type: "paragraph", children: [{ type: "image", href: "https://x" }, text("ok")] }]} />,
    );
    expect(container).toHaveTextContent("ok");
    expect(container.querySelector("img, table")).toBeNull();
  });
});
