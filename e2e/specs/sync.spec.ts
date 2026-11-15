import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-09 binds a MySQL table, previews, syncs, and shows the object", async ({ page }) => {
  test.setTimeout(90_000);
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`sync-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`同步${stamp}`);
  await page.getByTestId("project-namespace").locator("input").fill(`https://example.com/ont/${stamp}#`);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`model-sync-${stamp}`).click();

  await page.getByTestId("element-name").locator("input").fill("有哪些站点");
  await page.getByTestId("element-api").locator("input").fill("whichStations");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("有哪些站点");

  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-cq").selectOption({ label: "有哪些站点" });
  await page.getByTestId("element-name").locator("input").fill("变电站");
  await page.getByTestId("element-api").locator("input").fill("Substation");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("变电站");
  await page.getByTestId("element-name").locator("input").fill("设备");
  await page.getByTestId("element-api").locator("input").fill("Equipment");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("设备");

  await page.getByTestId("kind-PROPERTY").click();
  await page.getByTestId("element-name").locator("input").fill("站名");
  await page.getByTestId("element-api").locator("input").fill("stationName");
  await page.getByTestId("element-owner").selectOption({ label: "变电站" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("站名");
  await page.getByTestId("element-name").locator("input").fill("设备名");
  await page.getByTestId("element-api").locator("input").fill("equipmentName");
  await page.getByTestId("element-owner").selectOption({ label: "设备" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("设备名");
  await page.getByTestId("kind-OBJECT_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("巡检记录");
  await page.getByTestId("element-api").locator("input").fill("Inspection");
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("巡检记录");
  await page.getByTestId("kind-PROPERTY").click();
  await page.getByTestId("element-name").locator("input").fill("结论");
  await page.getByTestId("element-api").locator("input").fill("note");
  await page.getByTestId("element-owner").selectOption({ label: "巡检记录" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("结论");

  await page.getByTestId("kind-LINK_TYPE").click();
  await page.getByTestId("element-name").locator("input").fill("安装于");
  await page.getByTestId("element-api").locator("input").fill("installedAt");
  await page.getByTestId("element-owner").selectOption({ label: "设备" });
  await page.getByTestId("element-target").selectOption({ label: "变电站" });
  await page.getByTestId("create-element").click();
  await expect(page.getByTestId("element-list")).toContainText("安装于");

  await page.locator("aside").getByRole("link", { name: "数据源" }).click();
  await page.getByTestId("ds-name").locator("input").fill(`src-${stamp}`);
  await page.getByTestId("ds-type").selectOption("MYSQL");
  await page.getByTestId("ds-host").locator("input").fill(process.env.E2E_MYSQL_HOST ?? "mysql");
  await page.getByTestId("ds-port").locator("input").fill(process.env.E2E_MYSQL_PORT ?? "3306");
  await page.getByTestId("ds-database").locator("input").fill(process.env.E2E_MYSQL_DATABASE ?? "mop");
  await page.getByTestId("ds-username").locator("input").fill(process.env.E2E_MYSQL_USER ?? "mop");
  await page.getByTestId("ds-password").locator("input").fill(process.env.E2E_MYSQL_PASSWORD ?? "mop-local");
  await page.getByTestId("create-datasource").click();
  await expect(page.getByRole("row", { name: new RegExp(`src-${stamp}`) })).toBeVisible();

  await page.locator("aside").getByRole("link", { name: "同步" }).click();
  await page.getByTestId("sync-project").selectOption({ label: `同步${stamp}` });
  await page.getByTestId("sync-datasource").selectOption({ label: `src-${stamp}` });
  await page.getByTestId("sync-object").selectOption({ label: "变电站" });
  await page.getByTestId("sync-table").selectOption("src_station");
  await expect(page.getByTestId("sync-pk")).toContainText("id");
  await page.getByTestId("sync-pk").selectOption("id");
  await page.getByTestId("sync-incremental-column").selectOption("updated_at");
  await page.getByTestId("sync-property").selectOption({ label: "站名" });
  await page.getByTestId("sync-column").selectOption("name");
  await page.getByTestId("save-mapping").click();
  await expect(page.getByTestId("sync-message")).toContainText("已保存");
  await page.getByTestId("preview-mapping").click();
  await expect(page.getByTestId("preview-result")).toContainText("城东站");
  await page.getByTestId("sync-full").click();
  await expect(page.getByTestId("sync-objects")).toContainText("城东站");

  await page.getByTestId("sync-object").selectOption({ label: "设备" });
  await page.getByTestId("sync-table").selectOption("src_equipment");
  await page.getByTestId("sync-pk").selectOption("id");
  await page.getByTestId("sync-incremental-column").selectOption("updated_at");
  await page.getByTestId("sync-property").selectOption({ label: "设备名" });
  await page.getByTestId("sync-column").selectOption("name");
  await page.getByTestId("sync-link").selectOption({ label: "安装于" });
  await page.getByTestId("sync-target").selectOption({ label: "变电站" });
  await page.getByTestId("use-foreign-key").click();
  await expect(page.getByTestId("sync-fk")).toHaveValue("station_id");
  await page.getByTestId("save-mapping").click();
  await expect(page.getByTestId("sync-message")).toContainText("已保存");
  await page.getByTestId("preview-mapping").click();
  await expect(page.getByTestId("preview-result")).toContainText("一号变压器");
  await page.getByTestId("sync-full").click();
  await expect(page.getByTestId("sync-runs")).toContainText("SUCCEEDED");
  await expect(page.getByTestId("sync-objects")).toContainText("一号变压器");

  await page.getByTestId("sync-object").selectOption({ label: "巡检记录" });
  await page.getByTestId("sync-table").selectOption("src_inspection");
  await page.getByTestId("sync-pk").selectOption("id");
  await page.getByTestId("sync-incremental-column").selectOption("updated_at");
  await page.getByTestId("sync-property").selectOption({ label: "结论" });
  await page.getByTestId("sync-column").selectOption("note");
  await page.getByTestId("sync-link").selectOption("");
  await page.getByTestId("save-mapping").click();
  await expect(page.getByTestId("sync-message")).toContainText("已保存");
  await page.getByTestId("sync-full").click();
  await expect(page.getByTestId("sync-objects")).toContainText("运行正常");
  const stampNote = `n${stamp}`.slice(0, 32);
  const token = await page.evaluate(() => JSON.parse(sessionStorage.getItem("mop.session") || "{}").accessToken as string);
  const inserted = await page.request.post("/api/v1/demo/grid/inspections", {
    data: { id: stampNote, note: "新增巡检" },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(inserted.ok()).toBeTruthy();
  await page.getByTestId("sync-incremental").click();
  await expect(page.getByTestId("sync-objects")).toContainText("新增巡检");
  await page.locator("aside").getByRole("link", { name: "图谱" }).click();
  await page.getByTestId("graph-project").selectOption({ label: `同步${stamp}` });
  await page.getByTestId("graph-type").selectOption("Inspection");
  await expect(page.getByTestId("graph-nodes")).toContainText("新增巡检");
  await page.screenshot({ path: "test-results/sync.png", fullPage: true });
});
