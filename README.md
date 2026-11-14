# 沐曦本体平台

沐曦本体平台（Muxi Ontology Platform，MOP）是武汉沐曦云技术有限公司的企业本体工程平台。它要做成可执行的本体：建模、校验、治理，以及对象、动作和函数。

当前是 **M0 脚手架**。可以在本地用 Docker Compose 拉起空壳，并用演示账号登录工作台。建模、SHACL、推理、运行时对象等按 `docs/` 里的里程碑继续做。

实现方式是净室自研，见 [CLEAN_ROOM.md](CLEAN_ROOM.md)。第三方依赖见 [NOTICE](NOTICE)。仓库许可证是 [MIT](LICENSE)。

## 文档

- [产品需求](docs/MUXI-ONT-PRD-001.md)
- [技术开发文档](docs/MUXI-ONT-TD-001.md)

## 本地启动

需要 JDK 21、Maven 3.9、Node.js 22、pnpm 10、Docker Compose 2。

一键启动：

```bash
cd deploy/compose
cp .env.example .env
docker compose up -d --build
```

浏览器打开 <http://localhost:8899>。接口也可直接访问 <http://localhost:8080>。

对象存储镜像用 `cgr.dev/chainguard/minio`。Docker Hub 在 2026-09 下架了 `minio/minio`，这个镜像仍是 MinIO 服务端，应用不链接它。

只启动基础设施、在 IDE 里跑后端和前端：

```bash
cd deploy/compose
cp .env.example .env
docker compose up -d mysql redis neo4j minio
```

```bash
# 后端。先导出与 .env 相同的 MYSQL_*、MOP_JWT_SECRET、MOP_DEMO_*。
mvn -f backend/pom.xml -pl mop-server -am spring-boot:run

# 前端
cd frontend
pnpm install
pnpm dev
```

前端开发服务器是 <http://localhost:5173>，`/api` 会代理到 `localhost:8080`。

镜像也可以单独构建：

```bash
docker buildx bake -f deploy/docker-bake.hcl
```

## 演示账号

账号不写死在业务代码里。`MOP_DEMO_SEED=true` 时，后端启动会由 `DemoSeedService` 创建租户和管理员；用户名和密码来自环境变量：

| 变量 | 本地示例 |
|---|---|
| `MOP_DEMO_ADMIN_USERNAME` | `admin` |
| `MOP_DEMO_ADMIN_PASSWORD` | `demo-admin` |
| `MOP_JWT_SECRET` | 至少 32 字节，仅限本地 |

示例值在 [deploy/compose/.env.example](deploy/compose/.env.example)。这是公开的本地演示口令，不能用于任何真实环境。未设置 `MOP_DEMO_SEED` 时不会创建账号。

## 目录

```text
backend/                 Maven 多模块（Java 21、Spring Boot 3.5）
frontend/                Vue 3 + TypeScript + Vite
e2e/                     Playwright 登录用例
deploy/compose/          Docker Compose 与 Nginx
deploy/docker/           服务端和前端镜像
docs/                    PRD 与技术开发文档
.github/workflows/       ci.yml、security.yml
```

后端模块依赖方向由 ArchUnit 检查：`runtime → model` 的只读契约，`semantic → model`，`ai → model + semantic`，禁止反向依赖。模块之间通过 `*.api` 包协作。

## 常用检查

```bash
mvn -B -f backend/pom.xml verify
cd frontend && pnpm lint && pnpm type-check && pnpm test && pnpm build
cd e2e && pnpm test
```

端到端用例默认访问 `http://localhost:8899`。Compose 起来之后再跑。

## 依赖审查

`security.yml` 的 `dependency-review` 会检查 PR 里新增的依赖。这个仓库的 Dependency graph 还没开时，GitHub 会返回 “Dependency review is not supported”，该 job 只记下说明并成功结束，不挡住 CI。图打开之后，同一 job 会按高危及以上失败。

开启步骤：

1. 打开仓库 **Settings → Code security**（有的界面写 Advanced Security / Code security and analysis）。
2. 启用 **Dependency graph**。
3. 建议同时启用 **Dependabot alerts**。
4. 说明见 [About the dependency graph](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/about-the-dependency-graph)。

直接入口：<https://github.com/marcusmaomao/Muxi-Tech/settings/security_analysis>
