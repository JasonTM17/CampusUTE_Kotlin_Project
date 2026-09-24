# Published container images

CampusUTE publishes two first-party application images. PostgreSQL with
pgvector, Redis, and MinIO remain upstream dependencies supplied by the root
Compose file.

## Current release

The current published release is [`v1.1.0`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/releases/tag/v1.1.0),
built from commit [`fd0a1477a6964ce6b5542549cee3b3f731bf4ac8`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/commit/fd0a1477a6964ce6b5542549cee3b3f731bf4ac8).
Both images target `linux/amd64`. Docker Hub and GHCR tags `v1.1.0` and
`latest` resolve to the same manifest digest:

| Service | Docker Hub | GitHub Packages | Manifest digest |
|---|---|---|---|
| Backend | [`nguyenson1710/campusute-backend`](https://hub.docker.com/r/nguyenson1710/campusute-backend) | [`campusute-backend`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/pkgs/container/campusute-backend) | `sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749` |
| AI service | [`nguyenson1710/campusute-ai`](https://hub.docker.com/r/nguyenson1710/campusute-ai) | [`campusute-ai`](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/pkgs/container/campusute-ai) | `sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b` |

Docker Hub and GHCR are separate registries; authenticate to GHCR before pulling
from it if your GitHub account requires authentication. The `latest` tag
currently resolves to the same image, but can move when a future release is
published. For the strongest repeatability, use the immutable manifest digests
below instead of a mutable tag:

```bash
docker pull docker.io/nguyenson1710/campusute-backend:v1.1.0
docker pull docker.io/nguyenson1710/campusute-ai:v1.1.0
docker pull ghcr.io/jasontm17/campusute-backend:v1.1.0
docker pull ghcr.io/jasontm17/campusute-ai:v1.1.0
```

```bash
docker pull docker.io/nguyenson1710/campusute-backend@sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749
docker pull docker.io/nguyenson1710/campusute-ai@sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b
```

## Run with Docker Compose

Use Docker Compose v2.24.4 or newer so the overlay can clear the base `build`
settings. The base [`docker-compose.yml`](../../docker-compose.yml) builds backend and AI
images from source. Add [`deploy/docker-compose.registry.yml`](../../deploy/docker-compose.registry.yml)
to use the published release images instead. The overlay defaults to Docker
Hub and pins `v1.1.0`; it keeps the existing database, Redis, MinIO, health
dependencies, ports, and named data volumes from the base file.

1. Copy `.env.example` to `.env` and set a unique `AI_INGEST_TOKEN` and
   `JWT_SECRET`. Keep `.env` local and private.
2. Pull and start the stack:

   ```bash
   docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml pull backend ai-service
   docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml up -d --no-build
   ```

3. Check service health:

   - Backend: <http://localhost:8080/actuator/health>
   - AI service: <http://localhost:8600/health> (bound to loopback by Compose)
   - Swagger UI: <http://localhost:8080/swagger-ui>

To pull a different release, set `CAMPUSUTE_TAG` in `.env` to the published
version. To use GHCR instead, set these values in `.env` before running the
commands above:

```dotenv
CAMPUSUTE_REGISTRY=ghcr.io/jasontm17
CAMPUSUTE_TAG=v1.1.0
```

To make Compose use the exact v1.1.0 manifests regardless of tag movement, set
the complete image references in `.env` instead:

```dotenv
CAMPUSUTE_BACKEND_IMAGE=docker.io/nguyenson1710/campusute-backend@sha256:a8610b03175b37253c874b676f86eb8c49379fbff2b72599bdd05692db6e7749
CAMPUSUTE_AI_IMAGE=docker.io/nguyenson1710/campusute-ai@sha256:2cecfa4efd800ca9c5da04af373ba104b5e9865b9cc481c2233247209bbe5d6b
```

Stop the stack with `docker compose -f docker-compose.yml -f deploy/docker-compose.registry.yml down`.
That command preserves the named database and object-storage volumes; do not
add `--volumes`/`-v` unless you intend to delete that local data.

## Publication and metadata

The `v1.1.0` Docker Hub images are byte-for-byte manifest mirrors of the
released GHCR images. The release workflow always publishes to GHCR and is
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

Future tagged builds add OCI source/revision/version labels, an SBOM, and
BuildKit provenance. The `v1.1.0` images predate those records, which cannot be
added to an existing digest without changing the released artifact. A
vulnerability-scan gate is not configured yet. Use the release tag and digest
table above to identify the current images; do not infer a scan or signed
provenance result from the registry entry.
