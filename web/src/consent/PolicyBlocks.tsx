import type { ReactNode } from "react";
import type { components } from "@heracles/api-client";

export type Block = components["schemas"]["BlockResponse"];
export type Inline = components["schemas"]["InlineResponse"];

// The backend only lets https and mailto links through; the screen checks again, so a wrong block never becomes a link.
const SAFE_LINK = /^(https:\/\/|mailto:)/i;

function inlines(children: Inline[] | null | undefined): ReactNode[] {
  return (children ?? []).map((node, i) => {
    switch (node.type) {
      case "text":
        return node.text ?? "";
      case "strong":
        return <strong key={i}>{inlines(node.children as Inline[])}</strong>;
      case "emphasis":
        return <em key={i}>{inlines(node.children as Inline[])}</em>;
      case "break":
        return <br key={i} />;
      case "link":
        return node.href && SAFE_LINK.test(node.href) ? (
          <a key={i} href={node.href} target="_blank" rel="noopener noreferrer">
            {inlines(node.children as Inline[])}
          </a>
        ) : (
          inlines(node.children as Inline[])
        );
      default:
        return null; // an unknown kind is dropped, never guessed
    }
  });
}

/** Draws the restricted blocks of a policy with React elements only: no Markdown library and no `innerHTML`. */
export function PolicyBlocks(props: { blocks: Block[] }) {
  return (
    <div className="policy-text">
      {props.blocks.map((block, i) => {
        switch (block.type) {
          case "paragraph":
            return <p key={i}>{inlines(block.children as Inline[])}</p>;
          case "heading": {
            const Tag = `h${Math.min(Math.max(block.level ?? 2, 1), 6)}` as "h1";
            return <Tag key={i}>{inlines(block.children as Inline[])}</Tag>;
          }
          case "list": {
            const Tag = block.ordered ? "ol" : "ul";
            return (
              <Tag key={i}>
                {(block.items as Inline[][] | null | undefined)?.map((item, j) => <li key={j}>{inlines(item)}</li>)}
              </Tag>
            );
          }
          default:
            return null;
        }
      })}
    </div>
  );
}
