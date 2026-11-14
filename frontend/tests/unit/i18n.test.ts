import { describe, expect, it } from "vitest";
import { i18n } from "../../src/i18n";

describe("i18n", () => {
  it("defaults to Chinese and names the product", () => {
    expect(i18n.global.locale.value).toBe("zh-CN");
    expect(i18n.global.t("brand")).toBe("沐曦本体平台");
  });
});
