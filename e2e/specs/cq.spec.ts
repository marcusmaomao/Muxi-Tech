import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("E2E-08 CQ SPARQL passes, then deleting the link fails the test and lowers coverage", async ({ page }) => {
  await new LoginPage(page).signIn(username, password);
  await page.locator("aside").getByRole("link", { name: "项目" }).click();
  await page.getByTestId("project-code").locator("input").fill(`cq-${stamp}`);
  await page.getByTestId("project-name").locator("input").fill(`问题${stamp}`);
  const namespace = `https://example.com/ont/${stamp}#`;
  await page.getByTestId("project-namespace").locator("input").fill(namespace);
  await page.getByTestId("create-project").click();
  await page.getByTestId(`cq-cq-${stamp}`).click();
  await expect(page.getByTestId("cq-namespace")).toHaveText(namespace);

  await page.getByTestId("cq-name").locator("input").fill("由哪些零件组成");
  await page.getByTestId("cq-api").locator("input").fill("whichParts");
  await page.getByTestId("create-cq").click();
  await expect(page.getByTestId("cq-list")).toContainText("由哪些零件组成");

  await page.getByTestId("type-name").locator("input").fill("成品");
  await page.getByTestId("type-api").locator("input").fill("Product");
  await page.getByTestId("create-type").click();
  await expect(page.getByTestId("cq-message")).toContainText("对象已保存");
  await page.getByTestId("type-name").locator("input").fill("零件");
  await page.getByTestId("type-api").locator("input").fill("Part");
  await page.getByTestId("create-type").click();
  await expect(page.getByTestId("cq-message")).toContainText("对象已保存");

  await page.getByTestId("link-name").locator("input").fill("组成");
  await page.getByTestId("link-api").locator("input").fill("composedOf");
  await page.getByTestId("link-source").selectOption({ label: "成品" });
  await page.getByTestId("link-target").selectOption({ label: "零件" });
  await page.getByTestId("create-link").click();
  await expect(page.getByTestId("cq-links")).toContainText("组成");

  await page.getByTestId("fixture-name").locator("input").fill("手机夹具");
  await page.getByTestId("fixture-api").locator("input").fill("phoneFixture");
  await page.getByTestId("fixture-turtle").fill(`@prefix ex: <${namespace}> .\nex:phone ex:composedOf ex:screen .\n`);
  await page.getByTestId("create-fixture").click();
  await expect(page.getByTestId("cq-message")).toContainText("夹具已保存");

  await page.getByTestId("test-name").locator("input").fill("零件数量");
  await page.getByTestId("test-api").locator("input").fill("partCount");
  await page.getByTestId("test-fixture").selectOption({ label: "手机夹具" });
  await page.getByTestId("test-query").fill(`PREFIX ex: <${namespace}>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
SELECT ?part WHERE { ?product ex:composedOf ?part . ex:composedOf a owl:ObjectProperty . }
`);
  await page.getByTestId("create-test").click();
  await expect(page.getByTestId("cq-message")).toContainText("用例已保存");

  await page.getByTestId("run-cq").click();
  await expect(page.getByTestId("cq-report")).toContainText("partCount 通过");
  await expect(page.getByTestId("cq-report")).toContainText("CQ 1/1");
  await expect(page.getByTestId("cq-report")).toContainText("门禁通过");

  await page.getByTestId("cq-name").locator("input").fill("另一个问题");
  await page.getByTestId("cq-api").locator("input").fill("otherCq");
  await page.getByTestId("create-cq").click();
  await expect(page.getByTestId("cq-list")).toContainText("另一个问题");
  await page.getByTestId("accept-cq").selectOption({ label: "另一个问题" });
  await page.getByTestId("mark-covered").click();
  await expect(page.getByTestId("cq-message")).toContainText("已记下手工状态");
  await page.getByTestId("run-cq").click();
  await expect(page.getByTestId("cq-report")).toContainText("CQ 1/2");
  await expect(page.getByTestId("cq-report")).toContainText("门禁未通过");

  await page.getByTestId("delete-link-composedOf").click();
  await expect(page.getByTestId("cq-message")).toContainText("关系已删除");
  await page.getByTestId("run-cq").click();
  await expect(page.getByTestId("cq-report")).toContainText("partCount 失败");
  await expect(page.getByTestId("cq-report")).toContainText("CQ 0/2");
  await page.screenshot({ path: "test-results/cq.png", fullPage: true });
});
