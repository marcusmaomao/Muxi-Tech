import type { Page } from "@playwright/test";

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto("/login");
  }

  async signIn(username: string, password: string) {
    await this.open();
    if (!this.page.url().includes("/login")) {
      await this.page.evaluate(() => sessionStorage.removeItem("mop.session"));
      await this.open();
    }
    await this.page.getByTestId("captcha-image").locator("img").waitFor();
    const [response] = await Promise.all([
      this.page.waitForResponse((item) => item.url().includes("/api/v1/auth/captcha") && item.ok()),
      this.page.getByTestId("captcha-image").click(),
    ]);
    const body = (await response.json()) as { data: { debugCode: string } };
    await this.page.getByLabel("用户名").fill(username);
    await this.page.getByLabel("密码").fill(password);
    await this.page.getByTestId("captcha").locator("input").fill(body.data.debugCode);
    await this.page.getByRole("button", { name: "登录" }).click();
    await Promise.race([
      this.page.waitForURL("**/workbench"),
      this.page.getByTestId("login-error").waitFor({ state: "visible" }),
    ]);
  }
}
