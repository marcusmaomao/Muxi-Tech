import { describe, expect, it } from "vitest";
import { layeredLayout } from "../../src/canvas/layeredLayout";

describe("layered layout", () => {
  it("places a parent above its child", () => {
    const points = layeredLayout(
      [{ id: "child" }, { id: "parent" }],
      [{ id: "e1", source: "parent", target: "child" }],
    );
    const parent = points.find((point) => point.id === "parent");
    const child = points.find((point) => point.id === "child");
    expect(parent && child && parent.y < child.y).toBe(true);
  });
});
