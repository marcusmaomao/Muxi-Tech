import { describe, expect, it } from "vitest";
import { isAbsoluteNamespace } from "@/utils/namespace";

describe("namespace", () => {
  it("accepts an absolute URI ending with a hash or slash", () => {
    expect(isAbsoluteNamespace("https://muxi.example/ont/grid#")).toBe(true);
    expect(isAbsoluteNamespace("https://muxi.example/ont/grid/")).toBe(true);
    expect(isAbsoluteNamespace("grid")).toBe(false);
    expect(isAbsoluteNamespace("https://muxi.example/ont/grid")).toBe(false);
  });
});
