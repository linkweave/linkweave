# Companion container for local-LLM auto-tagging (FR-095).
# Wraps the official Ollama image with an entrypoint that pulls the model on
# first boot so the API never has to manage provisioning. Pulled weights persist
# in the ollama-models volume mounted at /root/.ollama.
FROM ollama/ollama:latest

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
