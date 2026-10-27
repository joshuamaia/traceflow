# Extra CA certificates (optional)

Leave this folder empty in normal environments.

If your network uses TLS inspection (corporate proxy, antivirus HTTPS scanning), the Docker builds cannot download
Maven or npm dependencies because the intercepting certificate is not trusted inside the containers. Export the
intercepting root certificate in PEM format, save it here with a `.crt` extension and rebuild:

```bash
docker compose build --no-cache && docker compose up -d
```

Both Dockerfiles import every `*.crt` file of this folder at build time only. The files are ignored by git.
