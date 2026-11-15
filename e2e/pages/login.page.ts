import type { Page } from "@playwright/test";

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto("/login");
  }

  async signIn(username: string, password: string) {
    const captcha = this.page.waitForResponse((response) => response.url().includes("/api/v1/auth/captcha"));
    await this.open();
    const response = await captcha;
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
