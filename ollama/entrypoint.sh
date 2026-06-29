#!/bin/sh
# Start the Ollama server, then pull the configured model if it isn't already in
# the volume. The pull is a no-op once the weights are cached, so this is a
# one-time cost across restarts. The server runs in the foreground as PID-tracked
# child so the container's lifecycle follows it.
set -eu

MODEL="${LINKWEAVE_AUTOTAG_MODEL:-gemma2:2b}"

ollama serve &
SERVER_PID=$!

# Wait for the server to accept requests before pulling.
until ollama ps >/dev/null 2>&1; do
    sleep 1
done

echo "Ensuring model '${MODEL}' is available..."
ollama pull "${MODEL}"

wait "${SERVER_PID}"
