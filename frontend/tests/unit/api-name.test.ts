import { describe, expect, it } from "vitest";
import { isApiName } from "@/utils/apiName";

describe("api name", () => {
  it("keeps the stable identifier to letters, digits, and underscores", () => {
    expect(isApiName("ratedVoltage")).toBe(true);
    expect(isApiName("1voltage")).toBe(false);
    expect(isApiName("额定电压")).toBe(false);
  });
});
