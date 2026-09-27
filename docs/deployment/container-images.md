# Published container images

CampusUTE publishes two first-party application images. PostgreSQL with
pgvector, Redis, and MinIO remain upstream dependencies supplied by the root
Compose file.

## Current release

The current Android/GitHub release is [`v1.1.0`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/releases/tag/v1.1.0),
built from commit [`fd0a1477a6964ce6b5542549cee3b3f731bf4ac8`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/commit/fd0a1477a6964ce6b5542549cee3b3f731bf4ac8).
The backend-only image patch is `v1.1.1`; the AI image and Android/GitHub
release remain at `v1.1.0`. Both container services target `linux/amd64`.
The backend patch disables demo seeding by default. Its image and `latest`
alias have been verified in both registries:

| Service | Docker Hub | GitHub Packages | Manifest digest |
|---|---|---|---|
| Backend (`v1.1.1`, `latest`) | [`nguyenson1710/campusute-backend`](https://hub.docker.com/r/nguyenson1710/campusute-backend) | [`campusute-backend`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/pkgs/container/campusute-backend) | `sha256:74c38334e8a9e23c1ef8cfcf247cfb798a1e5fb3486ac69bcb75e25e591213c4` |
| Historical backend (`v1.1.0`) | [`nguyenson1710/campusute-backend`](https://hub.docker.com/r/nguyenson1710/campusute-backend) | [`campusute-backend`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/pkgs/container/campusute-backend) | `sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749` |
| AI service (`v1.1.0`, `latest`) | [`nguyenson1710/campusute-ai`](https://hub.docker.com/r/nguyenson1710/campusute-ai) | [`campusute-ai`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/pkgs/container/campusute-ai) | `sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b` |

The historical backend `v1.1.0` digest is
`sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749`.
That image defaults demo seeding on when run directly; pass
`APP_DEMO_MODE=false` when using it. Backend `v1.1.1` defaults demo seeding
off when run directly.

Docker Hub and GHCR are separate registries; authenticate to GHCR before pulling
from it if your GitHub account requires authentication. For each service, the
`latest` tags currently resolve to the same digest in both registries; future
releases can move these aliases. For the strongest repeatability, use the
immutable manifest digests below instead of a mutable tag:

```bash
docker pull docker.io/nguyenson1710/campusute-backend:v1.1.1
docker pull docker.io/nguyenson1710/campusute-ai:v1.1.0
docker pull ghcr.io/jasontm17/campusute-backend:v1.1.1
docker pull ghcr.io/jasontm17/campusute-ai:v1.1.0
```

The registry Compose overlay defaults to the verified backend `v1.1.1` image
and the AI `v1.1.0` image.

```bash
docker pull docker.io/nguyenson1710/campusute-backend@sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749
docker pull docker.io/nguyenson1710/campusute-backend@sha256:74c38334e8a9e23c1ef8cfcf247cfb798a1e5fb3486ac69bcb75e25e591213c4
docker pull docker.io/nguyenson1710/campusute-ai@sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b
docker pull ghcr.io/jasontm17/campusute-backend@sha256:74c38334e8a9e23c1ef8cfcf247cfb798a1e5fb3486ac69bcb75e25e591213c4
docker pull ghcr.io/jasontm17/campusute-ai@sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b
```

## Run with Docker Compose

Use Docker Compose v2.24.4 or newer so the overlay can clear the base `build`
settings. The base [`docker-compose.yml`](../../docker-compose.yml) builds backend and AI
images from source. Add [`deploy/docker-compose.registry.yml`](../../deploy/docker-compose.registry.yml)
to use the published images instead. The overlay defaults to Docker Hub and
pins backend `v1.1.1` plus AI `v1.1.0`; it keeps the existing database, Redis,
MinIO, health dependencies, ports, and named data volumes from the base file.

1. Copy `.env.example` to `.env` and set a unique `AI_INGEST_TOKEN` and
   `JWT_SECRET`. Keep `.env` local and private.
   The published-image overlay sets demo seeding to `false` by default using
   `CAMPUSUTE_DEMO_MODE`, independently of the local-development
   `APP_DEMO_MODE` value in `.env.example`. For a disposable local demo only,
   set `CAMPUSUTE_DEMO_MODE=true`; never enable it on a shared or internet-facing
   deployment. Direct `docker run` users of the published backend `v1.1.0`
   image must pass `APP_DEMO_MODE=false`. Backend `v1.1.1` defaults demo
   seeding off without an environment override.
2. Pull and start the stack:

   ```bash
   docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml pull backend ai-service
   docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml up -d --no-build
   ```

3. Check service health:

   - Backend: <http://localhost:8080/actuator/health>
   - AI service: <http://localhost:8600/health> (bound to loopback by Compose)
   - Swagger UI: <http://localhost:8080/swagger-ui>

The registry overlay defaults to the published backend `v1.1.1` image and AI
`v1.1.0`. To select other published versions, set `CAMPUSUTE_BACKEND_TAG` and
`CAMPUSUTE_AI_TAG` independently in `.env`. `CAMPUSUTE_TAG` remains a shared
override when both services publish the same version. To use GHCR instead, set
these values in `.env` before running the commands above:

```dotenv
CAMPUSUTE_REGISTRY=ghcr.io/jasontm17
CAMPUSUTE_BACKEND_TAG=v1.1.1
CAMPUSUTE_AI_TAG=v1.1.0
```

To pin both services to the verified manifests instead of mutable tags, use:

```dotenv
CAMPUSUTE_BACKEND_IMAGE=docker.io/nguyenson1710/campusute-backend@sha256:74c38334e8a9e23c1ef8cfcf247cfb798a1e5fb3486ac69bcb75e25e591213c4
CAMPUSUTE_AI_IMAGE=docker.io/nguyenson1710/campusute-ai@sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b
```

Stop the stack with `docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml down`.
That command preserves the named database and object-storage volumes; do not
add `--volumes`/`-v` unless you intend to delete that local data.

## Publication and metadata

The backend-only `v1.1.1` security patch is a container-package update, not a
new Android/GitHub release. It was built from committed source revision
[`86e18bfe1304289d2c08d3508ad0f86a7baae881`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/commit/86e18bfe1304289d2c08d3508ad0f86a7baae881)
with OCI source/revision/version/title/license labels, an SBOM, and BuildKit
provenance. Docker Hub and GHCR both resolve `v1.1.1` and `latest` to
`sha256:74c38334e8a9e23c1ef8cfcf247cfb798a1e5fb3486ac69bcb75e25e591213c4`.
The `latest` aliases were promoted only after metadata and a direct-run smoke
test confirmed a healthy service and zero demo users without
`APP_DEMO_MODE`. The AI image and APK stay at `v1.1.0`. This backend-only patch
was published directly with Buildx; no Git release tag was pushed, avoiding
the combined workflow that also publishes the AI image and creates an APK
release. The existing `v1.1.0` Docker Hub images are byte-for-byte manifest
mirrors of the released GHCR images. The release workflow always
publishes to GHCR and is
configured to build once and publish the same image digest to Docker Hub when
both of these repository Actions secrets are present:

- `DOCKERHUB_USERNAME`: `nguyenson1710`
- `DOCKERHUB_TOKEN`: a Docker Hub personal access token with read/write access

When neither secret is set, a tagged release continues to publish to GHCR only.
When exactly one is set, the image job fails clearly rather than silently
publishing to only one configured destination. Adding the secrets does not
rewrite the existing `v1.1.0` release. These repository secrets are not
currently configured, so future tagged releases publish to GHCR only until the
owner adds both secrets.

The release workflow queues publication runs globally in FIFO order, paginates
the [full GitHub release history](https://docs.github.com/en/rest/releases/releases#list-releases),
and compares SemVer tags instead of relying on GitHub's [`latest release`
endpoint](https://docs.github.com/en/rest/releases/releases#get-the-latest-release),
which is ordered by `created_at` (the source commit date), not SemVer. Stable
releases must exceed the highest published stable version; a prerelease must
exceed the highest stable version and prior prereleases for its same
`major.minor.patch`. The version gate fails closed if any release-history page
cannot be read. The workflow also
refuses to publish a version tag already present in either enabled registry. It
publishes versioned image tags first, creates the GitHub release with its APK,
then promotes the same verified
manifest digest to `latest`. The promotion job rechecks each version tag against
the build digest before writing an alias, then copies by digest so a moved tag
cannot redirect `latest`. Prerelease tags do not move `latest`. This uses
GitHub Actions' [`concurrency.queue: max`](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency).

Docker Hub tag immutability is not enabled for this personal namespace: Docker's
immutable-tag management API requires an Organization Access Token, which is
not available for this account. Workflow serialization and version checks
prevent its own concurrent runs from moving `latest` backwards, but they cannot
prevent a direct external writer from moving a tag after the check. Use the
digest-pinned Compose references above when tag immutability matters. See the
[Docker Hub immutable-tag API requirements](https://docs.docker.com/reference/api/hub/latest/operations/UpdateRepositoryImmutableTags/).

Publishing to two registries is not atomic. If image publication fails partway
through, compare every version manifest in both registries before retrying. If
the APK release succeeds but promotion fails, compare the version and `latest`
manifests and reconcile the aliases explicitly. The workflow intentionally
stops when it sees any existing version tag; it does not automatically overwrite
a partial publication or roll back a registry that already accepted its image.

The existing `v1.1.0` images predate OCI source/revision/version labels, SBOM,
and BuildKit provenance, which cannot be
added to an existing digest without changing the released artifact. A
vulnerability-scan gate is not configured yet. Use the release tag and digest
table above to identify the current images; do not infer a scan or signed
provenance result from the registry entry.
