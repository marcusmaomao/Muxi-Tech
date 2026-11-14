import type { Page } from "@playwright/test";

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto("/login");
  }

  async signIn(username: string, password: string) {
    await this.page.getByLabel("用户名").fill(username);
    await this.page.getByLabel("密码").fill(password);
    await this.page.getByRole("button", { name: "登录" }).click();
  }
}
