#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${RGSCENTER_ENV_FILE:-${PROJECT_DIR}/.env}"
SQL_FILE="${1:-${SCRIPT_DIR}/000.rgscenter.tpl}"

for required_file in "$ENV_FILE" "$SQL_FILE"; do
  if [[ ! -f "$required_file" ]]; then
    echo "ERROR: Required file not found: $required_file" >&2
    exit 1
  fi
done

# Load all deployment settings from the project .env file.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

required_variables=(
  RGSCENTER_POSTGRES_DB_HOST
  RGSCENTER_POSTGRES_DB_PORT
  RGSCENTER_POSTGRES_DB_NAME
  RGSCENTER_POSTGRES_DB_ROOTUSER
  RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD
  RGSCENTER_DBNAME
  RGSCENTER_DEDICATED_USER_NAME
  RGSCENTER_DEDICATED_USER_PASSWORD
  RGSCENTER_DEDICATED_APP_USER_NAME
  RGSCENTER_DEDICATED_APP_USER_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "ERROR: $variable_name is not set in $ENV_FILE" >&2
    exit 1
  fi
done

export PGPASSWORD="$RGSCENTER_POSTGRES_DB_ROOTUSER_PASSWORD"

psql \
  -v ON_ERROR_STOP=1 \
  -v "dbname=$RGSCENTER_DBNAME" \
  -v "dedicated_user_name=$RGSCENTER_DEDICATED_USER_NAME" \
  -v "dedicated_user_password=$RGSCENTER_DEDICATED_USER_PASSWORD" \
  -v "dedicated_app_user_name=$RGSCENTER_DEDICATED_APP_USER_NAME" \
  -v "dedicated_app_user_password=$RGSCENTER_DEDICATED_APP_USER_PASSWORD" \
  -h "$RGSCENTER_POSTGRES_DB_HOST" \
  -p "$RGSCENTER_POSTGRES_DB_PORT" \
  -U "$RGSCENTER_POSTGRES_DB_ROOTUSER" \
  -d "$RGSCENTER_POSTGRES_DB_NAME" \
  -f "$SQL_FILE"

echo "RGSCenter database '$RGSCENTER_DBNAME' deployed successfully."
