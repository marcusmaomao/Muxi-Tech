import { expect, test } from "@playwright/test";
import { LoginPage } from "../pages/login.page";

const username = process.env.E2E_USERNAME ?? "admin";
const password = process.env.E2E_PASSWORD ?? "demo-admin";
const stamp = Date.now().toString(36);

test("canvas baseline keeps 500 nodes interactive", async ({ page, request }) => {
  test.setTimeout(180_000);
  await new LoginPage(page).signIn(username, password);
  const token = await page.evaluate(() => {
    const raw = sessionStorage.getItem("mop.session");
    return raw ? (JSON.parse(raw) as { accessToken: string }).accessToken : "";
  });
  const headers = { Authorization: `Bearer ${token}` };
  const project = await request.post("/api/v1/projects", {
    headers,
    data: { code: `scale-${stamp}`, name: `规模${stamp}`, namespace: `https://example.com/ont/${stamp}#` },
  });
  expect(project.ok()).toBeTruthy();
  const projectId = (await project.json()).data.id as string;
  const branches = await request.get(`/api/v1/projects/${projectId}/branches`, { headers });
  const branchId = (await branches.json()).data[0].id as string;
  const cq = await request.post(`/api/v1/branches/${branchId}/cqs`, {
    headers,
    data: { displayName: "规模", apiName: "scaleCq", priority: "P1" },
  });
  const cqUid = (await cq.json()).data.uid as string;
  const changes = Array.from({ length: 500 }, (_, index) => ({
    op: "ADD",
    type: "OBJECT_TYPE",
    apiName: `N${index}`,
    payload: { displayName: `节点${index}`, apiName: `N${index}`, cqUids: [cqUid], description: `节点${index}` },
  }));
  const committed = await request.post(`/api/v1/branches/${branchId}/commits`, {
    headers,
    data: { message: "500 个类", changes },
  });
  expect(committed.ok()).toBeTruthy();

  await page.goto(`/model/${projectId}/canvas`);
  await expect(page.getByTestId("canvas-ready")).toBeAttached({ timeout: 60_000 });
  await expect(page.getByTestId("canvas-nodes").locator("li")).toHaveCount(500);

  const frames = await page.evaluate(async () => {
    const host = document.querySelector("[data-testid=canvas-graph]");
    if (!host) {
      return 0;
    }
    const bounds = host.getBoundingClientRect();
    let count = 0;
    let running = true;
    const tick = () => {
      if (!running) {
        return;
      }
      count += 1;
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
    const started = performance.now();
    while (performance.now() - started < 1000) {
      host.dispatchEvent(new PointerEvent("pointermove", {
        bubbles: true,
        clientX: bounds.left + 30 + ((performance.now() - started) % 200),
        clientY: bounds.top + 40,
      }));
      await new Promise((resolve) => setTimeout(resolve, 16));
    }
    running = false;
    return count;
  });
  console.log(`BASELINE canvasFps=${frames}`);
  expect(frames).toBeGreaterThanOrEqual(30);
});
