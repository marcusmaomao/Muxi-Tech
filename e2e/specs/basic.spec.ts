import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("admin creates a project, checks mysql, and reads a file", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.getByRole("link", { name: "项目" }).click();
  await expect(page.getByTestId("projects-page")).toBeVisible();
  await page.getByTestId("project-code").locator("input").fill(`e2e-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill("端到端电网");
  await page.getByTestId("project-domain").locator("input").fill("电力");
  await page.getByTestId("project-namespace").locator("input").fill(`https://muxi.example/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await expect(page.getByTestId("project-table")).toContainText("端到端电网");
  await page.screenshot({ path: "test-results/projects.png", fullPage: true });

  await page.locator("aside").getByRole("link", { name: "数据源" }).click();
  await page.getByTestId("ds-name").locator("input").fill(`mysql-${stamp}`);
  await page.getByTestId("ds-type").selectOption("MYSQL");
  await page.getByTestId("ds-host").locator("input").fill(process.env.E2E_MYSQL_HOST ?? "mysql");
  await page.getByTestId("ds-port").locator("input").fill(process.env.E2E_MYSQL_PORT ?? "3306");
  await page.getByTestId("ds-database").locator("input").fill(process.env.E2E_MYSQL_DATABASE ?? "mop");
  await page.getByTestId("ds-username").locator("input").fill(process.env.E2E_MYSQL_USER ?? "mop");
  await page.getByTestId("ds-password").locator("input").fill(process.env.E2E_MYSQL_PASSWORD ?? "mop-local");
  await page.getByTestId("create-datasource").click();
  const row = page.getByRole("row", { name: new RegExp(`mysql-${stamp}`) });
  await row.getByRole("button", { name: "测试" }).click();
  await expect(page.getByTestId("ds-result")).toContainText("已连通");
  await row.getByRole("button", { name: "表" }).click();
  await page.getByRole("button", { name: "mop_project", exact: true }).click();
  await expect(page.getByTestId("column-list")).toContainText("code");
  await page.screenshot({ path: "test-results/datasources.png", fullPage: true });

  await page.locator("aside").getByRole("link", { name: "文件" }).click();
  await page.getByTestId("file-input").setInputFiles({
    name: "note.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("变压器检修记录"),
  });
  await page.getByTestId("upload-file").click();
  await page.getByTestId("file-table").locator("tr", { hasText: "note.txt" }).first().getByRole("button", { name: "文本" }).click();
  await expect(page.getByTestId("file-text")).toContainText("变压器检修记录");
  await page.screenshot({ path: "test-results/files.png", fullPage: true });
});
