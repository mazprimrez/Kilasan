# Journal backend

A small FastAPI service for Cloud Run. It sits between the app and Gemini on Vertex AI
and does four things: types out voice notes, types out notebook and pen pages, writes
the weekly, monthly and yearly recaps, and writes a book's summary and review ideas
from the thoughts logged while reading it. What the app sends and gets back is in
[docs/JOURNAL_BACKEND.md](../docs/JOURNAL_BACKEND.md).

| Path | What lives there |
| --- | --- |
| `app/settings.py` | Everything read from the environment, including the model |
| `app/auth.py` | The Google sign-in check (access token or ID token): one allowed account |
| `app/model.py` | The one call to Gemini on Vertex AI |
| `app/transcription.py` | `transcribe(audio, mime_type)`: the only place that knows who transcribes |
| `app/extraction.py` | Page image to text |
| `app/recap.py` | Recap request, reply shape and the quote check |
| `app/book_recap.py` | Book summary request, reply shape, and the checks on the quote and the points |
| `prompts/recap.md` | The recap prompt. Edit it and redeploy; no code changes |
| `prompts/book_recap.md` | The book summary prompt. The same |
| `tests/samples/` | A sample week, month and year of entries, and a sample book with its thoughts |

## Settings

| Variable | Needed | What it is |
| --- | --- | --- |
| `GEMINI_MODEL` | no | The model for every endpoint. Default `gemini-3.5-flash-lite` |
| `ALLOWED_EMAIL` | yes | The one Google account that may use the service |
| `GOOGLE_CLIENT_ID` | yes | The OAuth client ID the app's Google tokens are issued to: the app's Android client. Several can be given, separated by commas |
| `GOOGLE_CLOUD_PROJECT` | yes | The project whose Vertex AI is called |
| `GOOGLE_CLOUD_LOCATION` | no | Vertex AI location. Default `global` |

There is no API key. Vertex AI is called as the service account the service runs as.
The service refuses to start without `ALLOWED_EMAIL` and `GOOGLE_CLIENT_ID`.

## Changing the model

One value. On a running service:

```
gcloud run services update journal-backend --region asia-southeast2 \
  --update-env-vars GEMINI_MODEL=the-new-model-id
```

`GET /health` answers `{"ok": true, "model": "…"}` without signing in, so you can see
which model a deployment uses.

## Model usage

Every model call writes one JSON line to stdout (`event: "model_usage"`) with the
feature (`transcribe`, `extract-text`, `recap`, `book-recap`), the model, the status
the app got, the input, output and thinking tokens, and the latency. It never holds
what was sent, what came back, or who asked.

The **Journal backend: model usage** dashboard in Cloud Monitoring shows these lines
by feature, next to Vertex AI's own per-model numbers. Its log-based metrics and layout
live in `monitoring/` at the repo root. After changing them, run:

```
monitoring/apply.sh --account=<an account with access to day1sec>
```

## Tests

```
cd backend
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python -r requirements-dev.txt
.venv/bin/python -m pytest
```

The tests use a stand-in for the model and for Google's token check, so they need no
account and send nothing anywhere.

## Deploy to Cloud Run

Once per project: enable the APIs, and make a service account that may call Vertex AI
and nothing else.

```
PROJECT=your-project-id

gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com aiplatform.googleapis.com --project $PROJECT

gcloud iam service-accounts create journal-backend --project $PROJECT

gcloud projects add-iam-policy-binding $PROJECT \
  --member serviceAccount:journal-backend@$PROJECT.iam.gserviceaccount.com \
  --role roles/aiplatform.user
```

Then, from the repository root, each time you deploy:

```
gcloud run deploy journal-backend \
  --source backend \
  --project $PROJECT \
  --region asia-southeast2 \
  --service-account journal-backend@$PROJECT.iam.gserviceaccount.com \
  --allow-unauthenticated \
  --max-instances 2 \
  --timeout 120 \
  --set-env-vars GEMINI_MODEL=gemini-3.5-flash-lite,ALLOWED_EMAIL=you@example.com,GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com,GOOGLE_CLOUD_PROJECT=$PROJECT,GOOGLE_CLOUD_LOCATION=global
```

`--allow-unauthenticated` opens Cloud Run's own gate only. Cloud Run's gate cannot
check a Google sign-in from a phone app, so the service checks the Google token itself
on every request, and that is what keeps everyone else out.

The command prints the service's address. Put it in the app's `local.properties` as
`journal.backend.url`.

### Deploying on every push

After that first deploy, `.github/workflows/deploy-backend.yml` does it: a push to
`main` that changes anything under `backend/` (other than this README) runs the
tests, and if they pass, builds the image, pushes it and updates the service. The
service's settings (environment variables, its account) are left as they are; change
those with `gcloud run services update`. Runs are listed under the repository's
*Actions* tab.

GitHub signs in to Google Cloud without a stored key, through Workload Identity
Federation. Set up once per project (`REPO_ID` is the repository's number, from
`https://api.github.com/repos/<owner>/<repo>`):

```
NUMBER=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
DEPLOYER=github-deployer@$PROJECT.iam.gserviceaccount.com

gcloud services enable sts.googleapis.com iamcredentials.googleapis.com --project $PROJECT
gcloud iam service-accounts create github-deployer --project $PROJECT

# What the deployer may do: update Cloud Run services, push images, and run the
# service as its own account. Nothing else.
gcloud projects add-iam-policy-binding $PROJECT \
  --member serviceAccount:$DEPLOYER --role roles/run.developer
gcloud artifacts repositories add-iam-policy-binding cloud-run-source-deploy \
  --project $PROJECT --location asia-southeast2 \
  --member serviceAccount:$DEPLOYER --role roles/artifactregistry.writer
gcloud iam service-accounts add-iam-policy-binding \
  journal-backend@$PROJECT.iam.gserviceaccount.com --project $PROJECT \
  --member serviceAccount:$DEPLOYER --role roles/iam.serviceAccountUser

# Who may sign in as the deployer: this repository's main branch only.
gcloud iam workload-identity-pools create github --project $PROJECT --location global
gcloud iam workload-identity-pools providers create-oidc kilasan \
  --project $PROJECT --location global --workload-identity-pool github \
  --issuer-uri https://token.actions.githubusercontent.com \
  --attribute-mapping "google.subject=assertion.sub,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" \
  --attribute-condition "assertion.repository_id == '$REPO_ID' && assertion.ref == 'refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding $DEPLOYER --project $PROJECT \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/$NUMBER/locations/global/workloadIdentityPools/github/attribute.repository_id/$REPO_ID"
```

The project number, the pool and the deployer's name are written in the workflow
file. None of them is a secret. To stop automatic deploys, delete the workflow file
or the `github` pool.

## Run it on this machine

```
gcloud auth application-default login
ALLOWED_EMAIL=you@example.com GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com \
GOOGLE_CLOUD_PROJECT=your-project-id \
  .venv/bin/uvicorn app.main:create_app --factory --port 8080
```
