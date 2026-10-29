# Lunch² – Bring a Friend. Meet Two.

Mobile-first web app for the THWS Fit4Germany hackathon: students register in ~60 seconds via QR code, bring a friend,
and get matched into lunch groups of four (2 locally-based + 2 international students), with a group reveal,
AI conversation cards and mutual, opt-in contact sharing. Prototype – uses fictional demo data only.

## Prerequisites
- JDK 21 (`java -version`), Node.js ≥ 20 (`node -v`). Maven is optional – the backend ships `./mvnw`.
- Optional: `GEMINI_API_KEY` for AI conversation cards (without it the app uses built-in fallback cards).

## Install
```
cd backend && ./mvnw -q -DskipTests package && cd ..
cd frontend && npm install && cd ..
```

## Configuration
Backend keys and env vars are listed in `docs/API_CONTRACT.md` §5 (all have local defaults). Important ones:
| Env var | Default | Purpose |
|---|---|---|
| `LUNCH2_ADMIN_KEY` | `dev-admin-key` | header `X-Admin-Key` for seed/match/reset |
| `LUNCH2_DATA_FILE` | `./data/lunch2.json` | JSON persistence file |
| `LUNCH2_PUBLIC_BASE_URL` | `http://localhost:5173` | base for invite/restore links |
| `GEMINI_API_KEY` | *(empty)* | enables Gemini AI cards |
| `LUNCH2_AI_MODEL` | `gemini-3.8-flash` | Gemini model for cards |
Frontend: `frontend/.env.development` → `VITE_USE_MOCKS=true` serves mock data; put `VITE_USE_MOCKS=false` in `frontend/.env.development.local` to use the real backend.

### Local admin key

Copy `.env.example` to `.env` at the repository root (do not overwrite an existing local file).
Generate your private admin key with `openssl rand -hex 32` and replace the example `LUNCH2_ADMIN_KEY`
value in `.env`. Start the packaged backend from the repository root so it reads that directory's `.env` file.
For `quarkus:dev`, load the file into the shell first, then change directory:

```sh
set -a
source .env
set +a
cd backend
./mvnw quarkus:dev
```

An already-exported shell variable takes precedence over `.env`; restart the backend after changing the key.
Open `/#/admin` and enter exactly the same key. The UI keeps it in sessionStorage and sends it as
`X-Admin-Key`; never put it in a `VITE_` variable or a URL. Missing/incorrect keys return 401.
Local `.env` files are git-ignored; only the example should be committed.

This custom key applies to the real backend (`VITE_USE_MOCKS=false`). Frontend mock mode uses the
fixed fictional key `dev-admin-key`. Shell scripts such as `demo.sh` do not automatically load
`.env`; load it into the shell as shown above before running them.

## Run locally
```
cd backend && ./mvnw quarkus:dev        # API on http://localhost:8080
cd frontend && npm run dev              # UI on http://localhost:5173 (proxies /api to :8080)
```

## Verify
```
cd backend && ./mvnw -q verify                                   # backend tests
cd frontend && npm run lint && npm test && npm run build          # typecheck, tests, build
curl -s localhost:8080/api/ping                                   # {"ok":true} while quarkus:dev runs
```

## Deploy to AWS (one EC2 instance, fixed public IP)
Everything runs on one free-tier `t3.micro` with an Elastic IP: Quarkus serves the UI and the API on port 80, data is the JSON file on the instance.
No load balancer, NAT, database or domain. Cost: the instance is free-tier eligible; the public IPv4 costs about $0.005/h (~$3.60/month), covered by free-plan credits.
```
brew install awscli && aws configure          # once: access key of an IAM user with admin rights
./scripts/deploy.sh                            # build + secrets in SSM + cdk bootstrap/deploy, prints http://<IP>
ANTHROPIC_API_KEY=sk-... ./scripts/deploy.sh   # same, with AI conversation cards
./scripts/destroy.sh                           # delete the stack when the hackathon is over
cd infra && npm install && npm test           # offline tests of the CDK stack and the scripts (no AWS needed)
```
- Region defaults to `eu-central-1` (Frankfurt); override with `AWS_REGION=...`.
- Each deploy **replaces the instance** (same IP): stored data is wiped, so run admin seed again afterwards.
- HTTP only (a bare IP can't get a TLS certificate): fine for a demo with fictional data, not for real users. Browsers block `navigator.clipboard`/`navigator.share` on plain HTTP except on localhost, so the UI must offer a visible link fallback.
- Debug shell without SSH: the `ShellCommand` output (needs `brew install --cask session-manager-plugin`), then `sudo journalctl -u lunch2`.
- Any Mac works (Intel or Apple Silicon): the build is a plain JVM app compiled for Java 21, verified on Amazon Linux 2023 + Corretto 21. `build-all.sh` picks a JDK 21+ automatically and always does a clean build.
- `deploy.sh` stops if the folder has uncommitted changes (they would be deployed). Deploy from an up-to-date `main`; `ALLOW_DIRTY=1` overrides.
- Don't build the Docker image in `backend/src/main/docker` on an Apple Silicon Mac for this server – it would be arm64. The CDK deploy doesn't use Docker.

## Project structure
```
AGENTS.md / CLAUDE.md     rules for AI coding agents (CLAUDE.md imports AGENTS.md)
.claude/skills/           backend-endpoint, frontend-page, verify-integration
docs/                     SPEC, ARCHITECTURE, API_CONTRACT (frozen), TASKS (tickets + queues)
backend/                  Quarkus app, packages de.thws.lunch2.{common,domain,persistence,auth,participant,invite,connect,matching,group,cards,admin}
frontend/                 React + Vite + Tailwind, src/{api,components,pages,i18n}
infra/                    AWS CDK (TypeScript): VPC, security group, EC2, Elastic IP
scripts/                  build-all.sh, deploy.sh, destroy.sh
```
Team workflow, ownership and the ticket queue for each developer: `docs/TASKS.md`.
