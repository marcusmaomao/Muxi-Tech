import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("demo dataset is imported with the grid ontology and sample document", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await expect(page.getByTestId("project-table")).toContainText("电网设备");
  await page.locator("aside").getByRole("link", { name: "文件" }).click();
  await expect(page.getByTestId("file-table")).toContainText("电网设备说明.txt");
  await page.getByTestId("file-table").locator("tr", { hasText: "电网设备说明.txt" }).getByRole("button", { name: "文本" }).click();
  await expect(page.getByTestId("file-text")).toContainText("一号变压器安装在城东站");
});

test("E2E-01 tenant admin creates a user, a project, and a project role", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.getByRole("link", { name: "系统" }).click();
  await page.getByTestId("tenant-code").locator("input").fill(`t${stamp}`);
  await page.getByTestId("tenant-name").locator("input").fill(`租户${stamp}`);
  await page.getByTestId("tenant-admin").locator("input").fill(`ta${stamp}`);
  await page.getByTestId("create-tenant").click();
  await expect(page.getByText("租户已创建")).toBeVisible();
  await page.getByTestId("logout").click();
  await page.waitForURL("**/login");

  await new LoginPage(page).signIn(`ta${stamp}`, "demo-admin");
  await page.getByRole("link", { name: "系统" }).click();
  await page.getByTestId("new-username").locator("input").fill(`clerk${stamp}`);
  await page.getByTestId("new-display-name").locator("input").fill("文员");
  await page.getByTestId("create-user").click();
  await expect(page.getByTestId("user-table")).toContainText(`clerk${stamp}`);

  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`p${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`项目${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await expect(page.getByTestId("project-table")).toContainText(`项目${stamp}`);
  await page.getByTestId("member-project").selectOption({ label: `项目${stamp}` });
  await page.getByTestId("member-user").selectOption({ label: `clerk${stamp}` });
  await page.getByTestId("member-role").selectOption("EDITOR");
  await page.getByTestId("add-member").click();
  await expect(page.getByText("成员已加入")).toBeVisible();
});

test("E2E-18 menu and button permissions apply, and logs can be searched", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await expect(page.getByRole("link", { name: "系统" })).toBeVisible();
  await page.getByRole("link", { name: "系统" }).click();
  await expect(page.getByTestId("create-user")).toBeVisible();
  await page.getByTestId("role-code").locator("input").fill(`guest${stamp}`);
  await page.getByTestId("role-name").locator("input").fill(`访客${stamp}`);
  await page.getByTestId("create-role").click();
  await page.getByTestId("new-username").locator("input").fill(`guest${stamp}`);
  await page.getByTestId("new-display-name").locator("input").fill("访客用户");
  await page.getByTestId("create-user").click();
  await expect(page.getByTestId("user-table")).toContainText(`guest${stamp}`);
  await page.getByTestId("grant-user").selectOption({ label: `guest${stamp}` });
  await page.getByTestId("grant-role").selectOption({ label: `访客${stamp}` });
  await page.getByTestId("grant-role-button").click();
  await expect(page.getByText("已授权")).toBeVisible();
  await page.getByTestId("logout").click();
  await page.waitForURL("**/login");

  await new LoginPage(page).signIn(`guest${stamp}`, "demo-admin");
  await expect(page.getByRole("link", { name: "系统" })).toHaveCount(0);
  await page.goto("/system/users");
  await expect(page.getByTestId("create-user")).toHaveCount(0);
  await page.getByTestId("logout").click();
  await page.waitForURL("**/login");

  await new LoginPage(page).signIn(username, password);
  await page.getByRole("link", { name: "系统" }).click();
  await page.getByRole("link", { name: "日志" }).click();
  await page.getByTestId("log-query").fill(`guest${stamp}`);
  await expect(page.getByTestId("login-log-table")).toContainText(`guest${stamp}`);
  await page.getByTestId("log-query").fill("GRANT");
  await expect(page.getByTestId("oper-log-table")).toContainText("GRANT");
});
