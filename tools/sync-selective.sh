#!/bin/sh
# POSIX sh version
# Usage:
#   ./sync-selective.sh --source /path/src --dest user@host:/path/dst --include-file include.txt [--exclude-file exclude.txt] [--delete]
#                       [--interval 0] [--bwlimit 0] [--dry-run] [--log /path/logfile] [--ssh 'ssh -i key -p 22'] [-h|--help]

# Exit on error and unset vars
set -eu

SOURCE=""
DEST=""
INCLUDE_FILE=""
EXCLUDE_FILE=""
INTERVAL="0"
DELETE_FLAG="0"
BWLIMIT="0"
DRY_RUN="0"
LOG_FILE=""
RSYNC_RSH="${RSYNC_RSH-}"

print_help() {
  echo "Sync selective rsync"
  echo
  echo "Required:"
  echo "  --source PATH              Source directory (local path, user@host:/path, or //server/share/path)"
  echo "  --dest DEST                Destination (local path or user@host:/path)"
  echo "  --include-file FILE        File with rsync include patterns"
  echo
  echo "Optional:"
  echo "  --exclude-file FILE        File with rsync exclude patterns"
  echo "  --delete                   Mirror deletions"
  echo "  --interval N               Seconds between syncs (0 = one-shot). Default: 0"
  echo "  --bwlimit KBPS             Limit bandwidth (0 = unlimited)"
  echo "  --dry-run                  Show changes only"
  echo "  --log FILE                 Append rsync output to FILE"
  echo "  --ssh 'CMD'                Custom remote shell for rsync, e.g. 'ssh -i key -p 22'"
  echo "  -h, --help                 Show this help"
  echo
  echo "Include file tips:"
  echo "  List directories with trailing slash to include contents (e.g., dirA/)."
  echo "  List files by relative path (e.g., dirB/file.txt)."
  echo "  Everything not explicitly included is excluded."
}

# Parse args (POSIX style)
while [ "$#" -gt 0 ]; do
  case "$1" in
    --source)        SOURCE="${2-}"; shift 2 ;;
    --dest)          DEST="${2-}"; shift 2 ;;
    --include-file)  INCLUDE_FILE="${2-}"; shift 2 ;;
    --exclude-file)  EXCLUDE_FILE="${2-}"; shift 2 ;;
    --interval)      INTERVAL="${2-0}"; shift 2 ;;
    --delete)        DELETE_FLAG="1"; shift ;;
    --bwlimit)       BWLIMIT="${2-0}"; shift 2 ;;
    --dry-run)       DRY_RUN="1"; shift ;;
    --log)           LOG_FILE="${2-}"; shift 2 ;;
    --ssh)           RSYNC_RSH="${2-}"; shift 2 ;;
    -h|--help)       print_help; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; print_help; exit 1 ;;
  esac
done

# Validate required files/commands
if [ -z "$SOURCE" ] || [ -z "$DEST" ] || [ -z "$INCLUDE_FILE" ]; then
  echo "Error: --source, --dest, and --include-file are required." >&2
  print_help
  exit 1
fi
if [ ! -f "$INCLUDE_FILE" ]; then
  echo "Error: Include file not found: $INCLUDE_FILE" >&2
  exit 1
fi
if [ -n "$EXCLUDE_FILE" ] && [ ! -f "$EXCLUDE_FILE" ]; then
  echo "Error: Exclude file not found: $EXCLUDE_FILE" >&2
  exit 1
fi
if ! command -v rsync >/dev/null 2>&1; then
  echo "Error: rsync not found in PATH" >&2
  exit 1
fi

# Detect if SOURCE is local dir; skip -d check for remote/UNC
is_local_dir_check_required=1
case "$SOURCE" in
  *:*)                    is_local_dir_check_required=0 ;;  # rsync remote: user@host:/path or host:/path
  //*)                    is_local_dir_check_required=0 ;;  # UNC-like path //server/share/...
  smb://*|nfs://*|ssh://*) is_local_dir_check_required=0 ;; # URL forms
esac

if [ "$is_local_dir_check_required" = 1 ]; then
  if [ ! -d "$SOURCE" ]; then
    echo "Error: Source directory not found: $SOURCE" >&2
    exit 1
  fi
fi

# Build rsync options
RSYNC_OPTS="-a -v --human-readable --itemize-changes --stats --partial --inplace --delete"

# Bandwidth limit
if [ "$BWLIMIT" -gt 0 ] 2>/dev/null; then
  RSYNC_OPTS="$RSYNC_OPTS --bwlimit=$BWLIMIT"
fi

# Delete handling
if [ "$DELETE_FLAG" = "1" ]; then
  RSYNC_OPTS="$RSYNC_OPTS --delete --delete-excluded"
fi

# Dry run
if [ "$DRY_RUN" = "1" ]; then
  RSYNC_OPTS="$RSYNC_OPTS --dry-run"
fi

# Include/exclude rules
if [ -n "$EXCLUDE_FILE" ]; then
  RSYNC_OPTS="$RSYNC_OPTS --exclude-from=$EXCLUDE_FILE"
fi
RSYNC_OPTS="$RSYNC_OPTS --include-from=$INCLUDE_FILE --exclude=*"

# Remote shell
if [ -n "$RSYNC_RSH" ]; then
  RSYNC_OPTS="$RSYNC_OPTS -e \"$RSYNC_RSH\""
fi

# Add trailing slash only for local dirs; rsync remote paths should not be modified blindly
SRC_PATH="$SOURCE"
if [ "$is_local_dir_check_required" = 1 ]; then
  case "$SOURCE" in
    */) : ;;
    *)  SRC_PATH="$SOURCE/" ;;
  esac
fi
DEST_PATH="$DEST"

timestamp() {
  date "+%Y-%m-%d %H:%M:%S"
}

run_once() {
  if [ -n "$LOG_FILE" ]; then
    {
      echo "$(timestamp) rsync start"
      # shellcheck disable=SC2086
      eval rsync $RSYNC_OPTS "\"$SRC_PATH\"" "\"$DEST_PATH\""
      echo "$(timestamp) rsync end"
    } 2>&1 | tee -a "$LOG_FILE"
  else
    echo "$(timestamp) rsync start"
    # shellcheck disable=SC2086
    eval rsync $RSYNC_OPTS "\"$SRC_PATH\"" "\"$DEST_PATH\""
    echo "$(timestamp) rsync end"
  fi
}

case "$INTERVAL" in
  ''|0) run_once; exit $? ;;
  *)    echo "Starting looped sync every $INTERVAL seconds. Press Ctrl-C to stop."
        while :; do
          run_once
          sleep "$INTERVAL"
        done
        ;;
esac
